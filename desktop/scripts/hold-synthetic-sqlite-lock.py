"""Hold an exclusive lock only for an explicitly marked Windows acceptance root."""

import sqlite3
import sys
import time
from pathlib import Path


def main() -> int:
    root, ready, release = map(Path, sys.argv[1:4])
    marker = root / ".nanfeng-windows-acceptance.json"
    if not marker.is_file() or ready.parent != root or release.parent != root:
        raise SystemExit("unmarked acceptance root")
    database = root / "workspace.sqlite3"
    connection = sqlite3.connect(database, timeout=1, isolation_level=None)
    try:
        connection.execute("PRAGMA locking_mode=EXCLUSIVE")
        connection.execute("BEGIN EXCLUSIVE")
        connection.execute("SELECT COUNT(*) FROM workspaces").fetchone()
        ready.write_text("ready", encoding="ascii")
        deadline = time.monotonic() + 20
        while not release.is_file() and time.monotonic() < deadline:
            time.sleep(0.05)
        if not release.is_file():
            raise SystemExit("synthetic lock release timed out")
        connection.execute("ROLLBACK")
    finally:
        connection.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
