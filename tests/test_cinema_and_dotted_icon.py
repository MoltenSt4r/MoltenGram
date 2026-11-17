import os
import re
from PIL import Image
import math

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/res")
LIC_PATH = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java")
MANIFEST_PATH = os.path.join(REPO_ROOT, "TMessagesProj/src/main/AndroidManifest.xml")
FDROID_PATH = os.path.join(REPO_ROOT, ".github/workflows/fdroid.yml")

DENSITIES = [
    ("mdpi", 108, 48),
    ("hdpi", 162, 72),
    ("xhdpi", 216, 96),
    ("xxhdpi", 324, 144),
    ("xxxhdpi", 432, 192),
]


def test_dotted_icon_completely_removed():
    with open(LIC_PATH, "r", encoding="utf-8") as f:
        lic = f.read()
    assert "MOLTENGRAM_DOTTED" not in lic, "MOLTENGRAM_DOTTED must not exist in LauncherIcon enum"
    assert '"org.telegram.messenger.MoltenGramDottedIcon"' in lic, "MoltenGramDottedIcon must be in LEGACY_ALIASES"

    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = f.read()
    assert "MoltenGramDottedIcon" not in manifest, "MoltenGramDottedIcon must not be in AndroidManifest"

    # Check that no dotted assets exist in res
    for root, dirs, files in os.walk(RES_DIR):
        for file in files:
            assert "moltengram_dotted" not in file.lower(), f"Unexpected dotted file: {file}"


def test_cinema_icon_declared_in_code_and_manifest():
    with open(LIC_PATH, "r", encoding="utf-8") as f:
        lic = f.read()
    assert "MOLTENGRAM_CINEMA" in lic, "MOLTENGRAM_CINEMA must be declared in LauncherIconController"

    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = f.read()
    assert "MoltenGramCinemaIcon" in manifest, "MoltenGramCinemaIcon must be declared in AndroidManifest"


def test_cinema_icon_assets_exist_and_centered():
    for dens, bg_sz, mip_sz in DENSITIES:
        bg_file = os.path.join(RES_DIR, f"drawable-{dens}", "moltengram_cinema_icon_background.png")
        sq_file = os.path.join(RES_DIR, f"mipmap-{dens}", "ic_launcher_moltengram_cinema.png")
        rnd_file = os.path.join(RES_DIR, f"mipmap-{dens}", "ic_launcher_moltengram_cinema_round.png")

        assert os.path.exists(bg_file), f"Missing {bg_file}"
        assert os.path.exists(sq_file), f"Missing {sq_file}"
        assert os.path.exists(rnd_file), f"Missing {rnd_file}"

        bg_im = Image.open(bg_file)
        assert bg_im.size == (bg_sz, bg_sz)

        # Verify glyph is centered and within 72dp safe zone (radius = bg_sz * 36 / 108)
        safe_radius = bg_sz * 36.0 / 108.0
        center_x = bg_sz / 2.0
        center_y = bg_sz / 2.0

        min_x, min_y, max_x, max_y = bg_sz, bg_sz, 0, 0
        for y in range(bg_sz):
            for x in range(bg_sz):
                p = bg_im.getpixel((x, y))
                if p[0] > 10 or p[1] > 10 or p[2] > 10:
                    if x < min_x: min_x = x
                    if x > max_x: max_x = x
                    if y < min_y: min_y = y
                    if y > max_y: max_y = y
                    dist = math.hypot(x - center_x, y - center_y)
                    assert dist <= safe_radius, f"Pixel at ({x},{y}) exceeds safe radius {safe_radius} in {dens}"

        cx = (min_x + max_x) / 2.0
        cy = (min_y + max_y) / 2.0
        assert abs(cx - center_x) <= 2.0, f"Glyph X center {cx} not aligned with canvas center {center_x} in {dens}"
        assert abs(cy - center_y) <= 2.0, f"Glyph Y center {cy} not aligned with canvas center {center_y} in {dens}"


def test_fdroid_workflow_has_mirrors_and_large_file_filter():
    with open(FDROID_PATH, "r", encoding="utf-8") as f:
        fdroid = f.read()

    assert "mirrors:" in fdroid, "fdroid.yml must configure download mirror"
    assert "https://github.com/MoltenSt4r/MoltenGram/releases/download" in fdroid
    assert "find deploy/ -type f -size +95M -exec rm" in fdroid, "fdroid.yml must filter files >= 95M before git push"
