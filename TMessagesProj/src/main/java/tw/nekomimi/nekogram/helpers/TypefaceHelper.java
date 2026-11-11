package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;

import java.io.File;
import java.util.List;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

public class TypefaceHelper {

    private static final String TEST_TEXT;
    private static final int CANVAS_SIZE = dp(20);
    private static final Paint PAINT = new Paint() {{
        setTextSize(CANVAS_SIZE);
        setAntiAlias(true);
        setSubpixelText(false);
        setFakeBoldText(false);
    }};

    private static Boolean mediumWeightSupported = null;
    private static Boolean italicSupported = null;
    private static volatile Boolean usePixelGoogleSans = null;
    private static Typeface systemGoogleSans;
    private static boolean systemGoogleSansLoaded;
    private static Typeface systemGoogleSansMedium;
    private static boolean systemGoogleSansMediumLoaded;

    static {
        var lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
        if (List.of("zh", "ja", "ko").contains(lang)) {
            TEST_TEXT = "你好";
        } else if (List.of("ar", "fa").contains(lang)) {
            TEST_TEXT = "مرحبا";
        } else if ("iw".equals(lang)) {
            TEST_TEXT = "שלום";
        } else if ("th".equals(lang)) {
            TEST_TEXT = "สวัสดี";
        } else if ("hi".equals(lang)) {
            TEST_TEXT = "नमस्ते";
        } else if (List.of("ru", "uk", "ky", "be", "sr").contains(lang)) {
            TEST_TEXT = "Привет";
        } else {
            TEST_TEXT = "R";
        }
    }

