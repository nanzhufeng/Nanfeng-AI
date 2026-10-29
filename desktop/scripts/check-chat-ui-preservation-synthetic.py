import json, sqlite3, sys
from pathlib import Path
from contextlib import closing
receipt = json.loads(Path(sys.argv[1]).read_text("utf-8-sig"))
root = Path(receipt["root"]).resolve()
assert root.parent == Path("D:/CodexTemp").resolve() and root.name.startswith("nanfeng-ai-windows-acceptance.")
assert json.loads((root / ".nanfeng-windows-acceptance.json").read_text("utf-8-sig"))["identifier"] == "com.nanzhufeng.ai.desktop.windowsacceptance"
seed = json.loads((root / "chat-ui-preservation-seed.json").read_text("utf-8-sig"))
def connect(p): return sqlite3.connect(p.as_uri() + "?mode=ro", uri=True)
checked = []
for db in sorted(root.rglob("*.sqlite3")):
    with closing(connect(db)) as c:
        assert c.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
        assert not c.execute("PRAGMA foreign_key_check").fetchall()
        checked.append(str(db.relative_to(root)))
counts = {}
for area, path in [("CHAT", "workspace.sqlite3"), ("WORK", "work-area/workspace.sqlite3")]:
    with closing(connect(root / path)) as c:
        assert c.execute("PRAGMA user_version").fetchone()[0] == 56
        assert c.execute("SELECT count(*) FROM desktop_ordinary_chat_attempts").fetchone()[0] == 0
        exchange = json.loads(c.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (receipt["ids"][area],)).fetchone()[0])
        assert exchange == seed["snapshots"][area]["exchange"]
        counts[area] = {k: len(exchange[k]) for k in ["projects", "knowledge", "memory", "conversations"]}
        if area == "WORK":
            assert any(x["title"] == "合成旧项目保留" for x in exchange["projects"])
            assert any(x["body"] == "WORK_KNOWLEDGE_PRESERVED" for x in exchange["knowledge"])
            assert any(x["body"] == "WORK_MEMORY_PRESERVED" for x in exchange["memory"])
        else:
            assert any(x["body"] == "CHAT_MEMORY_PRESERVED" for x in exchange["memory"])
            conversation = next(x for x in exchange["conversations"] if x["id"] == seed["conversationId"])
            assert "CHAT_MESSAGE_PRESERVED" in json.dumps(conversation)
assert receipt["providerRequests"] == 0 and receipt["retiredRouteBlocked"]
assert len(receipt["observations"]) == 14 and all(x["retiredControls"] == 0 for x in receipt["observations"])
closes = seed["normalCloseReceipts"] + receipt["normalCloseReceipts"]
assert len(closes) == 3 and all(x["accepted"] and x["exitCode"] == 0 and x["responding"] for x in closes)
print(json.dumps({"root": str(root), "schema": 56, "sqliteCount": len(checked), "allHealthy": True, "wholeBothAreaExchangesPreserved": True, "counts": counts, "normalCloseCount": len(closes), "nativePageChecks": len(receipt["observations"]), "providerRequests": 0}, ensure_ascii=False))
