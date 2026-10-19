import glob
import os
import re
import pytest

REPO_ROOT = '/root/ExteraMS'
RES_DIR = os.path.join(REPO_ROOT, 'TMessagesProj/src/main/res')
JAVA_DIR = os.path.join(REPO_ROOT, 'TMessagesProj/src/main/java')


def test_all_anydpi_launcher_icons_have_monochrome():
    anydpi_dir = os.path.join(RES_DIR, 'mipmap-anydpi-v26')
    xml_files = glob.glob(os.path.join(anydpi_dir, '*.xml'))
    assert len(xml_files) > 0, "No anydpi icons found"

    missing = []
    for xml_path in xml_files:
        with open(xml_path, 'r', encoding='utf-8') as f:
            content = f.read()
        if '<monochrome' not in content:
            missing.append(os.path.basename(xml_path))
    assert len(missing) == 0, f"Icons missing monochrome vector tag: {missing}"


def test_monet_helper_dark_roles_surface_and_background_n1():
    monet_helper_path = os.path.join(JAVA_DIR, 'tw/nekomimi/nekogram/helpers/MonetHelper.java')
    with open(monet_helper_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Verify darkRoles surface and background are neutral 1 (n1:6), not neutral 2 (n2:6)
    assert 'put("background", "n1:6");' in content, "darkRoles background must map to n1:6"
    assert 'put("surface", "n1:6");' in content, "darkRoles surface must map to n1:6"
    assert 'put("surface_dim", "n1:6");' in content, "darkRoles surface_dim must map to n1:6"
    assert 'put("surface_container", "n1:12");' in content, "darkRoles surface_container must map to n1:12"
    assert 'put("on_surface", "n1:90");' in content, "darkRoles on_surface must map to n1:90"


def test_monet_helper_rejects_static_oem_baseline_roles():
    monet_helper_path = os.path.join(JAVA_DIR, 'tw/nekomimi/nekogram/helpers/MonetHelper.java')
    with open(monet_helper_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Verify baseline purple 80, purple 40, dark surface, and dark container constants exist
    assert 'd0bcff' in content.lower(), "Must detect Purple80 baseline"
    assert '6750a4' in content.lower(), "Must detect Purple40 baseline"
    assert '141218' in content.lower(), "Must detect #141218 dark surface baseline"
    assert '4a4458' in content.lower(), "Must detect #4A4458 dark container baseline"
    assert 'isStaticBaselineFrameworkRole' in content, "Must implement static baseline role detection"


def test_update_layout_uses_dynamic_monet_accent():
    update_layout_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Components/UpdateLayout.java')
    with open(update_layout_path, 'r', encoding='utf-8') as f:
        content = f.read()

    assert 'Theme.getColor(Theme.key_featuredStickers_addButton)' in content, (
        "UpdateLayout must query dynamic theme accent key_featuredStickers_addButton"
    )
