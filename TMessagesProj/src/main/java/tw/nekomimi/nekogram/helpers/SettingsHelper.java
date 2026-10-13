package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ProfileActivity.sendLogs;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.PrivacySettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import app.exteraless.ai.ui.AiSettingsActivity;
import app.exteraless.appearance.ChipFoldersSettingsActivity;
import app.exteraless.chats.TextAnimationSettingsActivity;
import app.exteraless.pillstack.PillStackSettingsActivity;
import app.exteraless.player.PlayerSettingsActivity;
import app.exteraless.plugins.ui.PluginStoreSettingsActivity;
import app.exteraless.plugins.ui.PluginsActivity;
import app.exteraless.settings.OpenExteraAppNavigationActivity;
import app.exteraless.settings.OpenExteraAppearanceActivity;
import app.exteraless.settings.OpenExteraAyuMomentsActivity;
import app.exteraless.settings.OpenExteraChatsActivity;
import app.exteraless.settings.OpenExteraCloudActivity;
import app.exteraless.settings.OpenExteraGeneralActivity;
import app.exteraless.settings.OpenExteraGlyphActivity;
import app.exteraless.settings.OpenExteraOtherActivity;
import app.exteraless.settings.OpenExteraSettingsActivity;
import app.exteraless.settings.OpenExteraUpdatesActivity;
import app.exteraless.shizuku.ShizukuSettingsActivity;
import app.exteraless.tor.TorSettingsActivity;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.settings.BaseNekoXSettingsActivity;
import tw.nekomimi.nekogram.settings.GhostModeActivity;
import tw.nekomimi.nekogram.settings.NekoAboutActivity;
import tw.nekomimi.nekogram.settings.NekoEmojiSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoPasscodeSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoTranslatorSettingsActivity;

public class SettingsHelper {

    private static final String HOST_NAGRAM = "nasettings";
    private static final String HOST_EXTERALESS = "exteraless";
    private static final String HOST_EXTERAMS = "exterams";
    private static final String HOST_MOLTENGRAM = "moltengram";

    private static final Map<String, String> SEARCH_TITLE_ALIASES = new HashMap<>();
    private static final Map<String, String> TRANSLATOR_ROWS = new HashMap<>();
    private static final Map<String, String> NAGRAM_ROWS = new HashMap<>();
    private static final Map<String, String> MOVED_ROWS = new HashMap<>();

