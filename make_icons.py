"""
Turn YOUR logo into all the Android launcher-icon sizes.

Usage (from the SmartAttendanceApp folder):
    pip install pillow
    python tools/make_icons.py path/to/my_logo.png

Use a square PNG, ideally 512x512 or bigger. Non-square images are
centre-cropped. The files are written into app/src/main/res/mipmap-*/.
"""
import os
import sys
from PIL import Image

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)

    img = Image.open(sys.argv[1]).convert("RGBA")
    side = min(img.size)
    left = (img.width - side) // 2
    top = (img.height - side) // 2
    img = img.crop((left, top, left + side, top + side))

    res_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
    for folder, px in SIZES.items():
        out_dir = os.path.join(res_dir, folder)
        os.makedirs(out_dir, exist_ok=True)
        img.resize((px, px), Image.LANCZOS).save(os.path.join(out_dir, "ic_launcher.png"))
        print("wrote", folder, px, "px")
    print("Done. Rebuild the app to see your icon.")

if __name__ == "__main__":
    main()
