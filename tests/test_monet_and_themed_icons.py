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


def test_v31_styles_use_system_accent_monet():
    v31_path = os.path.join(RES_DIR, 'values-v31/styles.xml')
    with open(v31_path, 'r', encoding='utf-8') as f:
        v31_content = f.read()
    assert '@android:color/system_accent1_' in v31_content, "values-v31 must define system_accent1"

    v31_night_path = os.path.join(RES_DIR, 'values-night-v31/styles.xml')
    with open(v31_night_path, 'r', encoding='utf-8') as f:
        night_content = f.read()
    assert '@android:color/system_accent1_' in night_content, "values-night-v31 must define system_accent1"


def test_theme_progress_circle_falls_back_to_monet_accent():
    theme_path = os.path.join(JAVA_DIR, 'org/telegram/ui/ActionBar/Theme.java')
    with open(theme_path, 'r', encoding='utf-8') as f:
        content = f.read()
    assert 'fallbackKeys.put(key_progressCircle, key_featuredStickers_addButton);' in content


def test_halloween_icon_pack_maps_all_drawer_icons():
    base_packs_path = os.path.join(JAVA_DIR, 'app/exteraless/icons/BaseIconPacks.java')
    with open(base_packs_path, 'r', encoding='utf-8') as f:
        content = f.read()
    assert 'msg_calls_hw_solar' in content
    assert 'msg_contacts_hw_solar' in content
    assert 'msg_groups_hw_solar' in content
    assert 'msg_saved_hw_solar' in content
    assert 'msg_settings_hw_solar' in content


def test_moltengram_launcher_icons_use_unified_monet_flame():
    for variant in ['nothing', 'halloween', 'default', 'classic', 'proton']:
        name = f'ic_launcher_moltengram_{variant}.xml' if variant != 'default' else 'ic_launcher_moltengram.xml'
        xml_path = os.path.join(RES_DIR, 'mipmap-anydpi-v26', name)
        assert os.path.exists(xml_path), f'{xml_path} must exist'
        with open(xml_path, 'r', encoding='utf-8') as f:
            content = f.read()
        assert '@drawable/moltengram_icon_monochrome' in content, f'{name} must use unified Monet icon moltengram_icon_monochrome'


