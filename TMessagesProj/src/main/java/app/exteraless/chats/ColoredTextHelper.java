package app.exteraless.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

public class ColoredTextHelper {

    public static final String PREFIX = "tg://color";

    public static boolean isColorUrl(String url) {
        return url != null && (url.startsWith(PREFIX) || url.startsWith("color:"));
    }

    public static void applyColorSpan(Spannable spannable, String url, int offset) {
        if (!app.exteraless.OpenExteraConfig.coloredText()) {
            return;
        }
        if (spannable == null || url == null) {
            return;
        }
        try {
            Uri uri = Uri.parse(url);
            String colorStr = uri.getQueryParameter("c");
            if (colorStr == null) {
                colorStr = uri.getQueryParameter("color");
            }
            if (colorStr == null && url.contains(":")) {
                colorStr = url.substring(url.indexOf(":") + 1);
                if (colorStr.startsWith("//")) colorStr = colorStr.substring(2);
                if (colorStr.startsWith("color?")) colorStr = colorStr.substring(6);
                if (colorStr.startsWith("#")) colorStr = colorStr.substring(1);
            }
            if (colorStr == null || colorStr.isEmpty()) {
                return;
            }
            if (colorStr.startsWith("#")) {
                colorStr = colorStr.substring(1);
            }
            long val = Long.parseLong(colorStr, 16);
            int color;
            if (colorStr.length() <= 6) {
                color = 0xFF000000 | (int) val;
            } else {
                color = (int) val;
            }

            int len = 0;
            String lenStr = uri.getQueryParameter("len");
            if (lenStr == null) {
                lenStr = uri.getQueryParameter("length");
            }
            if (lenStr != null) {
                try {
                    len = Integer.parseInt(lenStr);
                } catch (Exception ignored) {}
            }

            int spanStart = offset;
            if (offset < spannable.length() && spannable.charAt(offset) == '\u200B') {
                spanStart = offset + 1;
            }
            int spanEnd;
            if (len > 0) {
                spanEnd = Math.min(spannable.length(), spanStart + len);
            } else {
                spanEnd = spannable.length();
                for (int i = spanStart; i < spannable.length(); i++) {
                    if (spannable.charAt(i) == '\u200B') {
                        spanEnd = i;
                        break;
                    }
                }
            }
            if (spanStart < spanEnd && spanStart < spannable.length()) {
                spannable.setSpan(new ForegroundColorSpan(color), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void showColorPickerDialog(Context context, Theme.ResourcesProvider resourcesProvider, int initialColor, Utilities.Callback<Integer> onColorChosen) {
        if (context == null) return;

        final float[] hsv = new float[3];
        Color.colorToHSV(initialColor != 0 ? initialColor : 0xFFE91E63, hsv);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle("Цвет текста");

        int surfaceColor = Theme.getColor(Theme.key_dialogBackground, resourcesProvider);
        int textColor = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
        int subTextColor = Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider);
        int tonalBg = ColorUtils.blendARGB(surfaceColor, textColor, 0.08f);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(4));

        // 1. MD3 Tonal Preview Card with adaptive contrast
        FrameLayout previewBox = new FrameLayout(context);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(dp(20));
        int initRgb = Color.HSVToColor(hsv);
        previewBg.setColor(initRgb);
        previewBg.setStroke(dp(1), ColorUtils.setAlphaComponent(textColor, 0x1A));
        previewBox.setBackground(previewBg);
        previewBox.setPadding(dp(18), dp(14), dp(18), dp(14));

        TextView previewText = new TextView(context);
        previewText.setText("Образец текста");
        previewText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        previewText.setTypeface(AndroidUtilities.bold());
        int onSwatch = app.exteraless.utils.UIUtil.getOnAccentColor(initRgb);
        previewText.setTextColor(onSwatch);
        previewBox.addView(previewText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        TextView hexText = new TextView(context);
        hexText.setText(String.format("#%06X", (0xFFFFFF & initRgb)));
        hexText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        hexText.setTypeface(AndroidUtilities.headerTypeface());
        hexText.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable hexBadge = new GradientDrawable();
        hexBadge.setCornerRadius(dp(12));
        hexBadge.setColor(ColorUtils.setAlphaComponent(onSwatch, 0x22));
        hexText.setBackground(hexBadge);
        hexText.setTextColor(onSwatch);
        previewBox.addView(hexText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        root.addView(previewBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 60, 0, 0, 0, 14));

        // 2. Three global-styled SeekBarView sliders (follows NaConfig.getSliderStyle & AppearanceConfig.sliderThickness)
        LinearLayout hueRow = new LinearLayout(context);
        hueRow.setOrientation(LinearLayout.HORIZONTAL);
        hueRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView hueLabel = new TextView(context);
        hueLabel.setText("Оттенок");
        hueLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hueLabel.setTypeface(AndroidUtilities.bold());
        hueLabel.setTextColor(textColor);
        hueRow.addView(hueLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView hueVal = new TextView(context);
        hueVal.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        hueVal.setTypeface(AndroidUtilities.bold());
        hueVal.setTextColor(subTextColor);
        hueVal.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable huePill = new GradientDrawable();
        huePill.setCornerRadius(dp(10));
        huePill.setColor(tonalBg);
        hueVal.setBackground(huePill);
        hueVal.setText(Math.round(hsv[0]) + "°");
        hueRow.addView(hueVal, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        root.addView(hueRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        SeekBarView hueBar = new SeekBarView(context, resourcesProvider);
        hueBar.setReportChanges(true);
        hueBar.setProgress(hsv[0] / 360f);
        root.addView(hueBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 4));

        LinearLayout satRow = new LinearLayout(context);
        satRow.setOrientation(LinearLayout.HORIZONTAL);
        satRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView satLabel = new TextView(context);
        satLabel.setText("Насыщенность");
        satLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        satLabel.setTypeface(AndroidUtilities.bold());
        satLabel.setTextColor(textColor);
        satRow.addView(satLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView satVal = new TextView(context);
        satVal.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        satVal.setTypeface(AndroidUtilities.bold());
        satVal.setTextColor(subTextColor);
        satVal.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable satPill = new GradientDrawable();
        satPill.setCornerRadius(dp(10));
        satPill.setColor(tonalBg);
        satVal.setBackground(satPill);
        satVal.setText(Math.round(hsv[1] * 100f) + "%");
        satRow.addView(satVal, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        root.addView(satRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        SeekBarView satBar = new SeekBarView(context, resourcesProvider);
        satBar.setReportChanges(true);
        satBar.setProgress(hsv[1]);
        root.addView(satBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 4));

        LinearLayout valRow = new LinearLayout(context);
        valRow.setOrientation(LinearLayout.HORIZONTAL);
        valRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView valLabel = new TextView(context);
        valLabel.setText("Яркость");
        valLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valLabel.setTypeface(AndroidUtilities.bold());
        valLabel.setTextColor(textColor);
        valRow.addView(valLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView valVal = new TextView(context);
        valVal.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        valVal.setTypeface(AndroidUtilities.bold());
        valVal.setTextColor(subTextColor);
        valVal.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable valPill = new GradientDrawable();
        valPill.setCornerRadius(dp(10));
        valPill.setColor(tonalBg);
        valVal.setBackground(valPill);
        valVal.setText(Math.round(hsv[2] * 100f) + "%");
        valRow.addView(valVal, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        root.addView(valRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        SeekBarView valBar = new SeekBarView(context, resourcesProvider);
        valBar.setReportChanges(true);
        valBar.setProgress(hsv[2]);
        root.addView(valBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 10));

        int[] presets = new int[]{
                0xFFF44336, // Red
                0xFFFF9800, // Orange
                0xFFFFEB3B, // Yellow
                0xFF4CAF50, // Green
                0xFF00BCD4, // Cyan
                0xFF2196F3, // Blue
                0xFF9C27B0, // Purple
                0xFFE91E63  // Pink
        };
        LinearLayout presetsLayout = new LinearLayout(context);
        presetsLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetsLayout.setGravity(Gravity.CENTER);
        final View[] swatchViews = new View[presets.length];
        final GradientDrawable[] swatchDrawables = new GradientDrawable[presets.length];

        Runnable updateState = () -> {
            int curColor = Color.HSVToColor(hsv);
            int curOn = app.exteraless.utils.UIUtil.getOnAccentColor(curColor);
            previewBg.setColor(curColor);
            previewBox.invalidate();
            previewText.setTextColor(curOn);
            hexBadge.setColor(ColorUtils.setAlphaComponent(curOn, 0x22));
            hexText.setTextColor(curOn);
            hexText.setText(String.format("#%06X", (0xFFFFFF & curColor)));
            hueVal.setText(Math.round(hsv[0]) + "°");
            satVal.setText(Math.round(hsv[1] * 100f) + "%");
            valVal.setText(Math.round(hsv[2] * 100f) + "%");

            int trackBg = Theme.getColor(Theme.key_player_progressBackground, resourcesProvider);
            int pureHue = Color.HSVToColor(new float[]{hsv[0], 1f, 0.9f});
            hueBar.setColors(trackBg, pureHue);
            satBar.setColors(trackBg, curColor);
            valBar.setColors(trackBg, curColor);

            for (int i = 0; i < presets.length; i++) {
                boolean selected = (presets[i] & 0xFFFFFF) == (curColor & 0xFFFFFF);
                swatchDrawables[i].setStroke(dp(selected ? 2.5f : 1f), selected ? textColor : ColorUtils.setAlphaComponent(textColor, 0x26));
                swatchViews[i].setScaleX(selected ? 1.12f : 1f);
                swatchViews[i].setScaleY(selected ? 1.12f : 1f);
            }
        };

        hueBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                hsv[0] = Math.max(0f, Math.min(360f, progress * 360f));
                updateState.run();
            }
            @Override public void onSeekBarPressed(boolean pressed) {}
        });

        satBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                hsv[1] = Math.max(0f, Math.min(1f, progress));
                updateState.run();
            }
            @Override public void onSeekBarPressed(boolean pressed) {}
        });

        valBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                hsv[2] = Math.max(0f, Math.min(1f, progress));
                updateState.run();
            }
            @Override public void onSeekBarPressed(boolean pressed) {}
        });

        // 3. MD3 Circular preset swatches with active selection ring
        for (int i = 0; i < presets.length; i++) {
            int pColor = presets[i];
            View dot = new View(context);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(pColor);
            dot.setBackground(d);
            dot.setOnClickListener(v -> {
                Color.colorToHSV(pColor, hsv);
                hueBar.setProgress(hsv[0] / 360f, true);
                satBar.setProgress(hsv[1], true);
                valBar.setProgress(hsv[2], true);
                updateState.run();
            });
            swatchViews[i] = dot;
            swatchDrawables[i] = d;
            presetsLayout.addView(dot, LayoutHelper.createLinear(28, 28, 4, 0, 4, 0));
        }
        root.addView(presetsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));
        updateState.run();

        builder.setView(root);
        builder.setPositiveButton("Применить", (dialog, which) -> {
            int finalColor = Color.HSVToColor(hsv);
            if (onColorChosen != null) {
                onColorChosen.run(finalColor);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
