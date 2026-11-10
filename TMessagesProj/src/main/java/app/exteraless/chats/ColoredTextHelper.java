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
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class ColoredTextHelper {

    public static final String PREFIX = "tg://color";

    public static boolean isColorUrl(String url) {
        return url != null && (url.startsWith(PREFIX) || url.startsWith("color:"));
    }

    public static void applyColorSpan(Spannable spannable, String url, int offset) {
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

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(8));

        // 1. Preview box
        FrameLayout previewBox = new FrameLayout(context);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(dp(12));
        previewBg.setColor(Color.HSVToColor(hsv));
        previewBox.setBackground(previewBg);
        previewBox.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView previewText = new TextView(context);
        previewText.setText("Образец текста");
        previewText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        previewText.setTypeface(AndroidUtilities.bold());
        previewText.setTextColor(Color.WHITE);
        previewText.setShadowLayer(dp(2), 0, dp(1), 0x88000000);
        previewBox.addView(previewText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        TextView hexText = new TextView(context);
        hexText.setText(String.format("#%06X", (0xFFFFFF & Color.HSVToColor(hsv))));
        hexText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hexText.setTypeface(AndroidUtilities.headerTypeface());
        hexText.setTextColor(Color.WHITE);
        hexText.setShadowLayer(dp(2), 0, dp(1), 0x88000000);
        previewBox.addView(hexText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        root.addView(previewBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 0, 0, 16));

        // 2. Labels and 3 Sliders: Hue, Saturation, Brightness
        TextView hueLabel = new TextView(context);
        hueLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hueLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        hueLabel.setText("Оттенок: " + Math.round(hsv[0]) + "°");
        root.addView(hueLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        SeekBar hueBar = new SeekBar(context);
        hueBar.setMax(360);
        hueBar.setProgress(Math.round(hsv[0]));
        root.addView(hueBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        TextView satLabel = new TextView(context);
        satLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        satLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        satLabel.setText("Насыщенность: " + Math.round(hsv[1] * 100f) + "%");
        root.addView(satLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        SeekBar satBar = new SeekBar(context);
        satBar.setMax(100);
        satBar.setProgress(Math.round(hsv[1] * 100f));
        root.addView(satBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        TextView valLabel = new TextView(context);
        valLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        valLabel.setText("Яркость: " + Math.round(hsv[2] * 100f) + "%");
        root.addView(valLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        SeekBar valBar = new SeekBar(context);
        valBar.setMax(100);
        valBar.setProgress(Math.round(hsv[2] * 100f));
        root.addView(valBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        Runnable updateState = () -> {
            int curColor = Color.HSVToColor(hsv);
            previewBg.setColor(curColor);
            previewBox.invalidate();
            hexText.setText(String.format("#%06X", (0xFFFFFF & curColor)));
            hueLabel.setText("Оттенок: " + Math.round(hsv[0]) + "°");
            satLabel.setText("Насыщенность: " + Math.round(hsv[1] * 100f) + "%");
            valLabel.setText("Яркость: " + Math.round(hsv[2] * 100f) + "%");
        };

        hueBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hsv[0] = progress;
                updateState.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        satBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hsv[1] = progress / 100f;
                updateState.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        valBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hsv[2] = progress / 100f;
                updateState.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 3. Quick preset chips
        LinearLayout presetsLayout = new LinearLayout(context);
        presetsLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetsLayout.setGravity(Gravity.CENTER);
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
        for (int pColor : presets) {
            View dot = new View(context);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(pColor);
            d.setStroke(dp(1), 0x33000000);
            dot.setBackground(d);
            dot.setOnClickListener(v -> {
                Color.colorToHSV(pColor, hsv);
                hueBar.setProgress(Math.round(hsv[0]));
                satBar.setProgress(Math.round(hsv[1] * 100f));
                valBar.setProgress(Math.round(hsv[2] * 100f));
                updateState.run();
            });
            presetsLayout.addView(dot, LayoutHelper.createLinear(28, 28, 4, 0, 4, 0));
        }
        root.addView(presetsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

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
