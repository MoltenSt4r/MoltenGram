package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import app.exteraless.utils.UIUtil;
import tw.nekomimi.nekogram.helpers.MonetHelper;

/**
 * Интерактивный Material 3 Bottom Sheet для тонкой настройки цвета на базе Monet/акцента:
 * - Слайдер отклонения оттенка (Hue Shift: ±180° или 0..360°)
 * - Слайдер насыщенности (Saturation: 0..100%)
 * - Слайдер яркости (Lightness: 0..100%)
 * - Слайдер контраста (Contrast: -100..+100%)
 * - Живой предпросмотр получаемого цвета на фоне, иконке, мини-кнопке FAB и плашке
 * - Быстрые пресеты (Пастель, Глубокий, Инверсия, Третичный)
 * - Применение к FAB и/или к активной теме оформления приложения (Monet / Accent)
 */
public class AccentColorTunerBottomSheet extends BottomSheet {

    private final Theme.ResourcesProvider resourcesProvider;
    private final Consumer<Integer> onApply;
    private final Consumer<Integer> onLiveUpdate;
    private final boolean forIcon;

    private final int baseColor;
    private final float[] baseHsl = new float[3];

    private int hueShift = 0;
    private int saturationPct = 100;
    private int lightnessPct = 50;
    private int contrastPct = 0;
    private int calculatedColor;

    // UI elements
    private View baseColorSwatch;
    private View tunedColorSwatch;
    private TextView hexCodeView;
    private TextView hslInfoView;
    private MiniFabPreviewView miniFabPreview;
    private TextView pillPreviewButton;

    private TunerSliderRow hueSlider;
    private TunerSliderRow satSlider;
    private TunerSliderRow lightSlider;
    private TunerSliderRow contrastSlider;

    private TextView applyButton;

    public AccentColorTunerBottomSheet(
            @NonNull Context context,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            int initialColor,
            boolean forIcon,
            @NonNull Consumer<Integer> onApply,
            @Nullable Consumer<Integer> onLiveUpdate
    ) {
        super(context, true, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        this.onApply = onApply;
        this.onLiveUpdate = onLiveUpdate;
        this.forIcon = forIcon;

        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        // Resolve base accent
        Integer monet = MonetHelper.getColorOrNull("primary");
        if (monet != null) {
            baseColor = monet;
        } else {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            baseColor = (accent != 0) ? accent : 0xFF2675EB;
        }
        ColorUtils.colorToHSL(baseColor, baseHsl);

        // Calculate initial slider values
        if (initialColor != 0 && initialColor != UIUtil.FAB_COLOR_DEFAULT) {
            float[] initHsl = new float[3];
            ColorUtils.colorToHSL(initialColor, initHsl);

            float diff = initHsl[0] - baseHsl[0];
            while (diff > 180f) diff -= 360f;
            while (diff < -180f) diff += 360f;
            hueShift = Math.round(diff);
            saturationPct = Math.round(initHsl[1] * 100f);
            lightnessPct = Math.round(initHsl[2] * 100f);
            contrastPct = 0;
        } else {
            hueShift = 0;
            saturationPct = Math.round(baseHsl[1] * 100f);
            lightnessPct = Math.round(baseHsl[2] * 100f);
            contrastPct = 0;
        }

        calculatedColor = calculateTunedColor(baseColor, hueShift, lightnessPct, saturationPct, contrastPct);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(24));

        // Drag handle pill
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dp(3));
        handleBg.setColor(Theme.getColor(Theme.key_sheet_scrollUp, resourcesProvider));
        handle.setBackground(handleBg);
        content.addView(handle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Title & subtitle
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        TextView titleView = new TextView(context);
        titleView.setText(forIcon ? "Настройка цвета иконки на базе акцента" : "Создание цвета на базе акцентного");
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTextColor(textColor);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView subtitleView = new TextView(context);
        subtitleView.setText("Тонкая подстройка параметров цвета относительно системной палитры Monet / темы.");
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        content.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Live Preview Card
        buildPreviewCard(context, content);

        // Quick Preset Chips
        buildPresetChips(context, content);

        // Sliders
        buildSliders(context, content);

        // Action Buttons Row
        buildActionButtons(context, content);

        scrollView.addView(content);
        setCustomView(scrollView);

        updateVisuals();
    }