def test_package_installer_session_support_and_dialog_inline_progress_monet():
    ota_manager_path = os.path.join(JAVA_DIR, 'app/exteraless/ota/MoltenGramOtaManager.java')
    with open(ota_manager_path, 'r', encoding='utf-8') as f:
        ota_content = f.read()
    assert 'installViaPackageInstallerSession' in ota_content, "OTA Manager must support PackageInstaller.Session"
    assert 'USER_ACTION_NOT_REQUIRED' in ota_content, "Must use USER_ACTION_NOT_REQUIRED for silent in-app update"

    receiver_path = os.path.join(JAVA_DIR, 'app/exteraless/ota/MoltenGramPackageInstallReceiver.java')
    assert os.path.exists(receiver_path), f"{receiver_path} must exist"

    manifest_path = os.path.join(REPO_ROOT, 'TMessagesProj/src/main/AndroidManifest.xml')
    with open(manifest_path, 'r', encoding='utf-8') as f:
        manifest_content = f.read()
    assert 'MoltenGramPackageInstallReceiver' in manifest_content, "MoltenGramPackageInstallReceiver must be in manifest"

    theme_path = os.path.join(JAVA_DIR, 'org/telegram/ui/ActionBar/Theme.java')
    with open(theme_path, 'r', encoding='utf-8') as f:
        theme_content = f.read()
    assert 'fallbackKeys.put(key_dialog_inlineProgress, key_featuredStickers_addButton);' in theme_content, (
        "Inline progress dialog must follow Monet accent key_featuredStickers_addButton"
    )
    assert 'fallbackKeys.put(key_dialogLineProgress, key_featuredStickers_addButton);' in theme_content
    assert 'fallbackKeys.put(key_dialogLineProgressBackground, key_featuredStickers_unread);' in theme_content

    # Manifest checks for unattended updates
    assert 'UPDATE_PACKAGES_WITHOUT_USER_ACTION' in manifest_content, (
        "Manifest must declare UPDATE_PACKAGES_WITHOUT_USER_ACTION permission for Android 12+/14+ unattended installs"
    )
    assert 'android:name="app.exteraless.ota.MoltenGramPackageInstallReceiver"\n            android:exported="true"' in manifest_content or \
           'android:name="app.exteraless.ota.MoltenGramPackageInstallReceiver" android:exported="true"' in manifest_content or \
           ('MoltenGramPackageInstallReceiver' in manifest_content and 'android:exported="true"' in manifest_content)

    # In-app Monet update screen dialog checks
    assert 'showUpdateProgressDialog' in ota_content, "OTA Manager must provide showUpdateProgressDialog"
    assert 'STYLE_WAVY' in ota_content, "In-app update dialog must style wavy circular loader"
    assert 'key_featuredStickers_addButton' in ota_content, "In-app update dialog must query dynamic Monet accent"
    assert 'isStaticBaselineFrameworkRole' in ota_content, "Must validate colors against static baseline purple"
    assert 'surface_dark' in ota_content or 'surface_light' in ota_content, "Must query dynamic Monet surface tokens"
    assert 'isDestroyed' in ota_content, "Must guard against destroyed activity window crashes"

    # Receiver background activity start protection for API 34+
    with open(receiver_path, 'r', encoding='utf-8') as f:
        receiver_content = f.read()
    assert 'ActivityOptions' in receiver_content or 'setPendingIntentBackgroundActivityStartMode' in receiver_content, (
        "Receiver must guard API 34+ background activity starts"
    )

    # MonetHelper checks
    monet_helper_path = os.path.join(JAVA_DIR, 'tw/nekomimi/nekogram/helpers/MonetHelper.java')
    with open(monet_helper_path, 'r', encoding='utf-8') as f:
        monet_content = f.read()
    assert 'public static boolean isStaticBaselineFrameworkRole' in monet_content, (
        "isStaticBaselineFrameworkRole must be public for OTA dialog validation"
    )

    # RadialProgressView force wavy check
    radial_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Components/RadialProgressView.java')
    with open(radial_path, 'r', encoding='utf-8') as f:
        radial_content = f.read()
    assert 'setStyle(int style, boolean force)' in radial_content, (
        "RadialProgressView must support forcing wavy style"
    )

    # TransparentDialog Monet checks in v31 styles
    v31_path = os.path.join(RES_DIR, 'values-v31/styles.xml')
    with open(v31_path, 'r', encoding='utf-8') as f:
        v31_content = f.read()
    assert '<style name="TransparentDialog"' in v31_content
    assert '@android:color/system_accent1_' in v31_content

    v31_night_path = os.path.join(RES_DIR, 'values-night-v31/styles.xml')
    with open(v31_night_path, 'r', encoding='utf-8') as f:
        night_content = f.read()
    assert '<style name="TransparentDialog"' in night_content
    assert '@android:color/system_accent1_' in night_content

    assert 'import android.view.View;' in ota_content, "MoltenGramOtaManager must import android.view.View"
    assert 'setWavyValues(AndroidUtilities.dp(33), AndroidUtilities.dp(4f), AndroidUtilities.dp(12), 0.05f)' in ota_content
    assert 'setProgressImmediate(0.75f)' in ota_content
    assert '0xfffef7ff' not in ota_content.lower(), "MoltenGramOtaManager must not fall back to baseline purple 0xfffef7ff"


def test_plugin_store_config_safe_string_set():
    cfg_path = os.path.join(JAVA_DIR, 'app/exteraless/plugins/ui/PluginStoreConfig.java')
    with open(cfg_path, 'r', encoding='utf-8') as f:
        cfg_content = f.read()
    assert 'getStringSetSafe' in cfg_content
    assert 'ClassCastException' in cfg_content

    backup_path = os.path.join(JAVA_DIR, 'tw/nekomimi/nekogram/helpers/SettingsBackupHelper.java')
    with open(backup_path, 'r', encoding='utf-8') as f:
        backup_content = f.read()
    assert 'isJsonArray()' in backup_content
    assert 'putStringSet' in backup_content


def test_crossfade_slider_layout_and_monet_colors():
    slider_path = os.path.join(JAVA_DIR, 'app/exteraless/player/Md3RangeSlider.java')
    with open(slider_path, 'r', encoding='utf-8') as f:
        slider_content = f.read()
    assert 'MAX_SIDE_FRACTION' in slider_content
    assert 'trackRight - norm2 * sideSpan' in slider_content
    assert '0xFF2A2B36' not in slider_content

    player_settings_path = os.path.join(JAVA_DIR, 'app/exteraless/player/PlayerSettingsActivity.java')
    with open(player_settings_path, 'r', encoding='utf-8') as f:
        ps_content = f.read()
    assert 'slider.setLabels("", "")' in ps_content


