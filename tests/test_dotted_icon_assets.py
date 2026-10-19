import os
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


def test_dotted_icon_safe_zone_and_no_clipping():
    bg_path = os.path.join(RES_DIR, 'drawable-xxhdpi', 'moltengram_dotted_icon_background.png')
    im = Image.open(bg_path).convert('RGB')
    w, h = im.size
    assert (w, h) == (324, 324)

    cx, cy = w / 2.0, h / 2.0
    safe_radius = (270 - 54) / 2.0  # 108 px

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

    # Bounding box must be strictly inside [54, 270] safe zone
    assert min_x >= 54, f'min_x {min_x} < 54'
    assert max_x <= 270, f'max_x {max_x} > 270'
    assert min_y >= 54, f'min_y {min_y} < 54'
    assert max_y <= 270, f'max_y {max_y} > 270'

    # Must not have any lit flame pixel outside the safe circle
    assert outside_circle_pixels == 0, f'{outside_circle_pixels} pixels clipped by circular mask'
