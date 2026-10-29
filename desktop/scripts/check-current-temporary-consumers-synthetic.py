"""Read-only inverse checks of marked synthetic native consumer receipts."""
import hashlib
import json
from pathlib import Path
import sqlite3
import sys
import zipfile

IDENTIFIER = "com.nanzhufeng.ai.desktop.windowsacceptance"
PRIVATE = ["TEMP_CONSUMER_PRIVATE_MESSAGE", "TEMP_CONSUMER_PRIVATE_DRAFT",
           "TEMP_CONSUMER_PRIVATE_BYTES", "TEMP_CONSUMER_PRIVATE_NAME",
           "ORDINARY_UNSENT_DO_NOT_EXPORT", "PRIVATE_LIBRARY_NOT_IN_SELECTED_SYNC",
           "PRIVATE_MEMORY_NOT_IN_SELECTED_SYNC"]


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def marked(value):
    path = Path(value).resolve()
    assert path.is_relative_to(Path("D:/CodexTemp").resolve())
    assert load(path / ".nanfeng-windows-acceptance.json")["identifier"] == IDENTIFIER
    return path


def connect(path):
    connection = sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)
    connection.execute("PRAGMA query_only=ON")
    return connection


def integrity(root):
    checked = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        with path.open("rb") as file:
            if file.read(16) != b"SQLite format 3\0":
                continue
        with connect(path) as connection:
            assert connection.execute("PRAGMA integrity_check").fetchall() == [("ok",)], path.name
            assert not connection.execute("PRAGMA foreign_key_check").fetchall(), path.name
        checked.append(str(path.relative_to(root)))
    return checked


def count(connection, table):
    exists = connection.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone()
    return connection.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0] if exists else None


