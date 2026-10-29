import hashlib, json, sqlite3, sys
from pathlib import Path
from contextlib import closing
r = json.loads(Path(sys.argv[1]).read_text("utf-8-sig"))
root = Path(r["root"]).resolve()
assert root.parent == Path("D:/CodexTemp").resolve()
assert root.name.startswith("nanfeng-ai-windows-acceptance.")
assert json.loads((root / ".nanfeng-windows-acceptance.json").read_text("utf-8-sig"))["identifier"] == "com.nanzhufeng.ai.desktop.windowsacceptance"
render = r["render"]
assert "error" not in render
assert len(render["checks"]) == 26 and all(x["pass"] for x in render["checks"])
assert render["systemClipboard"] is False and render["physicalKeyboard"] is False and render["productionStream"] is False
checked = []
def connect(p): return sqlite3.connect(p.as_uri() + "?mode=ro", uri=True)
for db in sorted(root.rglob("*.sqlite3")):
    with closing(connect(db)) as c:
        assert c.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
        assert not c.execute("PRAGMA foreign_key_check").fetchall()
        checked.append(str(db.relative_to(root)))
for area, rel in [("CHAT", "workspace.sqlite3"), ("WORK", "work-area/workspace.sqlite3")]:
    saved = render["areas"][area]
    with closing(connect(root / rel)) as c:
        assert c.execute("PRAGMA user_version").fetchone()[0] == 56
        assert c.execute("SELECT count(*) FROM desktop_ordinary_chat_attempts").fetchone()[0] == 0
        raw = c.execute("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?", (saved["workspaceId"],)).fetchone()[0]
        exchange = json.loads(raw)
        digest = hashlib.sha256(json.dumps(exchange, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()).hexdigest()
        assert digest == saved["exchangeHash"]
        conversation = next(x for x in exchange["conversations"] if x["id"] == saved["conversationId"])
        text_blocks = [b for m in conversation["messages"] for b in m["blocks"] if b["kind"] == "TEXT"]
        assert any(b["text"] == render["text"] for b in text_blocks)
        asset = Path(__file__).resolve().parents[2] / "protocol/fixtures/assets/golden-note.txt"
        assert c.execute("SELECT sha256,byte_count FROM workspace_assets WHERE workspace_id=?", (saved["workspaceId"],)).fetchall() == [(hashlib.sha256(asset.read_bytes()).hexdigest(), asset.stat().st_size)]
        assert saved["draft"] is None
        assert any(x["name"] == f"{area} cold visible code exact" for x in render["checks"])
assert len(r["normalCloseReceipts"]) == 2
assert all(x["accepted"] and x["exitCode"] == 0 and x["responding"] and x["title"] == "南枫 AI · 隔离测试（非日常版）" for x in r["normalCloseReceipts"])
assert r["providerRequests"] == 0
print(json.dumps({"root": str(root), "sqliteCount": len(checked), "allHealthy": True, "schema": 56, "bothExchangeHashesExact": True, "originalMarkdownExact": True, "renderChecks": 26, "attempts": 0, "providerRequests": 0, "naturalCloseCount": 2, "systemClipboardVerified": False, "physicalKeyboardVerified": False}, ensure_ascii=False))
