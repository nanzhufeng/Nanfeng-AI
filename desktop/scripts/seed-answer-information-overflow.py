"""Add content-free completed attempts only inside a marked Windows acceptance root."""

import json
import sqlite3
import sys
from pathlib import Path

parent = Path(sys.argv[1]).resolve(strict=True)
root = Path(sys.argv[2]).resolve(strict=True)
assistant_message_id = sys.argv[3]
if root.parent != parent or not root.name.startswith("nanfeng-ai-windows-acceptance."):
    raise SystemExit("refusing non-acceptance root")
marker = json.loads((root / ".nanfeng-windows-acceptance.json").read_text(encoding="utf-8"))
if (
    marker.get("identifier") != "com.nanzhufeng.ai.desktop.windowsacceptance"
    or marker.get("schemaVersion") != 1
    or root.name != "nanfeng-ai-windows-acceptance." + marker.get("runId", "")
):
    raise SystemExit("acceptance marker mismatch")

database = root / "workspace.sqlite3"
if not database.is_file():
    raise SystemExit("CHAT database missing")
with sqlite3.connect(database) as connection:
    source = connection.execute(
        "SELECT attempt_id FROM desktop_ordinary_chat_attempts "
        "WHERE assistant_message_id=? AND state='COMPLETED'",
        (assistant_message_id,),
    ).fetchone()
    if source is None:
        raise SystemExit("completed source attempt missing")
    for index in range(201):
        connection.execute(
            "INSERT INTO desktop_ordinary_chat_attempts "
            "(attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,"
            "idempotency_key,request_fingerprint,state,created_at_ms,updated_at_ms,web_search_route) "
            "SELECT ?,workspace_id,conversation_id,user_message_id,?,?,request_fingerprint,"
            "'COMPLETED',created_at_ms+?,updated_at_ms+?,web_search_route "
            "FROM desktop_ordinary_chat_attempts WHERE attempt_id=?",
            (
                f"attempt-answer-info-overflow-{index}",
                f"assistant-answer-info-overflow-{index}",
                f"idempotency-answer-info-overflow-{index}",
                index + 1,
                index + 1,
                source[0],
            ),
        )
    integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
    if integrity != "ok":
        raise SystemExit("synthetic database integrity failed")
print("ANSWER_INFO_OVERFLOW_SEEDED 201")