    static {
        SEARCH_TITLE_ALIASES.put("OEGeneral:lastfm", "OEGeneralLastFm");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuGhost", "GhostMode");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuDisableAll", "OEGeneralAyuMomentsDisableAll");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuClearDatabase", "ClearMessageDatabase");
        SEARCH_TITLE_ALIASES.put("OEAppearance:appNavigation", "OEAppearanceNavigation");
        SEARCH_TITLE_ALIASES.put("OEAppearance:hideStories", "OEAppearanceStories");
        SEARCH_TITLE_ALIASES.put("OEChats:disableGreeting", "OEChatsDisableGreetingSticker");
        SEARCH_TITLE_ALIASES.put("OEChats:hideKeyboardOnScroll", "HideKeyboardOnChatScroll");
        SEARCH_TITLE_ALIASES.put("OEChats:transcribeProvider", "PremiumPreviewVoiceToText");
        SEARCH_TITLE_ALIASES.put("OEGeneral:translator", "TranslatorSettings");
        SEARCH_TITLE_ALIASES.put("OEGeneral:translateInSheet", "OEChatsTranslateInSheet");
        SEARCH_TITLE_ALIASES.put("OEGeneral:glyph", "OEGlyphTitle");
        SEARCH_TITLE_ALIASES.put("OpenExtera:channel", "ProfileChannel");
        SEARCH_TITLE_ALIASES.put("OEGeneral:downloadSpeed", "OEGeneralSpeedHeader");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Styles", "OEAppearanceMaterialDesign3");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Loading", "OEAppearanceNewLoadingStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Slider", "OEAppearanceSliderStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Switch", "OEAppearanceSwitchStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3NavBar", "OEAppearanceNewNavigationBarStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3ListItems", "OEAppearanceM3ListItems");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosStyles", "OEAppearanceIosDesign");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosNavBar", "OEAppearanceIosNavigationBarStyle");
        SEARCH_TITLE_ALIASES.put("OEChats:doubleTapReaction", "DoubleTapSetting");
        SEARCH_TITLE_ALIASES.put("OEChats:quickTransition", "OEChatsQuickTransitions");
        SEARCH_TITLE_ALIASES.put("OEChats:inlineMathResult", "OEChatsInlineMath");
        SEARCH_TITLE_ALIASES.put("OEChats:searchHashtagChat", "SearchHashtagDefaultPageChat");
        SEARCH_TITLE_ALIASES.put("OEChats:searchHashtagChannel", "SearchHashtagDefaultPageChannel");
        SEARCH_TITLE_ALIASES.put("OEChats:replaceEdited", "OEChatsReplaceEditedWithIcon");
        SEARCH_TITLE_ALIASES.put("OEChats:videoMessagesCamera", "CameraInVideoMessages");
        SEARCH_TITLE_ALIASES.put("OEChats:voskModels", "VoskModelsShort");
        MOVED_ROWS.put("chats:translateInSheet", "general");
        MOVED_ROWS.put("other:unlimitedPinnedDialogs", "general");
        MOVED_ROWS.put("other:glyph", "general");
        moveRows("chats", "menus", "bottomButton", "adminShortcuts", "chatMenu", "messageMenu",
                "groupedMessageMenu", "textStyle", "TextStyle", "mediaViewerMenu", "actionBarButtons",
                "defaultDeleteMenu", "DefaultDeleteMenu");
        moveRows("chats", "media", "cameraType", "extendedSettings", "videoMessagesCamera",
                "rememberLastUsedCamera", "zoomSlider", "staticZoom", "alwaysSendInHD", "hdrPhotos",
                "disableInstantCamera", "DisableInstantCamera", "doubleTapSeekDuration", "preferOriginalQuality",
                "videoPlayerDecoder", "VideoPlayerDecoder", "swipeToPip", "unmuteWithVolumeButtons",
                "showSmallGIF", "ShowSmallGIF", "dontAutoPlayNextVoice", "DontAutoPlayNextVoice",
                "disableProximityEvents", "DisableProximityEvents", "pauseOnMinimize", "transcribeProvider",
                "TranscribeProviderShort", "cloudflareCredentials", "CloudflareCredentials", "llmProviderGeminiKey",
                "LlmProviderGeminiKey", "transcribeProviderOpenAI", "TranscribeProviderOpenAI", "voskModels",
                "VoskModelsShort");
        moveRows("chats", "hiding", "disableTrending", "DisableTrending", "hideGroupSticker", "hideReactions",
                "disableGreeting", "hideSendAsPeer", "hideShareButton", "hideGiftButton", "hideSearchButton",
                "hideCameraTile", "premiumElements", "PremiumElements");
        moveRows("other", "media", "noiseSuppressAndVoiceEnhance", "NoiseSuppressAndVoiceEnhance",
                "enhancedVideoBitrate", "EnhancedVideoBitrate");
        moveRows("appearance", "hiding", "hideActionBarStatus", "hideStories", "HideStoriesFromHeader",
                "DisableStories", "hideFloatingButton", "hideSearchBar", "hideAllChats", "hideAi", "hideAiEditor",
                "hideAiSummary", "hideAiIv", "hideSettingsSections", "hidePremiumSection", "HidePremiumSection",
                "hideHelpSection", "HideHelpSection");
        TRANSLATOR_ROWS.put("translateButton", "showTranslate");
        TRANSLATOR_ROWS.put("translateChatButton", "TelegramUIAutoTranslate");
        TRANSLATOR_ROWS.put("translationProvider", "translationProvider");
        TRANSLATOR_ROWS.put("translateToLang", "TranslateTo");
        TRANSLATOR_ROWS.put("doNotTranslate", "DoNotTranslate");
        NAGRAM_ROWS.put("HidePremiumSection", "appearance");
        NAGRAM_ROWS.put("HideHelpSection", "appearance");
        NAGRAM_ROWS.put("HideStoriesFromHeader", "appearance");
        NAGRAM_ROWS.put("DisableStories", "appearance");
        NAGRAM_ROWS.put("MainTabsHideTitles", "navigation");
        NAGRAM_ROWS.put("MainTabsHideContacts", "navigation");
        NAGRAM_ROWS.put("MainTabsHideCallsSettings", "navigation");
        NAGRAM_ROWS.put("MainTabsHideProfile", "navigation");
        NAGRAM_ROWS.put("DisableSystemAccount", "privacy");
        NAGRAM_ROWS.put("PerformanceClass", "powersaving");
        NAGRAM_ROWS.put("VideoPlayerDecoder", "chats");
    }

    private static void moveRows(String from, String to, String... rows) {
        for (String row : rows) {
            MOVED_ROWS.put(from + ":" + row, to);
        }
    }

    private static final Set<String> EXTERALESS_SCREENS = new HashSet<>(Arrays.asList(
            "settings", "general", "appearance", "chats", "plugins", "pillstack", "other",
            "ayumoments", "navigation", "privacy", "powersaving",
            "ota_updates", "updates", "update",
            "plugin_store_settings", "plugin_store", "plugins_store",
            "shizuku_settings", "shizuku",
            "tor_settings", "tor", "tor_proxy",
            "player_settings", "player",
            "chip_folders_settings", "chip_folders", "chips",
            "text_animation_settings", "text_animation",
            "ai_settings", "ai",
            "exteraless_cloud", "cloud",
            "exteraless_glyph", "glyph",
            "ghost_mode", "ghost"));

    public static boolean isDeepLink(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith(HOST_NAGRAM + "/") || path.startsWith(HOST_MOLTENGRAM + "/")) {
            return true;
        }
        if (path.startsWith(HOST_EXTERAMS + "/")) {
            return EXTERALESS_SCREENS.contains(path.substring(HOST_EXTERAMS.length() + 1));
        }
        if (path.startsWith(HOST_EXTERALESS + "/")) {
            return EXTERALESS_SCREENS.contains(path.substring(HOST_EXTERALESS.length() + 1));
        }
        return false;
    }

    public static String linkPathFor(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "moltengram":
            case "exteraless":
            case "exterams":
                return HOST_MOLTENGRAM + "/settings";
            case "moltengram_general":
            case "exteraless_general":
            case "exterams_general":
                return HOST_MOLTENGRAM + "/general";
            case "moltengram_appearance":
            case "exteraless_appearance":
            case "exterams_appearance":
                return HOST_MOLTENGRAM + "/appearance";
            case "moltengram_chats":
            case "exteraless_chats":
            case "exterams_chats":
                return HOST_MOLTENGRAM + "/chats";
            case "moltengram_other":
            case "exteraless_other":
            case "exterams_other":
                return HOST_MOLTENGRAM + "/other";
            case "moltengram_ayumoments":
            case "exteraless_ayumoments":
            case "exterams_ayumoments":
                return HOST_MOLTENGRAM + "/ayumoments";
            case "pillstack":
                return HOST_MOLTENGRAM + "/pillstack";
            case "moltengram_navigation":
            case "exteraless_navigation":
            case "exterams_navigation":
                return HOST_MOLTENGRAM + "/navigation";
            case "ota_updates":
            case "updates":
            case "update":
                return HOST_MOLTENGRAM + "/settings";
            default:
                return HOST_MOLTENGRAM + "/" + key;
        }
    }

    public static void processDeepLink(Activity activity, Uri uri, Callback callback, Runnable unknown) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2) {
            unknown.run();
            return;
        }
        final boolean exteraless = HOST_MOLTENGRAM.equals(segments.get(0)) || HOST_EXTERAMS.equals(segments.get(0)) || HOST_EXTERALESS.equals(segments.get(0));
        if (!exteraless && !HOST_NAGRAM.equals(segments.get(0))) {
            unknown.run();
            return;
        }
        if (exteraless && segments.size() < 2) {
            unknown.run();
            return;
        }
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        BaseFragment fragment;
        BaseNekoSettingsActivity neko_fragment = null;
        BaseNekoXSettingsActivity nekox_fragment = null;
        OpenExteraAppNavigationActivity navigation_fragment = null;
        final String screen = nagramScreen(segments.size() == 1 ? null : segments.get(1), row);
        if (screen != null) {
            switch (screen) {
                case "settings":
                    fragment = neko_fragment = new OpenExteraSettingsActivity();
                    break;
                case "general":
                    if (TRANSLATOR_ROWS.containsKey(row)) {
                        fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                        row = TRANSLATOR_ROWS.get(row);
                    } else {
                        fragment = neko_fragment = new OpenExteraGeneralActivity();
                    }
                    break;
                case "appearance":
                    fragment = neko_fragment = new OpenExteraAppearanceActivity();
                    break;
                case "chats":
                    fragment = neko_fragment = new OpenExteraChatsActivity();
                    break;
                case "other":
                    fragment = neko_fragment = new OpenExteraOtherActivity();
                    break;
                case "ayumoments":
                    fragment = neko_fragment = new OpenExteraAyuMomentsActivity();
                    break;
                case "pillstack":
                    fragment = neko_fragment = new PillStackSettingsActivity();
                    break;
                case "plugins":
                    fragment = new PluginsActivity();
                    break;
                case "navigation":
                    fragment = navigation_fragment = new OpenExteraAppNavigationActivity();
                    break;
                case "privacy":
                    fragment = new PrivacySettingsActivity();
                    break;
                case "powersaving":
                    fragment = new LiteModeSettingsActivity();
                    break;
                case "ota_updates":
                    fragment = neko_fragment = new OpenExteraUpdatesActivity();
                    break;
                case "plugin_store_settings":
                    fragment = neko_fragment = new PluginStoreSettingsActivity();
                    break;
                case "shizuku_settings":
                    fragment = neko_fragment = new ShizukuSettingsActivity();
                    break;
                case "tor_settings":
                    fragment = neko_fragment = new TorSettingsActivity();
                    break;
                case "player_settings":
                    fragment = neko_fragment = new PlayerSettingsActivity();
                    break;
                case "chip_folders_settings":
                    fragment = neko_fragment = new ChipFoldersSettingsActivity();
                    break;
                case "text_animation_settings":
                    fragment = neko_fragment = new TextAnimationSettingsActivity();
                    break;
                case "ai_settings":
                    fragment = neko_fragment = new AiSettingsActivity();
                    break;
                case "cloud":
                    fragment = neko_fragment = new OpenExteraCloudActivity();
                    break;
                case "glyph":
                    fragment = neko_fragment = new OpenExteraGlyphActivity();
                    break;
                case "ghost_mode":
                    fragment = neko_fragment = new GhostModeActivity();
                    break;
                default:
                    unknown.run();
                    return;
            }
        } else if (PasscodeHelper.getSettingsKey().equals(segments.get(1))) {
            fragment = neko_fragment = new NekoPasscodeSettingsActivity();
        } else {
            switch (segments.get(1)) {
                case "about":
                    fragment = new NekoAboutActivity();
                    break;
                case "emoji":
                    fragment = neko_fragment = new NekoEmojiSettingsActivity();
                    break;
                case "translator":
                case "translate":
                case "t":
                    fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                    break;
                case "exteraless":
                    fragment = neko_fragment = new OpenExteraSettingsActivity();
                    break;
                case "exteraless_general":
                    if (TRANSLATOR_ROWS.containsKey(row)) {
                        fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                        row = TRANSLATOR_ROWS.get(row);
                    } else {
                        fragment = neko_fragment = new OpenExteraGeneralActivity();
                    }
                    break;
                case "exteraless_appearance":
                    fragment = neko_fragment = new OpenExteraAppearanceActivity();
                    break;
                case "exteraless_chats":
                    fragment = neko_fragment = new OpenExteraChatsActivity();
                    break;
                case "exteraless_other":
                    fragment = neko_fragment = new OpenExteraOtherActivity();
                    break;
                case "exteraless_ayumoments":
                    fragment = neko_fragment = new OpenExteraAyuMomentsActivity();
                    break;
                case "pillstack":
                    fragment = neko_fragment = new PillStackSettingsActivity();
                    break;
                case "ota_updates":
                case "updates":
                    fragment = neko_fragment = new OpenExteraUpdatesActivity();
                    break;
                case "plugin_store_settings":
                case "plugin_store":
                case "plugins_store":
                    fragment = neko_fragment = new PluginStoreSettingsActivity();
                    break;
                case "shizuku_settings":
                case "shizuku":
                    fragment = neko_fragment = new ShizukuSettingsActivity();
                    break;
                case "tor_settings":
                case "tor":
                    fragment = neko_fragment = new TorSettingsActivity();
                    break;
                case "player_settings":
                case "player":
                    fragment = neko_fragment = new PlayerSettingsActivity();
                    break;
                case "chip_folders_settings":
                case "chip_folders":
                case "chips":
                    fragment = neko_fragment = new ChipFoldersSettingsActivity();
                    break;
                case "text_animation_settings":
                case "text_animation":
                    fragment = neko_fragment = new TextAnimationSettingsActivity();
                    break;
                case "ai_settings":
                case "ai":
                    fragment = neko_fragment = new AiSettingsActivity();
                    break;
                case "exteraless_cloud":
                case "cloud":
                    fragment = neko_fragment = new OpenExteraCloudActivity();
                    break;
                case "exteraless_glyph":
                case "glyph":
                    fragment = neko_fragment = new OpenExteraGlyphActivity();
                    break;
                case "ghost_mode":
                case "ghost":
                    fragment = neko_fragment = new GhostModeActivity();
                    break;
                case "send_logs":
                    sendLogs(activity, false);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.presentFragment(fragment);
        var value = uri.getQueryParameter("v");
        if (TextUtils.isEmpty(value)) {
            value = uri.getQueryParameter("value");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            if (neko_fragment != null) {
                BaseNekoSettingsActivity finalNeko_fragment = neko_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNeko_fragment.scrollToRow(rowFinal, unknown));
            } else if (navigation_fragment != null) {
                OpenExteraAppNavigationActivity finalNavigation_fragment = navigation_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNavigation_fragment.scrollToRow(rowFinal, unknown));
            } else if (nekox_fragment != null) {
                BaseNekoXSettingsActivity finalNekoX_fragment = nekox_fragment;
                if (!TextUtils.isEmpty(value)) {
                    String finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.importToRow(rowFinal, finalValue, unknown));
                } else {
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.scrollToRow(rowFinal, unknown));
                }
            }
        }
    }

    private static String nagramScreen(String segment, String row) {
        if (segment == null) {
            return "settings";
        }
        String fallback;
        switch (segment) {
            case "settings":
            case "s":
                fallback = "settings";
                break;
            case "general":
            case "g":
            case "exteraless_general":
            case "moltengram_general":
            case "exterams_general":
                fallback = "general";
                break;
            case "chat":
            case "chats":
            case "c":
            case "exteraless_chats":
            case "moltengram_chats":
            case "exterams_chats":
                fallback = "chats";
                break;
            case "experimental":
            case "e":
            case "other":
            case "exteraless_other":
            case "moltengram_other":
            case "exterams_other":
                fallback = "other";
                break;
            case "appearance":
            case "a":
            case "exteraless_appearance":
            case "moltengram_appearance":
            case "exterams_appearance":
                fallback = "appearance";
                break;
            case "ayumoments":
            case "ayu":
            case "exteraless_ayumoments":
            case "moltengram_ayumoments":
            case "exterams_ayumoments":
                fallback = "ayumoments";
                break;
            case "pillstack":
                fallback = "pillstack";
                break;
            case "plugins":
                fallback = "plugins";
                break;
            case "navigation":
            case "exteraless_navigation":
            case "moltengram_navigation":
            case "exterams_navigation":
                fallback = "navigation";
                break;
            case "privacy":
                fallback = "privacy";
                break;
            case "powersaving":
                fallback = "powersaving";
                break;
            case "ota_updates":
            case "updates":
            case "update":
                fallback = "ota_updates";
                break;
            case "plugin_store_settings":
            case "plugin_store":
            case "plugins_store":
                fallback = "plugin_store_settings";
                break;
            case "shizuku_settings":
            case "shizuku":
                fallback = "shizuku_settings";
                break;
            case "tor_settings":
            case "tor":
            case "tor_proxy":
                fallback = "tor_settings";
                break;
            case "player_settings":
            case "player":
                fallback = "player_settings";
                break;
            case "chip_folders_settings":
            case "chip_folders":
            case "chips":
                fallback = "chip_folders_settings";
                break;
            case "text_animation_settings":
            case "text_animation":
                fallback = "text_animation_settings";
                break;
            case "ai_settings":
            case "ai":
                fallback = "ai_settings";
                break;
            case "exteraless_cloud":
            case "cloud":
                fallback = "cloud";
                break;
            case "exteraless_glyph":
            case "glyph":
                fallback = "glyph";
                break;
            case "ghost_mode":
            case "ghost":
                fallback = "ghost_mode";
                break;
            default:
                return null;
        }
        String moved = row == null ? null : NAGRAM_ROWS.get(row);
        return moved != null ? moved : fallback;
    }

    private static String rowTitle(BaseNekoSettingsActivity fragment, String key) {
        String title = getString(key);
        if (title != null && !title.isEmpty() && !title.equals(key)) {
            return title;
        }
        if (key.isEmpty()) {
            return null;
        }
        String capitalized = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        String prefix = fragment.getSearchPrefix();
        if (prefix != null) {
            String alias = SEARCH_TITLE_ALIASES.get(prefix + ":" + key);
            if (alias != null) {
                title = resolved(alias);
                if (title != null) {
                    return title;
                }
            }
            title = resolved(prefix + capitalized);
            if (title != null) {
                return title;
            }
        }
        return resolved(capitalized);
    }

    private static String resolved(String name) {
        if (LocaleController.getStringResId(name) == 0) {
            return null;
        }
        String value = getString(name);
        return value == null || value.isEmpty() ? null : value;
    }

    public interface Callback {
        void presentFragment(BaseFragment fragment);
    }

    public static ArrayList<SettingsSearchResult> onCreateSearchArray(Callback callback) {
        ArrayList<SettingsSearchResult> items = new ArrayList<>();
        ArrayList<BaseNekoXSettingsActivity> fragments = new ArrayList<>();
        fragments.add(new NekoTranslatorSettingsActivity());

        ArrayList<BaseNekoSettingsActivity> exteralessFragments = new ArrayList<>();
        exteralessFragments.add(new OpenExteraSettingsActivity());
        exteralessFragments.add(new OpenExteraGeneralActivity());
        exteralessFragments.add(new OpenExteraAppearanceActivity());
        exteralessFragments.add(new OpenExteraChatsActivity());
        exteralessFragments.add(new OpenExteraOtherActivity());
        exteralessFragments.add(new OpenExteraAyuMomentsActivity());
        exteralessFragments.add(new PillStackSettingsActivity());
        exteralessFragments.add(new AiSettingsActivity());
        exteralessFragments.add(new OpenExteraUpdatesActivity());
        exteralessFragments.add(new PluginStoreSettingsActivity());
        exteralessFragments.add(new ShizukuSettingsActivity());
        exteralessFragments.add(new TorSettingsActivity());
        exteralessFragments.add(new PlayerSettingsActivity());
        exteralessFragments.add(new ChipFoldersSettingsActivity());
        exteralessFragments.add(new TextAnimationSettingsActivity());

        String e_title = getString(R.string.OpenExtera);
        for (BaseNekoSettingsActivity fragment : exteralessFragments) {
            try {
                fragment.buildRowsForSearch();
            } catch (Exception e) {
                continue;
            }
            int uid = fragment.getSearchGuid();
            int drawable = fragment.getSearchIcon();
            String f_title = fragment.getSearchTitle();
            for (Map.Entry<Integer, String> entry : fragment.getSearchRows().entrySet()) {
                String key = entry.getValue();
                if (key == null || key.endsWith("Header") || key.equals(String.valueOf(entry.getKey()))) {
                    continue;
                }
                String title = rowTitle(fragment, key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                items.add(new SettingsSearchResult(
                        uid + entry.getKey(), title, e_title, f_title, drawable, open));
            }
        }

        String n_title = getString(R.string.Language);
        for (BaseNekoXSettingsActivity fragment: fragments) {
            int uid = fragment.getBaseGuid();
            int drawable = fragment.getDrawable();
            String f_title = fragment.getTitle();
            for (Map.Entry<Integer, String> entry : fragment.getRowMapReverse().entrySet()) {
                Integer i = entry.getKey();
                String key = entry.getValue();
                if (key.equals(String.valueOf(i))) {
                    continue;
                }
                int guid = uid + i;
                String title = getString(key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                SettingsSearchResult result = new SettingsSearchResult(
                        guid, title, n_title, f_title, drawable, open
                );
                items.add(result);
            }
        }
        return items;
    }
}
