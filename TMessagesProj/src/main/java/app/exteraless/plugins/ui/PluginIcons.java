package app.exteraless.plugins.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Base64;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.ApplicationLoader;
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
import java.io.FileOutputStream;
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
            "[\"']?(?:__icon__|icon|иконка|значок)[\"']?\\s*[:=]\\s*(?:[\"']([^\"']+)[\"']|([a-zA-Z0-9_./@:-]+))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern COMMENT_ICON_PATTERN = Pattern.compile(
            "#\\s*(?:__icon__|icon|иконка|значок)\\s*[:=]?\\s*(?:[\"']([^\"']+)[\"']|([a-zA-Z0-9_./@:-]+))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
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

    private static File extractEntryToCache(ZipFile zip, ZipEntry entry, String baseName) {
        if (zip == null || entry == null) return null;
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return null;
            File dir = new File(ctx.getCacheDir(), "plugin_icons");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String safeEntry = entry.getName().replace('/', '_').replace('\\', '_');
            String safeBase = baseName != null ? baseName.replace('/', '_').replace('\\', '_') : "icon";
            File out = new File(dir, safeBase + "_" + safeEntry);
            if (out.exists() && out.length() == entry.getSize() && out.length() > 0) {
                return out;
            }
            try (InputStream in = zip.getInputStream(entry);
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    fos.write(buf, 0, r);
                }
                fos.flush();
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractIconFromFile(File file) {
        return extractIconFromFile(file, null);
    }

    public static String extractIconFromFile(File file, String declaredIcon) {
        if (file == null || !file.exists()) {
            return declaredIcon;
        }

        // Handle directory
        if (file.isDirectory()) {
            if (!TextUtils.isEmpty(declaredIcon)) {
                File rel = new File(file, declaredIcon);
                if (rel.exists() && rel.isFile() && rel.length() > 0) {
                    return rel.getAbsolutePath();
                }
            }
            String[] imgNames = {"icon.png", "icon.webp", "icon.jpg", "assets/icon.png", "logo.png"};
            for (String img : imgNames) {
                File imgFile = new File(file, img);
                if (imgFile.exists() && imgFile.isFile() && imgFile.length() > 0) {
                    return imgFile.getAbsolutePath();
                }
            }
            File manifest = new File(file, "manifest.json");
            if (manifest.exists() && manifest.isFile()) {
                try (InputStream is = new FileInputStream(manifest)) {
                    String parsed = parseStreamForIcon(is, 150);
                    if (!TextUtils.isEmpty(parsed)) {
                        File rel = new File(file, parsed);
                        if (rel.exists() && rel.isFile() && rel.length() > 0) {
                            return rel.getAbsolutePath();
                        }
                        return parsed;
                    }
                } catch (Exception ignored) {}
            }
            return declaredIcon;
        }

        if (!file.isFile() || file.length() == 0) {
            return declaredIcon;
        }

        // 1. Check if file is a ZIP archive (.plugin / .eaf / .elyx / .zip)
        try (ZipFile zip = new ZipFile(file)) {
            // A. If declaredIcon is given and points to an entry in the zip
            if (!TextUtils.isEmpty(declaredIcon) && !declaredIcon.startsWith("http://")
                    && !declaredIcon.startsWith("https://") && !declaredIcon.startsWith("emoji/")) {
                ZipEntry directEntry = zip.getEntry(declaredIcon);
                if (directEntry == null && declaredIcon.startsWith("/")) {
                    directEntry = zip.getEntry(declaredIcon.substring(1));
                }
                if (directEntry == null) {
                    directEntry = zip.getEntry("assets/" + declaredIcon);
                }
                if (directEntry != null && !directEntry.isDirectory()) {
                    File extracted = extractEntryToCache(zip, directEntry, file.getName());
                    if (extracted != null) return extracted.getAbsolutePath();
                }
            }

            // B. Parse metadata files
            String[] candidates = {
                "manifest.json", "plugin.json", "metainfo.yaml", "metainfo.yml", "metainfo.json",
                "refmap.yaml", "refmap.yml", "refmap.json", "__init__.py", "main.py", "plugin.py"
            };
            for (String cand : candidates) {
                ZipEntry entry = zip.getEntry(cand);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        String icon = parseStreamForIcon(is, 250);
                        if (!TextUtils.isEmpty(icon)) {
                            // If parsed icon is a relative entry inside the zip:
                            ZipEntry iconEntry = zip.getEntry(icon);
                            if (iconEntry == null && icon.startsWith("/")) {
                                iconEntry = zip.getEntry(icon.substring(1));
                            }
                            if (iconEntry == null) {
                                iconEntry = zip.getEntry("assets/" + icon);
                            }
                            if (iconEntry != null && !iconEntry.isDirectory()) {
                                File extracted = extractEntryToCache(zip, iconEntry, file.getName());
                                if (extracted != null) return extracted.getAbsolutePath();
                            }
                            return icon;
                        }
                    } catch (Exception ignored) {}
                }
            }

            // C. Look for candidate image entries directly in the zip
            String[] directImageEntries = {
                "icon.png", "icon.webp", "icon.jpg", "icon.jpeg",
                "assets/icon.png", "assets/icon.webp", "assets/icon.jpg",
                "res/drawable/icon.png", "res/icon.png", "logo.png", "logo.webp"
            };
            for (String imgEntryName : directImageEntries) {
                ZipEntry imgEntry = zip.getEntry(imgEntryName);
                if (imgEntry != null && !imgEntry.isDirectory() && imgEntry.getSize() > 0) {
                    File extracted = extractEntryToCache(zip, imgEntry, file.getName());
                    if (extracted != null) return extracted.getAbsolutePath();
                }
            }

            // D. Search all entries for image files containing icon or logo
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            int checked = 0;
            ZipEntry bestImageEntry = null;
            while (entries.hasMoreElements() && checked < 50) {
                ZipEntry entry = entries.nextElement();
                checked++;
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    if (name.contains("icon") || name.contains("logo") || name.startsWith("assets/")) {
                        bestImageEntry = entry;
                        break;
                    }
                }
            }
            if (bestImageEntry != null) {
                File extracted = extractEntryToCache(zip, bestImageEntry, file.getName());
                if (extracted != null) return extracted.getAbsolutePath();
            }
        } catch (Exception ignored) {
            // Not a zip file, process as plain file
        }

        // 2. Plain text / python file
        try (InputStream is = new FileInputStream(file)) {
            String parsed = parseStreamForIcon(is, 300);
            if (!TextUtils.isEmpty(parsed)) {
                File parent = file.getParentFile();
                if (parent != null) {
                    File sib = new File(parent, parsed);
                    if (sib.exists() && sib.isFile() && sib.length() > 0) {
                        return sib.getAbsolutePath();
                    }
                }
                return parsed;
            }
        } catch (Exception ignored) {
        }
        return declaredIcon;
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
        return apply(imageView, plugin, null, onLoaded);
    }

    public static boolean apply(BackupImageView imageView, Plugin plugin, Drawable fallbackDrawable, Runnable onLoaded) {
        if (plugin == null || imageView == null) {
            return false;
        }
        String icon = plugin.icon;
        if (TextUtils.isEmpty(icon) && !TextUtils.isEmpty(plugin.path)) {
            File f = new File(plugin.path);
            if (f.exists()) {
                String extracted = extractIconFromFile(f, null);
                if (!TextUtils.isEmpty(extracted)) {
                    plugin.icon = extracted;
                    icon = extracted;
                }
            }
        } else if (!TextUtils.isEmpty(icon) && !icon.startsWith("/") && !icon.startsWith("file://")
                && !icon.startsWith("http://") && !icon.startsWith("https://")
                && !icon.startsWith("emoji/") && !icon.startsWith("data:")
                && !TextUtils.isEmpty(plugin.path)) {
            File f = new File(plugin.path);
            if (f.exists()) {
                String extracted = extractIconFromFile(f, icon);
                if (!TextUtils.isEmpty(extracted)) {
                    plugin.icon = extracted;
                    icon = extracted;
                }
            }
        }
        return apply(imageView, icon, fallbackDrawable, onLoaded);
    }

    public static boolean apply(BackupImageView imageView, String icon, Runnable onLoaded) {
        return apply(imageView, icon, null, onLoaded);
    }

    public static boolean apply(BackupImageView imageView, String icon, Drawable fallbackDrawable, Runnable onLoaded) {
        if (imageView == null || TextUtils.isEmpty(icon)) {
            if (imageView != null && fallbackDrawable != null) {
                imageView.setImageDrawable(fallbackDrawable);
                imageView.setVisibility(android.view.View.VISIBLE);
            }
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

        // 2. Base64 data URI (data:image/...;base64,...)
        if (trimmed.startsWith("data:image/") && trimmed.contains(";base64,")) {
            try {
                String base64Data = trimmed.substring(trimmed.indexOf(";base64,") + 8).trim();
                byte[] decoded = Base64.decode(base64Data, Base64.DEFAULT);
                if (decoded != null && decoded.length > 0) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    if (bmp != null) {
                        imageView.setTag("plugin_base64_" + decoded.length);
                        imageView.setAnimatedEmojiDrawable(null);
                        imageView.setImageBitmap(bmp);
                        imageView.setVisibility(android.view.View.VISIBLE);
                        if (onLoaded != null) onLoaded.run();
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Local file path
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

        // 4. Android Drawable resource name (e.g. "@drawable/msg_settings", "msg_settings", "baseline_extension_24")
        if (trimmed.startsWith("@drawable/") || trimmed.startsWith("drawable/")
                || (!trimmed.contains("/") && !trimmed.contains(".") && imageView.getContext() != null)) {
            String resName = trimmed;
            if (resName.startsWith("@drawable/")) {
                resName = resName.substring(10);
            } else if (resName.startsWith("drawable/")) {
                resName = resName.substring(9);
            }
            Context ctx = imageView.getContext();
            if (ctx != null) {
                int resId = ctx.getResources().getIdentifier(resName, "drawable", ctx.getPackageName());
                if (resId != 0) {
                    Drawable d = ContextCompat.getDrawable(ctx, resId);
                    if (d != null) {
                        imageView.setTag("plugin_res_" + resId);
                        imageView.setAnimatedEmojiDrawable(null);
                        imageView.setImageDrawable(d);
                        imageView.setVisibility(android.view.View.VISIBLE);
                        if (onLoaded != null) {
                            onLoaded.run();
                        }
                        return true;
                    }
                }
            }
        }

        // 5. Custom Emoji by Document ID (e.g. emoji/12345)
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

        // 6. Unicode Emoji (e.g. 🎵, ⚙️, 🚀)
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

        // 7. Telegram Sticker Links (t.me/addstickers or tg://addstickers)
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

        // 8. Sticker Pack / Index (e.g. "exteraPlugins/12" or "pack_name")
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
                .getStickerSet(input, null, false, set -> {
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
