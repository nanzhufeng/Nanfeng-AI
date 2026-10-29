"""Fault injection and exact checks confined to a marked acceptance database."""
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
import sqlite3
import sys


TABLES = ["desktop_selected_conversation_sync", "workspaces", "workspace_exchange",
          "desktop_cloud_account_state", "sync_account_metadata", "sync_intents",
          "desktop_portable_personalization_v1"]


def marked(value):
    root = Path(value).resolve()
    assert root.is_relative_to(Path("D:/CodexTemp").resolve())
    assert root.name.startswith("nanfeng-ai-windows-acceptance.")
    marker = json.loads((root / ".nanfeng-windows-acceptance.json").read_text(encoding="utf-8-sig"))
    assert marker["identifier"] == "com.nanzhufeng.ai.desktop.windowsacceptance"
    for relative in ["workspace.sqlite3", "work-area/workspace.sqlite3"]:
        assert (root / relative).resolve().is_relative_to(root)
    return root


def connect(path, writable=False):
    return sqlite3.connect(path if writable else path.as_uri() + "?mode=ro", uri=not writable)


def snapshot(connection):
    return {table: connection.execute(f"SELECT * FROM {table} ORDER BY 1").fetchall() for table in TABLES}


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()).hexdigest()


def check_restored(root, connection):
    before = json.loads((root / "synthetic-restore-business-before.json").read_text(encoding="utf-8"))
    documents = json.loads((root / "synthetic-valid-cloud-documents.json").read_text(encoding="utf-8"))
    assert len(documents) == 13
    rows = connection.execute("SELECT document_id,workspace_id,conversation_id,remote_revision,payload_hash FROM desktop_selected_conversation_sync").fetchall()
    assert len(rows) == len(documents)
    by_id = {row[0]: row for row in rows}
    assert connection.execute("SELECT COUNT(*) FROM workspaces").fetchone()[0] == 13 + len(before["workspaces"])
    assert connection.execute("SELECT COUNT(*) FROM workspace_exchange").fetchone()[0] == 13 + len(before["workspace_exchange"])
    for table in ["workspaces", "workspace_exchange"]:
        after = json.loads(json.dumps(connection.execute(f"SELECT * FROM {table} ORDER BY 1").fetchall()))
        assert all(row in after for row in before[table]), "original local workspaces must remain exact"
    for document in documents:
        row = by_id[document["documentId"]]
        record = document["payload"]["records"][0]
        assert row[1] != "missing-workspace" and row[2:] == (record["id"], 1, document["payloadHash"])
        exchange = json.loads(connection.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (row[1],)).fetchone()[0])
        assert len(exchange["conversations"]) == 1
        conversation = exchange["conversations"][0]
        assert conversation["id"] == record["id"] and conversation["title"] == record["content"]["title"]
        assert len(conversation["messages"]) == 1
        message = conversation["messages"][0]
        node = record["content"]["nodes"][0]
        assert message["id"] == node["id"] and message["parentId"] == node["parentMessageId"]
        assert message["role"] == node["role"].lower() and message["revision"] == node["revision"]
        expected_time = datetime.fromtimestamp(node["createdAtEpochMs"] / 1000, timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
        assert message["createdAt"] == expected_time and message["delivery"] == node["deliveryState"]
        blocks = message["blocks"]
        assert [block["text"] for block in blocks if block["kind"] == "TEXT"] == node["text"]
    attempts = {}
    for table in ["desktop_ordinary_chat_attempts", "desktop_ocr_attempts_v1", "desktop_transcription_tasks", "desktop_conversation_title_attempts"]:
        attempts[table] = connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
    assert all(count == 0 for count in attempts.values())
    assert connection.execute("SELECT COUNT(*) FROM desktop_sync_jobs WHERE stage IN ('COMMITTING','UNKNOWN')").fetchone()[0] == 0
    return {"documentCount": 13, "recoveredWorkspaceCount": 13, "originalWorkspaceCount": len(before["workspaces"]), "restoredMessageCount": 13, "originalWorkspacesExact": True, "exactTextAndIdentity": True, "attempts": attempts,
            "snapshotSha256": digest(snapshot(connection))}


def run(root, mode, args):
    writable = mode in ["seed", "clear"]
    with connect(root / "workspace.sqlite3", writable) as connection:
        if mode == "seed":
            assert connection.execute("SELECT COUNT(*) FROM desktop_selected_conversation_sync").fetchone()[0] == 0
            assert connection.execute("SELECT COUNT(*) FROM workspaces WHERE id='missing-workspace'").fetchone()[0] == 0
            accounts = connection.execute("SELECT account_ref FROM desktop_cloud_account_state").fetchall()
            assert len(accounts) == 1
            connection.execute("INSERT INTO desktop_selected_conversation_sync VALUES(?,'missing-workspace',?,?,7,'old-payload','old-content',123)",
                               (accounts[0][0], args["conversationId"], args["documentId"]))
            connection.execute("CREATE TRIGGER synthetic_cloud_restore_atomic_reject BEFORE INSERT ON workspace_exchange BEGIN SELECT RAISE(ABORT,'synthetic workspace rejection'); END")
            connection.commit()
            baseline = snapshot(connection)
            (root / "synthetic-restore-business-before.json").write_text(json.dumps(baseline, ensure_ascii=False, indent=2), encoding="utf-8")
            result = {"selectedCount": 1, "recoveredWorkspaceCount": 0, "originalWorkspaceCount": len(baseline["workspaces"]), "snapshotSha256": digest(baseline)}
        elif mode == "assert-failed":
            before = json.loads((root / "synthetic-restore-business-before.json").read_text(encoding="utf-8"))
            after = json.loads(json.dumps(snapshot(connection)))
            assert after == before, "failed restore changed the old receipt or business state"
            result = {"businessExact": True, "selectedCount": 1, "recoveredWorkspaceCount": 0, "originalWorkspaceCount": len(before["workspaces"]), "snapshotSha256": digest(after)}
        elif mode == "clear":
            connection.execute("DROP TRIGGER synthetic_cloud_restore_atomic_reject")
            result = {"removedOnlySyntheticTrigger": True}
        elif mode == "assert-restored":
            result = check_restored(root, connection)
        else:
            raise ValueError("unknown synthetic operation")
        assert connection.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
        assert not connection.execute("PRAGMA foreign_key_check").fetchall()
    with connect(root / "work-area/workspace.sqlite3") as work:
        assert work.execute("SELECT COUNT(*) FROM desktop_selected_conversation_sync").fetchone()[0] == 0
        assert work.execute("SELECT COUNT(*) FROM workspaces").fetchone()[0] == 0
    result["workBusinessEmpty"] = True
    return result


if __name__ == "__main__":
    print(json.dumps(run(marked(sys.argv[1]), sys.argv[2], json.load(sys.stdin)), ensure_ascii=False))