def test_ota_monet_wallpaper_seed_and_baseline_purple_rejection():
    monet_helper_path = os.path.join(JAVA_DIR, 'tw/nekomimi/nekogram/helpers/MonetHelper.java')
    with open(monet_helper_path, 'r', encoding='utf-8') as f:
        monet_content = f.read()
    assert 'getWallpaperPrimarySeed' in monet_content
    assert 'WallpaperManager.getInstance' in monet_content
    assert 'getWallpaperColors' in monet_content
    assert 'hue >= 265d && hue <= 318d' in monet_content

    ota_path = os.path.join(JAVA_DIR, 'app/exteraless/ota/MoltenGramOtaManager.java')
    with open(ota_path, 'r', encoding='utf-8') as f:
        ota_content = f.read()
    assert 'MonetHelper.getWallpaperPrimarySeed' in ota_content
    assert 'Theme.key_windowBackgroundWhite' in ota_content


def test_settings_search_formats_placeholder_strings():
    sh_path = os.path.join(JAVA_DIR, 'tw/nekomimi/nekogram/helpers/SettingsHelper.java')
    with open(sh_path, 'r', encoding='utf-8') as f:
        sh_content = f.read()
    assert '"OEAppearance:hideAllChats".equals(fullKey)' in sh_content
    assert 'LocaleController.formatString(R.string.OEAppearanceHideAllChats' in sh_content
    assert '"OEChats:replaceEdited".equals(fullKey)' in sh_content
    assert '"OEChats:hideShareButton".equals(fullKey)' in sh_content


def test_media_carousel_reactions_bottom_padding_reduced():
    cmc_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Cells/ChatMessageCell.java')
    with open(cmc_path, 'r', encoding='utf-8') as f:
        cmc_content = f.read()
    assert 'boolean hasReactionsRow = !reactionsLayoutInBubble.isEmpty && !reactionsLayoutInBubble.isSmall;' in cmc_content
    assert 'int footerHeight = (captionLayout != null && !captionAbove) ? dp(12) : (hasReactionsRow ? dp(8) : dp(26));' in cmc_content


def test_saved_messages_avatar_shape_clears_crossfade_and_sets_self_peer():
    ir_path = os.path.join(JAVA_DIR, 'org/telegram/messenger/ImageReceiver.java')
    with open(ir_path, 'r', encoding='utf-8') as f:
        ir_content = f.read()
    assert 'crossfadeImage = null;' in ir_content
    assert 'crossfadingWithThumb = false;' in ir_content
    assert 'AvatarDrawable.AVATAR_TYPE_SAVED' in ir_content

    dc_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Cells/DialogCell.java')
    with open(dc_path, 'r', encoding='utf-8') as f:
        dc_content = f.read()
    assert 'avatarImage.setPeerType(app.exteraless.appearance.Md3AvatarShapes.PEER_TYPE_SELF);' in dc_content


def test_mini_player_updates_dialogs_floating_button_offset():
    da_path = os.path.join(JAVA_DIR, 'org/telegram/ui/DialogsActivity.java')
    with open(da_path, 'r', encoding='utf-8') as f:
        da_content = f.read()
    assert 'updateStoriesPosting();\n        updateFloatingButtonOffset();' in da_content

    mt_path = os.path.join(JAVA_DIR, 'org/telegram/ui/MainTabsActivity.java')
    with open(mt_path, 'r', encoding='utf-8') as f:
        mt_content = f.read()
    assert 'miniPlayer.update(true);\n            onMiniPlayerOffsetChanged();' in mt_content


def test_player_sheet_custom_cover_shape_eliminates_square_shadow_and_morph_rect():
    ps_path = os.path.join(JAVA_DIR, 'app/exteraless/player/PlayerSheet.java')
    with open(ps_path, 'r', encoding='utf-8') as f:
        ps_content = f.read()
    assert 'outline.setAlpha(0f);' in ps_content
    assert 'Md3AvatarShapes.getShapePath' in ps_content

    ci_path = os.path.join(JAVA_DIR, 'app/exteraless/player/CoverImage.java')
    with open(ci_path, 'r', encoding='utf-8') as f:
        ci_content = f.read()
    assert 'setTranslationZ(0);' in ci_content


