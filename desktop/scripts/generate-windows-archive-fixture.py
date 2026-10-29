"""Deterministic, synthetic ZIP for Windows local preview acceptance."""
from pathlib import Path
from io import BytesIO
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

fixtures = Path(__file__).resolve().parents[1] / "src-tauri/tests/fixtures"
def put(archive, name, data):
    info = ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
    info.compress_type = ZIP_DEFLATED
    archive.writestr(info, data)

nested = BytesIO()
with ZipFile(nested, "w") as archive:
    put(archive, "inner.txt", b"synthetic nested archive")
with ZipFile(fixtures / "windows-preview-archive.zip", "w") as archive:
    put(archive, "docs/report.pdf", (fixtures / "windows-preview-matrix.pdf").read_bytes())
    put(archive, "docs/notes.md", "# 合成目录\nZIP 内本地文本，不外发。\n".encode("utf-8"))
    put(archive, "docs/broken.pdf", b"%PDF-1.7\nsynthetic invalid document")
    put(archive, "docs/locked.pdf", (fixtures / "windows-preview-encrypted.pdf").read_bytes())
    put(archive, "docs/workload.pdf", (fixtures / "windows-preview-cancellation.pdf").read_bytes())
    put(archive, "nested.zip", nested.getvalue())
print("Created deterministic archive with PDF, text, invalid/encrypted and nested archive entries.")
