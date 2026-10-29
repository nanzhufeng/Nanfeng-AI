"""Synthetic PDF coverage; needs reportlab, pypdf and Pillow (bundled runtime).

STSong-Light is a standard CJK font reference; no system font bytes are copied.
The encrypted fixture password is a public test value, never a user credential.
"""
from io import BytesIO
from pathlib import Path

from PIL import Image
from pypdf import PdfReader, PdfWriter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader

destination = Path(__file__).resolve().parents[1] / "src-tauri/tests/fixtures"
pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))
stream = BytesIO()
document = canvas.Canvas(stream, pagesize=(300, 500), invariant=1)
document.setTitle("Nanfeng synthetic PDF preview matrix")
document.setFont("STSong-Light", 22)
document.drawString(20, 440, "南枫 PDF 预览验收")
document.setFont("Helvetica", 16)
document.drawString(20, 390, "Page 1 / local rendering")
document.rect(10, 10, 280, 480)
document.showPage()
document.setFont("Helvetica", 24)
document.drawString(20, 430, "Rotated page 2")
document.setFillColorRGB(1, 0, 0)
document.rect(0, 0, 80, 80, fill=1, stroke=0)
document.showPage()
document.setPageSize((400, 300))
scan = Image.new("RGB", (160, 90), "white")
for x in range(160):
    for y in range(90):
        scan.putpixel((x, y), (220, 30, 30) if x < 53 else (30, 180, 30) if x < 106 else (30, 30, 220))
document.drawImage(ImageReader(scan), 0, 0, width=400, height=300)
document.showPage()
document.setPageSize((1000, 250))
document.setFont("Helvetica", 40)
document.drawString(30, 120, "Last page 4 / wide landscape")
document.rect(5, 5, 990, 240)
document.showPage()
document.save()

reader = PdfReader(BytesIO(stream.getvalue()))
writer = PdfWriter()
for index, page in enumerate(reader.pages):
    if index == 1:
        page.rotate(90)
    writer.add_page(page)
with (destination / "windows-preview-matrix.pdf").open("wb") as output:
    writer.write(output)

encrypted = PdfWriter()
encrypted.add_page(reader.pages[0])
encrypted.encrypt("synthetic-preview-password", algorithm="RC4-128")
with (destination / "windows-preview-encrypted.pdf").open("wb") as output:
    encrypted.write(output)
print("Created four-page synthetic matrix and password-protected rejection fixture.")

# Many overlapping vector paths provide actual asynchronous renderer work for
# cancellation tests, without large raster assets or external font resources.
heavy = canvas.Canvas(str(destination / "windows-preview-cancellation.pdf"), pagesize=(600, 800), invariant=1)
for index in range(50000):
    heavy.setFillColorRGB((index % 17) / 17, (index % 23) / 23, (index % 31) / 31)
    heavy.circle((index * 37) % 600, (index * 53) % 800, 12 + index % 29, stroke=0, fill=1)
heavy.showPage()
heavy.save()
print("Created synthetic vector workload for in-flight cancellation.")
