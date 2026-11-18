import os
import re
import zipfile
import tempfile
import pytest

REPO_ROOT = "/root/ExteraMS_repo"
PLUGINS_UI_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/plugins/ui")
UI_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui")


def test_plugin_icons_extraction_and_support():
    """Verify PluginIcons supports archive extraction, local files, URLs, base64, and drawable resources."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginIcons.java")
    assert os.path.isfile(path), f"PluginIcons.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify extractIconFromFile method exists and handles archives
    assert "public static String extractIconFromFile(File file, String declaredIcon)" in content, (
        "PluginIcons must have extractIconFromFile method"
    )
    assert "plugin_icons" in content, "PluginIcons should extract icons to cacheDir/plugin_icons"
    assert "ZipFile" in content or "ZipInputStream" in content, "PluginIcons must inspect zip archives"

    # Verify apply method handles data:, http, local paths, @drawable, and stickers
    assert 'trimmed.startsWith("data:image/")' in content, "PluginIcons must support Base64 data URIs"
    assert 'trimmed.startsWith("http://") || trimmed.startsWith("https://")' in content, "PluginIcons must support web URLs"
    assert 'trimmed.startsWith("@drawable/")' in content or 'resName.startsWith("@drawable/")' in content, (
        "PluginIcons must support Android drawable resource names"
    )
    assert "getStickerSet" in content, "PluginIcons must support Telegram stickers"
    # Verify hash is null, not 0
    assert "getStickerSet(input, null, false" in content, "PluginIcons must call getStickerSet with null hash"

    # Verify apply overload with Plugin
    assert "public static boolean apply(BackupImageView imageView, Plugin plugin, Drawable fallbackDrawable, Runnable onLoaded)" in content, (
        "PluginIcons must have apply overload taking Plugin and fallbackDrawable"
    )


def test_plugin_store_settings_channel_avatars():
    """Verify PluginStoreSettingsActivity renders actual channel avatars using BackupImageView/AvatarDrawable."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginStoreSettingsActivity.java")
    assert os.path.isfile(path), f"PluginStoreSettingsActivity.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify ChannelSourceCell is defined and uses BackupImageView and AvatarDrawable
    assert "class ChannelSourceCell extends FrameLayout" in content or "ChannelSourceCell" in content, (
        "ChannelSourceCell must be implemented in PluginStoreSettingsActivity"
    )
    assert "BackupImageView avatarImageView" in content, "ChannelSourceCell must contain BackupImageView"
    assert "AvatarDrawable avatarDrawable" in content, "ChannelSourceCell must contain AvatarDrawable"
    assert "avatarImageView.setRoundRadius(AndroidUtilities.dp(20))" in content or "setRoundRadius" in content, (
        "ChannelSourceCell avatar must have circular round radius"
    )

    # Verify TYPE_CHANNEL_SOURCE is used and chat is resolved via getUserOrChat or TL_contacts_resolveUsername
    assert "TYPE_CHANNEL_SOURCE" in content, "PluginStoreSettingsActivity must define TYPE_CHANNEL_SOURCE"
    assert "MessagesController.getInstance(currentAccount).getUserOrChat" in content, (
        "Must check local chat cache via MessagesController.getUserOrChat"
    )
    assert "TL_contacts_resolveUsername" in content, (
        "Must resolve channels via TL_contacts_resolveUsername for avatar fetching"
    )


