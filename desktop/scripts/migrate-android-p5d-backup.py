#!/usr/bin/env python3
"""Promote a verified Android P5-D local backup into one Desktop workspace.

This is an operator migration tool, not a replacement for the user-facing v2
exchange contract.  P5-D backups are deliberately large (they retain every
private attachment) and contain a Room snapshot rather than a portable
exchange package.  The tool therefore verifies the source manifest and every
asset while streaming, maps only documented semantic rows into the Desktop v1
workspace representation, and retains the original backup as the authority.

It is deliberately fail-closed: assets are written before the transaction,
but a workspace, indexes, provenance, and receipt become visible together in
one SQLite transaction.  It never overwrites an existing workspace.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import shutil
import sqlite3
import sys
import tempfile
import zipfile
from collections import defaultdict
from pathlib import Path


DATABASE_ENTRY = "database/nanfeng-ai.snapshot"
WORKSPACE_ID = "workspace-android-p5d-20260910"
WORKSPACE_TITLE = "Android 手机数据（2026-09-10）"
SOURCE_VERSION = "android-local-backup-v1-to-desktop-v1/2"


def die(message: str) -> "None":
    raise RuntimeError(message)


def sha256_path(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def android_content_hash(title: str, body: str) -> str:
    """Mirror the Android owner hash before adapting it to the v1 wire hash."""
    normalize = lambda value: " ".join(value.strip().lower().split())
    return hashlib.sha256(f"{normalize(title)}\n{normalize(body)}".encode()).hexdigest()


def instant(epoch_ms: int) -> str:
    return dt.datetime.fromtimestamp(epoch_ms / 1000, tz=dt.timezone.utc).isoformat().replace("+00:00", "Z")


def row_dicts(connection: sqlite3.Connection, query: str, values: tuple[object, ...] = ()) -> list[dict[str, object]]:
    return [dict(row) for row in connection.execute(query, values)]


def optional_non_negative_int(row: dict[str, object], key: str) -> int | None:
    value = row.get(key)
    if value is None:
        return None
    if not isinstance(value, int) or value < 0:
        die(f"手机回答归因字段无效：{key}")
    return value


def response_attribution_projection(row: dict[str, object]) -> dict[str, object]:
    """Map Android's content-free response facts onto the Desktop message owner."""
    model_id = str(row.get("modelId") or "").strip()
    provider_id = str(row.get("providerId") or "").strip()
    display_name = str(row.get("modelDisplayName") or "").strip()
    if not model_id or not provider_id:
        die("手机回答归因缺少 Provider 或模型")
    usage = {
        key: value for key in ("inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "reasoningTokens")
        if (value := optional_non_negative_int(row, key)) is not None
    }
    projection: dict[str, object] = {
        "actualModelId": model_id,
        "modelSnapshot": {"providerId": provider_id, "modelId": model_id, "displayName": display_name or model_id},
    }
    if usage:
        projection["usage"] = usage
    charge = optional_non_negative_int(row, "costTotalMicros")
    if charge is not None:
        currency = str(row.get("costCurrencyCode") or "").strip()
        cost_source = str(row.get("costSource") or "").strip()
        if len(currency) != 3 or not currency.isascii() or not currency.isupper() or cost_source not in {"PROVIDER_RESPONSE", "LOCAL_ESTIMATE"}:
            die("手机回答费用归因缺少有效币种或来源")
        projection.update({"chargeMicros": charge, "currencyCode": currency, "costSource": cost_source})
        price_version = str(row.get("costPriceVersion") or "").strip()
        if price_version:
            projection["costPriceVersion"] = price_version
    return projection


def response_attributions(connection: sqlite3.Connection, node_ids: set[str]) -> tuple[dict[str, dict[str, object]], dict[str, int]]:
    rows = row_dicts(connection, "SELECT * FROM assistant_response_model_attributions ORDER BY assistantMessageId, recordedAtEpochMs, attemptId")
    selected: dict[str, dict[str, object]] = {}
    orphan_rows = 0
    for row in rows:
        message_id = str(row["assistantMessageId"])
        if message_id not in node_ids:
            orphan_rows += 1
            continue
        # A reply may retain retries.  Android orders facts by completion time;
        # its latest completed attempt is the one represented by the visible node.
        selected[message_id] = response_attribution_projection(row)
    return selected, {
        "responseAttributionRowCount": len(rows),
        "responseAttributionMessageCount": len(selected),
        "responseAttributionOrphanRowCount": orphan_rows,
        "responseAttributionAmountCount": sum(1 for value in selected.values() if "chargeMicros" in value),
    }


def build_exchange(source_db: Path, source_hash: str) -> tuple[dict[str, object], dict[str, object], dict[str, dict[str, object]]]:
    connection = sqlite3.connect(f"file:{source_db}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        if connection.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            die("手机业务快照 SQLite 完整性检查失败")
        assets = {
            item["attachmentId"]: item
            for item in row_dicts(connection, "SELECT attachmentId, storageKey, mimeType, displayName, byteCount, sha256 FROM private_attachment_assets")
        }
        block_rows = row_dicts(connection, "SELECT * FROM message_content_blocks ORDER BY messageId, position")
        blocks_by_message: dict[str, list[dict[str, object]]] = defaultdict(list)
        for block in block_rows:
            blocks_by_message[str(block["messageId"])].append(block)

        node_rows = row_dicts(connection, "SELECT * FROM message_nodes ORDER BY conversationId, createdAtEpochMs, id")
        nodes_by_conversation: dict[str, list[dict[str, object]]] = defaultdict(list)
        node_ids: set[str] = set()
        for node in node_rows:
            nodes_by_conversation[str(node["conversationId"])].append(node)
            node_ids.add(str(node["id"]))
        attribution_by_message, attribution_counts = response_attributions(connection, node_ids)

        source_conversations = row_dicts(connection, "SELECT * FROM conversations WHERE surface='CHAT' AND deletedAtEpochMs IS NULL ORDER BY createdAtEpochMs, id")
        conversations: list[dict[str, object]] = []
        empty_conversation_ids: list[str] = []
        referenced_assets: dict[str, dict[str, object]] = {}
        for conversation in source_conversations:
            conversation_id = str(conversation["id"])
            nodes = nodes_by_conversation.get(conversation_id, [])
            leaf = conversation["currentLeafMessageId"]
            if not nodes or leaf is None or str(leaf) not in {str(node["id"]) for node in nodes}:
                empty_conversation_ids.append(conversation_id)
                continue
            messages: list[dict[str, object]] = []
            for node in nodes:
                message_blocks: list[dict[str, object]] = []
                for block in blocks_by_message.get(str(node["id"]), []):
                    kind = str(block["kind"])
                    if kind == "TEXT":
                        message_blocks.append({"kind": "TEXT", "ordinal": int(block["position"]), "text": str(block["textContent"] or "")})
                    elif kind == "ATTACHMENT":
                        attachment_id = str(block["attachmentId"] or "")
                        asset = assets.get(attachment_id)
                        if asset is None:
                            die(f"消息附件缺少 owner 元数据：{attachment_id}")
                        sha256 = str(asset["sha256"])
                        if sha256 != str(block["sha256"]):
                            die(f"消息附件哈希与 owner 不一致：{attachment_id}")
                        mapped = {
                            "id": attachment_id,
                            "entry": f"assets/{sha256}",
                            "mimeType": str(asset["mimeType"]),
                            "displayName": str(asset["displayName"] or "未命名附件"),
                            "byteCount": int(asset["byteCount"]),
                            "sha256": sha256,
                            "classification": "HIGH_SENSITIVE",
                        }
                        referenced_assets[sha256] = mapped
                        message_blocks.append({"kind": "ASSET_REF", "ordinal": int(block["position"]), "asset": mapped})
                    else:
                        die(f"不支持的消息块类型：{kind}")
                message = {
                    "id": str(node["id"]), "parentId": node["parentMessageId"],
                    "ordinal": int(node["siblingPosition"]), "role": str(node["role"]).lower(),
                    "delivery": str(node["deliveryState"]), "revision": int(node["revision"]),
                    "createdAt": instant(int(node["createdAtEpochMs"])), "blocks": message_blocks,
                }
                if str(node["role"]).lower() == "assistant":
                    message.update(attribution_by_message.get(str(node["id"]), {}))
                messages.append(message)
            conversations.append({
                "id": conversation_id, "projectId": None, "title": str(conversation["title"]),
                "currentLeafId": str(leaf), "pinned": conversation["pinnedAtEpochMs"] is not None,
                "archived": conversation["archivedAtEpochMs"] is not None, "revision": int(conversation["revision"]),
                "createdAt": instant(int(conversation["createdAtEpochMs"])), "updatedAt": instant(int(conversation["updatedAtEpochMs"])),
                "messages": messages,
            })

        knowledge: list[dict[str, object]] = []
        for item in row_dicts(connection, "SELECT * FROM knowledge_items WHERE deletedAtEpochMs IS NULL ORDER BY createdAtEpochMs, id"):
            tags = [str(row["tag"]) for row in row_dicts(connection, "SELECT tag FROM knowledge_item_tags WHERE knowledgeId=? ORDER BY tag", (str(item["id"]),))]
            scope = "GLOBAL"
            project_id = None
            scope_row = connection.execute("SELECT projectId FROM knowledge_project_scopes WHERE knowledgeId=?", (str(item["id"]),)).fetchone()
            if scope_row and scope_row[0]:
                scope, project_id = "PROJECT", str(scope_row[0])
            body = str(item["body"])
            if android_content_hash(str(item["title"]), body) != str(item["contentHash"]):
                die(f"知识正文哈希不一致：{item['id']}")
            # v1 uses a body-only hash; the Android owner hash above has already authenticated
            # title and body under the source contract.
            knowledge.append({"id": str(item["id"]), "title": str(item["title"]), "body": body, "tags": tags, "scope": scope, "projectId": project_id, "status": str(item["status"]), "revision": 1, "contentHash": hashlib.sha256(body.encode()).hexdigest(), "createdAt": instant(int(item["createdAtEpochMs"])), "updatedAt": instant(int(item["updatedAtEpochMs"])), "classification": "HIGH_SENSITIVE"})

        memories: list[dict[str, object]] = []
        for item in row_dicts(connection, "SELECT * FROM memories WHERE deletedAtEpochMs IS NULL ORDER BY createdAtEpochMs, id"):
            body = str(item["body"])
            if android_content_hash(str(item["title"]), body) != str(item["contentHash"]):
                die(f"记忆正文哈希不一致：{item['id']}")
            scope = str(item["scopeKind"])
            scope_id = item["projectId"] or item["conversationId"] or "global"
            memories.append({"id": str(item["id"]), "body": body, "scope": scope, "scopeId": str(scope_id), "status": str(item["status"]), "revision": 1, "contentHash": hashlib.sha256(body.encode()).hexdigest(), "createdAt": instant(int(item["createdAtEpochMs"])), "updatedAt": instant(int(item["updatedAtEpochMs"])), "classification": "HIGH_SENSITIVE"})

        created_at = instant(min(int(item["createdAtEpochMs"]) for item in source_conversations))
        exchange: dict[str, object] = {
            "format": "nfai.exchange", "version": 1,
            "export": {"id": "export-android-p5d-20260910", "createdAt": created_at, "origin": {"platform": "ANDROID", "appVersion": "0.3.0-p10j"}, "semanticHash": "", "sensitivity": "HIGH_SENSITIVE"},
            "projects": [], "conversations": conversations, "knowledge": knowledge, "memory": memories, "relations": [],
            # P5-D intentionally excludes preference storage.  These are protocol-safe defaults,
            # not a claim that Android appearance/personalization settings were recovered.
            "settings": {"uiLanguage": "zh-CN", "theme": "SYSTEM"},
        }
        semantic_copy = json.loads(json.dumps(exchange))
        semantic_copy["export"].pop("semanticHash")
        exchange["export"]["semanticHash"] = hashlib.sha256(canonical(semantic_copy).encode()).hexdigest()
        receipt = {
            "migrationVersion": SOURCE_VERSION, "sourceBackupSha256": source_hash,
            "sourceDatabaseEntry": DATABASE_ENTRY, "workspaceId": WORKSPACE_ID,
            "semanticHash": exchange["export"]["semanticHash"], "conversationCount": len(conversations),
            "emptyConversationCount": len(empty_conversation_ids), "emptyConversationIds": empty_conversation_ids,
            "knowledgeCount": len(knowledge), "memoryCount": len(memories),
            "allPrivateAssetCount": len(assets), "referencedAssetCount": len(referenced_assets),
            **attribution_counts,
            "settings": {"state": "NOT_IN_P5D_BACKUP", "reason": "Android P5-D local backup excludes SharedPreferences and encrypted provider credentials."},
        }
        return exchange, receipt, {str(item["sha256"]): item for item in assets.values()}
    finally:
        connection.close()


def semantic_hash(exchange: dict[str, object]) -> str:
    semantic_copy = json.loads(json.dumps(exchange))
    semantic_copy["export"].pop("semanticHash", None)
    return hashlib.sha256(canonical(semantic_copy).encode()).hexdigest()


def restore_usage_ledger(desktop_root: Path, exchange: dict[str, object]) -> dict[str, int]:
    """Append Android response facts once, preserving the historical cost source."""
    ledger_path = desktop_root / "usage-ledger" / "usage-ledger-v1.sqlite3"
    ledger_path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(ledger_path)
    try:
        connection.execute("CREATE TABLE IF NOT EXISTS usage_ledger_entries (entry_id TEXT PRIMARY KEY NOT NULL, replay_token TEXT UNIQUE NOT NULL, execution_id TEXT NOT NULL, conversation_id TEXT NOT NULL, branch_leaf_message_id TEXT NOT NULL, invocation_id TEXT NOT NULL, attempt_id TEXT NOT NULL, kind TEXT NOT NULL, fact_grade TEXT NOT NULL, requested_model_id TEXT NOT NULL, actual_model_id TEXT, input_tokens INTEGER, output_tokens INTEGER, cached_input_tokens INTEGER, charge_micros INTEGER, budget_micros INTEGER, adjustment_micros INTEGER, currency_code TEXT, cost_source TEXT, reconciliation_fingerprint TEXT, reconciles_entry_id TEXT, source TEXT NOT NULL, occurred_at_ms INTEGER NOT NULL)")
        columns = {str(row[1]) for row in connection.execute("PRAGMA table_info(usage_ledger_entries)")}
        if "cost_source" not in columns:
            connection.execute("ALTER TABLE usage_ledger_entries ADD COLUMN cost_source TEXT")
        connection.execute("BEGIN IMMEDIATE")
        appended = replayed = 0
        for conversation in exchange["conversations"]:
            messages = conversation["messages"]
            leaves = {str(message["parentId"]) for message in messages if message.get("parentId") is not None}
            default_leaf = str(conversation["currentLeafId"])
            for message in messages:
                if message.get("role") != "assistant" or not any(key in message for key in ("chargeMicros", "usage")):
                    continue
                usage = message.get("usage") if isinstance(message.get("usage"), dict) else {}
                input_tokens = usage.get("inputTokens")
                output_tokens = usage.get("outputTokens")
                cached_tokens = usage.get("cachedInputTokens")
                charge = message.get("chargeMicros")
                if not any(value is not None for value in (input_tokens, output_tokens, cached_tokens, charge)):
                    continue
                identity = hashlib.sha256(f"{message['id']}:{message.get('actualModelId', '')}".encode()).hexdigest()
                entry = (
                    f"usage-import-android-p5d-{identity}", f"android-p5d-attribution-{identity}",
                    f"android-p5d-execution-{identity}", str(conversation["id"]),
                    str(message["id"]) if str(message["id"]) not in leaves else default_leaf,
                    f"android-p5d-invocation-{identity}", f"android-p5d-attempt-{identity}",
                    "FINAL_MEASURED", "PROVIDER_REPORTED", str(message.get("actualModelId") or ""),
                    str(message.get("actualModelId") or ""), input_tokens, output_tokens, cached_tokens,
                    charge, None, None, message.get("currencyCode"), message.get("costSource"),
                    None, None, "CROSS_PLATFORM_IMPORT", int(dt.datetime.fromisoformat(str(message["createdAt"]).replace("Z", "+00:00")).timestamp() * 1000),
                )
                if not entry[9] or (charge is not None and (entry[17] is None or entry[18] not in {"PROVIDER_RESPONSE", "LOCAL_ESTIMATE"})):
                    die("Desktop 用量账本拒绝不完整的 Android 回答归因")
                current = connection.execute("SELECT entry_id,replay_token,execution_id,conversation_id,branch_leaf_message_id,invocation_id,attempt_id,kind,fact_grade,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,budget_micros,adjustment_micros,currency_code,cost_source,reconciliation_fingerprint,reconciles_entry_id,source,occurred_at_ms FROM usage_ledger_entries WHERE entry_id=?", (entry[0],)).fetchone()
                if current is not None:
                    if tuple(current) != entry:
                        die("现有 Desktop 用量账本与手机回答归因冲突；拒绝覆盖")
                    replayed += 1
                    continue
                connection.execute("INSERT INTO usage_ledger_entries (entry_id,replay_token,execution_id,conversation_id,branch_leaf_message_id,invocation_id,attempt_id,kind,fact_grade,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,budget_micros,adjustment_micros,currency_code,cost_source,reconciliation_fingerprint,reconciles_entry_id,source,occurred_at_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", entry)
                appended += 1
        connection.commit()
        return {"usageLedgerAppendedCount": appended, "usageLedgerReplayedCount": replayed}
    except BaseException:
        connection.rollback()
        raise
    finally:
        connection.close()


def restore_response_attributions(desktop_db: Path, desktop_root: Path, exchange: dict[str, object], source_hash: str) -> dict[str, int]:
    package_hash = f"android-p5d-{source_hash}"
    expected_messages = {
        str(message["id"]): {key: message[key] for key in ("actualModelId", "modelSnapshot", "usage", "chargeMicros", "currencyCode", "costSource", "costPriceVersion") if key in message}
        for conversation in exchange["conversations"] for message in conversation["messages"]
        if message.get("role") == "assistant" and any(key in message for key in ("actualModelId", "usage", "chargeMicros"))
    }
    ledger_receipt = restore_usage_ledger(desktop_root, exchange)
    connection = sqlite3.connect(desktop_db)
    try:
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("BEGIN IMMEDIATE")
        workspace = connection.execute("SELECT package_hash FROM workspaces WHERE id=?", (WORKSPACE_ID,)).fetchone()
        if workspace is None or workspace[0] != package_hash:
            die("现有 Desktop 工作区不匹配这份手机备份；拒绝补写")
        stored_row = connection.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (WORKSPACE_ID,)).fetchone()
        if stored_row is None:
            die("现有 Desktop 工作区缺少交换数据；拒绝补写")
        stored = json.loads(str(stored_row[0]))
        stored_messages = {str(message["id"]): message for conversation in stored.get("conversations", []) for message in conversation.get("messages", [])}
        recovered = replayed = 0
        for message_id, expected in expected_messages.items():
            current = stored_messages.get(message_id)
            if current is None or str(current.get("role", "")).lower() != "assistant":
                die("Desktop 消息树与手机备份不一致；拒绝补写")
            changed = False
            for key, value in expected.items():
                if key == "usage" and isinstance(value, dict):
                    existing_usage = current.get("usage")
                    if existing_usage is None:
                        current["usage"] = value
                        changed = True
                    elif not isinstance(existing_usage, dict):
                        die("Desktop 消息费用归因类型冲突；拒绝覆盖")
                    else:
                        for usage_key, usage_value in value.items():
                            if usage_key not in existing_usage or existing_usage[usage_key] is None:
                                existing_usage[usage_key] = usage_value
                                changed = True
                            elif existing_usage[usage_key] != usage_value:
                                die("Desktop 消息费用归因与手机备份冲突；拒绝覆盖")
                elif key not in current or current[key] is None:
                    current[key] = value
                    changed = True
                elif current[key] != value:
                    die("Desktop 消息费用归因与手机备份冲突；拒绝覆盖")
            if changed:
                recovered += 1
            else:
                replayed += 1
        stored["export"]["semanticHash"] = semantic_hash(stored)
        current_hash = str(stored["export"]["semanticHash"])
        connection.execute("UPDATE workspace_exchange SET exchange_json=? WHERE workspace_id=?", (canonical(stored), WORKSPACE_ID))
        connection.execute("UPDATE workspaces SET semantic_hash=? WHERE id=?", (current_hash, WORKSPACE_ID))
        connection.execute("UPDATE import_journal SET semantic_hash=? WHERE package_hash=? AND workspace_id=?", (current_hash, package_hash, WORKSPACE_ID))
        connection.execute("INSERT INTO desktop_local_search_index_state(workspace_id,index_version,semantic_hash) VALUES(?,?,?) ON CONFLICT(workspace_id) DO UPDATE SET semantic_hash=excluded.semantic_hash", (WORKSPACE_ID, 2, current_hash))
        connection.commit()
        return {"restoredResponseMessageCount": recovered, "replayedResponseMessageCount": replayed, **ledger_receipt}
    except BaseException:
        connection.rollback()
        raise
    finally:
        connection.close()


def extract_and_verify_assets(backup: Path, manifest: dict[str, object], assets: dict[str, dict[str, object]], desktop_root: Path) -> int:
    manifest_entries = {str(item["path"]): item for item in manifest["entries"]}
    root_assets = desktop_root / "assets"
    root_assets.mkdir(parents=True, exist_ok=True)
    copied = 0
    with zipfile.ZipFile(backup) as source:
        for sha256, asset in sorted(assets.items()):
            source_path = f"assets/{asset['storageKey']}"
            listed = manifest_entries.get(source_path)
            if listed is None or str(listed["sha256"]) != sha256 or int(listed["bytes"]) != int(asset["byteCount"]):
                die(f"附件 manifest 与数据库不一致：{asset['attachmentId']}")
            target = root_assets / sha256
            if target.exists():
                if target.stat().st_size == int(asset["byteCount"]) and sha256_path(target) == sha256:
                    continue
                die(f"Desktop 已有同 hash 附件但内容不一致：{sha256}")
            part = target.with_suffix(".part")
            digest = hashlib.sha256()
            byte_count = 0
            with source.open(source_path) as input_stream, part.open("xb") as output_stream:
                for chunk in iter(lambda: input_stream.read(1024 * 1024), b""):
                    digest.update(chunk); byte_count += len(chunk); output_stream.write(chunk)
                output_stream.flush(); os.fsync(output_stream.fileno())
            if byte_count != int(asset["byteCount"]) or digest.hexdigest() != sha256:
                part.unlink(missing_ok=True)
                die(f"附件内容校验失败：{sha256}")
            os.replace(part, target)
            copied += 1
    return copied


def normalize_search(value: str) -> str:
    return " ".join(value.split()).lower()


def attachment_file_type(mime_type: str, display_name: str) -> str:
    extension = display_name.rsplit(".", 1)[-1].lower() if "." in display_name else ""
    if mime_type == "text/markdown" or extension in {"md", "markdown"}: return "markdown"
    if mime_type == "application/pdf" or extension == "pdf": return "pdf"
    if mime_type in {"application/zip", "application/x-zip-compressed"} or extension == "zip": return "zip"
    if mime_type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" or extension == "docx": return "docx"
    if mime_type == "text/plain" or extension == "txt": return "txt"
    if mime_type == "application/json" or extension == "json": return "json"
    return "other"


def branch_leaf(messages: list[dict[str, object]], target_id: str) -> str | None:
    by_id = {str(message["id"]): message for message in messages}
    parents = {str(message["parentId"]) for message in messages if message["parentId"] is not None}
    leaves = sorted((message for message in messages if str(message["id"]) not in parents), key=lambda message: (str(message["createdAt"]), str(message["id"])), reverse=True)
    for leaf in leaves:
        cursor: str | None = str(leaf["id"])
        for _ in range(len(messages) + 1):
            if cursor == target_id: return str(leaf["id"])
            cursor = str(by_id[cursor]["parentId"]) if cursor and by_id.get(cursor, {}).get("parentId") is not None else None
    return None


def rebuild_search_index(connection: sqlite3.Connection, exchange: dict[str, object]) -> None:
    """Keep the desktop full-text / attachment search projection in sync with the exchange."""
    workspace = WORKSPACE_ID
    connection.execute("DELETE FROM desktop_local_search_index WHERE workspace_id=?", (workspace,))
    for conversation in exchange["conversations"]:
        conversation_id, title = str(conversation["id"]), str(conversation["title"])
        revision, archived, updated_at = int(conversation["revision"]), int(bool(conversation["archived"])), str(conversation["updatedAt"])
        normalized_title = normalize_search(title)
        if normalized_title:
            connection.execute("INSERT INTO desktop_local_search_index(workspace_id,entry_id,conversation_id,message_id,attachment_id,content_kind,title,normalized_text,snippet,timestamp,mime_type,display_name,file_type,byte_count,branch_leaf_id,source_label,archived,conversation_revision,title_match) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (workspace, f"{conversation_id}:title", conversation_id, None, None, "TITLE", title, normalized_title, title, updated_at, None, None, None, len(title.encode()), None, None, archived, revision, 1))
        messages = conversation["messages"]
        for message in messages:
            if message["role"] not in {"user", "assistant"}: continue
            message_id, message_time = str(message["id"]), str(message["createdAt"])
            leaf = branch_leaf(messages, message_id)
            text_blocks = [str(block["text"]) for block in message["blocks"] if block["kind"] == "TEXT"]
            full_text, normalized = "\n".join(text_blocks), normalize_search("\n".join(text_blocks))
            if normalized:
                connection.execute("INSERT INTO desktop_local_search_index(workspace_id,entry_id,conversation_id,message_id,attachment_id,content_kind,title,normalized_text,snippet,timestamp,mime_type,display_name,file_type,byte_count,branch_leaf_id,source_label,archived,conversation_revision,title_match) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (workspace, f"{conversation_id}:{message_id}:text", conversation_id, message_id, None, "TEXT", title, normalized, normalize_search(full_text)[:240], message_time, None, None, None, len(full_text.encode()), leaf, None, archived, revision, 0))
            for position, block in enumerate(message["blocks"]):
                if block["kind"] != "ASSET_REF": continue
                asset = block["asset"]; mime_type, display_name = str(asset["mimeType"]), str(asset["displayName"])
                kind = "IMAGE" if mime_type.startswith("image/") else "VIDEO" if mime_type.startswith("video/") else "AUDIO" if mime_type.startswith("audio/") else "FILE"
                connection.execute("INSERT INTO desktop_local_search_index(workspace_id,entry_id,conversation_id,message_id,attachment_id,content_kind,title,normalized_text,snippet,timestamp,mime_type,display_name,file_type,byte_count,branch_leaf_id,source_label,archived,conversation_revision,title_match) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (workspace, f"{conversation_id}:{message_id}:attachment:{asset['id']}:{position}", conversation_id, message_id, str(asset["id"]), kind, title, normalize_search(f"{display_name} {mime_type}"), f"{display_name} · {normalize_search(full_text)[:160]}", message_time, mime_type, display_name, attachment_file_type(mime_type, display_name), int(asset["byteCount"]), leaf, None, archived, revision, 0))
    connection.execute("INSERT INTO desktop_local_search_index_state(workspace_id,index_version,semantic_hash) VALUES(?,?,?) ON CONFLICT(workspace_id) DO UPDATE SET index_version=excluded.index_version,semantic_hash=excluded.semantic_hash", (workspace, 2, str(exchange["export"]["semanticHash"])))


def persist_workspace(desktop_db: Path, exchange: dict[str, object], receipt: dict[str, object], assets: dict[str, dict[str, object]], source_hash: str) -> None:
    package_hash = f"android-p5d-{source_hash}"
    connection = sqlite3.connect(desktop_db)
    try:
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("BEGIN IMMEDIATE")
        if connection.execute("SELECT 1 FROM workspaces WHERE id=?", (WORKSPACE_ID,)).fetchone():
            die("该 Android 备份已经迁移为可见 Desktop 工作区；不会覆盖")
        now = dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")
        semantic_hash = str(exchange["export"]["semanticHash"])
        connection.execute("INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?,?,?,?,?)", (WORKSPACE_ID, WORKSPACE_TITLE, semantic_hash, package_hash, now))
        connection.execute("INSERT INTO workspace_exchange(workspace_id,exchange_json) VALUES(?,?)", (WORKSPACE_ID, canonical(exchange)))
        for sha256, asset in assets.items():
            connection.execute("INSERT INTO workspace_assets(workspace_id,sha256,byte_count) VALUES(?,?,?)", (WORKSPACE_ID, sha256, int(asset["byteCount"])))
        # Attachments visible in conversation content receive Desktop preview metadata. Assets
        # not presently referenced are still copied and listed in the external receipt.
        referenced: dict[str, dict[str, object]] = {}
        for conversation in exchange["conversations"]:
            for message in conversation["messages"]:
                for block in message["blocks"]:
                    if block["kind"] == "ASSET_REF":
                        referenced[block["asset"]["sha256"]] = block["asset"]
        created_ms = int(dt.datetime.now(dt.timezone.utc).timestamp() * 1000)
        for sha256, asset in referenced.items():
            connection.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?,?,?,?,?,1) ON CONFLICT(sha256) DO UPDATE SET reference_count=desktop_attachment_assets.reference_count+1,last_referenced_at_ms=excluded.last_referenced_at_ms", (sha256, int(asset["byteCount"]), created_ms, created_ms, created_ms))
            connection.execute("INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?,?,?,?,?,?,?)", (WORKSPACE_ID, asset["id"], asset["mimeType"], asset["displayName"], int(asset["byteCount"]), sha256, now))
        rebuild_search_index(connection, exchange)
        for entity, values in (("conversation", exchange["conversations"]), ("knowledge", exchange["knowledge"]), ("memory", exchange["memory"])):
            for item in values:
                connection.execute("INSERT INTO object_provenance(workspace_id,entity,object_id,source,origin_semantic_hash,imported_revision,edited_revision) VALUES(?,?,?,?,?,?,NULL)", (WORKSPACE_ID, entity, item["id"], "IMPORTED", semantic_hash, int(item["revision"])))
        connection.execute("INSERT INTO import_journal(package_hash,workspace_id,semantic_hash,committed_at) VALUES(?,?,?,?)", (package_hash, WORKSPACE_ID, semantic_hash, now))
        connection.commit()
    except BaseException:
        connection.rollback()
        raise
    finally:
        connection.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--desktop-root", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--rebuild-search-index-only", action="store_true")
    parser.add_argument("--verify-response-attributions-only", action="store_true")
    parser.add_argument("--restore-response-attributions-only", action="store_true")
    args = parser.parse_args()
    backup, desktop_root = args.backup.resolve(), args.desktop_root.resolve()
    desktop_db = desktop_root / "workspace.sqlite3"
    if not backup.is_file() or not desktop_db.is_file(): die("备份或 Desktop 数据库不存在")
    source_hash = sha256_path(backup)
    selected_modes = sum((args.rebuild_search_index_only, args.verify_response_attributions_only, args.restore_response_attributions_only))
    if selected_modes > 1:
        die("只能选择一种迁移维护模式")
    with zipfile.ZipFile(backup) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        if manifest.get("format") != "nanfeng-ai.local-backup" or manifest.get("version") != 1: die("不是受支持的 Android P5-D 备份")
        if DATABASE_ENTRY not in archive.namelist(): die("备份缺少业务快照")
        temp_root = Path(tempfile.mkdtemp(prefix="nanfeng-ai-p5d-", dir="/tmp"))
        try:
            snapshot = temp_root / "android.sqlite"
            with archive.open(DATABASE_ENTRY) as source, snapshot.open("xb") as destination: shutil.copyfileobj(source, destination, 1024 * 1024)
            exchange, receipt, assets = build_exchange(snapshot, source_hash)
        finally:
            shutil.rmtree(temp_root, ignore_errors=True)
    if args.verify_response_attributions_only:
        print(json.dumps({"workspaceId": WORKSPACE_ID, "semanticHash": exchange["export"]["semanticHash"], **{key: receipt[key] for key in receipt if key.startswith("responseAttribution")}}, ensure_ascii=False, sort_keys=True))
        return 0
    if args.restore_response_attributions_only:
        restored = restore_response_attributions(desktop_db, desktop_root, exchange, source_hash)
        output = {"workspaceId": WORKSPACE_ID, "semanticHash": exchange["export"]["semanticHash"], **{key: receipt[key] for key in receipt if key.startswith("responseAttribution")}, **restored, "completedAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")}
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps(output, ensure_ascii=False, sort_keys=True))
        return 0
    if args.rebuild_search_index_only:
        connection = sqlite3.connect(desktop_db)
        try:
            connection.execute("PRAGMA foreign_keys=ON")
            stored = connection.execute("SELECT semantic_hash FROM workspaces WHERE id=?", (WORKSPACE_ID,)).fetchone()
            if stored is None or stored[0] != exchange["export"]["semanticHash"]:
                die("现有 Desktop 工作区不匹配该手机备份；拒绝重建索引")
            connection.execute("BEGIN IMMEDIATE")
            rebuild_search_index(connection, exchange)
            connection.commit()
        except BaseException:
            connection.rollback()
            raise
        finally:
            connection.close()
        print(json.dumps({"workspaceId": WORKSPACE_ID, "semanticHash": exchange["export"]["semanticHash"], "indexRebuilt": True}, ensure_ascii=False, sort_keys=True))
        return 0
    copied = extract_and_verify_assets(backup, manifest, assets, desktop_root)
    persist_workspace(desktop_db, exchange, receipt, assets, source_hash)
    receipt["copiedAssetCount"] = copied
    receipt["completedAt"] = dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(receipt, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"迁移失败：{error}", file=sys.stderr)
        raise SystemExit(1)
