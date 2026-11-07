package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import app.exteraless.appearance.AppearanceConfig;

public class LiquidGlassIntensityCell extends LinearLayout {

    public interface OnIntensityChangeListener {
        void onIntensityChanged(int intensity);
    }

    private final TextView titleView;
    private final TextView descView;
    private final TextView valueView;
    private final SeekBar seekBar;
    private OnIntensityChangeListener listener;
    private final Theme.ResourcesProvider resourcesProvider;

    public LiquidGlassIntensityCell(@NonNull Context context) {
        this(context, null);
    }

    public LiquidGlassIntensityCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(VERTICAL);
        setPadding(dp(21), dp(12), dp(21), dp(12));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(getString(R.string.OEAppearancePlayerGlassIntensity));
        header.addView(titleView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        valueView.setTypeface(AndroidUtilities.bold());
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        valueView.setTextColor(accent != 0 ? accent : 0xff00d2b4);
        header.addView(valueView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        descView = new TextView(context);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        descView.setText(getString(R.string.OEAppearancePlayerGlassIntensityDesc));
        descView.setPadding(0, dp(4), 0, dp(8));
        addView(descView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        seekBar = new SeekBar(context);
        seekBar.setMax(100);
        int current = AppearanceConfig.playerGlassIntensity();
        seekBar.setProgress(current);
        updateValueText(current);

        int trackColor = accent != 0 ? accent : 0xff00d2b4;
        ColorStateList tint = ColorStateList.valueOf(trackColor);
        seekBar.setProgressTintList(tint);
        seekBar.setThumbTintList(tint);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    AppearanceConfig.playerGlassIntensity.setConfigInt(progress);
                    updateValueText(progress);
                    if (listener != null) {
                        listener.onIntensityChanged(progress);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        addView(seekBar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(38)));
    }

    public void setOnIntensityChangeListener(OnIntensityChangeListener listener) {
        this.listener = listener;
    }

    public void bind(int value) {
        seekBar.setProgress(value);
        updateValueText(value);
    }

    public void update() {
        int current = AppearanceConfig.playerGlassIntensity();
        seekBar.setProgress(current);
        updateValueText(current);
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        int color = accent != 0 ? accent : 0xff00d2b4;
        valueView.setTextColor(color);
        ColorStateList tint = ColorStateList.valueOf(color);
        seekBar.setProgressTintList(tint);
        seekBar.setThumbTintList(tint);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
    }

    private void updateValueText(int val) {
        valueView.setText(LocaleController.formatString(R.string.OEAppearancePlayerGlassIntensityValue, val));
    }
}