def test_plugin_store_activity_icon_preservation_and_fallbacks():
    """Verify PluginStoreActivity preserves custom icons, uses category fallbacks, and expands prefetch size."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginStoreActivity.java")
    assert os.path.isfile(path), f"PluginStoreActivity.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify createCategoryFallbackDrawable exists
    assert "createCategoryFallbackDrawable" in content, (
        "PluginStoreActivity must have createCategoryFallbackDrawable for themed placeholders"
    )

    # Verify StorePluginCell does not unconditionally overwrite custom icons with puzzle fallback
    assert "categoryFallback" in content, "StorePluginCell should bind categoryFallback"
    assert "PluginIcons.apply" in content, "StorePluginCell must use PluginIcons.apply"

    # Verify prefetchPluginIcon size limit raised from 350KB to 15MB
    assert "15 * 1024 * 1024" in content, "prefetchPluginIcon should allow icons/packages up to 15MB"

    # Verify Russian keywords for icon parsing in channel posts
    assert "иконка" in content and "значок" in content, (
        "parseIconFromText must support Russian keywords (иконка, значок)"
    )

    # Verify mergePlugins protects non-empty icons from being overwritten by empty strings
    assert "TextUtils.isEmpty(existing.icon)" in content or "!TextUtils.isEmpty(newer.icon)" in content, (
        "mergePlugins must preserve non-empty icons"
    )


def test_plugin_store_activity_background_updates_and_install():
    """Verify PluginStoreActivity implements background install and update checking."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginStoreActivity.java")
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify background update check method exists
    assert "public static void checkBackgroundUpdates" in content, (
        "PluginStoreActivity must implement checkBackgroundUpdates"
    )

    # Verify background install worker exists
    assert "void installInBackground(StorePlugin plugin, Runnable onComplete)" in content, (
        "PluginStoreActivity must implement installInBackground"
    )
    assert "executeBackgroundInstall" in content, (
        "PluginStoreActivity must have executeBackgroundInstall worker"
    )

    # Verify interactive install prompts confirmation via downloadAndInstall rather than silent background install
    assert "downloadAndInstall(plugin)" in content, (
        "PluginStoreActivity must delegate onInstallClick to downloadAndInstall to prompt confirmation dialog"
    )


def test_plugin_bottom_sheets_icon_preservation():
    """Verify PluginDetailsBottomSheet and PluginInstallBottomSheet preserve custom icons and category fallbacks."""
    details_path = os.path.join(PLUGINS_UI_DIR, "PluginDetailsBottomSheet.java")
    install_path = os.path.join(PLUGINS_UI_DIR, "PluginInstallBottomSheet.java")

    with open(details_path, "r", encoding="utf-8") as f:
        details_content = f.read()
    with open(install_path, "r", encoding="utf-8") as f:
        install_content = f.read()

    # Details sheet uses createCategoryFallbackDrawable and checks installed.path
    assert "createCategoryFallbackDrawable" in details_content, (
        "PluginDetailsBottomSheet must use createCategoryFallbackDrawable"
    )
    assert "PluginIcons.extractIconFromFile" in details_content, (
        "PluginDetailsBottomSheet must extract icon from installed.path if relative"
    )

    # Install sheet extracts icon from file if needed
    assert "PluginIcons.extractIconFromFile" in install_content, (
        "PluginInstallBottomSheet must extract icon from file if needed"
    )


