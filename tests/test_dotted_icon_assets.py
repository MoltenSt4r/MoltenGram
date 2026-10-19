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


def test_dotted_icon_has_bleed_false_in_launcher_controller():
    controller_path = '/root/ExteraMS/TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java'
    with open(controller_path, 'r', encoding='utf-8') as f:
        content = f.read()
    # MOLTENGRAM_DOTTED must explicitly set hasBleed = false so in-app previews do not zoom/bleed crop
    assert 'MOLTENGRAM_DOTTED("MoltenGramDottedIcon", R.drawable.moltengram_dotted_icon_background,\n                0, R.string.AppIconMoltenGramDotted, IconGroup.MOLTENGRAM, "@moltenst4r", "Nothing OS glyph dot matrix", 0xFFFFFFFF, false)' in content


def test_dotted_icon_1to1_no_clipping_and_not_miniaturized():
    bg_path = os.path.join(RES_DIR, 'drawable-xxhdpi', 'moltengram_dotted_icon_background.png')
    im = Image.open(bg_path).convert('RGB')
    w, h = im.size
    assert (w, h) == (324, 324)

    cx, cy = w / 2.0, h / 2.0
    circle_radius = w / 2.0  # 162 px with hasBleed = false

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
                if dist > circle_radius:
                    outside_circle_pixels += 1

    # Flame must be at 1:1 scale (height >= 200 on 324px canvas, not shrunk)
    flame_height = max_y - min_y
    assert flame_height >= 200, f'Flame height {flame_height} is too small, appears downscaled'

    # Must not have any lit flame pixel outside the circle
    assert outside_circle_pixels == 0, f'{outside_circle_pixels} pixels clipped by circular mask'

