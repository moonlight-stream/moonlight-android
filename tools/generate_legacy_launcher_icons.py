#!/usr/bin/env python3
"""Generate pre-Android 8 launcher icons from the Saba artwork using Pillow."""

from pathlib import Path

from PIL import Image


REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE = REPO_ROOT / "app/src/main/res/drawable-nodpi/saba.webp"
RES_DIR = REPO_ROOT / "app/src/main/res"
LANCZOS = getattr(Image, "Resampling", Image).LANCZOS
ICON_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def main() -> None:
    with Image.open(SOURCE) as source_image:
        source = source_image.convert("RGB")

    if source.width != source.height:
        raise ValueError(f"Expected square launcher artwork, got {source.size}")

    for density, icon_size in ICON_SIZES.items():
        # Legacy launchers render this bitmap as the complete icon, so adaptive
        # foreground insets must not be baked into the fallback resource.
        icon = source.resize(
            (icon_size, icon_size),
            LANCZOS,
        )

        output = RES_DIR / f"mipmap-{density}/ic_launcher_saba.png"
        output.parent.mkdir(parents=True, exist_ok=True)
        icon.save(output, format="PNG", optimize=True)
        print(f"Generated {output.relative_to(REPO_ROOT)} ({icon_size}x{icon_size})")


if __name__ == "__main__":
    main()