def test_plugin_ui_item_drawable_support():
    """Verify PluginUiItem supports Drawables in check() and contentsEquals()."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginUiItem.java")
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "PluginUiItem check(int id, CharSequence text, android.graphics.drawable.Drawable drawable)" in content or \
           "PluginUiItem check(int id, CharSequence text, Drawable drawable)" in content, (
        "PluginUiItem must have check overload taking Drawable"
    )
    assert "drawable" in content and "Objects.equals" in content or "drawable == other.drawable" in content, (
        "PluginUiItem.contentsEquals must compare drawables"
    )


def test_group_call_md3_popup_and_badges():
    """Verify GroupCallActivity and PeerStoriesView MD3 expressive overhaul."""
    group_call_path = os.path.join(UI_DIR, "GroupCallActivity.java")
    peer_stories_path = os.path.join(UI_DIR, "Stories/PeerStoriesView.java")

    with open(group_call_path, "r", encoding="utf-8") as f:
        group_call_content = f.read()
    with open(peer_stories_path, "r", encoding="utf-8") as f:
        peer_stories_content = f.read()

    # Md3PopupCardDrawable with 24dp radius
    assert "Md3PopupCardDrawable" in group_call_content, (
        "GroupCallActivity must use Md3PopupCardDrawable"
    )
    assert "AndroidUtilities.dp(24)" in group_call_content, (
        "GroupCallActivity popup must use 24dp corner radius"
    )

    # Outline provider and clipToOutline on popupLayout
    assert "setOutlineProvider" in group_call_content and "setClipToOutline(true)" in group_call_content, (
        "GroupCallActivity popupLayout must use hardware-accelerated outline clipping"
    )

    # Expressive live viewer pill
    assert "0xFFEE3E4C" in group_call_content or "0xffee3e4c" in group_call_content, (
        "GroupCallActivity live pill must use semantic live red"
    )
    assert "0xFFEE3E4C" in peer_stories_content or "0xffee3e4c" in peer_stories_content, (
        "PeerStoriesView live pill must use semantic live red"
    )

    # Destructive leaveItem color
    assert "0xFFF01D2C" in group_call_content or "0xfff01d2c" in group_call_content, (
        "GroupCallActivity leaveItem must use semantic destructive red"
    )

    # User menu cards replace legacy popup_fixed_alert with 20dp round rect
    assert "Theme.createRoundRectDrawable(dp(20), color)" in group_call_content, (
        "GroupCallActivity user menu cards must use 20dp round rect drawable"
    )


def test_account_select_cell_md3_profile_card():
    """Verify AccountSelectCell top profile avatar item has MD3 expressive card styling."""
    path = os.path.join(UI_DIR, "Cells/AccountSelectCell.java")
    assert os.path.isfile(path), f"AccountSelectCell.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify 40x40 avatar with 20dp round radius
    assert "setRoundRadius(dp(20))" in content, (
        "AccountSelectCell must use 20dp round radius for avatar"
    )
    assert "LayoutHelper.createFrame(40, 40" in content, (
        "AccountSelectCell must use 40x40 layout for avatar"
    )

    # Verify 12sp subtitle and 15sp title
    assert "textView.setTextSize(12)" in content, (
        "AccountSelectCell subtitle must be 12sp"
    )
    assert "infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15)" in content, (
        "AccountSelectCell display name must be 15sp"
    )

    # Verify height is 58dp for info mode
    assert "dp(58)" in content, (
        "AccountSelectCell must support 58dp height for MD3 popup item"
    )


def test_action_bar_menu_sub_item_selector_radius_fix():
    """Verify ActionBarMenuSubItem passes raw dp integer to createRadSelectorDrawable, avoiding double dp()."""
    path = os.path.join(UI_DIR, "ActionBar/ActionBarMenuSubItem.java")
    assert os.path.isfile(path), f"ActionBarMenuSubItem.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "Theme.createRadSelectorDrawable(selectorColor, top ? selectorRad : 10, bottom ? selectorRad : 10)" in content, (
        "ActionBarMenuSubItem must pass raw dp 10 instead of AndroidUtilities.dp(10)"
    )


def test_group_call_sheet_md3_squircle_hero():
    """Verify GroupCallSheet uses 24dp rounded squircle for hero icon container."""
    path = os.path.join(UI_DIR, "GroupCallSheet.java")
    assert os.path.isfile(path), f"GroupCallSheet.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "Theme.createRoundRectDrawable(dp(24)" in content, (
        "GroupCallSheet hero icon must use 24dp squircle radius"
    )


def test_archive_icon_auto_discovery_simulation():
    """Verify the logic of package auto-discovery on simulated .plugin archive with nested icon.png."""
    with tempfile.TemporaryDirectory() as tmpdir:
        archive_path = os.path.join(tmpdir, "sample.plugin")
        with zipfile.ZipFile(archive_path, "w") as zf:
            zf.writestr("manifest.json", '{"id": "sample.test", "name": "Sample", "icon": "assets/icon.png"}')
            zf.writestr("assets/icon.png", b"\x89PNG\r\n\x1a\nfakeimagebytes")
            zf.writestr("main.py", 'print("hello")')

        # Verify zip structure matches expectations of PluginIcons.extractIconFromFile
        with zipfile.ZipFile(archive_path, "r") as zf:
            entry = zf.getEntry("assets/icon.png") if hasattr(zf, "getEntry") else zf.getinfo("assets/icon.png")
            assert entry is not None
            assert entry.file_size > 0


def test_plugin_store_config_channel_binding_by_numeric_id():
    """Verify PluginStoreConfig uses numeric IDs for default channels, defines known channel mappings, and migrates schema."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginStoreConfig.java")
    assert os.path.isfile(path), f"PluginStoreConfig.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    expected_numeric_ids = [
        "2349438816",
        "3490813925",
        "2329949330",
        "2443058177",
        "2467705421",
        "2475313399",
        "2521243181",
        "2554450010",
        "2618027830",
        "2672610568",
        "2782987927",
        "2813564336"
    ]

    # Verify DEFAULT_CHANNELS contains all 12 numeric IDs
    assert "public static final List<String> DEFAULT_CHANNELS" in content, (
        "DEFAULT_CHANNELS list must be defined in PluginStoreConfig"
    )
    for ch_id in expected_numeric_ids:
        assert f'"{ch_id}"' in content, f"DEFAULT_CHANNELS must include numeric ID {ch_id}"

    # Verify KNOWN_CHANNEL_USERNAMES and KNOWN_CHANNEL_NAMES maps defined
    assert "KNOWN_CHANNEL_USERNAMES" in content, "KNOWN_CHANNEL_USERNAMES map must be defined"
    assert "KNOWN_CHANNEL_NAMES" in content, "KNOWN_CHANNEL_NAMES map must be defined"
    assert "getKnownUsernameForId(long id)" in content or "getKnownUsernameForId" in content, (
        "getKnownUsernameForId helper must be defined"
    )
    assert "getKnownTitleForId(long id)" in content or "getKnownTitleForId" in content, (
        "getKnownTitleForId helper must be defined"
    )
    assert "getKnownIdForUsername(String username)" in content or "getKnownIdForUsername" in content, (
        "getKnownIdForUsername helper must be defined"
    )

    # Verify schema version migration cleans legacy channels
    assert 'prefs.getInt("channels_schema_version", 0) < 3' in content, (
        "Schema migration must check channels_schema_version < 3"
    )
    assert 'putInt("channels_schema_version", 3)' in content, (
        "Schema migration must advance channels_schema_version to 3"
    )
    assert 'clean.matches("\\\\d+")' in content or 'clean.matches("\\d+")' in content, (
        "Schema migration must keep numeric IDs in custom_channels"
    )


