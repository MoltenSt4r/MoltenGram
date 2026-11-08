package app.exteraless.plugins.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import app.exteraless.plugins.Plugin;

public final class PluginIcons {

    private static final Pattern ICON_PATTERN = Pattern.compile(
            "[\"']?(?:__icon__|icon)[\"']?\\s*[:=]\\s*(?:[\"']([^\"']+)[\"']|([a-zA-Z0-9_./@:-]+))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMMENT_ICON_PATTERN = Pattern.compile(
            "#\\s*(?:__icon__|icon)\\s*:\\s*([a-zA-Z0-9_./@:-]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STICKER_URL_PATTERN = Pattern.compile(
            "(?:https?://)?t\\.me/addstickers/([a-zA-Z0-9_]+)(?:/([0-9]+))?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TG_STICKER_PATTERN = Pattern.compile(
            "tg://addstickers\\?set=([a-zA-Z0-9_]+)(?:&index=([0-9]+))?",
            Pattern.CASE_INSENSITIVE
    );

    private PluginIcons() {
    }

    public static String extractIconFromFile(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() == 0) {
            return null;
        }

        // 1. Check if file is a ZIP archive (.plugin / .eaf)
        try (ZipFile zip = new ZipFile(file)) {
            String[] candidates = {
                "manifest.json", "plugin.json", "metainfo.yaml", "metainfo.yml", "metainfo.json",
                "refmap.yaml", "refmap.yml", "refmap.json", "__init__.py", "main.py", "plugin.py"
            };
            for (String cand : candidates) {
                ZipEntry entry = zip.getEntry(cand);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        String icon = parseStreamForIcon(is, 250);
                        if (!TextUtils.isEmpty(icon)) return icon;
                    } catch (Exception ignored) {}
                }
            }
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            int checked = 0;
            while (entries.hasMoreElements() && checked < 25) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith("manifest.json") || name.endsWith("plugin.json")
                        || name.endsWith("metainfo.yaml") || name.endsWith("metainfo.yml")
                        || name.endsWith("metainfo.json") || name.endsWith("__init__.py")
                        || name.endsWith("main.py") || name.endsWith(".py")) {
                    checked++;
                    try (InputStream is = zip.getInputStream(entry)) {
                        String icon = parseStreamForIcon(is, 250);
                        if (!TextUtils.isEmpty(icon)) return icon;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
            // Not a zip file, process as plain file
        }

        // 2. Plain text / python file
        try (InputStream is = new FileInputStream(file)) {
            return parseStreamForIcon(is, 300);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String parseStreamForIcon(InputStream is, int maxLines) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < maxLines) {
                linesRead++;
                Matcher m = ICON_PATTERN.matcher(line);
                if (m.find()) {
                    String icon = m.group(1);
                    if (TextUtils.isEmpty(icon)) {
                        icon = m.group(2);
                    }
                    if (!TextUtils.isEmpty(icon)) {
                        icon = icon.trim();
                        if (icon.endsWith(",") || icon.endsWith(";")) {
                            icon = icon.substring(0, icon.length() - 1).trim();
                        }
                        if ((icon.startsWith("\"") && icon.endsWith("\"")) || (icon.startsWith("'") && icon.endsWith("'"))) {
                            icon = icon.substring(1, icon.length() - 1).trim();
                        }
                        if (!TextUtils.isEmpty(icon)) {
                            return icon;
                        }
                    }
                }
                Matcher cm = COMMENT_ICON_PATTERN.matcher(line);
                if (cm.find()) {
                    String icon = cm.group(1);
                    if (!TextUtils.isEmpty(icon)) {
                        return icon.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Drawable createCategoryFallbackDrawable(Context context, String category, int accentColor, int radiusDp) {
        int iconRes = R.drawable.baseline_extension_24;
        if (category != null) {
            switch (category.toLowerCase(java.util.Locale.ROOT)) {
                case "ui":
                case "interface":
                    iconRes = R.drawable.msg_theme;
                    break;
                case "utils":
                case "tweaks":
                    iconRes = R.drawable.msg_settings;
                    break;
                case "tools":
                    iconRes = R.drawable.msg_customize_s;
                    break;
                case "ai":
                    iconRes = R.drawable.msg_bot;
                    break;
                case "media":
                    iconRes = R.drawable.msg_photos;
                    break;
                case "chats":
                    iconRes = R.drawable.msg_discussion;
                    break;
                case "dev":
                    iconRes = R.drawable.msg_log;
                    break;
                case "fun":
                    iconRes = R.drawable.msg_smile_status;
                    break;
            }
        }
        Drawable bg = Theme.createRoundRectDrawable(dp(radiusDp), Theme.multAlpha(accentColor, 0.14f));
        Drawable icon = ContextCompat.getDrawable(context, iconRes);
        if (icon != null) {
            icon = icon.mutate();
            icon.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
            CombinedDrawable combined = new CombinedDrawable(bg, icon);
            combined.setIconSize(dp(24), dp(24));
            return combined;
        }
        return bg;
    }

    public static boolean apply(BackupImageView imageView, Plugin plugin, Runnable onLoaded) {
        String icon = plugin != null ? plugin.icon : null;
        return apply(imageView, icon, onLoaded);
    }

    public static boolean apply(BackupImageView imageView, String icon, Runnable onLoaded) {
        return apply(imageView, icon, null, onLoaded);
    }

    public static boolean apply(BackupImageView imageView, String icon, Drawable fallbackDrawable, Runnable onLoaded) {
        if (imageView == null || TextUtils.isEmpty(icon)) {
            return false;
        }
        String trimmed = icon.trim();

        // 1. Direct Web / HTTP Image URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            imageView.setTag("plugin_web_" + trimmed);
            imageView.setAnimatedEmojiDrawable(null);
            imageView.setImage(trimmed, "100_100", fallbackDrawable);
            imageView.setVisibility(android.view.View.VISIBLE);
            if (onLoaded != null) {
                onLoaded.run();
            }
            return true;
        }

        // 2. Local file path
        if (trimmed.startsWith("file://") || trimmed.startsWith("/")) {
            String filePath = trimmed.startsWith("file://") ? trimmed.substring(7) : trimmed;
            File f = new File(filePath);
            if (f.exists() && f.length() > 0) {
                imageView.setTag("plugin_file_" + filePath);
                imageView.setAnimatedEmojiDrawable(null);
                imageView.setImage(filePath, "100_100", fallbackDrawable);
                imageView.setVisibility(android.view.View.VISIBLE);
                if (onLoaded != null) {
                    onLoaded.run();
                }
                return true;
            }
        }

        // 3. Custom Emoji by Document ID (e.g. emoji/12345)
        if (trimmed.startsWith("emoji/")) {
            try {
                long docId = Long.parseLong(trimmed.substring("emoji/".length()).trim());
                if (docId != 0) {
                    imageView.setTag("plugin_emoji_" + docId);
                    imageView.setVisibility(android.view.View.VISIBLE);
                    AnimatedEmojiDrawable emojiDrawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, docId);
                    imageView.setAnimatedEmojiDrawable(emojiDrawable);
                    if (onLoaded != null) {
                        onLoaded.run();
                    }
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // 4. Unicode Emoji (e.g. 🎵, ⚙️, 🚀)
        try {
            Drawable emojiDrawable = Emoji.getEmojiDrawable(trimmed);
            if (emojiDrawable != null) {
                imageView.setTag("plugin_unicode_" + trimmed);
                imageView.setAnimatedEmojiDrawable(null);
                imageView.setImageDrawable(emojiDrawable);
                imageView.setVisibility(android.view.View.VISIBLE);
                if (onLoaded != null) {
                    onLoaded.run();
                }
                return true;
            }
        } catch (Throwable ignored) {
        }

        // 5. Telegram Sticker Links (t.me/addstickers or tg://addstickers)
        Matcher sm = STICKER_URL_PATTERN.matcher(trimmed);
        if (sm.find()) {
            String pack = sm.group(1);
            String idx = sm.group(2) != null ? sm.group(2) : "0";
            trimmed = pack + "/" + idx;
        } else {
            Matcher tgm = TG_STICKER_PATTERN.matcher(trimmed);
            if (tgm.find()) {
                String pack = tgm.group(1);
                String idx = tgm.group(2) != null ? tgm.group(2) : "0";
                trimmed = pack + "/" + idx;
            }
        }

        // 6. Sticker Pack / Index (e.g. "exteraPlugins/12" or "pack_name")
        final String pack;
        final int index;
        final int slash = trimmed.lastIndexOf('/');
        if (slash > 0 && slash < trimmed.length() - 1) {
            pack = trimmed.substring(0, slash).trim();
            int parsed = 0;
            try {
                parsed = Integer.parseInt(trimmed.substring(slash + 1).trim());
            } catch (NumberFormatException ignored) {}
            index = Math.max(0, parsed);
        } else if (slash < 0 && trimmed.matches("^[a-zA-Z0-9_]+$")) {
            pack = trimmed;
            index = 0;
        } else {
            return false;
        }

        final String tag = "plugin_icon_" + pack + "_" + index;
        imageView.setTag(tag);
        imageView.setAnimatedEmojiDrawable(null);
        if (fallbackDrawable != null) {
            imageView.setImageDrawable(fallbackDrawable);
            imageView.setVisibility(android.view.View.VISIBLE);
        } else {
            imageView.setImageDrawable(null);
            imageView.setVisibility(android.view.View.GONE);
        }

        TLRPC.TL_inputStickerSetShortName input = new TLRPC.TL_inputStickerSetShortName();
        input.short_name = pack;
        MediaDataController.getInstance(UserConfig.selectedAccount)
                .getStickerSet(input, 0, false, set -> {
                    if (set == null || set.documents == null || set.documents.isEmpty()) {
                        return;
                    }
                    if (!tag.equals(imageView.getTag())) {
                        return;
                    }
                    int safeIndex = index < set.documents.size() ? index : (index % set.documents.size());
                    TLRPC.Document document = set.documents.get(safeIndex);
                    Drawable thumb = DocumentObject.getSvgThumb(document,
                            Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                    imageView.setImage(ImageLocation.getForDocument(document), "100_100",
                            thumb != null ? thumb : fallbackDrawable, 0, document);
                    imageView.setVisibility(android.view.View.VISIBLE);
                    imageView.invalidate();
                    if (onLoaded != null) {
                        onLoaded.run();
                    }
                });
        return true;
    }
}