    public static Typeface createTypeface(String assetPath) {
        return switch (assetPath) {
            case AndroidUtilities.TYPEFACE_ROBOTO_REGULAR -> createTypeface(400, false);
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM -> {
                if (NekoConfig.forceFontWeightFallback.Bool()) {
                    yield createTypeface(700, false);
                }
                if (shouldUsePixelGoogleSans()) {
                    yield pixelMediumTypeface(false);
                }
                yield isMediumWeightSupported() ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.BOLD);
            }
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC -> {
                if (NekoConfig.forceFontWeightFallback.Bool()) {
                    yield createTypeface(700, true);
                }
                if (shouldUsePixelGoogleSans()) {
                    yield pixelMediumTypeface(true);
                }
                yield isMediumWeightSupported() ? Typeface.create("sans-serif-medium", Typeface.ITALIC) : Typeface.create("sans-serif", Typeface.BOLD_ITALIC);
            }
            case AndroidUtilities.TYPEFACE_RCONDENSED_BOLD ->
                    Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD ->
                    createTypeface(800, false);
            case AndroidUtilities.TYPEFACE_RITALIC ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? Typeface.create(baseTypeface(), 400, true) : Typeface.create("sans-serif", Typeface.ITALIC);
            case AndroidUtilities.TYPEFACE_ROBOTO_MONO ->
                    Typeface.MONOSPACE;
            default -> createTypefaceFromAsset(assetPath);
        };
    }

    public static Typeface createTypefaceFromAsset(String assetPath) {
        Typeface.Builder builder = new Typeface.Builder(ApplicationLoader.applicationContext.getAssets(), assetPath);
        if (assetPath.contains("rextrabold")) {
            builder.setWeight(800);
        }
        if (assetPath.contains("medium") || assetPath.contains("rbold")) {
            builder.setWeight(700);
        }
        if (assetPath.contains("italic")) {
            builder.setItalic(true);
        }
        return builder.build();
    }

    public static boolean isMediumWeightSupported() {
        if (mediumWeightSupported == null) {
            mediumWeightSupported = testTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            FileLog.d("mediumWeightSupported = " + mediumWeightSupported);
        }
        return mediumWeightSupported;
    }

    public static boolean isItalicSupported() {
        if (italicSupported == null) {
            italicSupported = testTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            FileLog.d("italicSupported = " + italicSupported);
        }
        return italicSupported;
    }

    private static Typeface pixelMediumTypeface(boolean italic) {
        final Typeface regular = createTypeface(400, italic);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            final Typeface weighted = createTypeface(500, italic);
            if (rendersDifferently(weighted, regular)) {
                return weighted;
            }
        }
        final Typeface medium = getSystemGoogleSansMedium();
        if (medium != null) {
            final Typeface styled = italic ? Typeface.create(medium, Typeface.ITALIC) : medium;
            if (rendersDifferently(styled, regular)) {
                return styled;
            }
        }
        final Typeface systemMedium = Typeface.create("sans-serif-medium", italic ? Typeface.ITALIC : Typeface.NORMAL);
        if (rendersDifferently(systemMedium, regular)) {
            return systemMedium;
        }
        return Typeface.create(baseTypeface(), italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
    }

    private static Typeface baseTypeface() {
        if (!shouldUsePixelGoogleSans()) {
            return Typeface.create("sans-serif", Typeface.NORMAL);
        }
        if (!systemGoogleSansLoaded) {
            systemGoogleSansLoaded = true;
            for (String family : new String[]{"google-sans-text", "google-sans"}) {
                final Typeface candidate = Typeface.create(family, Typeface.NORMAL);
                if (rendersDifferently(candidate, Typeface.DEFAULT)) {
                    systemGoogleSans = candidate;
                    FileLog.d("system google sans alias = " + family);
                    break;
                }
            }
            if (systemGoogleSans == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final File file = findGoogleSansFile();
                if (file != null) {
                    try {
                        systemGoogleSans = Typeface.createFromFile(file);
                        FileLog.d("system google sans file = " + file.getAbsolutePath());
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        }
        return systemGoogleSans != null ? systemGoogleSans : Typeface.create("sans-serif", Typeface.NORMAL);
    }

    private static Typeface getSystemGoogleSansMedium() {
        if (!shouldUsePixelGoogleSans()) {
            return null;
        }
        if (!systemGoogleSansMediumLoaded) {
            systemGoogleSansMediumLoaded = true;
            for (String family : new String[]{"variable-title-medium-emphasized", "variable-title-medium"}) {
                final Typeface candidate = Typeface.create(family, Typeface.NORMAL);
                if (rendersDifferently(candidate, Typeface.DEFAULT)) {
                    systemGoogleSansMedium = candidate;
                    FileLog.d("system google sans medium alias = " + family);
                    break;
                }
            }
        }
        return systemGoogleSansMedium;
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private static File findGoogleSansFile() {
        try {
            for (Font font : SystemFonts.getAvailableFonts()) {
                final File file = font.getFile();
                if (file == null) {
                    continue;
                }
                final String name = file.getName().toLowerCase(Locale.US);
                if ((name.contains("googlesans") || name.contains("google-sans"))
                        && !name.contains("medium") && !name.contains("bold") && !name.contains("italic") && !name.contains("condensed")) {
                    return file;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static boolean isGooglePixelDevice() {
        return "google".equalsIgnoreCase(Build.MANUFACTURER) && Build.MODEL != null && Build.MODEL.toLowerCase(Locale.US).startsWith("pixel");
    }

    public static boolean shouldUsePixelGoogleSans() {
        if (!isGooglePixelDevice()) {
            return false;
        }
        if (usePixelGoogleSans == null) {
            synchronized (TypefaceHelper.class) {
                if (usePixelGoogleSans == null) {
                    boolean use;
                    try {
                        use = hasSimilarMetrics(Typeface.create("sans-serif", Typeface.NORMAL), createTypefaceFromAsset(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
                    } catch (Exception e) {
                        FileLog.e(e);
                        use = false;
                    }
                    usePixelGoogleSans = use;
                    FileLog.d("usePixelGoogleSans = " + use);
                }
            }
        }
        return usePixelGoogleSans;
    }

    private static boolean hasSimilarMetrics(Typeface a, Typeface b) {
        final String sample = "Hamburgefontsiv0123456789";
        final Paint paint = new Paint();
        paint.setTextSize(100);
        final float[] first = new float[sample.length()];
        final float[] second = new float[sample.length()];
        paint.setTypeface(a);
        paint.getTextWidths(sample, first);
        paint.setTypeface(b);
        paint.getTextWidths(sample, second);
        for (int i = 0; i < sample.length(); i++) {
            if (second[i] == 0 || Math.abs(first[i] - second[i]) / second[i] > 0.01f) {
                return false;
            }
        }
        return true;
    }

    private static boolean rendersDifferently(Typeface a, Typeface b) {
        final Canvas canvas = new Canvas();
        final Bitmap first = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        final Bitmap second = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        synchronized (PAINT) {
            canvas.setBitmap(first);
            PAINT.setTypeface(a);
            canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);
            canvas.setBitmap(second);
            PAINT.setTypeface(b);
            canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);
            PAINT.setTypeface(null);
        }
        final boolean different = !first.sameAs(second);
        AndroidUtilities.recycleBitmaps(List.of(first, second));
        return different;
    }

    private static boolean testTypeface(Typeface typeface) {
        Canvas canvas = new Canvas();

        Bitmap bitmap1 = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap1);
        PAINT.setTypeface(null);
        canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);

        Bitmap bitmap2 = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap2);
        PAINT.setTypeface(typeface);
        canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);

        boolean supported = !bitmap1.sameAs(bitmap2);
        AndroidUtilities.recycleBitmaps(List.of(bitmap1, bitmap2));
        return supported;
    }

    public static Typeface createTypeface(int weight, boolean italic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (weight >= 500 && weight < 700 && shouldUsePixelGoogleSans()) {
                final Typeface medium = getSystemGoogleSansMedium();
                if (medium != null) {
                    return Typeface.create(medium, weight, italic);
                }
            }
            return Typeface.create(baseTypeface(), weight, italic);
        }
        if (weight == 700) {
            return Typeface.create("sans-serif", italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
        }
        var family = switch (weight) {
            case 800 -> "sans-serif-black";
            case 500 -> "sans-serif-medium";
            default -> "sans-serif";
        };
        return Typeface.create(family, italic ? Typeface.ITALIC : Typeface.NORMAL);
    }

    public static SpannableStringBuilder getTitleText(int currentAccount) {
        String title = (String) NaConfig.INSTANCE.getCustomTitle().defaultValue;
        // openExtera: заголовок списка чатов (AppearanceConfig.titleText).
        // Перенесено из exteraGram 12.9.0, LocaleUtils.getActionBarTitle(int).
        // 0 — имя приложения (NagramX customTitle), 1 — username, 2 — имя, 3 — «Чаты».
        final int oeTitleText = app.exteraless.appearance.AppearanceConfig.titleText();
        if (oeTitleText == 3) {
            title = LocaleController.getString(R.string.FilterChats);
        } else if (oeTitleText == app.exteraless.appearance.AppearanceConfig.TITLE_TEXT_CUSTOM) {
            String customTitle = NaConfig.INSTANCE.getCustomTitle().String();
            if (!TextUtils.isEmpty(customTitle)) {
                title = customTitle;
            }
        } else if (oeTitleText != 0) {
            TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
            String username = oeTitleText == 1 ? UserObject.getPublicUsername(self) : null;
            if (!TextUtils.isEmpty(username)) {
                title = username;
            } else {
                String firstName = UserObject.getFirstName(self);
                if (!TextUtils.isEmpty(firstName)) {
                    title = firstName;
                }
            }
        }
        var builder = new SpannableStringBuilder(title);
        builder.setSpan(new LeadingMarginSpan.Standard(dp(2), 0), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Typeface titleTypeface = app.exteraless.appearance.AppearanceConfig.ndotFontHeaders() ? ndotTypeface() : (NekoConfig.typeface.Bool() && NekoConfig.forceFontWeightFallback.Bool() ? createTypeface(700, false) : createTypeface(600, false));
        builder.setSpan(new TypefaceSpan(titleTypeface, 0, Theme.key_telegram_color_dialogsLogo, null), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private static Typeface lyricsTypeface;

    public static Typeface lyricsTypeface() {
        if (lyricsTypeface != null) {
            return lyricsTypeface;
        }
        Typeface base = null;
        for (String family : new String[]{"google-sans-display", "google-sans", "google-sans-text"}) {
            final Typeface candidate = Typeface.create(family, Typeface.NORMAL);
            if (rendersDifferently(candidate, Typeface.DEFAULT)) {
                base = candidate;
                break;
            }
        }
        if (base == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final File file = findGoogleSansFile();
            if (file != null) {
                try {
                    base = Typeface.createFromFile(file);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        if (base == null) {
            lyricsTypeface = AndroidUtilities.bold();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lyricsTypeface = Typeface.create(base, 700, false);
        } else {
            lyricsTypeface = Typeface.create(base, Typeface.BOLD);
        }
        return lyricsTypeface;
    }

    private static Typeface ndotTypeface;

    public static Typeface ndotTypeface() {
        if (ndotTypeface == null) {
            try {
                ndotTypeface = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), "fonts/ndot57caps.otf");
            } catch (Exception e) {
                FileLog.e("Could not load ndot font", e);
                ndotTypeface = AndroidUtilities.bold();
            }
        }
        return ndotTypeface;
    }

    public static void clearNdotTypeface() {
        ndotTypeface = null;
    }

}
