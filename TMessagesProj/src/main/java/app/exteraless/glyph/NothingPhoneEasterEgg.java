package app.exteraless.glyph;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/**
 * Easter egg manager for Nothing and CMF devices.
 * Automatically identifies the exact device model (Phone 1, 2, 2a, 2a Plus, 3, 3a, 4a, CMF 1, CMF 2 Pro)
 * and triggers an on-screen particle explosion with hardware glyphs & vibration.
 */
public class NothingPhoneEasterEgg {

    public static boolean isNothingOrCmfDevice() {
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toLowerCase() : "";
        String product = Build.PRODUCT != null ? Build.PRODUCT.toLowerCase() : "";

        return brand.contains("nothing") || manufacturer.contains("nothing") ||
                brand.contains("cmf") || manufacturer.contains("cmf") ||
                model.contains("nothing") || model.contains("cmf") ||
                model.startsWith("a06") || model.startsWith("a14") || model.startsWith("a01") ||
                model.startsWith("a02") || model.startsWith("a05") || model.startsWith("a00") ||
                model.startsWith("ain06") ||
                device.contains("spacewar") || device.contains("pong") || device.contains("pacman") ||
                device.contains("tetris") || device.contains("aerodactyl") || device.contains("frogger") ||
                device.contains("asteroids") || device.contains("metroid") || device.contains("galaga") ||
                device.contains("blastoise") || device.contains("supercontra") || device.contains("galaxian") ||
                product.contains("spacewar") || product.contains("pong") || product.contains("pacman") ||
                product.contains("tetris") || product.contains("aerodactyl") || product.contains("frogger") ||
                product.contains("asteroids") || product.contains("metroid") || product.contains("galaga") ||
                product.contains("blastoise") || product.contains("supercontra") || product.contains("galaxian");
    }

    public static String getDeviceDisplayName() {
        String model = (Build.MODEL != null ? Build.MODEL : "").toLowerCase();
        String device = (Build.DEVICE != null ? Build.DEVICE : "").toLowerCase();

        // 1. CMF Phone 2 Pro (A001 / Galaga)
        if ((model.contains("cmf") && (model.contains("2") || model.contains("pro"))) || (model.contains("a001") && !model.contains("a001t")) || device.contains("galaga") || device.contains("cmf2")) {
            return "CMF Phone 2 Pro";
        }
        // 2. CMF Phone 1 (A015 / Tetris)
        if (model.contains("cmf") || model.contains("a015") || device.contains("tetris")) {
            return "CMF Phone 1";
        }
        // 3. Nothing Phone (4a) Pro (A069P / FroggerPro)
        if (model.contains("a069p") || (model.contains("4a") && model.contains("pro")) || device.contains("froggerpro")) {
            return "Nothing Phone (4a) Pro";
        }
        // 4. Nothing Phone (4b) (A009P / Blastoise / SuperContra)
        if (model.contains("a009p") || model.contains("4b") || device.contains("blastoise") || device.contains("supercontra")) {
            return "Nothing Phone (4b)";
        }
        // 5. Nothing Phone (4a) (A069 / Frogger)
        if (model.contains("a069") || model.contains("4a") || model.contains("phone 4") || device.contains("frogger")) {
            return "Nothing Phone (4a)";
        }
        // 6. Nothing Phone (3a) Pro
        if (model.contains("3a") && model.contains("pro")) {
            return "Nothing Phone (3a) Pro";
        }
        // 7. Nothing Phone (3a) Lite (A001T / Galaxian)
        if ((model.contains("3a") && model.contains("lite")) || model.contains("a001t") || device.contains("galaxian")) {
            return "Nothing Phone (3a) Lite";
        }
        // 8. Nothing Phone (3a) (A059 / Asteroids)
        if (model.contains("a059") || model.contains("3a") || device.contains("asteroids")) {
            return "Nothing Phone (3a)";
        }
        // 9. Nothing Phone (3) (A024 / Metroid)
        if (model.contains("a024") || model.contains("phone 3") || model.contains("phone (3)") || device.contains("metroid") || device.contains("phone3")) {
            return "Nothing Phone (3)";
        }
        // 10. Nothing Phone (2a) Plus (A142P / PacmanPro)
        if (model.contains("a142p") || (model.contains("2a") && (model.contains("plus") || model.contains("+"))) || device.contains("pacmanpro")) {
            return "Nothing Phone (2a) Plus";
        }
        // 11. Nothing Phone (2a) (A142 / Pacman / Aerodactyl)
        if (model.contains("a142") || model.contains("2a") || device.contains("pacman") || device.contains("aerodactyl")) {
            return "Nothing Phone (2a)";
        }
        // 12. Nothing Phone (2) (A065 / Pong)
        if (model.contains("a065") || model.contains("ain065") || device.contains("pong") || model.contains("phone 2") || model.contains("phone (2)")) {
            return "Nothing Phone (2)";
        }
        // 13. Nothing Phone (1) (A063 / Spacewar)
        if (model.contains("a063") || device.contains("spacewar") || model.contains("phone 1") || model.contains("phone (1)")) {
            return "Nothing Phone (1)";
        }
        return "Nothing Phone";
    }

