import os
import xml.etree.ElementTree as ET
from PIL import Image
import pytest
import javalang

RES_DIR = '/root/ExteraMS/TMessagesProj/src/main/res'
MANIFEST_PATH = '/root/ExteraMS/TMessagesProj/src/main/AndroidManifest.xml'
LAUNCHER_CONTROLLER_PATH = '/root/ExteraMS/TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java'

DENSITIES = {
    'mdpi': (108, 48),
    'hdpi': (162, 72),
    'xhdpi': (216, 96),
    'xxhdpi': (324, 144),
    'xxxhdpi': (432, 192),
}

VARIANTS = ['halloween', 'nothing']


@pytest.mark.parametrize('variant', VARIANTS)
@pytest.mark.parametrize('density,sizes', DENSITIES.items())
def test_icon_density_files_exist_and_sizes_match(variant, density, sizes):
    bg_size, mip_size = sizes
    bg_path = os.path.join(RES_DIR, f'drawable-{density}', f'moltengram_{variant}_icon_background.png')
    mip_path = os.path.join(RES_DIR, f'mipmap-{density}', f'ic_launcher_moltengram_{variant}.png')
    round_path = os.path.join(RES_DIR, f'mipmap-{density}', f'ic_launcher_moltengram_{variant}_round.png')

    assert os.path.exists(bg_path), f'{bg_path} must exist'
    assert os.path.exists(mip_path), f'{mip_path} must exist'
    assert os.path.exists(round_path), f'{round_path} must exist'

    with Image.open(bg_path) as im:
        assert im.size == (bg_size, bg_size)
        assert im.mode == 'RGBA'

    with Image.open(mip_path) as im:
        assert im.size == (mip_size, mip_size)
        assert im.mode == 'RGBA'

    with Image.open(round_path) as im:
        assert im.size == (mip_size, mip_size)
        assert im.mode == 'RGBA'
        assert im.getpixel((0, 0))[3] == 0, f'{round_path} corners must be transparent'


@pytest.mark.parametrize('variant', VARIANTS)
def test_icon_anydpi_xml_monochrome_and_background(variant):
    for name in [f'ic_launcher_moltengram_{variant}.xml', f'ic_launcher_moltengram_{variant}_round.xml']:
        xml_path = os.path.join(RES_DIR, 'mipmap-anydpi-v26', name)
        assert os.path.exists(xml_path), f'{xml_path} must exist'
        with open(xml_path, 'r', encoding='utf-8') as f:
            content = f.read()
        assert '<monochrome' in content, f'{name} must contain <monochrome tag'
        assert f'moltengram_{variant}_icon_background' in content, f'{name} must reference moltengram_{variant}_icon_background'
        assert f'moltengram_{variant}_icon_monochrome' in content, f'{name} must reference moltengram_{variant}_icon_monochrome'


@pytest.mark.parametrize('variant', VARIANTS)
def test_dedicated_monochrome_drawable_valid_vector(variant):
    drawable_path = os.path.join(RES_DIR, 'drawable', f'moltengram_{variant}_icon_monochrome.xml')
    assert os.path.exists(drawable_path), f'{drawable_path} must exist'
    tree = ET.parse(drawable_path)
    root = tree.getroot()
    assert root.tag == 'vector', f'{drawable_path} root tag must be vector'
    ns = {'android': 'http://schemas.android.com/apk/res/android'}
    width = root.attrib.get('{http://schemas.android.com/apk/res/android}width')
    height = root.attrib.get('{http://schemas.android.com/apk/res/android}height')
    assert width == '108dp', f'Expected width 108dp, got {width}'
    assert height == '108dp', f'Expected height 108dp, got {height}'
    paths = root.findall('.//path')
    assert len(paths) >= 1, f'{drawable_path} must contain at least one path'


@pytest.mark.parametrize('density,sizes', DENSITIES.items())
def test_nothing_icon_safe_zone_no_clipping_all_densities(density, sizes):
    import math
    bg_size, _ = sizes
    bg_path = os.path.join(RES_DIR, f'drawable-{density}', 'moltengram_nothing_icon_background.png')
    im = Image.open(bg_path).convert('RGB')
    w, h = im.size
    cx, cy = w / 2.0, h / 2.0
    safe_radius = 108.0 * (bg_size / 324.0)

    outside = 0
    for y in range(h):
        for x in range(w):
            r, g, b = im.getpixel((x, y))
            if max(r, g, b) > 30:
                if math.hypot(x - cx, y - cy) > safe_radius:
                    outside += 1
    assert outside == 0, f'{density} Nothing OS glyph has {outside} pixels outside the safe circle'


