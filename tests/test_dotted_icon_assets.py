import os
import re
import math
from PIL import Image
import pytest

RES_DIR = '/root/ExteraMS/TMessagesProj/src/main/res'
DENSITIES = {
    'mdpi': (108, 48),
    'hdpi': (162, 72),
    'xhdpi': (216, 96),
    'xxhdpi': (324, 144),
    'xxxhdpi': (432, 192),
}


@pytest.mark.parametrize('density,sizes', DENSITIES.items())
def test_dotted_icon_density_files_exist_and_sizes_match(density, sizes):
    bg_size, mip_size = sizes
    bg_path = os.path.join(RES_DIR, f'drawable-{density}', 'moltengram_dotted_icon_background.png')
    mip_path = os.path.join(RES_DIR, f'mipmap-{density}', 'ic_launcher_moltengram_dotted.png')
    round_path = os.path.join(RES_DIR, f'mipmap-{density}', 'ic_launcher_moltengram_dotted_round.png')

    assert os.path.exists(bg_path), f'{bg_path} must exist'
    assert os.path.exists(mip_path), f'{mip_path} must exist'
    assert os.path.exists(round_path), f'{round_path} must exist'

    with Image.open(bg_path) as im:
        assert im.size == (bg_size, bg_size)

    with Image.open(mip_path) as im:
        assert im.size == (mip_size, mip_size)

    with Image.open(round_path) as im:
        assert im.size == (mip_size, mip_size)
        assert im.mode == 'RGBA'
        assert im.getpixel((0, 0))[3] == 0, 'Round mipmap corners must be transparent'


@pytest.mark.parametrize('density,sizes', DENSITIES.items())
def test_dotted_icon_safe_zone_and_no_clipping_all_densities(density, sizes):
    bg_size, _ = sizes
    bg_path = os.path.join(RES_DIR, f'drawable-{density}', 'moltengram_dotted_icon_background.png')
    im = Image.open(bg_path).convert('RGB')
    w, h = im.size
    assert (w, h) == (bg_size, bg_size)

    scale = bg_size / 324.0
    safe_min = 54.0 * scale
    safe_max = 270.0 * scale
    safe_radius = 108.0 * scale

    cx, cy = w / 2.0, h / 2.0

    min_x, max_x, min_y, max_y = w, -1, h, -1
    outside_circle_pixels = 0

    for y in range(h):
        for x in range(w):
            r, g, b = im.getpixel((x, y))
            if max(r, g, b) > 20:  # lit pixel
                if x < min_x: min_x = x
                if x > max_x: max_x = x
                if y < min_y: min_y = y
                if y > max_y: max_y = y

                dist = math.hypot(x - cx, y - cy)
                if dist > safe_radius:
                    outside_circle_pixels += 1

    # Bounding box must be strictly inside safe zone
    assert min_x >= safe_min, f'{density} min_x {min_x} < {safe_min}'
    assert max_x <= safe_max, f'{density} max_x {max_x} > {safe_max}'
    assert min_y >= safe_min, f'{density} min_y {min_y} < {safe_min}'
    assert max_y <= safe_max, f'{density} max_y {max_y} > {safe_max}'

    # Must not have any lit flame pixel outside the safe circle
    assert outside_circle_pixels == 0, f'{density} {outside_circle_pixels} pixels clipped by circular mask'


def test_launcher_icon_dotted_has_bleed_true():
    path = '/root/ExteraMS/TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java'
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    match = re.search(r'MOLTENGRAM_DOTTED\([^)]+\)', content)
    assert match is not None, "MOLTENGRAM_DOTTED must be declared in LauncherIconController"
    assert 'false' not in match.group(0), "MOLTENGRAM_DOTTED must not set hasBleed=false"


def test_launcher_icon_halloween_has_bleed_false():
    path = '/root/ExteraMS/TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java'
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    match = re.search(r'MOLTENGRAM_HALLOWEEN\([^)]+\)', content)
    assert match is not None, "MOLTENGRAM_HALLOWEEN must be declared in LauncherIconController"
    assert 'false' in match.group(0), "MOLTENGRAM_HALLOWEEN must set hasBleed=false to prevent preview zoom/cropping"