def check(root, candidate):
    receipt = load(root / "temporary-consumer-native.json")
    assert receipt["candidateSha256"] == candidate["sha256"]
    assert Path(receipt["root"]).resolve() == root
    receiver = marked(receipt["receiverRoot"])
    assert receiver != root
    assert receipt["temporarySyncRequestDelta"] == 0
    assert receipt["sourceColdExact"] and receipt["receiverColdExact"]
    assert len(receipt["closes"]) == 4
    assert all(item["accepted"] and item["responding"] for item in receipt["closes"])
    temp = receipt["temporaryBefore"]
    with connect(root / "workspace.sqlite3") as source:
        rows = source.execute("SELECT recovery_json,updated_at_ms FROM desktop_temporary_recovery WHERE temporary_id=?", (temp["temporaryId"],)).fetchall()
        assert len(rows) == 1 and load_json(rows[0][0]) == temp
        assert rows[0][1] == temp["updatedAtMs"]
        attachments = source.execute("SELECT attachment_id,mime_type,display_name,byte_count,sha256 FROM desktop_temporary_attachments WHERE temporary_id=? ORDER BY attachment_id", (temp["temporaryId"],)).fetchall()
        expected = sorted((a["id"], a["mimeType"], a["displayName"], a["byteCount"], a["sha256"]) for a in temp["attachments"])
        assert attachments == expected
        for attachment in temp["attachments"]:
            data = (root / "assets" / attachment["sha256"]).read_bytes()
            assert len(data) == attachment["byteCount"]
            assert hashlib.sha256(data).hexdigest() == attachment["sha256"]
        source_exchange = load_json(source.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (receipt["workspaceId"],)).fetchone()[0])
        assert source_exchange == receipt["imported"]["exchange"]
        selected = source.execute("SELECT conversation_id,remote_revision,payload_hash FROM desktop_selected_conversation_sync").fetchall()
        assert len(selected) == 1 and selected[0][0] == receipt["conversationId"]
        assert selected[0][1] == 1 and len(selected[0][2]) == 64
        assert source.execute("SELECT COUNT(*) FROM desktop_sync_jobs WHERE conversation_id=?", (temp["temporaryId"],)).fetchone()[0] == 0
        ordinary_draft = source.execute("SELECT text FROM desktop_conversation_drafts_v1 WHERE workspace_id=? AND conversation_key='new-conversation-draft'", (receipt["workspaceId"],)).fetchone()
        if "searchExitVerified" in receipt:
            assert receipt["searchExitVerified"] and ordinary_draft == (PRIVATE[4],)
        no_attempts = {table: count(source, table) for table in ["desktop_ordinary_chat_attempts", "desktop_ocr_attempts_v1", "desktop_transcription_tasks", "desktop_conversation_title_attempts"]}
        assert all(value == 0 for value in no_attempts.values())
    with connect(receiver / "workspace.sqlite3") as restored:
        assert count(restored, "desktop_temporary_recovery") == 0
        assert count(restored, "desktop_temporary_attachments") == 0
        restored_exchange = load_json(restored.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (receipt["receiverWorkspaceId"],)).fetchone()[0])
        assert restored_exchange == receipt["receiver"]["exchange"]
        assert all(marker not in json.dumps(restored_exchange) for marker in PRIVATE)
        messages = restored_exchange["conversations"][0]["messages"]
        original = source_exchange["conversations"][0]["messages"]
        assert [(m["id"], m["parentId"], m["role"], m["createdAt"], [(b["kind"], b["text"]) for b in m["blocks"] if b["kind"] == "TEXT"]) for m in messages] == [(m["id"], m["parentId"], m["role"], m["createdAt"], [(b["kind"], b["text"]) for b in m["blocks"] if b["kind"] == "TEXT"]) for m in original]
        assert all(b["kind"] == "TEXT" for m in messages for b in m["blocks"])
        private_hashes = [a["sha256"] for a in temp["attachments"]]
        for table in ["workspace_assets", "desktop_attachment_assets"]:
            assert not restored.execute(f'SELECT sha256 FROM "{table}" WHERE sha256 IN ({",".join("?" for _ in private_hashes)})', private_hashes).fetchall()
        assert all(not (receiver / "assets" / value).exists() for value in private_hashes)
        assert all(count(restored, table) == 0 for table in no_attempts)
    with connect(root / "work-area/workspace.sqlite3") as work:
        assert count(work, "desktop_temporary_recovery") == 0
        assert count(work, "desktop_selected_conversation_sync") == 0
    with zipfile.ZipFile(receipt["exportPath"]) as package:
        for name in package.namelist():
            data = package.read(name)
            assert all(marker.encode() not in data for marker in PRIVATE[:5])
    documents = load(root / "synthetic-remote-documents.json")
    assert len(documents) == 1
    wire = json.dumps(documents)
    shared_hash = next(b["asset"]["sha256"] for b in source_exchange["conversations"][0]["messages"][0]["blocks"] if b["kind"] == "ASSET_REF")
    assert all(marker not in wire for marker in PRIVATE + [shared_hash])
    records = documents[0]["payload"]["records"]
    assert len([item for item in records if item["kind"] == "conversation"]) == 1
    requests = receipt["requests"]
    assert len([r for r in requests if r["path"] == "/rest/v1/rpc/nanfeng_sync_commit_document"]) == 1
    assert all(not r["privateMarkerPresent"] for r in requests)
    healthy = {"source": integrity(root), "receiver": integrity(receiver)}
    return {"root": str(root), "receiverRoot": str(receiver), "candidateSha256": candidate["sha256"],
            "sqliteCount": sum(map(len, healthy.values())), "sqliteFiles": healthy, "integrityOk": True,
            "temporaryRecordAndTimestampExact": True, "temporaryAttachmentMetadataAndBytesExact": True,
            "ordinaryExchangeExact": True, "receiverMessageIdentityParentRoleTimestampAndTextExact": True,
            "ordinaryDraftPreserved": ordinary_draft is not None, "temporarySyncJobs": 0,
            "sourceSelectedReceiptCount": 1, "selectedRevision": 1, "commitCount": 1,
            "wireKinds": [item["kind"] for item in records], "noAttemptTables": no_attempts,
            "pids": receipt["pids"], "realService": False}


load_json = json.loads
if __name__ == "__main__":
    candidate = load(Path(__file__).resolve().parents[1] / "output/windows-acceptance/current.json")
    assert hashlib.sha256(Path(candidate["executable"]).read_bytes()).hexdigest().upper() == candidate["sha256"]
    output = [check(marked(value), candidate) for value in sys.argv[1:]]
    print(json.dumps(output, ensure_ascii=False, indent=2))