    public static int[] getDeviceModelDrawables() {
        return getDrawablesForModelName(getDeviceDisplayName());
    }

    public static final String[] ALL_MODELS = new String[]{
            "Nothing Phone (4a) Pro",
            "Nothing Phone (4a)",
            "Nothing Phone (4b)",
            "Nothing Phone (3a) Pro",
            "Nothing Phone (3a)",
            "Nothing Phone (3a) Lite",
            "Nothing Phone (3)",
            "Nothing Phone (2a) Plus",
            "Nothing Phone (2a)",
            "Nothing Phone (2)",
            "Nothing Phone (1)",
            "CMF Phone 2 Pro",
            "CMF Phone 1"
    };

    public static int[] getDrawablesForModelName(String modelName) {
        if ("Nothing Phone (4a) Pro".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone4a_pro_black,
                    R.drawable.glyph_easter_phone4a_pro_white,
                    R.drawable.glyph_easter_phone4a_pro_pink
            };
        }
        if ("Nothing Phone (4a)".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone4a_black,
                    R.drawable.glyph_easter_phone4a_blue,
                    R.drawable.glyph_easter_phone4a_pink
            };
        }
        if ("Nothing Phone (4b)".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone4b_black,
                    R.drawable.glyph_easter_phone4b_white,
                    R.drawable.glyph_easter_phone4b_blue
            };
        }
        if ("Nothing Phone (3a) Pro".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone3a_pro_grey,
                    R.drawable.glyph_easter_phone3a_pro_black
            };
        }
        if ("Nothing Phone (3a)".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone3a_white,
                    R.drawable.glyph_easter_phone3a_blue,
                    R.drawable.glyph_easter_phone3a_black
            };
        }
        if ("Nothing Phone (3a) Lite".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone3a_lite_white,
                    R.drawable.glyph_easter_phone3a_lite_black,
                    R.drawable.glyph_easter_phone3a_lite_blue
            };
        }
        if ("Nothing Phone (3)".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone3_white,
                    R.drawable.glyph_easter_phone3_black
            };
        }
        if ("Nothing Phone (2a) Plus".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone2a_plus_grey,
                    R.drawable.glyph_easter_phone2a_plus_black
            };
        }
        if ("Nothing Phone (2a)".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone2a_black,
                    R.drawable.glyph_easter_phone2a_white,
                    R.drawable.glyph_easter_phone2a_blue
            };
        }
        if ("Nothing Phone (2)".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone2_white,
                    R.drawable.glyph_easter_phone2_black
            };
        }
        if ("Nothing Phone (1)".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_phone1_white,
                    R.drawable.glyph_easter_phone1_black
            };
        }
        if ("CMF Phone 2 Pro".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_cmf2_pro_black,
                    R.drawable.glyph_easter_cmf2_pro_orange,
                    R.drawable.glyph_easter_cmf2_pro_lightgreen,
                    R.drawable.glyph_easter_cmf2_pro_lightgrey
            };
        }
        if ("CMF Phone 1".equals(modelName)) {
            return new int[]{
                    R.drawable.glyph_easter_cmf1_orange,
                    R.drawable.glyph_easter_cmf1_lightgreen,
                    R.drawable.glyph_easter_cmf1_black
            };
        }
        return new int[]{
                R.drawable.glyph_easter_phone1_white,
                R.drawable.glyph_easter_phone1_black
        };
    }

    public static void playBurst(Activity activity, BaseFragment fragment) {
        playBurstForModel(activity, fragment, getDeviceDisplayName());
    }

    public static void playBurstForModel(Activity activity, BaseFragment fragment, String modelName) {
        if (activity == null) {
            return;
        }

        // 1. Haptic feedback
        try {
            Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] timings = new long[]{0, 40, 30, 60, 30, 80};
                    int[] amplitudes = new int[]{0, 180, 0, 220, 0, 255};
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 40, 30, 60, 30, 80}, -1);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }

        // 2. Hardware Glyphs if supported
        try {
            if (GlyphController.getInstance().isSupported()) {
                GlyphController.getInstance().playPhone1BurstEffect(activity);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }

        // 3. On-screen Particle Explosion of the specified phone model
        NothingPhoneBurstOverlay.show(activity, getDrawablesForModelName(modelName));

        // 4. Bulletin info
        if (fragment != null) {
            String title = "💥 " + modelName;
            BulletinFactory.of(fragment)
                    .createSimpleBulletin(R.raw.info, title)
                    .show();
        }
    }
}
