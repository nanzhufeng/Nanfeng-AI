import json, sqlite3, sys
from pathlib import Path
from contextlib import closing
receipt = json.loads(Path(sys.argv[1]).read_text("utf-8-sig"))
root = Path(receipt["root"]).resolve()
assert root.parent == Path("D:/CodexTemp").resolve()
assert root.name.startswith("nanfeng-ai-windows-acceptance.")
assert json.loads((root / ".nanfeng-windows-acceptance.json").read_text("utf-8-sig"))["identifier"] == "com.nanzhufeng.ai.desktop.windowsacceptance"
def connect(p):
    return sqlite3.connect(p.as_uri() + "?mode=ro", uri=True)
checks = []
for db in sorted(root.rglob("*.sqlite3")):
    with closing(connect(db)) as c:
        assert c.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
        assert not c.execute("PRAGMA foreign_key_check").fetchall()
        checks.append(str(db.relative_to(root)))
with closing(connect(root / "workspace.sqlite3")) as c:
    c.row_factory = sqlite3.Row
    assert c.execute("PRAGMA user_version").fetchone()[0] == 56
    envelope = dict(c.execute("SELECT * FROM desktop_compare_executions WHERE execution_id=?", (receipt["executionId"],)).fetchone())
    assert envelope["state"] == "UNKNOWN"
    attempts = [dict(r) for r in c.execute("SELECT * FROM desktop_ordinary_chat_attempts WHERE compare_execution_id=?", (receipt["executionId"],))]
    assert len(attempts) == c.execute("SELECT count(*) FROM desktop_ordinary_chat_attempts").fetchone()[0] == 2
    by_model = {a["compare_logical_model"]: a for a in attempts}
    done, unknown = by_model["CHATGPT"], by_model["CLAUDE"]
    assert done["state"] == "COMPLETED" and done["safe_error_code"] is None
    assert (done["requested_model_id"], done["actual_model_id"], done["input_tokens"], done["output_tokens"], done["cached_input_tokens"], done["charge_micros"], done["currency_code"], done["cost_source"]) == ("openai/gpt-5.6-terra", "openai/gpt-5.6-terra", 11, 7, 2, 17, "USD", "PROVIDER_REPORTED")
    assert (unknown["state"], unknown["safe_error_code"], unknown["requested_model_id"]) == ("UNKNOWN", "PROCESS_INTERRUPTED", "anthropic/claude-sonnet-5")
    assert all(unknown[k] is None for k in ["actual_model_id", "input_tokens", "output_tokens", "charge_micros"])
    assert done["user_message_id"] == unknown["user_message_id"] == envelope["user_message_id"]
    assert done["idempotency_key"] != unknown["idempotency_key"]
    assert done["attempt_id"] == envelope["chatgpt_attempt_id"] and unknown["attempt_id"] == envelope["claude_attempt_id"]
    exchange = json.loads(c.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (receipt["workspaceId"],)).fetchone()[0])
    conversation = next(v for v in exchange["conversations"] if v["id"] == receipt["conversationId"])
    assert conversation["currentLeafId"] == unknown["assistant_message_id"]
    messages = {m["id"]: m for m in conversation["messages"]}
    assert "合成ChatGPT已完成分支" in json.dumps(messages[done["assistant_message_id"]], ensure_ascii=False)
    assert "合成Claude中断前部分正文" in json.dumps(messages[unknown["assistant_message_id"]], ensure_ascii=False)
with closing(connect(root / "work-area/workspace.sqlite3")) as c:
    assert c.execute("PRAGMA user_version").fetchone()[0] == 56
    assert c.execute("SELECT count(*) FROM desktop_compare_executions").fetchone()[0] == 0
    assert c.execute("SELECT count(*) FROM desktop_ordinary_chat_attempts").fetchone()[0] == 0
with closing(connect(root / "usage-ledger/usage-ledger-v1.sqlite3")) as c:
    c.row_factory = sqlite3.Row
    ledger = [dict(r) for r in c.execute("SELECT * FROM usage_ledger_entries WHERE conversation_id=?", (receipt["conversationId"],))]
    assert len(ledger) == 1
    assert ledger[0]["attempt_id"] == done["attempt_id"]
    assert (ledger[0]["input_tokens"], ledger[0]["output_tokens"], ledger[0]["cached_input_tokens"], ledger[0]["charge_micros"], ledger[0]["currency_code"]) == (11, 7, 2, 17, "USD")
assert receipt["providerRequests"] == 0 and len(receipt["normalCloseReceipts"]) == 3
assert all(c["accepted"] and c["exitCode"] == 0 and c["responding"] and c["title"] == "南枫 AI · 隔离测试（非日常版）" for c in receipt["normalCloseReceipts"])
print(json.dumps({"root": str(root), "schema": 56, "sqliteCount": len(checks), "sqliteHealthy": True, "independentBranchFacts": True, "unknownFeeNotInvented": True, "oneSuccessfulLedgerEntry": True, "workEmpty": True, "naturalCloseCount": 3, "providerRequests": 0}, ensure_ascii=False))