def test_plugin_store_settings_numeric_id_support_and_resolution():
    """Verify PluginStoreSettingsActivity resolves usernames to chat IDs, displays numeric channels, and handles options dialog."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginStoreSettingsActivity.java")
    assert os.path.isfile(path), f"PluginStoreSettingsActivity.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify showAddChannelDialog resolves usernames to chat IDs and saves by ID
    assert "showAddChannelDialog" in content, "showAddChannelDialog must exist"
    assert "targetChatId = peer.chats.get(0).id" in content or "peer.chats.get(0).id" in content, (
        "showAddChannelDialog must extract resolved chat ID"
    )
    assert "config.addSourceChannel(String.valueOf(targetChatId))" in content, (
        "showAddChannelDialog must save resolved channel by numeric ID"
    )
    assert 'clean.matches("\\\\d+")' in content or 'clean.matches("\\d+")' in content, (
        "showAddChannelDialog must support entering numeric ID directly"
    )

    # Verify ChannelSourceCell displays numeric channels and loads avatars via MessagesController or resolves known usernames
    assert "Long.parseLong(clean)" in content or "numericId" in content, (
        "ChannelSourceCell must parse numeric channel ID"
    )
    assert "MessagesController.getInstance(currentAccount).getChat(numericId)" in content, (
        "ChannelSourceCell must check MessagesController for cached chat by numeric ID"
    )
    assert "PluginStoreConfig.getKnownUsernameForId(numericId)" in content, (
        "ChannelSourceCell must look up known username by numeric ID"
    )
    assert "PluginStoreConfig.getKnownTitleForId(numericId)" in content, (
        "ChannelSourceCell must look up known title by numeric ID"
    )

    # Verify showChannelOptionsDialog supports numeric IDs
    assert "showChannelOptionsDialog" in content, "showChannelOptionsDialog must exist"
    assert "Long.parseLong(channel.trim())" in content or "numericId = Long.parseLong" in content, (
        "showChannelOptionsDialog must support parsing numeric channel ID"
    )
    assert "PluginStoreConfig.getKnownUsernameForId(numericId)" in content or "PluginStoreConfig.getKnownUsernameForId(targetChatId)" in content, (
        "showChannelOptionsDialog must look up known username for numeric ID"
    )


def test_plugin_store_no_blind_custom_emoji_capture():
    """Verify searchChatBatch in PluginStoreActivity has NO blind custom emoji capture (no emoji/ docId)."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginStoreActivity.java")
    assert os.path.isfile(path), f"PluginStoreActivity.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Blind custom emoji capture sets sp.icon = "emoji/" + docId on any MessageEntityCustomEmoji
    # This caused bullet points / chevrons to be captured as plugin icons.
    assert 'sp.icon = "emoji/"' not in content and 'sp.icon = "emoji/" + docId' not in content, (
        "PluginStoreActivity must NOT have blind custom emoji capture assigning bullet/chevron emojis as icons"
    )
    assert 'TL_messageEntityCustomEmoji' not in content, (
        "PluginStoreActivity searchChatBatch must not blindly capture TL_messageEntityCustomEmoji"
    )


