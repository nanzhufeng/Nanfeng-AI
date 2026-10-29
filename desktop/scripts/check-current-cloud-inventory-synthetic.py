"""Read-only checks of candidate-scoped inventory probes and final cold SQL."""
import importlib.util
import json
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location("checks", Path(__file__).with_name("check-current-temporary-consumers-synthetic.py"))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


def check(value):
    root = checks.marked(value)
    receipt = checks.load(root / "cloud-inventory-native.json")
    assert Path(receipt["root"]).resolve() == root
    with checks.connect(root / "workspace.sqlite3") as connection:
        exchange = json.loads(connection.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (receipt["workspaceId"],)).fetchone()[0])
        assert exchange == receipt["imported"]["exchange"]
        assert checks.count(connection, "desktop_selected_conversation_sync") == 0
        attempts = {table: checks.count(connection, table) for table in ["desktop_ordinary_chat_attempts", "desktop_ocr_attempts_v1", "desktop_transcription_tasks", "desktop_conversation_title_attempts"]}
        assert all(count == 0 for count in attempts.values())
    with checks.connect(root / "work-area/workspace.sqlite3") as connection:
        assert checks.count(connection, "desktop_selected_conversation_sync") == 0
    cases = {case["mode"]: case for case in receipt["cases"]}
    assert len(cases) == 8
    assert all(case["ui"]["selectedCount"] == 3 and len(case["ui"]["cloudRows"]) == 3 for mode, case in cases.items() if mode != "exact-empty")
    assert cases["10000-items"]["response"]["arrayCount"] == 10000
    assert cases["10000-items"]["ui"]["error"] == "" and "10000" in cases["10000-items"]["ui"]["status"]
    assert cases["10001-items"]["ui"]["error"]
    assert cases["over-bytes"]["response"]["bytes"] == 33554433 and cases["over-bytes"]["ui"]["error"]
    assert cases["exact-empty"]["response"]["bytes"] == 33554432 and not cases["exact-empty"]["ui"]["cloudRows"]
    assert receipt["coldExact"] and len(receipt["closes"]) == 2
    assert all(close["accepted"] and close["responding"] for close in receipt["closes"])
    assert not any(request["path"] == "/rest/v1/rpc/nanfeng_sync_commit_document" for request in receipt["requests"])
    files = checks.integrity(root)
    return {"candidateSha256": receipt["candidateSha256"], "root": str(root), "sqliteCount": len(files), "sqliteFiles": files,
            "integrityOk": True, "foreignKeys": 0, "sourceExchangeExact": True, "finalSelectedCount": 0, "workSelectedCount": 0,
            "noAttemptTables": attempts, "cases": [{"mode": case["mode"], "candidateSha256": case["candidateSha256"],
            "elapsedMs": case["elapsedMs"], "bytes": case["response"]["bytes"], "arrayCount": case["response"]["arrayCount"],
            "kind": case["ui"]["progress"]["kind"], "cloudRows": len(case["ui"]["cloudRows"])} for case in receipt["cases"]],
            "normalCloseCount": 2, "newProbeCommitCount": 0, "pids": receipt["pids"]}


if __name__ == "__main__":
    print(json.dumps([check(root) for root in sys.argv[1:]], ensure_ascii=False, indent=2))
