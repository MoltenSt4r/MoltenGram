package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    private static final String[] LEGACY_ALIASES = {
        "org.telegram.messenger.ExteraMSMD3Icon",
        "org.telegram.messenger.ExteraMSGoogleIcon",
        "org.telegram.messenger.ExteraMSDottedIcon",
        "org.telegram.messenger.MoltenStarIcon",
        "org.telegram.messenger.MoltenStarGoogleIcon",
        "org.telegram.messenger.MoltenStarDottedIcon",
        "org.telegram.messenger.MoltenGramMonochromeIcon",
        "org.telegram.messenger.DefaultIcon"
    };

    public static void tryFixLauncherIconIfNeeded() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;
        PackageManager pm = ctx.getPackageManager();
        boolean hasEnabled = false;
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (pm.getComponentEnabledSetting(icon.getComponentName(ctx)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                hasEnabled = true;
                break;
            }
        }
        for (String legacy : LEGACY_ALIASES) {
            ComponentName cn = new ComponentName(ctx.getPackageName(), legacy);
            try {
                if (pm.getComponentEnabledSetting(cn) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                }
            } catch (Exception ignored) {}
        }
        if (!hasEnabled) {
            setIcon(LauncherIcon.MOLTENGRAM);
        }
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return false;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.MOLTENGRAM;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
        for (String legacy : LEGACY_ALIASES) {
            ComponentName cn = new ComponentName(ctx.getPackageName(), legacy);
            try {
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            } catch (Exception ignored) {}
        }
    }

    public static LauncherIcon getCurrentIcon() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return icon;
            }
        }
        return LauncherIcon.MOLTENGRAM;
    }

    public static android.graphics.drawable.Drawable createIconPreviewDrawable(Context context, LauncherIcon icon, int sizeDp) {
        if (icon == null || context == null) return null;
        int size = org.telegram.messenger.AndroidUtilities.dp(sizeDp);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Path path = new android.graphics.Path();
        path.addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW);
        canvas.clipPath(path);
        android.graphics.drawable.Drawable bg = icon.background != 0 ? androidx.core.content.ContextCompat.getDrawable(context, icon.background) : null;
        android.graphics.drawable.Drawable fg = icon.foreground != 0 ? androidx.core.content.ContextCompat.getDrawable(context, icon.foreground) : null;
        app.exteraless.appicons.AppIcons.draw(canvas, bg, fg, size, icon.hasBleed);
        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    public enum IconGroup {
        MOLTENGRAM,
        TELEGRAM
    }

    public enum LauncherIcon {
        // MoltenGram (15 curated styles)
        MOLTENGRAM("MoltenGramIcon", R.drawable.moltengram_adaptive_icon_background,
                R.drawable.moltengram_icon_foreground, R.string.AppIconMoltenGramDefault, IconGroup.MOLTENGRAM, "@moltenst4r", "Monet Material You adaptive", 0xFF1E713B),
        MOLTENGRAM_CLASSIC("MoltenGramClassicIcon", R.drawable.moltengram_icon_background,
                0, R.string.AppIconMoltenGramClassic, IconGroup.MOLTENGRAM, "@moltenst4r", "Signature emerald flame", 0xFF1E713B),
        MOLTENGRAM_PROTON("MoltenGramProtonIcon", R.drawable.moltengram_proton_icon_background,
                0, R.string.AppIconMoltenGramProton, IconGroup.MOLTENGRAM, "@moltenst4r", "Proton cyber aesthetics", 0xFF6D4AFF),
        MOLTENGRAM_SAD("MoltenGramSadIcon", R.drawable.moltengram_sad_icon_background,
                0, R.string.AppIconMoltenGramSad, IconGroup.MOLTENGRAM, "@moltenst4r", "Melancholy moody vibes", 0xFF4A6572),
        MOLTENGRAM_SCHIZOPHRENIA("MoltenGramSchizophreniaIcon", R.drawable.moltengram_schizophrenia_icon_background,
                0, R.string.AppIconMoltenGramSchizophrenia, IconGroup.MOLTENGRAM, "@moltenst4r", "Chaotic glitch reality", 0xFFD32F2F),
        MOLTENGRAM_BURNING("MoltenGramBurningIcon", R.drawable.moltengram_burning_icon_background,
                0, R.string.AppIconMoltenGramBurning, IconGroup.MOLTENGRAM, "@moltenst4r", "Blazing fiery inferno", 0xFFFF5722),
        MOLTENGRAM_ANOTHERCHANCE("MoltenGramAnotherChanceIcon", R.drawable.moltengram_anotherchance_icon_background,
                0, R.string.AppIconMoltenGramAnotherChance, IconGroup.MOLTENGRAM, "@moltenst4r", "Another chance renewal", 0xFF00897B),
        MOLTENGRAM_DOTTED("MoltenGramDottedIcon", R.drawable.moltengram_dotted_icon_background,
                0, R.string.AppIconMoltenGramDotted, IconGroup.MOLTENGRAM, "@moltenst4r", "Nothing OS glyph dot matrix", 0xFFFFFFFF),
        MOLTENGRAM_ENDEAVOUROS("MoltenGramEndeavourOSIcon", R.drawable.moltengram_endeavouros_icon_background,
                0, R.string.AppIconMoltenGramEndeavourOS, IconGroup.MOLTENGRAM, "@moltenst4r", "EndeavourOS indigo violet", 0xFF7B52AB),
        MOLTENGRAM_SUNRISE("MoltenGramSunriseIcon", R.drawable.moltengram_sunrise_icon_background,
                0, R.string.AppIconMoltenGramSunrise, IconGroup.MOLTENGRAM, "@moltenst4r", "Morning twilight gradient", 0xFFE06C53),
        MOLTENGRAM_STAR("MoltenGramStarIcon", R.drawable.moltengram_star_icon_background,
                0, R.string.AppIconMoltenGramStar, IconGroup.MOLTENGRAM, "@moltenst4r", "Deep cosmic space starlight", 0xFFFFD700),
        MOLTENGRAM_COFFEE("MoltenGramCoffeeIcon", R.drawable.moltengram_coffee_icon_background,
                0, R.string.AppIconMoltenGramCoffee, IconGroup.MOLTENGRAM, "@moltenst4r", "Warm marble mocha coffee", 0xFF8B5A2B),
        MOLTENGRAM_GLACIER("MoltenGramGlacierIcon", R.drawable.moltengram_glacier_icon_background,
                0, R.string.AppIconMoltenGramGlacier, IconGroup.MOLTENGRAM, "@moltenst4r", "Deep ocean glacier cyan", 0xFF00B4D8),
        MOLTENGRAM_GOOGLE("MoltenGramGoogleIcon", R.drawable.moltengram_google_icon_background,
                0, R.string.AppIconMoltenGramGoogle, IconGroup.MOLTENGRAM, "@moltenst4r", "Material 3 Expressive", 0xFF4285F4),
        MOLTENGRAM_BEER("MoltenGramBeerIcon", R.drawable.moltengram_beer_icon_background,
                0, R.string.AppIconMoltenGramBeer, IconGroup.MOLTENGRAM, "@moltenst4r", "Golden craft beer foam", 0xFFEC9401),
        MOLTENGRAM_MAGMA("MoltenGramMagmaIcon", R.drawable.moltengram_magma_icon_background,
                0, R.string.AppIconMoltenGramMagma, IconGroup.MOLTENGRAM, "@moltenst4r", "Molten glowing magma", 0xFFC72303),
        MOLTENGRAM_HOLO("MoltenGramHoloIcon", R.drawable.moltengram_holo_icon_background,
                0, R.string.AppIconMoltenGramHolo, IconGroup.MOLTENGRAM, "@moltenst4r", "Iridescent liquid chrome", 0xFFEA6FCC),
        MOLTENGRAM_KRAFT("MoltenGramKraftIcon", R.drawable.moltengram_kraft_icon_background,
                0, R.string.AppIconMoltenGramKraft, IconGroup.MOLTENGRAM, "@moltenst4r", "Vintage kraft paper texture", 0xFFB2A585),
        MOLTENGRAM_CORAL("MoltenGramCoralIcon", R.drawable.moltengram_coral_icon_background,
                0, R.string.AppIconMoltenGramCoral, IconGroup.MOLTENGRAM, "@moltenst4r", "Vibrant coral sunset gradient", 0xFFFF7058),
        MOLTENGRAM_ONYX("MoltenGramOnyxIcon", R.drawable.moltengram_onyx_icon_background,
                0, R.string.AppIconMoltenGramOnyx, IconGroup.MOLTENGRAM, "@moltenst4r", "Deep contrast black and white", 0xFFFFFFFF),
        MOLTENGRAM_NEON("MoltenGramNeonIcon", R.drawable.moltengram_neon_icon_background,
                0, R.string.AppIconMoltenGramNeon, IconGroup.MOLTENGRAM, "@moltenst4r", "Electric neon lime glow", 0xFFCCFF00),
        MOLTENGRAM_EXTERAGRAM("MoltenGramExteraGramIcon", R.drawable.moltengram_exteragram_icon_background,
                0, R.string.AppIconMoltenGramExteraGram, IconGroup.MOLTENGRAM, "@moltenst4r", "Classic ExteraGram signature style", 0xFFE83030),
        MOLTENGRAM_HALLOWEEN("MoltenGramHalloweenIcon", R.drawable.moltengram_halloween_icon_background,
                0, R.string.AppIconMoltenGramHalloween, IconGroup.MOLTENGRAM, "@moltenst4r", "Spooky Halloween night flame", 0xFFFF7701),
        MOLTENGRAM_NOTHING("MoltenGramNothingIcon", R.drawable.moltengram_nothing_icon_background,
                0, R.string.AppIconMoltenGramNothing, IconGroup.MOLTENGRAM, "@moltenst4r", "Nothing OS red glyph matrix", 0xFFFF0401),

        // Telegram
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal, IconGroup.TELEGRAM, "@telegram", "The classic paper plane", 0xFF143854),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage, IconGroup.TELEGRAM, "@telegram", "Retro nostalgic style", 0xFF442A16),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua, IconGroup.TELEGRAM, "@telegram", "Deep ocean water drop", 0xFF123C4A),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium, IconGroup.TELEGRAM, "@telegram", "Telegram Star gradient", 0xFF36184E),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo, IconGroup.TELEGRAM, "@telegram", "Speed and propulsion", 0xFF14345C),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox, IconGroup.TELEGRAM, "@telegram", "Starry night sky", 0xFF161628);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;
        public final IconGroup group;
        public final String author;
        public final String description;
        public final int glowColor;
        public final boolean hasBleed;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title, IconGroup group, String author, String description, int glowColor) {
            this(key, background, foreground, title, false, group, author, description, glowColor, true);
        }

        LauncherIcon(String key, int background, int foreground, int title, IconGroup group, String author, String description, int glowColor, boolean hasBleed) {
            this(key, background, foreground, title, false, group, author, description, glowColor, hasBleed);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium, IconGroup group, String author, String description, int glowColor) {
            this(key, background, foreground, title, premium, group, author, description, glowColor, true);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium, IconGroup group, String author, String description, int glowColor, boolean hasBleed) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
            this.group = group;
            this.author = author;
            this.description = description;
            this.glowColor = glowColor;
            this.hasBleed = hasBleed;
        }

        LauncherIcon(String key, int background, int foreground, int title, IconGroup group, String author) {
            this(key, background, foreground, title, false, group, author, null, 0xFF202020, true);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium, IconGroup group, String author) {
            this(key, background, foreground, title, premium, group, author, null, 0xFF202020, true);
        }

    }
}
