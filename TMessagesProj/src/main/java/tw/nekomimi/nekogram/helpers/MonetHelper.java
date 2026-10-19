package tw.nekomimi.nekogram.helpers;

import static org.telegram.ui.Components.Switch.SWITCH_STYLE_MD3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.PatternMatcher;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.utilities.Blend;
import com.google.android.material.color.utilities.Hct;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xyz.nextalone.nagram.NaConfig;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MonetHelper {
    private static final float DARK_NAME_SOFTEN_RATIO = 0.22f;
    private static final HashMap<String, Integer> ids = new HashMap<>() {{
        put("a1_0", android.R.color.system_accent1_0);
        put("a1_10", android.R.color.system_accent1_10);
        put("a1_50", android.R.color.system_accent1_50);
        put("a1_100", android.R.color.system_accent1_100);
        put("a1_200", android.R.color.system_accent1_200);
        put("a1_300", android.R.color.system_accent1_300);
        put("a1_400", android.R.color.system_accent1_400);
        put("a1_500", android.R.color.system_accent1_500);
        put("a1_600", android.R.color.system_accent1_600);
        put("a1_700", android.R.color.system_accent1_700);
        put("a1_800", android.R.color.system_accent1_800);
        put("a1_900", android.R.color.system_accent1_900);
        put("a1_1000", android.R.color.system_accent1_1000);
        put("a2_0", android.R.color.system_accent2_0);
        put("a2_10", android.R.color.system_accent2_10);
        put("a2_50", android.R.color.system_accent2_50);
        put("a2_100", android.R.color.system_accent2_100);
        put("a2_200", android.R.color.system_accent2_200);
        put("a2_300", android.R.color.system_accent2_300);
        put("a2_400", android.R.color.system_accent2_400);
        put("a2_500", android.R.color.system_accent2_500);
        put("a2_600", android.R.color.system_accent2_600);
        put("a2_700", android.R.color.system_accent2_700);
        put("a2_800", android.R.color.system_accent2_800);
        put("a2_900", android.R.color.system_accent2_900);
        put("a2_1000", android.R.color.system_accent2_1000);
        put("a3_0", android.R.color.system_accent3_0);
        put("a3_10", android.R.color.system_accent3_10);
        put("a3_50", android.R.color.system_accent3_50);
        put("a3_100", android.R.color.system_accent3_100);
        put("a3_200", android.R.color.system_accent3_200);
        put("a3_300", android.R.color.system_accent3_300);
        put("a3_400", android.R.color.system_accent3_400);
        put("a3_500", android.R.color.system_accent3_500);
        put("a3_600", android.R.color.system_accent3_600);
        put("a3_700", android.R.color.system_accent3_700);
        put("a3_800", android.R.color.system_accent3_800);
        put("a3_900", android.R.color.system_accent3_900);
        put("a3_1000", android.R.color.system_accent3_1000);
        put("n1_0", android.R.color.system_neutral1_0);
        put("n1_10", android.R.color.system_neutral1_10);
        put("n1_50", android.R.color.system_neutral1_50);
        put("n1_100", android.R.color.system_neutral1_100);
        put("n1_200", android.R.color.system_neutral1_200);
        put("n1_300", android.R.color.system_neutral1_300);
        put("n1_400", android.R.color.system_neutral1_400);
        put("n1_500", android.R.color.system_neutral1_500);
        put("n1_600", android.R.color.system_neutral1_600);
        put("n1_700", android.R.color.system_neutral1_700);
        put("n1_800", android.R.color.system_neutral1_800);
        put("n1_900", android.R.color.system_neutral1_900);
        put("n1_1000", android.R.color.system_neutral1_1000);
        put("n2_0", android.R.color.system_neutral2_0);
        put("n2_10", android.R.color.system_neutral2_10);
        put("n2_50", android.R.color.system_neutral2_50);
        put("n2_100", android.R.color.system_neutral2_100);
        put("n2_200", android.R.color.system_neutral2_200);
        put("n2_300", android.R.color.system_neutral2_300);
        put("n2_400", android.R.color.system_neutral2_400);
        put("n2_500", android.R.color.system_neutral2_500);
        put("n2_600", android.R.color.system_neutral2_600);
        put("n2_700", android.R.color.system_neutral2_700);
        put("n2_800", android.R.color.system_neutral2_800);
        put("n2_900", android.R.color.system_neutral2_900);
        put("n2_1000", android.R.color.system_neutral2_1000);
        put("monetRedLight", R.color.monetRedLight);
        put("monetRedDark", R.color.monetRedDark);
        put("monetRedCall", R.color.monetRedCall);
        put("monetGreenCall", R.color.monetGreenCall);
    }};

    private static final HashMap<String, Integer> avatarBaseColors = new HashMap<>() {{
        put("monetAvatarRed", 0xffFF845E);
        put("monetAvatarOrange", 0xffFEBB5B);
        put("monetAvatarViolet", 0xffB694F9);
        put("monetAvatarGreen", 0xff9AD164);
        put("monetAvatarCyan", 0xff5BCBE3);
        put("monetAvatarBlue", 0xff5CAFFA);
        put("monetAvatarPink", 0xffFF8AAC);
        put("monetAvatarNameRed", 0xffCC5049);
        put("monetAvatarNameOrange", 0xffD67722);
        put("monetAvatarNameViolet", 0xff955CDB);
        put("monetAvatarNameGreen", 0xff40A920);
        put("monetAvatarNameCyan", 0xff309EBA);
        put("monetAvatarNameBlue", 0xff368AD1);
        put("monetAvatarNamePink", 0xffC7508B);
        put("monetAvatarNameDarkRed", 0xffCC5049);
        put("monetAvatarNameDarkOrange", 0xffD67722);
        put("monetAvatarNameDarkViolet", 0xff955CDB);
        put("monetAvatarNameDarkGreen", 0xff40A920);
        put("monetAvatarNameDarkCyan", 0xff309EBA);
        put("monetAvatarNameDarkBlue", 0xff368AD1);
        put("monetAvatarNameDarkPink", 0xffC7508B);
    }};

    private static final HashMap<String, Integer> materialColors = new HashMap<>() {{
        put("mBlack", 0xff000000);
        put("mWhite", 0xffffffff);
        put("mRed200", 0xffef9a9a);
        put("mRed500", 0xfff44336);
        put("mRed800", 0xffc62828);
        put("mGreen200", 0xffa5d6a7);
        put("mGreen500", 0xff4caf50);
        put("mGreen800", 0xff2e7d32);
    }};

    private static final HashMap<String, Integer> constantColors = new HashMap<>() {{
        put("white", 0xffffffff);
        put("black", 0xff000000);
        put("transparent", 0x00000000);
        put("error_light", 0xffb3261e);
        put("on_error_light", 0xffffffff);
        put("error_container_light", 0xfff9dedc);
        put("on_error_container_light", 0xff410e0b);
        put("error_dark", 0xfff2b8b5);
        put("on_error_dark", 0xff601410);
        put("error_container_dark", 0xff8c1d18);
        put("on_error_container_dark", 0xfff9dedc);
    }};

    private static final HashMap<String, Integer> paletteSeeds = new HashMap<>() {{
        put("blue", 0xff0000ff);
        put("red", 0xffff0000);
        put("green", 0xff00ff00);
        put("orange", 0xffffaa00);
        put("violet", 0xffeb00ff);
        put("pink", 0xffff32ac);
        put("cyan", 0xff14aaac);
    }};

    private static final HashMap<String, String> tonalPalettes = new HashMap<>() {{
        put("primary", "a1");
        put("secondary", "a2");
        put("tertiary", "a3");
        put("neutral", "n1");
        put("neutral_variant", "n2");
    }};

    private static final HashMap<Integer, Integer> toneToShade = new HashMap<>() {{
        put(100, 0);
        put(99, 10);
        put(95, 50);
        put(90, 100);
        put(80, 200);
        put(70, 300);
        put(60, 400);
        put(50, 500);
        put(40, 600);
        put(30, 700);
        put(20, 800);
        put(10, 900);
        put(0, 1000);
    }};

    private static final HashMap<String, String> lightRoles = new HashMap<>() {{
        put("primary", "a1:40");
        put("on_primary", "a1:100");
        put("primary_container", "a1:90");
        put("on_primary_container", "a1:10");
        put("inverse_primary", "a1:80");
        put("surface_tint", "a1:40");
        put("secondary", "a2:40");
        put("on_secondary", "a2:100");
        put("secondary_container", "a2:90");
        put("on_secondary_container", "a2:10");
        put("tertiary", "a3:40");
        put("on_tertiary", "a3:100");
        put("tertiary_container", "a3:90");
        put("on_tertiary_container", "a3:10");
        put("background", "n2:98");
        put("on_background", "n2:10");
        put("surface", "n2:98");
        put("on_surface", "n2:10");
        put("surface_variant", "n2:90");
        put("on_surface_variant", "n2:30");
        put("inverse_surface", "n2:20");
        put("inverse_on_surface", "n2:95");
        put("outline", "n2:50");
        put("outline_variant", "n2:80");
        put("scrim", "n2:0");
        put("shadow", "n2:0");
        put("surface_bright", "n2:98");
        put("surface_dim", "n2:87");
        put("surface_container", "n2:94");
        put("surface_container_high", "n2:92");
        put("surface_container_highest", "n2:90");
        put("surface_container_low", "n2:96");
        put("surface_container_lowest", "n2:100");
    }};

    private static final HashMap<String, String> darkRoles = new HashMap<>() {{
        put("primary", "a1:80");
        put("on_primary", "a1:20");
        put("primary_container", "a1:30");
        put("on_primary_container", "a1:90");
        put("inverse_primary", "a1:40");
        put("surface_tint", "a1:80");
        put("secondary", "a2:80");
        put("on_secondary", "a2:20");
        put("secondary_container", "a2:30");
        put("on_secondary_container", "a2:90");
        put("tertiary", "a3:80");
        put("on_tertiary", "a3:20");
        put("tertiary_container", "a3:30");
        put("on_tertiary_container", "a3:90");
        put("background", "n1:6");
        put("on_background", "n2:90");
        put("surface", "n1:6");
        put("on_surface", "n2:90");
        put("surface_variant", "n2:30");
        put("on_surface_variant", "n2:80");
        put("inverse_surface", "n2:90");
        put("inverse_on_surface", "n2:20");
        put("outline", "n2:60");
        put("outline_variant", "n2:30");
        put("scrim", "n2:0");
        put("shadow", "n2:0");
        put("surface_bright", "n2:24");
        put("surface_dim", "n1:6");
        put("surface_container", "n2:12");
        put("surface_container_high", "n2:17");
        put("surface_container_highest", "n2:22");
        put("surface_container_low", "n2:10");
        put("surface_container_lowest", "n2:4");
    }};

    private static final HashMap<String, String> fixedRoles = new HashMap<>() {{
        put("primary_fixed", "a1:90");
        put("primary_fixed_dim", "a1:80");
        put("on_primary_fixed_dim", "a1:80");
        put("on_primary_fixed", "a1:10");
        put("on_primary_fixed_variant", "a1:30");
        put("secondary_fixed", "a2:90");
        put("secondary_fixed_dim", "a2:80");
        put("on_secondary_fixed_dim", "a2:80");
        put("on_secondary_fixed", "a2:10");
        put("on_secondary_fixed_variant", "a2:30");
        put("tertiary_fixed", "a3:90");
        put("tertiary_fixed_dim", "a3:80");
        put("on_tertiary_fixed_dim", "a3:80");
        put("on_tertiary_fixed", "a3:10");
        put("on_tertiary_fixed_variant", "a3:30");
    }};

    private static final Pattern MODIFIER_PATTERN = Pattern.compile("^([^(]+)\\(([^)]+)\\)?$");
    private static final String ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED";
    private static final HashMap<String, Integer> tokenCache = new HashMap<>();
    private static final OverlayChangeReceiver overlayChangeReceiver = new OverlayChangeReceiver();
    private static int lastMonetColor = 0;

    public static int getColor(String color) {
        Integer resolved = getColorOrNull(color);
        if (resolved == null) {
            FileLog.e("Error loading color " + color);
            return 0;
        }
        return resolved;
    }

    public static Integer getColorOrNull(String color) {
        try {
            String rawColor = color == null ? "" : color.trim();
            if (rawColor.isEmpty()) {
                return null;
            }

            int saturation = 100;
            int lightness = 100;
            int alpha = 100;

            Matcher matcher = MODIFIER_PATTERN.matcher(rawColor);
            if (matcher.find()) {
                String base = matcher.group(1);
                String params = matcher.group(2);
                if (base == null || params == null) {
                    return null;
                }
                rawColor = base.trim();
                for (String param : params.split(",")) {
                    String[] parts = param.split("=");
                    if (parts.length != 2) {
                        continue;
                    }
                    int parsed;
                    try {
                        parsed = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException ignore) {
                        continue;
                    }
                    switch (parts[0].trim()) {
                        case "s":
                            saturation = parsed;
                            break;
                        case "l":
                            lightness = parsed;
                            break;
                        case "a":
                            alpha = parsed;
                            break;
                    }
                }
            }

            Integer resolved = resolveToken(rawColor);
            if (resolved == null) {
                return null;
            }

            int value = resolved;
            if (saturation != 100) {
                value = ColorUtils.blendARGB(0xffffffff, value, saturation / 100f);
            }
            if (lightness != 100) {
                value = ColorUtils.blendARGB(0xff000000, value, lightness / 100f);
            }
            if (alpha != 100) {
                value = ColorUtils.setAlphaComponent(value, (int) (alpha * 2.55f));
            }
            return value;
        } catch (Exception e) {
            FileLog.e("Error loading color " + color, e);
            return null;
        }
    }

    public static void invalidateCache() {
        synchronized (tokenCache) {
            tokenCache.clear();
        }
        invalidateRoleTrust();
    }

    private static Integer resolveToken(String token) {
        synchronized (tokenCache) {
            Integer cached = tokenCache.get(token);
            if (cached != null) {
                return cached;
            }
        }
        Integer resolved = computeToken(token);
        if (resolved != null) {
            synchronized (tokenCache) {
                tokenCache.put(token, resolved);
            }
        }
        return resolved;
    }

    private static Integer computeToken(String token) {
        Integer constant = constantColors.get(token);
        if (constant != null) {
            return constant;
        }

        Integer id = ids.get(token);
        if (id != null) {
            return ApplicationLoader.applicationContext.getColor(id);
        }

        Integer avatarBaseColor = avatarBaseColors.get(token);
        if (avatarBaseColor != null) {
            int harmonizedColor = getHarmonizedAvatarColor(avatarBaseColor);
            if (token.startsWith("monetAvatarNameDark")) {
                return softenColorForDarkText(harmonizedColor);
            }
            return harmonizedColor;
        }

        Integer materialColor = materialColors.get(token);
        if (materialColor != null) {
            if (token.startsWith("mRed") || token.startsWith("mGreen")) {
                return harmonizeColor(materialColor);
            }
            return materialColor;
        }

        Integer role = resolveRole(token);
        if (role != null) {
            return role;
        }

        int lastUnderscore = token.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= token.length() - 1) {
            return null;
        }
        String prefix = token.substring(0, lastUnderscore);
        String suffix = token.substring(lastUnderscore + 1);
        if (!isDigitsOnly(suffix)) {
            return null;
        }
        int number = Integer.parseInt(suffix);

        String tonalPalette = tonalPalettes.get(prefix);
        if (tonalPalette != null) {
            Integer shade = toneToShade.get(number);
            if (shade == null) {
                return null;
            }
            Integer shadeId = ids.get(tonalPalette + "_" + shade);
            if (shadeId == null) {
                return null;
            }
            return ApplicationLoader.applicationContext.getColor(shadeId);
        }

        Integer seed = paletteSeeds.get(prefix);
        if (seed != null && number >= 0 && number <= 100) {
            return getCustomPaletteColor(seed, number);
        }

        Integer base = resolveToken(prefix);
        if (base != null) {
            return darkenByPercent(base, number);
        }
        return null;
    }

    private static Integer resolveRole(String token) {
        String mode;
        if (token.endsWith("_light")) {
            mode = "light";
        } else if (token.endsWith("_dark")) {
            mode = "dark";
        } else {
            String fixed = fixedRoles.get(token);
            return fixed == null ? null : resolveRoleTone(fixed);
        }

        String role = token.substring(0, token.length() - mode.length() - 1);
        String tone = "light".equals(mode) ? lightRoles.get(role) : darkRoles.get(role);
        if (tone == null) {
            return null;
        }

        Integer framework = resolveFrameworkRole(role, mode);
        if (framework != null) {
            return framework;
        }
        return resolveRoleTone(tone);
    }

    private static final int BASELINE_PURPLE_80 = 0xffd0bcff;
    private static final int BASELINE_DARK_SURFACE = 0xff141218;

    private static boolean isStaticBaselineFrameworkRole(int color) {
        int rgb = color & 0xffffff;
        return rgb == (BASELINE_PURPLE_80 & 0xffffff) || rgb == (BASELINE_DARK_SURFACE & 0xffffff);
    }

    private static Boolean frameworkRolesTrusted;

    private static final String[][] ROLE_PROBES = {
            {"primary", "dark"},
            {"surface", "dark"},
            {"primary", "light"},
            {"surface", "light"},
    };

    private static final int ROLE_PROBE_TOLERANCE = 24;

    private static boolean frameworkRolesMatchPalettes() {
        Boolean cached = frameworkRolesTrusted;
        if (cached != null) {
            return cached;
        }

        // OEM / Nothing OS static framework roles check:
        // When dynamic colors are active, framework roles must not be overridden by
        // OEM hardcoded static baseline Purple80 (0xFFD0BCFF) or surface (#141218).
        Integer probePrimary = readFrameworkRole("primary", "dark");
        Integer probeSurface = readFrameworkRole("surface", "dark");
        if ((probePrimary != null && isStaticBaselineFrameworkRole(probePrimary))
                || (probeSurface != null && isStaticBaselineFrameworkRole(probeSurface))) {
            frameworkRolesTrusted = false;
            return false;
        }

        boolean trusted = true;
        int checked = 0;
        for (String[] probe : ROLE_PROBES) {
            String descriptor = "light".equals(probe[1])
                    ? lightRoles.get(probe[0]) : darkRoles.get(probe[0]);
            if (descriptor == null) {
                continue;
            }
            Integer framework = readFrameworkRole(probe[0], probe[1]);
            Integer computed = resolveRoleTone(descriptor);
            if (framework == null || computed == null) {
                continue;
            }
            checked++;
            if (channelDistance(framework, computed) > ROLE_PROBE_TOLERANCE) {
                trusted = false;
                break;
            }
        }
        if (checked == 0) {
            trusted = false;
        }
        frameworkRolesTrusted = trusted;
        return trusted;
    }

    private static int channelDistance(int a, int b) {
        return Math.max(Math.max(
                Math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
                Math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b))),
                Math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)));
    }

    private static void invalidateRoleTrust() {
        frameworkRolesTrusted = null;
    }

    private static Integer resolveFrameworkRole(String role, String mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return null;
        }
        if (!frameworkRolesMatchPalettes()) {
            return null;
        }
        Integer color = readFrameworkRole(role, mode);
        if (color != null && isStaticBaselineFrameworkRole(color)) {
            return null;
        }
        return color;
    }

    private static Integer readFrameworkRole(String role, String mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return null;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return null;
            }
            int id = context.getResources().getIdentifier(
                    "system_" + role + "_" + mode, "color", "android");
            if (id == 0) {
                return null;
            }
            return context.getColor(id);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Integer resolveRoleTone(String descriptor) {
        int separator = descriptor.indexOf(':');
        String palette = descriptor.substring(0, separator);
        int tone = Integer.parseInt(descriptor.substring(separator + 1));

        Integer shade = toneToShade.get(tone);
        if (shade != null) {
            Integer shadeId = ids.get(palette + "_" + shade);
            if (shadeId == null) {
                return null;
            }
            try {
                return ApplicationLoader.applicationContext.getColor(shadeId);
            } catch (Exception ignore) {
                return null;
            }
        }

        Integer keyId = ids.get(palette + "_600");
        if (keyId == null) {
            return null;
        }
        try {
            return setLuminance(ApplicationLoader.applicationContext.getColor(keyId), tone);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static int setLuminance(int color, double luminance) {
        if (luminance < 0.0001d || luminance > 99.9999d) {
            return com.google.android.material.color.utilities.ColorUtils.argbFromLstar(luminance);
        }
        Hct source = Hct.fromInt(color);
        return Hct.from(source.getHue(), source.getChroma(), luminance).toInt();
    }

    private static int getCustomPaletteColor(int seed, int tone) {
        int accentColor = ApplicationLoader.applicationContext.getColor(
                android.R.color.system_accent1_500);
        Hct source = Hct.fromInt(Blend.harmonize(seed, accentColor));
        Hct target = Hct.fromInt(accentColor);
        Hct matched = Hct.fromInt(
                Hct.from(source.getHue(), target.getChroma() * 0.9d, source.getTone()).toInt());
        return Hct.from(matched.getHue(), matched.getChroma(), tone).toInt();
    }

    private static int getHarmonizedAvatarColor(int baseColor) {
        return harmonizeColor(baseColor);
    }

    public static int harmonizeColor(int baseColor) {
        int accentColor = ApplicationLoader.applicationContext.getColor(
                android.R.color.system_accent1_600);
        return Blend.harmonize(baseColor, accentColor);
    }

    private static int softenColorForDarkText(int color) {
        int neutralTextColor = ApplicationLoader.applicationContext.getColor(
                android.R.color.system_neutral1_50);
        return ColorUtils.blendARGB(color, neutralTextColor, DARK_NAME_SOFTEN_RATIO);
    }

    private static int darkenByPercent(int color, int percent) {
        int normalizedPercent = Math.clamp(percent, 1, 100);
        if (normalizedPercent == 100) {
            return color;
        }

        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] = Math.clamp(hsl[2] * normalizedPercent / 100f, 0f, 1f);

        return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), Color.alpha(color));
    }

    private static boolean isDigitsOnly(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    public static boolean useMonetMd3Colors() {
        return NaConfig.INSTANCE.getSwitchStyle().Int() == SWITCH_STYLE_MD3
            && Theme.getActiveTheme() != null
            && Theme.getActiveTheme().isMonet();
    }

    public static void registerOverlayReceiver() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                overlayChangeReceiver.register(context);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static class OverlayChangeReceiver extends BroadcastReceiver {
        private boolean registered;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_OVERLAY_CHANGED.equals(intent.getAction())) {
                return;
            }
            invalidateCache();
            lastMonetColor = 0;
            Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
            if (activeTheme == null || !activeTheme.isMonet()) {
                return;
            }
            boolean isNight = Theme.isCurrentThemeNight();
            Theme.applyTheme(activeTheme, isNight);
            NotificationCenter.getGlobalInstance().postNotificationName(
                    NotificationCenter.needSetDayNightTheme, activeTheme, isNight, null, -1
            );
        }

        void register(Context context) {
            if (registered) {
                return;
            }
            IntentFilter filter = new IntentFilter(ACTION_OVERLAY_CHANGED);
            filter.addDataScheme("package");
            filter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL);
            context.registerReceiver(this, filter);
            registered = true;
        }
    }

    /**
     * Refresh Monet theme if the system color has changed.
     * Called in LaunchActivity.onResume()
     */
    public static void refreshMonetThemeIfChanged() {
        // Quick check: if the current theme is not a Monet theme, return directly
        Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
        if (activeTheme == null || !activeTheme.isMonet()) {
            lastMonetColor = 0; // Reset to detect correctly when switching back to Monet theme
            return;
        }

        int currentColor = getColor("a1_600");

        // Record the color only on the first call, do not trigger refresh
        if (lastMonetColor == 0) {
            lastMonetColor = currentColor;
            return;
        }

        // Return directly if the color has not changed
        if (lastMonetColor == currentColor) {
            return;
        }

        invalidateCache();

        // Refresh theme
        boolean isNight = Theme.isCurrentThemeNight();
        Theme.applyTheme(activeTheme, isNight);
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.needSetDayNightTheme, activeTheme, isNight, null, -1
        );

        lastMonetColor = currentColor;
    }
}
