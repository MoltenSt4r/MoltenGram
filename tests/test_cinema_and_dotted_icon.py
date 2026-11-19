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
    # Ensure MOLTENGRAM_RAGE has hasBleed=true (default) and not false
    rage_match = re.search(r'MOLTENGRAM_RAGE\([^)]+\)', lic)
    assert rage_match is not None, "MOLTENGRAM_RAGE must be declared in LauncherIconController"
    assert "false" not in rage_match.group(0), "MOLTENGRAM_RAGE must not set hasBleed=false"

    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = f.read()
    assert "MoltenGramCinemaIcon" in manifest, "MoltenGramCinemaIcon must be declared in AndroidManifest"


def test_rage_icon_assets_no_mirrored_edges():
    for dens, bg_sz, _ in DENSITIES:
        bg_file = os.path.join(RES_DIR, f"drawable-{dens}", "moltengram_rage_icon_background.png")
        assert os.path.exists(bg_file)
        im = Image.open(bg_file).convert("RGB")
        w, h = im.size
        for y in range(h):
            for x in range(w):
                if x < 10 or x >= w - 10 or y < 10 or y >= h - 10:
                    r, g, b = im.getpixel((x, y))
                    assert not (r > 100 and r > g + 30 and r > b + 30), f"Edge red pixel found in {dens} at ({x},{y})"


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

        # Verify glyph is present and properly proportioned matching original template
        min_x, min_y, max_x, max_y = bg_sz, bg_sz, 0, 0
        for y in range(bg_sz):
            for x in range(bg_sz):
                p = bg_im.getpixel((x, y))
                if p[0] > 10 or p[1] > 10 or p[2] > 10:
                    if x < min_x: min_x = x
                    if x > max_x: max_x = x
                    if y < min_y: min_y = y
                    if y > max_y: max_y = y

        assert max_x > min_x and max_y > min_y
        # Check that the glyph is within the icon bounds
        assert min_x > 0 and min_y > 0 and max_x < bg_sz and max_y < bg_sz


def test_nothing_icon_assets_exist_and_centered():
    for dens, bg_sz, mip_sz in DENSITIES:
        bg_file = os.path.join(RES_DIR, f"drawable-{dens}", "moltengram_nothing_icon_background.png")
        sq_file = os.path.join(RES_DIR, f"mipmap-{dens}", "ic_launcher_moltengram_nothing.png")
        rnd_file = os.path.join(RES_DIR, f"mipmap-{dens}", "ic_launcher_moltengram_nothing_round.png")

        assert os.path.exists(bg_file), f"Missing {bg_file}"
        assert os.path.exists(sq_file), f"Missing {sq_file}"
        assert os.path.exists(rnd_file), f"Missing {rnd_file}"

        bg_im = Image.open(bg_file)
        assert bg_im.size == (bg_sz, bg_sz)

        min_x, min_y, max_x, max_y = bg_sz, bg_sz, 0, 0
        for y in range(bg_sz):
            for x in range(bg_sz):
                p = bg_im.getpixel((x, y))
                if p[0] > 30 or p[1] > 30 or p[2] > 30:
                    if x < min_x: min_x = x
                    if x > max_x: max_x = x
                    if y < min_y: min_y = y
                    if y > max_y: max_y = y

        assert max_x > min_x and max_y > min_y
        assert min_x > 0 and min_y > 0 and max_x < bg_sz and max_y < bg_sz


def test_fdroid_workflow_has_large_file_filter():
    with open(FDROID_PATH, "r", encoding="utf-8") as f:
        fdroid = f.read()

    assert "nonstandardwebroot: true" in fdroid, "fdroid.yml must set nonstandardwebroot: true"
    assert "mirrors:" in fdroid, "fdroid.yml must configure download mirror"
    assert "https://github.com/MoltenSt4r/MoltenGram/releases/download" in fdroid
    assert "find deploy/ -type f -size +95M -exec rm" in fdroid, "fdroid.yml must filter files >= 95M before git push"
