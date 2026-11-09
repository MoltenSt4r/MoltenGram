import os
import pytest

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def test_root_settings_structure():
    root_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "app", "exteraless", "settings", "OpenExteraSettingsActivity.java"
    )
    with open(root_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify search menu item and cloud menu item
    assert "MENU_SEARCH" in content
    assert "MENU_CLOUD" in content
    assert "showSettingsSearchDialog()" in content

    # Verify root categories: general, appearance, chats, plugins, tools, other
    assert "generalRow = addRow(\"general\");" in content
    assert "appearanceRow = addRow(\"appearance\");" in content
    assert "chatsRow = addRow(\"chats\");" in content
    assert "pluginsRow = addRow(\"plugins\");" in content
    assert "toolsRow = addRow(\"tools\");" in content
    assert "otherRow = addRow(\"other\");" in content

    # Loose tools should not be in root categories anymore
    assert "voiceChangerRow = addRow" not in content
    assert "deviceSpoofRow = addRow" not in content
    assert "torRow = addRow" not in content
    assert "shizukuRow = addRow" not in content


def test_tools_activity_structure():
    tools_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "app", "exteraless", "settings", "OpenExteraToolsActivity.java"
    )
    assert os.path.isfile(tools_path), "OpenExteraToolsActivity.java must exist"

    with open(tools_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Must contain voice changer, ayu moments, tor vpn, device spoofing, and shizuku
    assert "voiceChangerRow = addRow(\"voiceChanger\");" in content
    assert "ayuMomentsRow = addRow(\"ayuMoments\");" in content
    assert "torRow = addRow(\"tor\");" in content
    assert "deviceSpoofRow = addRow(\"deviceSpoof\");" in content
    assert "shizukuRow = addRow(\"shizuku\");" in content

    assert "OpenExteraVoiceChangerActivity" in content
    assert "OpenExteraAyuMomentsActivity" in content
    assert "TorSettingsActivity" in content
    assert "DeviceSpoofActivity" in content
    assert "ShizukuSettingsActivity" in content


def test_base_neko_settings_search_dialog():
    base_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "tw", "nekomimi", "nekogram", "settings", "BaseNekoSettingsActivity.java"
    )
    with open(base_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "protected void showSettingsSearchDialog()" in content
    assert "SettingsHelper.onCreateSearchArray" in content


def test_settings_helper_indexes_tools():
    helper_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "tw", "nekomimi", "nekogram", "helpers", "SettingsHelper.java"
    )
    with open(helper_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "OpenExteraToolsActivity" in content
    assert "case \"tools\":" in content
    assert "case \"exteraless_tools\":" in content


def test_text_detail_settings_cell_launcher_icon_preview():
    cell_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Cells", "TextDetailSettingsCell.java"
    )
    with open(cell_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Uses LauncherIconController.createIconPreviewDrawable for circular clipping
    assert "createIconPreviewDrawable(getContext(), icon, 36)" in content