def test_plugin_store_precompiles_regex_and_caches_installed_lookup():
    psa_path = os.path.join(JAVA_DIR, 'app/exteraless/plugins/ui/PluginStoreActivity.java')
    with open(psa_path, 'r', encoding='utf-8') as f:
        psa_content = f.read()
    assert 'NORM_VERSION_SUFFIX_PATTERN' in psa_content
    assert 'ensureInstalledPluginsSnap' in psa_content
    assert 'scheduleUiRefresh' in psa_content


def test_md3_avatar_shapes_inline_infinite_carousel():
    mas_path = os.path.join(JAVA_DIR, 'app/exteraless/appearance/Md3AvatarShapesActivity.java')
    with open(mas_path, 'r', encoding='utf-8') as f:
        mas_content = f.read()
    assert 'TYPE_SHAPE_CAROUSEL' in mas_content
    assert 'PeerShapeCarouselCell' in mas_content
    assert 'INFINITE_COUNT = Integer.MAX_VALUE' in mas_content
    assert 'Math.floorMod(position, Md3AvatarShapes.SHAPES_COUNT)' in mas_content


def test_media_carousel_scrim_context_menu_and_sibling_suppression():
    ca_path = os.path.join(JAVA_DIR, 'org/telegram/ui/ChatActivity.java')
    with open(ca_path, 'r', encoding='utf-8') as f:
        ca_content = f.read()
    assert 'if (cell.isSiblingGroupCarouselMessage()) {' in ca_content
    assert 'else if (cell != null && cell.isMediaCarouselMessage())' in ca_content
    assert '!cell.isMediaCarouselMessage()' in ca_content

    cmc_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Cells/ChatMessageCell.java')
    with open(cmc_path, 'r', encoding='utf-8') as f:
        cmc_content = f.read()
    assert 'if (!isSiblingGroupCarouselMessage() && (currentPosition == null || isMediaCarouselMessage() || (currentPosition.flags & captionFlag()) != 0))' in cmc_content
    assert 'public boolean hasReactionsToDraw() {\n        if (isSiblingGroupCarouselMessage()) {\n            return false;\n        }' in cmc_content


def test_ota_update_smooth_wave_and_single_stage_transition():
    m3_path = os.path.join(JAVA_DIR, 'app/exteraless/appearance/M3CircularProgress.java')
    with open(m3_path, 'r', encoding='utf-8') as f:
        m3_content = f.read()
    assert 'Math.max(64, Math.min(720,' in m3_content
    assert 'effectiveWaveLength' in m3_content, "M3CircularProgress must quantize wave length"
    assert 'rampDist' in m3_content or 'envelope' in m3_content, "M3CircularProgress must taper wave amplitude at ends"
    assert 'getWaveAmplitude' in m3_content, "M3CircularProgress must provide getWaveAmplitude"

    radial_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Components/RadialProgressView.java')
    with open(radial_path, 'r', encoding='utf-8') as f:
        radial_content = f.read()
    assert 'strokeInset' in radial_content, "RadialProgressView must inset cicleRect to prevent clipping"

    ota_path = os.path.join(JAVA_DIR, 'app/exteraless/ota/MoltenGramOtaManager.java')
    with open(ota_path, 'r', encoding='utf-8') as f:
        ota_content = f.read()
    assert 'isSelfInstallerOfRecord' in ota_content
    assert 'PACKAGE_SOURCE_STORE' in ota_content
    assert 'setProgressImmediate(0.75f)' in ota_content


def test_media_carousel_reactions_binding_and_touch():
    cmc_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Cells/ChatMessageCell.java')
    with open(cmc_path, 'r', encoding='utf-8') as f:
        cmc_content = f.read()
    assert 'reactionsLayoutInBubble.checkTouchEvent(event)' in cmc_content
    assert 'isMediaCarouselMessage() || ((currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0)' in cmc_content
    assert 'isMediaCarouselMessage() ? false : !messageObject.shouldDrawReactionsInLayout()' in cmc_content

    ca_path = os.path.join(JAVA_DIR, 'org/telegram/ui/ChatActivity.java')
    with open(ca_path, 'r', encoding='utf-8') as f:
        ca_content = f.read()
    assert 'AppearanceConfig.mediaGroupCarousel()' in ca_content
    assert 'cell.isSiblingGroupCarouselMessage()' in ca_content
    assert 'cell.hasReactionsToDraw() || (message != null && message.hasReactions())' in ca_content


