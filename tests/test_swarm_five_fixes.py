from pathlib import Path
import re

REPO = Path(__file__).resolve().parent.parent


def _read(rel: str) -> str:
    return (REPO / rel).read_text(encoding="utf-8")


# 1. Plugin icon loading & text_color adaptive tinting (including CactusPlugins/1)
def test_plugin_icons_text_color_tinting_and_cactus_pack_support():
    icons = _read("TMessagesProj/src/main/java/app/exteraless/plugins/ui/PluginIcons.java")
    assert "private static ColorFilter getTextColorFilter()" in icons
    assert "Theme.getAnimatedEmojiColorFilter(null)" in icons
    assert "Theme.key_windowBackgroundWhiteBlackText" in icons
    assert "MessageObject.isTextColorEmoji(document)" in icons
    assert "set.set != null && set.set.text_color" in icons
    assert "imageView.setColorFilter(getTextColorFilter())" in icons
    assert "imageView.setEmojiColorFilter(getTextColorFilter())" in icons
    # Sticker pack callback passes set as parentObject for animated/text_color stickers (e.g. CactusPlugins/1)
    assert "thumb != null ? thumb : fallbackDrawable, 0, set);" in icons
    # Pack/index parser handles CactusPlugins/1 format and zip fallback scan
    assert "COMMENT_ICON_PATTERN" in icons
    assert "cm.group(2)" in icons
    assert 'lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".py")' in icons


def test_plugin_icons_clear_stale_filters_and_install_fallback():
    details = _read("TMessagesProj/src/main/java/app/exteraless/plugins/ui/PluginDetailsBottomSheet.java")
    cell = _read("TMessagesProj/src/main/java/app/exteraless/plugins/ui/components/PluginCell.java")
    store = _read("TMessagesProj/src/main/java/app/exteraless/plugins/ui/PluginStoreActivity.java")
    install = _read("TMessagesProj/src/main/java/app/exteraless/plugins/ui/PluginInstallBottomSheet.java")

    for src in (details, cell, store):
        assert "setColorFilter(null);" in src
        assert "setEmojiColorFilter(null);" in src
        assert "setAnimatedEmojiDrawable(null);" in src

    assert "prefetchCallbacks" in store
    assert "Map<String, java.util.List<Runnable>> prefetchCallbacks" in store or "Map<String, List<Runnable>> prefetchCallbacks" in store
    assert "if (applied && loaded[0])" in install


# 2. Media player anti-flicker & smooth cover/lyrics loading
def test_media_player_anti_flicker_cover_and_lyrics():
    cover = _read("TMessagesProj/src/main/java/app/exteraless/player/CoverImage.java")
    lyrics_view = _read("TMessagesProj/src/main/java/app/exteraless/player/LyricsView.java")
    online_lyrics = _read("TMessagesProj/src/main/java/app/exteraless/player/OnlineLyrics.java")
    player_sheet = _read("TMessagesProj/src/main/java/app/exteraless/player/PlayerSheet.java")

    assert "setCrossfadeWithOldImage(true)" in cover
    assert "setCrossfadeDuration(200)" in cover
    assert "lastCheckedAttachTrackKey" in cover
    assert "Drawable keepThumb =" in cover
    assert "isCrossfadingWithOldImage()" in cover

    assert "if (state == newState && (newState != STATE_LYRICS || lyrics != null))" in lyrics_view
    assert "if (state == STATE_LYRICS && lyrics == value && value != null)" in lyrics_view
    assert "inFlightFetches" in online_lyrics

    assert "refreshLyricsIfNeeded(MessageObject" in player_sheet
    assert "scheduleLoadingState(" in player_sheet
    assert "cover.setMessage(mo)" in player_sheet
    assert "smallCover.setMessage(mo)" in player_sheet


# 3. Liquid Glass bubble swipe blur coordinate offset fix
def test_liquid_glass_swipe_blur_offset_fix():
    cell = _read("TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java")
    assert "float offsetX = inParentSpace ? 0f : getX();" in cell
    assert "slidingOffsetX + animationOffsetX" not in re.search(
        r"float offsetX\s*=.*?;", cell
    ).group(0)
    assert "if (getTranslationX() != tx) {\n            setTranslationX(tx);\n            if (shouldDrawGlassBubble()) {\n                invalidate();\n            }\n        }" in cell


# 4. Voice Changer MD3 UI overhaul
def test_voice_changer_md3_ui_overhaul():
    vc = _read("TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraVoiceChangerActivity.java")
    assert "extends BaseNekoSettingsActivity" in vc
    assert "TYPE_TEST_MIC_CARD = 100" in vc
    assert "TYPE_PRESETS = 101" in vc
    assert "TYPE_SLIDER_ROW = 102" in vc
    assert "isSectionContent(" in vc
    assert "class PresetChipsView" in vc
    assert "class CheckIndicatorView" in vc
    assert "class VoiceSliderCell" in vc
    assert "class MicTestCard" in vc
    assert "AvatarCornersSeekBar" not in vc
    assert "SeekBarView" in vc
    assert "AppearanceConfig.resolveThemeToken" in vc
    assert "ChatsConfig.voiceChanger" in vc
    assert "VoiceChangerController.getInstance()" in vc


# 5. Colored text global visibility & auto BotFather bot creation on emoji status without Premium
def test_colored_text_global_visibility():
    helper = _read("TMessagesProj/src/main/java/app/exteraless/chats/ColoredTextHelper.java")
    msg_obj = _read("TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java")
    edit_caption = _read("TMessagesProj/src/main/java/org/telegram/ui/Components/EditTextCaption.java")

    # applyColorSpan must not be gated by OpenExteraConfig.coloredText()
    apply_fn = re.search(r"public static void applyColorSpan\([^)]*\)\s*\{.*?\n    \}", helper, re.S)
    assert apply_fn is not None
    assert "OpenExteraConfig.coloredText()" not in apply_fn.group(0)

    # Spoiler check in MessageObject.addEntitiesToText does not skip tg://color URLs
    assert "ColoredTextHelper.isColorUrl" in msg_obj

    # EditTextCaption reuses existing zero-width marker when re-coloring
    assert "boolean reusedMarker = false;" in edit_caption
    assert "editable.removeSpan(prevUrl);" in edit_caption


def test_auto_botfather_on_emoji_status_without_premium():
    cfg = _read("TMessagesProj/src/main/kotlin/app/exteraless/OpenExteraConfig.kt")
    status_helper = _read("TMessagesProj/src/main/kotlin/app/exteraless/chats/CustomEmojiStatusHelper.kt")

    assert "fun customEmojiStatus(): Boolean = customEmojiStatus.Bool()" in cfg
    assert "val needsBotSetup = !OpenExteraConfig.botProfileSync.Bool() ||" in status_helper
    assert "BotMetadataSyncHelper.getSavedSelfBotSeed(user.id) == null" in status_helper
    assert "BotMetadataSyncHelper.ensureBotReady(account, allowInteractiveBotFather = true)" in status_helper
    assert "OpenExteraConfig.botProfileSync.setConfigBool(true)" in status_helper
    assert "ProfileInvisibleMetadata.updateSelfEmojiStatus" in status_helper
    assert "OEBotProfileSyncProgress" in status_helper
    assert "OEBotProfileSyncSuccess" in status_helper
