package app.exteraless.plugins.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.DocumentObject;
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.exteraless.plugins.Plugin;

public final class PluginIcons {

    private static final Pattern ICON_PATTERN = Pattern.compile("__icon__\\s*=\\s*[\"']([^\"']+)[\"']");

    private PluginIcons() {
    }

    public static String extractIconFromFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 120) {
                linesRead++;
                Matcher m = ICON_PATTERN.matcher(line);
                if (m.find()) {
                    String icon = m.group(1);
                    if (!TextUtils.isEmpty(icon)) {
                        return icon.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String resolveDefaultPackIcon(String id, String name) {
        String seed = (!TextUtils.isEmpty(id) ? id : "") + ":" + (!TextUtils.isEmpty(name) ? name : "plugin");
        int idx = (seed.hashCode() & 0x7fffffff) % 24;
        return "exteraPlugins/" + idx;
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
        if (TextUtils.isEmpty(icon) && plugin != null) {
            icon = resolveDefaultPackIcon(plugin.id, plugin.name);
        }
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

        final int slash = trimmed.lastIndexOf('/');
        if (slash <= 0 || slash == trimmed.length() - 1) {
            return false;
        }
        final String pack = trimmed.substring(0, slash).trim();
        final int index;
        try {
            index = Integer.parseInt(trimmed.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (index < 0) {
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