@pytest.mark.parametrize('density,sizes', DENSITIES.items())
def test_halloween_flame_safe_zone_no_clipping_all_densities(density, sizes):
    import math
    bg_size, _ = sizes
    bg_path = os.path.join(RES_DIR, f'drawable-{density}', 'moltengram_halloween_icon_background.png')
    im = Image.open(bg_path).convert('RGB')
    w, h = im.size
    cx, cy = w / 2.0, h / 2.0
    safe_radius = 108.0 * (bg_size / 324.0)

    # Flood fill flame from center
    center_flame = (int(round(cx)), int(round(cy)))
    visited = set()
    queue = [center_flame]
    visited.add(center_flame)
    flame_pts = []

    while queue:
        px, py = queue.pop(0)
        flame_pts.append((px, py))
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nx, ny = px + dx, py + dy
            if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in visited:
                r, g, b = im.getpixel((nx, ny))
                if r > 200 and 90 < g < 150 and b < 30:
                    visited.add((nx, ny))
                    queue.append((nx, ny))

    assert len(flame_pts) > 0, f'{density} flame not found at center'
    outside = sum(1 for pt in flame_pts if math.hypot(pt[0] - cx, pt[1] - cy) > safe_radius)
    assert outside == 0, f'{density} Halloween flame has {outside} pixels outside the safe circle'


@pytest.mark.parametrize('variant,alias_suffix', [
    ('halloween', 'MoltenGramHalloweenIcon'),
    ('nothing', 'MoltenGramNothingIcon'),
])
def test_manifest_activity_alias(variant, alias_suffix):
    full_alias_name = f'org.telegram.messenger.{alias_suffix}'
    tree = ET.parse(MANIFEST_PATH)
    root = tree.getroot()

    ns = {'android': 'http://schemas.android.com/apk/res/android'}
    aliases = root.findall('.//activity-alias')
    matching = None
    for a in aliases:
        name_attr = a.attrib.get('{http://schemas.android.com/apk/res/android}name')
        if name_attr == full_alias_name:
            matching = a
            break

    assert matching is not None, f'activity-alias {full_alias_name} not found in AndroidManifest.xml'

    target = matching.attrib.get('{http://schemas.android.com/apk/res/android}targetActivity')
    assert target == 'org.telegram.ui.LaunchActivity', f'Expected targetActivity LaunchActivity, got {target}'

    icon = matching.attrib.get('{http://schemas.android.com/apk/res/android}icon')
    assert icon == f'@mipmap/ic_launcher_moltengram_{variant}', f'Unexpected icon attribute: {icon}'

    round_icon = matching.attrib.get('{http://schemas.android.com/apk/res/android}roundIcon')
    assert round_icon == f'@mipmap/ic_launcher_moltengram_{variant}_round', f'Unexpected roundIcon attribute: {round_icon}'

    exported = matching.attrib.get('{http://schemas.android.com/apk/res/android}exported')
    assert exported == 'true', f'activity-alias must be exported="true"'

    # Check intent filter
    actions = [act.attrib.get('{http://schemas.android.com/apk/res/android}name')
               for act in matching.findall('.//action')]
    assert 'android.intent.action.MAIN' in actions

    categories = [cat.attrib.get('{http://schemas.android.com/apk/res/android}name')
                  for cat in matching.findall('.//category')]
    assert 'android.intent.category.LAUNCHER' in categories
    assert 'android.intent.category.MULTIWINDOW_LAUNCHER' in categories


def test_launcher_icon_controller_enums():
    with open(LAUNCHER_CONTROLLER_PATH, 'r', encoding='utf-8') as f:
        code = f.read()

    tree = javalang.parse.parse(code)
    enum_constants = {}
    for path, node in tree.filter(javalang.tree.EnumConstantDeclaration):
        enum_constants[node.name] = node

    assert 'MOLTENGRAM_HALLOWEEN' in enum_constants, 'MOLTENGRAM_HALLOWEEN enum entry missing'
    assert 'MOLTENGRAM_NOTHING' in enum_constants, 'MOLTENGRAM_NOTHING enum entry missing'

    # Check MOLTENGRAM_HALLOWEEN declaration content in source
    assert 'MOLTENGRAM_HALLOWEEN("MoltenGramHalloweenIcon", R.drawable.moltengram_halloween_icon_background,' in code
    assert 'R.string.AppIconMoltenGramHalloween' in code
    assert '0xFFFF7701' in code

    # Check MOLTENGRAM_NOTHING declaration content in source
    assert 'MOLTENGRAM_NOTHING("MoltenGramNothingIcon", R.drawable.moltengram_nothing_icon_background,' in code
    assert 'R.string.AppIconMoltenGramNothing' in code
    assert '"Nothing OS red glyph matrix"' in code
    assert '0xFFFF0401' in code


def test_strings_resources_defined():
    for values_dir, expected_hw in [
        ('values', 'Halloween'),
        ('values-ru-rRU', 'Хэллоуин')
    ]:
        xml_path = os.path.join(RES_DIR, values_dir, 'strings_oe.xml')
        assert os.path.exists(xml_path), f'{xml_path} must exist'
        tree = ET.parse(xml_path)
        root = tree.getroot()

        strings_dict = {elem.attrib.get('name'): elem.text for elem in root.findall('string')}
        assert 'AppIconMoltenGramHalloween' in strings_dict
        assert strings_dict['AppIconMoltenGramHalloween'] == expected_hw
        assert 'AppIconMoltenGramNothing' in strings_dict
        assert strings_dict['AppIconMoltenGramNothing'] == 'Nothing OS'
