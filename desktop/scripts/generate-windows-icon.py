"""Package the existing approved Desktop artwork as Windows ICO, without redesign."""
import hashlib
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
MASTER = ROOT / 'artwork/source/nanfeng_ai_launcher_icon_master_20260828_subject_085.png'
EXPECTED_SHA256 = 'c3474b35a2f772ba94e56f6ea8bc27a92e29ea1996f28a6d7ba404b2c6b98b63'
SIZES = [(s, s) for s in (16, 20, 24, 32, 40, 48, 64, 128, 256)]
CORNER_RADIUS_RATIO = 0.20


def windows_round_mask(size):
    # Windows does not mask application icons. Apply the requested outline at
    # export time, retaining the approved artwork's RGB pixels and canvas ratio.
    scale = 4
    mask = Image.new('L', (size[0] * scale, size[1] * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, mask.width - 1, mask.height - 1),
        radius=min(mask.size) * CORNER_RADIUS_RATIO,
        fill=255,
    )
    return mask.resize(size, Image.Resampling.LANCZOS)


def main():
    if hashlib.sha256(MASTER.read_bytes()).hexdigest() != EXPECTED_SHA256:
        raise SystemExit('Approved Desktop artwork changed; review the source before exporting.')
    with Image.open(MASTER) as source:
        if source.size != (1254, 1254):
            raise SystemExit('Unexpected artwork canvas; refusing to crop or stretch.')
        artwork = source.convert('RGBA')
        rounded = artwork.copy()
        rounded.putalpha(windows_round_mask(artwork.size))
        if rounded.convert('RGB').tobytes() != artwork.convert('RGB').tobytes():
            raise SystemExit('Windows export altered the artwork colors or geometry.')
        rounded.save(ROOT / 'desktop/src-tauri/icons/icon.ico', sizes=SIZES)
        rounded.resize((1024, 1024), Image.Resampling.LANCZOS).save(
            ROOT / 'desktop/src-tauri/icons/icon.png')
    with Image.open(ROOT / 'desktop/src-tauri/icons/icon.ico') as result:
        if result.ico.sizes() != set(SIZES):
            raise SystemExit('ICO is missing a required Windows display size.')
        for size in SIZES:
            frame = result.ico.getimage(size).convert('RGBA')
            if frame.getpixel((0, 0))[3] > 2 or frame.getpixel((size[0] // 2, size[1] // 2))[3] != 255:
                raise SystemExit('Windows icon transparency or opaque center is invalid.')
    print('Windows PNG/ICO exported with transparent rounded corners; 9 sizes, original artwork ratio.')


if __name__ == '__main__':
    main()
