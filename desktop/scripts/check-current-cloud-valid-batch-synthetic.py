"""Read-only SQL inspection; baseline writes only a synthetic evidence JSON file."""
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location("fault", Path(__file__).with_name("fault-cloud-restore-synthetic.py"))
fault = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fault)


def original_rows(connection):
    return {table: connection.execute(f"SELECT * FROM {table} ORDER BY 1").fetchall()
            for table in ["workspaces", "workspace_exchange", "desktop_selected_conversation_sync"]}


def check(root, mode):
    with fault.connect(root / "workspace.sqlite3") as connection, fault.connect(root / "work-area/workspace.sqlite3") as work:
        assert connection.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
        assert not connection.execute("PRAGMA foreign_key_check").fetchall()
        assert work.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
        assert not work.execute("PRAGMA foreign_key_check").fetchall()
        baseline_file = root / "synthetic-valid-batch-baseline.json"
        if mode == "baseline":
            assert not baseline_file.exists(), "preserve the original baseline"
            baseline = {"chat": original_rows(connection), "work": original_rows(work)}
            assert not baseline["chat"]["desktop_selected_conversation_sync"]
            baseline_file.write_text(json.dumps(baseline, ensure_ascii=False), encoding="utf-8")
            return {"originalChatWorkspaceCount": len(baseline["chat"]["workspaces"]), "originalWorkWorkspaceCount": len(baseline["work"]["workspaces"])}
        assert mode in ["verify", "verify-partial"]
        count = json.loads((root / "synthetic-current-batch.json").read_text(encoding="utf-8"))["count"]
        assert count in [256, 2048, 10000]
        documents = json.loads((root / f"synthetic-valid-cloud-documents-{count}.json").read_text(encoding="utf-8"))
        assert len(documents) == count
        baseline = json.loads(baseline_file.read_text(encoding="utf-8"))
        assert json.loads(json.dumps(original_rows(work))) == baseline["work"], "WORK business rows changed"
        selected = connection.execute("SELECT document_id,workspace_id,conversation_id,remote_revision,payload_hash FROM desktop_selected_conversation_sync").fetchall()
        by_id = {row[0]: row for row in selected}
        expected_count = len(selected) if mode == "verify-partial" else count
        assert 0 < expected_count <= count
        for table in ["workspaces", "workspace_exchange"]:
            after = json.loads(json.dumps(connection.execute(f"SELECT * FROM {table} ORDER BY 1").fetchall()))
            assert len(after) == expected_count + len(baseline["chat"][table])
            assert all(row in after for row in baseline["chat"][table]), "original local workspace changed"
        exchanges = dict(connection.execute("SELECT workspace_id,exchange_json FROM workspace_exchange"))
        assert len(selected) == len(by_id) == expected_count
        assert set(by_id) <= {document["documentId"] for document in documents}
        for document in documents:
            if document["documentId"] not in by_id:
                continue
            row = by_id[document["documentId"]]
            record = document["payload"]["records"][0]
            assert row[2:] == (record["id"], 1, document["payloadHash"])
            exchange = json.loads(exchanges[row[1]])
            assert len(exchange["conversations"]) == 1
            conversation = exchange["conversations"][0]
            assert conversation["id"] == record["id"] and conversation["title"] == record["content"]["title"]
            assert len(conversation["messages"]) == 1
            message = conversation["messages"][0]
            node = record["content"]["nodes"][0]
            expected_time = datetime.fromtimestamp(node["createdAtEpochMs"] / 1000, timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
            assert (message["id"], message["parentId"], message["role"], message["revision"], message["createdAt"], message["delivery"]) == (
                node["id"], node["parentMessageId"], node["role"].lower(), node["revision"], expected_time, node["deliveryState"])
            assert [block["text"] for block in message["blocks"] if block["kind"] == "TEXT"] == node["text"]
        attempts = {table: connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] for table in
                    ["desktop_ordinary_chat_attempts", "desktop_ocr_attempts_v1", "desktop_transcription_tasks", "desktop_conversation_title_attempts"]}
        assert all(value == 0 for value in attempts.values())
        assert connection.execute("SELECT COUNT(*) FROM desktop_sync_jobs WHERE stage IN ('COMMITTING','UNKNOWN')").fetchone()[0] == 0
        return {"requestedCount": count, "restoredCount": expected_count, "restoredMessageCount": expected_count, "allIdentityAndTextExact": True, "originalLocalWorkspacesExact": True,
                "workBusinessExact": True, "attempts": attempts, "businessSha256": fault.digest(fault.snapshot(connection))}


if __name__ == "__main__":
    print(json.dumps(check(fault.marked(sys.argv[1]), sys.argv[2]), ensure_ascii=False))