    private void buildPreviewCard(Context context, LinearLayout parent) {
        FrameLayout card = new FrameLayout(context);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(16));
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        cardBg.setColor(Theme.multAlpha(textColor, 0.05f));
        cardBg.setStroke(dp(1), Theme.multAlpha(textColor, 0.10f));
        card.setBackground(cardBg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout cardLayout = new LinearLayout(context);
        cardLayout.setOrientation(LinearLayout.VERTICAL);

        // Row 1: Swatches comparison & Hex text
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        // Base accent dot
        baseColorSwatch = new View(context);
        GradientDrawable baseBg = new GradientDrawable();
        baseBg.setShape(GradientDrawable.OVAL);
        baseBg.setColor(baseColor);
        baseBg.setStroke(dp(1.5f), 0x33000000);
        baseColorSwatch.setBackground(baseBg);
        row1.addView(baseColorSwatch, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        TextView baseLabel = new TextView(context);
        baseLabel.setText("Акцент");
        baseLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        baseLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        row1.addView(baseLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.msg_arrowright);
        arrow.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), PorterDuff.Mode.SRC_IN);
        row1.addView(arrow, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        // Tuned color dot
        tunedColorSwatch = new View(context);
        GradientDrawable tunedBg = new GradientDrawable();
        tunedBg.setShape(GradientDrawable.OVAL);
        tunedBg.setColor(calculatedColor);
        tunedBg.setStroke(dp(1.5f), 0x33000000);
        tunedColorSwatch.setBackground(tunedBg);
        row1.addView(tunedColorSwatch, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        // Hex & Hsl labels
        LinearLayout hexCol = new LinearLayout(context);
        hexCol.setOrientation(LinearLayout.VERTICAL);

        hexCodeView = new TextView(context);
        hexCodeView.setText(String.format("#%06X", (0xFFFFFF & calculatedColor)));
        hexCodeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        hexCodeView.setTypeface(AndroidUtilities.bold());
        hexCodeView.setTextColor(textColor);
        hexCol.addView(hexCodeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        hslInfoView = new TextView(context);
        hslInfoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        hslInfoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        hexCol.addView(hslInfoView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        row1.addView(hexCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        cardLayout.addView(row1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // Row 2: Live interactive previews (Mini FAB preview + Pill button)
        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);

        miniFabPreview = new MiniFabPreviewView(context);
        row2.addView(miniFabPreview, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        pillPreviewButton = new TextView(context);
        pillPreviewButton.setText("Образец элемента");
        pillPreviewButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        pillPreviewButton.setTypeface(AndroidUtilities.bold());
        pillPreviewButton.setGravity(Gravity.CENTER);
        pillPreviewButton.setPadding(dp(18), dp(9), dp(18), dp(9));
        row2.addView(pillPreviewButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        cardLayout.addView(row2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        card.addView(cardLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        parent.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));
    }

    private void buildPresetChips(Context context, LinearLayout parent) {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);

        LinearLayout chipsContainer = new LinearLayout(context);
        chipsContainer.setOrientation(LinearLayout.HORIZONTAL);

        class Preset {
            final String label;
            final int h;
            final int s;
            final int l;
            final int c;

            Preset(String label, int h, int s, int l, int c) {
                this.label = label;
                this.h = h;
                this.s = s;
                this.l = l;
                this.c = c;
            }
        }

        int baseSat = Math.round(baseHsl[1] * 100f);
        int baseLight = Math.round(baseHsl[2] * 100f);

        Preset[] presets = new Preset[]{
                new Preset("Исходный акцент", 0, baseSat, baseLight, 0),
                new Preset("Пастель (MD3)", 0, Math.min(baseSat, 45), 82, -15),
                new Preset("Глубокий (Vibrant)", 0, Math.min(100, baseSat + 25), 38, 25),
                new Preset("Инверсия (+180°)", 180, baseSat, baseLight, 0),
                new Preset("Третичный (+60°)", 60, baseSat, baseLight, 0),
                new Preset("Холодный (-60°)", -60, baseSat, baseLight, 0)
        };

        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        int inactiveBg = Theme.multAlpha(textColor, 0.07f);

        for (int i = 0; i < presets.length; i++) {
            final Preset p = presets[i];
            TextView chip = new TextView(context);
            chip.setText(p.label);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            chip.setTextColor(textColor);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setBackground(Theme.createRoundRectDrawable(dp(14), inactiveBg));

            chip.setOnClickListener(v -> {
                performHaptic();
                hueShift = p.h;
                saturationPct = p.s;
                lightnessPct = p.l;
                contrastPct = p.c;

                hueSlider.setValue(hueShift);
                satSlider.setValue(saturationPct);
                lightSlider.setValue(lightnessPct);
                contrastSlider.setValue(contrastPct);

                recalculateAndNotify();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < presets.length - 1) {
                lp.rightMargin = dp(8);
            }
            chipsContainer.addView(chip, lp);
        }

        hsv.addView(chipsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        parent.addView(hsv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    private void buildSliders(Context context, LinearLayout parent) {
        // 1. Hue Shift (-180..+180)
        hueSlider = new TunerSliderRow(
                context,
                "Смещение оттенка (Hue Shift)",
                "-180°", "+180°",
                -180, 180,
                hueShift,
                "°",
                val -> {
                    hueShift = val;
                    recalculateAndNotify();
                }
        );
        parent.addView(hueSlider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // 2. Saturation (0..100)
        satSlider = new TunerSliderRow(
                context,
                "Насыщенность (Saturation)",
                "0% (Серый)", "100% (Сочный)",
                0, 100,
                saturationPct,
                "%",
                val -> {
                    saturationPct = val;
                    recalculateAndNotify();
                }
        );
        parent.addView(satSlider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // 3. Lightness (0..100)
        lightSlider = new TunerSliderRow(
                context,
                "Яркость (Lightness)",
                "0% (Темный)", "100% (Светлый)",
                0, 100,
                lightnessPct,
                "%",
                val -> {
                    lightnessPct = val;
                    recalculateAndNotify();
                }
        );
        parent.addView(lightSlider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // 4. Contrast (-100..+100)
        contrastSlider = new TunerSliderRow(
                context,
                "Контраст (Contrast)",
                "Мягкий (-100%)", "Резкий (+100%)",
                -100, 100,
                contrastPct,
                "%",
                val -> {
                    contrastPct = val;
                    recalculateAndNotify();
                }
        );
        parent.addView(contrastSlider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));
    }

    private void buildActionButtons(Context context, LinearLayout parent) {
        // Primary button: Apply to FAB
        applyButton = new TextView(context);
        applyButton.setText(forIcon ? "Применить цвет к иконке" : "Применить цвет к кнопке FAB");
        applyButton.setGravity(Gravity.CENTER);
        applyButton.setTypeface(AndroidUtilities.bold());
        applyButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        applyButton.setPadding(0, dp(13), 0, dp(13));
        applyButton.setOnClickListener(v -> {
            if (onApply != null) {
                onApply.accept(calculatedColor);
            }
            dismiss();
        });
        parent.addView(applyButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Secondary button: Apply as theme accent
        TextView applyThemeBtn = new TextView(context);
        applyThemeBtn.setText("Применить также к интерфейсу (акцент темы)");
        applyThemeBtn.setGravity(Gravity.CENTER);
        applyThemeBtn.setTypeface(AndroidUtilities.bold());
        applyThemeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        applyThemeBtn.setTextColor(textColor);
        GradientDrawable secondaryBg = new GradientDrawable();
        secondaryBg.setCornerRadius(dp(12));
        secondaryBg.setColor(Theme.multAlpha(textColor, 0.07f));
        applyThemeBtn.setBackground(secondaryBg);
        applyThemeBtn.setPadding(0, dp(11), 0, dp(11));
        applyThemeBtn.setOnClickListener(v -> {
            applyCustomAccentToInterface(calculatedColor);
            if (onApply != null) {
                onApply.accept(calculatedColor);
            }
            dismiss();
        });
        parent.addView(applyThemeBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Cancel flat text button
        TextView cancelBtn = new TextView(context);
        cancelBtn.setText(LocaleController.getString(R.string.Cancel));
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider));
        cancelBtn.setPadding(0, dp(8), 0, dp(8));
        cancelBtn.setOnClickListener(v -> dismiss());
        parent.addView(cancelBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void recalculateAndNotify() {
        calculatedColor = calculateTunedColor(baseColor, hueShift, lightnessPct, saturationPct, contrastPct);
        updateVisuals();
        if (onLiveUpdate != null) {
            onLiveUpdate.accept(calculatedColor);
        }
    }

    private void updateVisuals() {
        if (tunedColorSwatch != null) {
            GradientDrawable tunedBg = new GradientDrawable();
            tunedBg.setShape(GradientDrawable.OVAL);
            tunedBg.setColor(calculatedColor);
            tunedBg.setStroke(dp(1.5f), 0x33000000);
            tunedColorSwatch.setBackground(tunedBg);
        }

        if (hexCodeView != null) {
            hexCodeView.setText(String.format("#%06X", (0xFFFFFF & calculatedColor)));
        }

        if (hslInfoView != null) {
            float[] hsl = new float[3];
            ColorUtils.colorToHSL(calculatedColor, hsl);
            hslInfoView.setText(String.format(java.util.Locale.US, "H: %d°  S: %d%%  L: %d%%",
                    Math.round(hsl[0]), Math.round(hsl[1] * 100), Math.round(hsl[2] * 100)));
        }

        if (miniFabPreview != null) {
            miniFabPreview.setColor(calculatedColor, forIcon);
        }

        if (pillPreviewButton != null) {
            int onAccent = UIUtil.getOnAccentColor(calculatedColor);
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(dp(16));
            pillBg.setColor(calculatedColor);
            pillPreviewButton.setBackground(pillBg);
            pillPreviewButton.setTextColor(onAccent);
        }

        if (applyButton != null) {
            int btnBg = calculatedColor;
            int onAccent = UIUtil.getOnAccentColor(btnBg);
            GradientDrawable btnDrawable = new GradientDrawable();
            btnDrawable.setCornerRadius(dp(12));
            btnDrawable.setColor(btnBg);
            applyButton.setBackground(btnDrawable);
            applyButton.setTextColor(onAccent);
        }
    }

    private void performHaptic() {
        try {
            if (applyButton != null) {
                applyButton.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Вычисляет производный цвет относительно базового акцента:
     * - Отклонение оттенка Hue Shift (-180°..+180°)
     * - Насыщенность (0..100%)
     * - Яркость (0..100%)
     * - Контраст (-100%..+100%)
     */
    public static int calculateTunedColor(int baseColor, int hueShift, int lightnessPct, int saturationPct, int contrastPct) {
        float[] baseHsl = new float[3];
        ColorUtils.colorToHSL(baseColor, baseHsl);

        float h = (baseHsl[0] + hueShift) % 360f;
        if (h < 0f) {
            h += 360f;
        }

        float s = Math.max(0f, Math.min(1f, saturationPct / 100f));
        float l = Math.max(0f, Math.min(1f, lightnessPct / 100f));

        if (contrastPct != 0) {
            float factor = 1.0f + (contrastPct / 100f);
            l = 0.5f + (l - 0.5f) * factor;
            l = Math.max(0f, Math.min(1f, l));
        }

        return ColorUtils.HSLToColor(new float[]{h, s, l});
    }

    /**
     * Применяет кастомный цвет как активный акцент темы приложения и перезагружает цвета интерфейса.
     */
    public static void applyCustomAccentToInterface(int color) {
        try {
            Theme.ThemeInfo currentTheme = Theme.getCurrentTheme();
            if (currentTheme != null) {
                Theme.ThemeAccent accent = currentTheme.getAccent(true);
                if (accent != null) {
                    accent.accentColor = color;
                    Theme.saveThemeAccents(currentTheme, true, false, false, false);
                    Theme.refreshThemeColors();
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * Компактная MD3 строка слайдера с бейджем значения и подписями диапазона.
     */
    private static class TunerSliderRow extends LinearLayout {

        private final int min;
        private final int max;
        private final String suffix;
        private final Consumer<Integer> onChange;

        private final TextView badgeView;
        private final SeekBarView seekBarView;
        private int currentValue;

        public TunerSliderRow(
                Context context,
                String title,
                String leftLabel,
                String rightLabel,
                int min,
                int max,
                int initialValue,
                String suffix,
                Consumer<Integer> onChange
        ) {
            super(context);
            this.min = min;
            this.max = max;
            this.suffix = suffix;
            this.onChange = onChange;
            this.currentValue = initialValue;

            setOrientation(VERTICAL);
            setPadding(0, dp(4), 0, dp(4));

            // Header line: Title on left, Badge on right
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            titleView.setTypeface(AndroidUtilities.bold());
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            titleView.setTextColor(textColor);
            header.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            badgeView = new TextView(context);
            badgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            badgeView.setTypeface(AndroidUtilities.bold());
            badgeView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(6));
            badgeBg.setColor(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton), 0.12f));
            badgeView.setBackground(badgeBg);
            badgeView.setPadding(dp(7), dp(2), dp(7), dp(2));
            header.addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

            // Seekbar
            seekBarView = new SeekBarView(context, true, null);
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int val = Math.round(min + (max - min) * progress);
                    if (val != currentValue) {
                        currentValue = val;
                        updateBadge();
                        if (onChange != null) {
                            onChange.accept(currentValue);
                        }
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }
            });
            addView(seekBarView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 0, 0, 0));

            // Subtitle range labels
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(HORIZONTAL);

            TextView left = new TextView(context);
            left.setText(leftLabel);
            left.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            left.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            labels.addView(left, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView right = new TextView(context);
            right.setText(rightLabel);
            right.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            right.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            right.setGravity(Gravity.RIGHT);
            labels.addView(right, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            addView(labels, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));

            setValue(initialValue);
        }

        public void setValue(int value) {
            currentValue = Math.max(min, Math.min(max, value));
            float progress = (max == min) ? 0f : (currentValue - min) / (float) (max - min);
            seekBarView.setProgress(progress);
            updateBadge();
        }

        private void updateBadge() {
            String sign = (min < 0 && currentValue > 0) ? "+" : "";
            badgeView.setText(sign + currentValue + suffix);
        }
    }

    /**
     * Мини-вид кнопки FAB с выбранной формой для живого предпросмотра контраста и цвета.
     */
    private static class MiniFabPreviewView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Drawable icon;
        private int color;
        private boolean forIconMode;

        public MiniFabPreviewView(Context context) {
            super(context);
            paint.setStyle(Paint.Style.FILL);
            try {
                icon = ContextCompat.getDrawable(context, R.drawable.msg_edit);
                if (icon != null) {
                    icon = icon.mutate();
                }
            } catch (Throwable ignored) {
            }
        }

        public void setColor(int color, boolean forIconMode) {
            this.color = color;
            this.forIconMode = forIconMode;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            int shapeId = AppearanceConfig.fabShape();
            Path path = Md3AvatarShapes.getShapePath(shapeId, 0, 0, w, h);

            int bgColor = forIconMode ? Theme.getColor(Theme.key_featuredStickers_addButton) : color;
            int iconColor = forIconMode ? color : UIUtil.getOnAccentColor(bgColor);

            paint.setColor(bgColor);
            canvas.drawPath(path, paint);

            if (icon != null) {
                int iconSize = (int) (w * 0.46f);
                int left = (w - iconSize) / 2;
                int top = (h - iconSize) / 2;
                icon.setBounds(left, top, left + iconSize, top + iconSize);
                icon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
                icon.draw(canvas);
            }
        }
    }
}