def test_plugin_store_direct_download_and_safe_username_resolution():
    psa_path = os.path.join(JAVA_DIR, 'app/exteraless/plugins/ui/PluginStoreActivity.java')
    with open(psa_path, 'r', encoding='utf-8') as f:
        psa_content = f.read()
    assert 'downloadDocumentAndInstall' in psa_content
    assert 'openTelegramLinkSafely' in psa_content
    assert 'searchPluginAcrossMirrors' in psa_content
    assert 'parseTelegramUsernameAndPostId' in psa_content

    pdbs_path = os.path.join(JAVA_DIR, 'app/exteraless/plugins/ui/PluginDetailsBottomSheet.java')
    with open(pdbs_path, 'r', encoding='utf-8') as f:
        pdbs_content = f.read()
    assert 'parentActivity.downloadAndInstall(plugin)' in pdbs_content
    assert 'storeActivity.downloadAndInstall(ver)' in pdbs_content
    assert 'openTelegramLinkSafely' in pdbs_content


def test_fast_reactions_race_condition_fix():
    ca_path = os.path.join(JAVA_DIR, 'org/telegram/ui/ChatActivity.java')
    with open(ca_path, 'r', encoding='utf-8') as f:
        ca_content = f.read()
    assert 'currentReactionRunnable' in ca_content
    assert 'executed[0]' in ca_content
    assert 'messageInList.messageOwner.reactions = message.messageOwner.reactions' in ca_content


def test_noto_animated_emoji_and_current_emoji_reactions():
    kt_path = os.path.join(REPO_ROOT, 'TMessagesProj/src/main/kotlin/app/exteraless/OpenExteraConfig.kt')
    with open(kt_path, 'r', encoding='utf-8') as f:
        kt_content = f.read()
    assert 'useNotoAnimatedEmoji' in kt_content
    assert 'useCurrentEmojiForReactions' in kt_content

    noto_path = os.path.join(JAVA_DIR, 'app/exteraless/chats/NotoEmojiHelper.java')
    assert os.path.exists(noto_path)
    with open(noto_path, 'r', encoding='utf-8') as f:
        noto_content = f.read()
    assert 'getWebpUrl' in noto_content

    rcl_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Components/ReactionsContainerLayout.java')
    with open(rcl_path, 'r', encoding='utf-8') as f:
        rcl_content = f.read()
    assert 'useCurrentEmojiForReactions()' in rcl_content

    rlb_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Components/Reactions/ReactionsLayoutInBubble.java')
    with open(rlb_path, 'r', encoding='utf-8') as f:
        rlb_content = f.read()
    assert 'useCurrentEmojiForReactions()' in rlb_content

    reo_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Components/Reactions/ReactionsEffectOverlay.java')
    with open(reo_path, 'r', encoding='utf-8') as f:
        reo_content = f.read()
    assert 'import android.graphics.drawable.Drawable;' in reo_content
    assert 'import org.telegram.messenger.Emoji;' in reo_content
    assert 'useCurrentEmojiForReactions()' in reo_content

    assert 'Emoji.getEmojiBigDrawable' not in rlb_content, "ReactionsLayoutInBubble must not call getEmojiBigDrawable"
    assert 'fullSize = false' in rlb_content

    assert 'Emoji.getEmojiBigDrawable' not in rcl_content, "ReactionsContainerLayout must not call getEmojiBigDrawable"
    assert 'fullSize = false' in rcl_content

    saed_path = os.path.join(JAVA_DIR, 'org/telegram/ui/SelectAnimatedEmojiDialog.java')
    with open(saed_path, 'r', encoding='utf-8') as f:
        saed_content = f.read()
    assert 'useCurrentEmojiForReactions()' in saed_content
    assert 'fullSize = false' in saed_content

    cmc_path = os.path.join(JAVA_DIR, 'org/telegram/ui/Cells/ChatMessageCell.java')
    with open(cmc_path, 'r', encoding='utf-8') as f:
        cmc_content = f.read()
    assert 'ImageLocation.getForVideoPath(messageObject.notoAnimatedEmojiUrl)' in cmc_content






