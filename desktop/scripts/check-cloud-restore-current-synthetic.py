"""Read-only verification of atomic restore receipts and all preserved SQLite files."""
import importlib.util
import json
from pathlib import Path
import sys


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


checks = module("checks", "check-current-temporary-consumers-synthetic.py")
fault = module("fault", "fault-cloud-restore-synthetic.py")


def check(value):
    root = fault.marked(value)
    receipt = checks.load(root / "cloud-restore-atomic-native.json")
    assert Path(receipt["root"]).resolve() == root
    assert receipt["failureExact"]["snapshotSha256"] == receipt["failureColdExact"]["snapshotSha256"] == receipt["seed"]["snapshotSha256"]
    assert receipt["restored"]["snapshotSha256"] == receipt["restoredCold"]["snapshotSha256"]
    assert len(receipt["closes"]) == 3 and not receipt["forcedCleanup"]
    assert all(close["responding"] and close["accepted"] for close in receipt["closes"])
    assert all(request["path"] in ["/auth/v1/token", "/rest/v1/rpc/nanfeng_sync_inventory"] for request in receipt["requests"])
    restored = fault.run(root, "assert-restored", {})
    assert restored == receipt["restoredCold"]
    assert {k: v for k, v in receipt["partialIsolation"].items() if k != "snapshotSha256"} == {k: v for k, v in receipt["restoredCold"].items() if k != "snapshotSha256"}
    assert next(c for c in receipt["cases"] if c["stage"] == "valid-13-with-one-invalid")["ui"]["progress"]["kind"] == "attention"
    files = checks.integrity(root)
    return {"candidateSha256": receipt["candidateSha256"], "root": str(root), "sqliteCount": len(files), "sqliteFiles": files,
            "integrityOk": True, "foreignKeys": 0, "failureAndColdBusinessExact": True, "restoredAndColdBusinessExact": True,
            "normalCloseCount": 3, "pids": receipt["pids"], "validBatchCount": 13,
            "validBatchElapsedMs": next(c["elapsedMs"] for c in receipt["cases"] if c["stage"] == "valid-batch-13"),
            "partialRestoredBusinessFactsExact": True, "partialAndSuccessWholeSnapshotsEqual": receipt["partialIsolation"] == receipt["restoredCold"],
            "partialDocumentCount": 14, "partialValidCount": 13, "newCloudCommitCount": 0,
            "priorFixtureFailurePreserved": bool(list(root.glob("cloud-restore-atomic-failure-*.json")))}


if __name__ == "__main__":
    print(json.dumps([check(value) for value in sys.argv[1:]], ensure_ascii=False, indent=2))