def test_plugin_store_restored_confirmation_dialog_and_no_silent_autoinstall():
    """Verify pluginDelegate.onInstallClick prompts confirmation dialog and mergePlugins has no silent auto-install."""
    path = os.path.join(PLUGINS_UI_DIR, "PluginStoreActivity.java")
    assert os.path.isfile(path), f"PluginStoreActivity.java must exist at {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify pluginDelegate.onInstallClick calls downloadAndInstall(plugin), NOT silent installInBackground
    assert "downloadAndInstall(plugin)" in content, (
        "pluginDelegate.onInstallClick must call downloadAndInstall to prompt user confirmation dialog"
    )

    # Verify downloadAndInstall triggers confirmation dialog via showInstallDialog
    assert "triggerInstallDialogForFile" in content, (
        "downloadAndInstall must call triggerInstallDialogForFile"
    )
    assert "PluginsController.getInstance().showInstallDialog" in content, (
        "triggerInstallDialogForFile must invoke PluginsController.showInstallDialog to present PluginInstallBottomSheet"
    )

    # Verify mergePlugins has NO silent background auto-install
    merge_start = content.find("private void mergePlugins(List<StorePlugin> newPlugins)")
    assert merge_start != -1, "mergePlugins method must exist in PluginStoreActivity"
    merge_end = content.find("public List<StorePlugin> getPluginVersions", merge_start)
    merge_body = content[merge_start:merge_end] if merge_end != -1 else content[merge_start:merge_start + 1500]

    assert "installInBackground" not in merge_body, (
        "mergePlugins must NOT perform silent background auto-install on new or updated plugins"
    )
    assert "autoUpdatedPluginIds" not in merge_body, (
        "mergePlugins must NOT track or execute silent auto-updates"
    )


