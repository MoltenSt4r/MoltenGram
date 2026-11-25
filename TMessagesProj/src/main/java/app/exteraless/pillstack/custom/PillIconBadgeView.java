package app.exteraless.pillstack.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.SettingsActivity;

@SuppressLint("ViewConstructor")
public class PillIconBadgeView extends FrameLayout {

    private static final float GLYPH_FIT = 0.64f;

    private final Theme.ResourcesProvider resourcesProvider;
    private final boolean followMonet;
    private final SettingsActivity.SettingCell.Background background = new SettingsActivity.SettingCell.Background();
    private final PillIconView iconView;
    private int colorTop;
    private int colorBottom;

    public PillIconBadgeView(Context context, Theme.ResourcesProvider resourcesProvider, boolean followMonet) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.followMonet = followMonet;
        setBackground(background);
        iconView = new PillIconView(context, 24);
        iconView.setGlyphFit(GLYPH_FIT);
        addView(iconView, LayoutHelper.createFrameMatchParent());
    }

    public void set(@Nullable CustomPillSpec.Icon icon, int iconRes, int colorTop, int colorBottom) {
        if (icon != null) {
            iconView.setIcon(icon);
        } else {
            iconView.setIconRes(iconRes);
        }
        this.colorTop = colorTop;
        this.colorBottom = colorBottom;
        updateColors();
    }

    public void updateColors() {
        background.setDrawBorder(resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark());
        boolean monet = followMonet && Theme.isCurrentThemeMonet();
        if (monet) {
            background.setMonetColor(Theme.getColor(Theme.key_chats_actionBackground, resourcesProvider));
        } else {
            background.setColor(colorTop, colorBottom);
        }
        iconView.setColor(monet ? Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider) : PillColors.foreground(colorTop, colorBottom));
        invalidate();
    }
}
