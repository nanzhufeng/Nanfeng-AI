"""Read-only cancellation checks against marked native roots and private SQL."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location("temporary_checks", Path(__file__).with_name("check-current-temporary-consumers-synthetic.py"))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


def check(value):
    root = checks.marked(value)
    receipt = checks.load(root / "selected-cancel-owner-native.json")
    assert Path(receipt["root"]).resolve() == root
    assert receipt["coldExact"] and len(receipt["closes"]) == 2
    assert all(item["accepted"] and item["responding"] for item in receipt["closes"])
    executable = Path(__file__).resolve().parents[1] / "output/windows-acceptance" / receipt["candidateSha256"] / "NanfengAI-Windows-Acceptance.exe"
    assert hashlib.sha256(executable.read_bytes()).hexdigest().upper() == receipt["candidateSha256"]
    selected = {}
    no_attempts = {}
    for area, database in [("CHAT", root / "workspace.sqlite3"), ("WORK", root / "work-area/workspace.sqlite3")]:
        with checks.connect(database) as connection:
            rows = connection.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (receipt["workspaceId"],)).fetchall()
            assert len(rows) == 1
            assert json.loads(rows[0][0]) == receipt["imported"][area]["exchange"]
            selected[area] = connection.execute("SELECT COUNT(*) FROM desktop_selected_conversation_sync").fetchone()[0]
            assert selected[area] == (0 if area == "CHAT" else 1)
            assert connection.execute("SELECT COUNT(*) FROM desktop_sync_jobs WHERE stage IN ('COMMITTING','UNKNOWN')").fetchone()[0] == 0
            no_attempts[area] = {name: checks.count(connection, name) for name in ["desktop_ordinary_chat_attempts", "desktop_ocr_attempts_v1", "desktop_transcription_tasks", "desktop_conversation_title_attempts"]}
            assert all(value == 0 for value in no_attempts[area].values())
    before = checks.load(root / "synthetic-remote-documents-before-cancel.json")
    after = checks.load(root / "synthetic-remote-documents.json")
    assert len(before) == 2 and len(after) == 1
    assert receipt["chatDocumentId"] != receipt["workDocumentId"]
    assert after[0]["documentId"] == receipt["workDocumentId"]
    original_work = next(item for item in before if item["documentId"] == receipt["workDocumentId"])
    assert after[0] == original_work
    surfaces = {item["documentId"]: next(r["content"]["surface"] for r in item["payload"]["records"] if r["kind"] == "conversation") for item in before}
    assert surfaces[receipt["chatDocumentId"]] == "CHAT" and surfaces[receipt["workDocumentId"]] == "WORK"
    deletes = [r for r in receipt["requests"] if r["path"] == "/rest/v1/rpc/nanfeng_sync_delete_document"]
    commits = [r for r in receipt["requests"] if r["path"] == "/rest/v1/rpc/nanfeng_sync_commit_document"]
    assert len(deletes) == 1 and deletes[0]["documentId"] == receipt["chatDocumentId"]
    assert len(commits) == 2 and {r["documentId"] for r in commits} == set(surfaces)
    assert receipt["crossAreaErrorPollution"] == receipt["expectedPollution"]
    if not receipt["expectedPollution"]:
        assert receipt["workBefore"] == receipt["workAfter"]
    files = checks.integrity(root)
    return {"root": str(root), "candidateSha256": receipt["candidateSha256"], "historicalBaseline": receipt["expectedPollution"],
            "sqliteCount": len(files), "sqliteFiles": files, "integrityOk": True, "foreignKeyErrors": 0,
            "selectedCounts": selected, "ordinaryExchangeExactBothAreas": True, "workRemoteDocumentExact": True,
            "deleteCount": 1, "setupCommitCount": 2, "noResurrectionAfterCold": True,
            "crossAreaErrorPollution": receipt["crossAreaErrorPollution"], "noAttemptTables": no_attempts,
            "pids": receipt["pids"], "realService": False}


if __name__ == "__main__":
    print(json.dumps([check(root) for root in sys.argv[1:]], ensure_ascii=False, indent=2))
