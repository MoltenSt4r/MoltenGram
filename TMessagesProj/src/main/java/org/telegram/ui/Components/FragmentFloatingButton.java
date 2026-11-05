package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class FragmentFloatingButton extends FrameLayout implements FactorAnimator.Target {
    private final int ANIMATOR_ID_BUTTON_VISIBLE = 0;
    private final int ANIMATOR_ID_PROGRESS_VISIBLE = 1;

    private final BoolAnimator animatorButtonVisible = new BoolAnimator(ANIMATOR_ID_BUTTON_VISIBLE, this,
        CubicBezierInterpolator.EASE_OUT_QUINT, 380, true);

    private final BoolAnimator animatorProgressVisible = new BoolAnimator(ANIMATOR_ID_PROGRESS_VISIBLE, this,
        CubicBezierInterpolator.EASE_OUT_QUINT, 380);


    public final RLottieImageView imageView;
    public final RadialProgressView progressView;
    private final Theme.ResourcesProvider resourcesProvider;
    private ArrayList<View> additionalContentViews;
    private final boolean isSubButton;

    public FragmentFloatingButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, false);
    }

    public FragmentFloatingButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, boolean isSubButton) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        this.isSubButton = isSubButton;

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        progressView.setSize(dp(18));
        progressView.setStrokeWidth(2);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setAnimatedVisibility(progressView, 0);

        ScaleStateListAnimator.apply(this);
        if (!isSubButton) {
            // exteraGram 12.9.0, FragmentFloatingButton.java:67 — squircle вместо круга.
            setOutlineProvider(app.exteraless.appearance.AppearanceConfig.squareFab()
                    ? ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(14))
                    : ViewOutlineProviderImpl.BOUNDS_OVAL);
            setTranslationZ(dpf2(0.5f));
        }

        if (isSubButton) {
            iBlur3ColorProviderTabs = new BlurredBackgroundColorProviderThemed(null, Theme.key_dialogBackground) {
                @Override
                public int getStrokeColorTop() {
                    return isDark() ? 0x06FFFFFF : 0x11000000;
                }

                @Override
                public int getStrokeColorBottom() {
                    return isDark() ? 0x11FFFFFF : 0x20000000;
                }

                @Override
                public int getShadowColor() {
                    return isDark() ? 0x04FFFFFF : 0x20000000;
                }
            };
            iBlur3SourceColor = new BlurredBackgroundSourceColor();
            iBlur3Background = iBlur3SourceColor.createDrawable();
            iBlur3Background.setColorProvider(iBlur3ColorProviderTabs);
            iBlur3Background.setStrokeWidth(dpf2(0.4f), dpf2(0.4f));
            iBlur3Background.setRadius(dp(app.exteraless.appearance.AppearanceConfig.squareFab() ? 10 : 18));
            iBlur3Background.setPadding(dp(5.66f));
        }

        updateColors();
    }

    public void setProgressVisible(boolean visible, boolean animated) {
        animatorProgressVisible.setValue(visible, animated);
    }

    public void setButtonVisible(boolean visible, boolean animated) {
        animatorButtonVisible.setValue(visible, animated);
    }

    public boolean getButtonVisible() {
        return animatorButtonVisible.getValue();
    }

    public boolean getProgressVisible() {
        return animatorProgressVisible.getValue();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_BUTTON_VISIBLE) {
            setAnimatedVisibility(this, factor);
            setClickable(factor >= 0.99f);
            setAdditionalTranslationY(dp(isSubButton ? 64 : 40) * (1f - factor));
        } else if (id == ANIMATOR_ID_PROGRESS_VISIBLE) {
            setAnimatedVisibility(progressView, factor);
            setAnimatedVisibility(imageView, 1f - factor);
            if (additionalContentViews != null) {
                for (View v : additionalContentViews) {
                    setAnimatedVisibility(v, 1f - factor);
                }
            }
        }
    }

    public void setAnimation(@RawRes int animation, int size) {
        imageView.setAnimation(animation, size, size);
    }

    public void setImageResource(@DrawableRes int drawable) {
        imageView.setImageResource(drawable);
    }

    private BlurredBackgroundSourceColor iBlur3SourceColor;
    private BlurredBackgroundDrawable iBlur3Background;
    private BlurredBackgroundColorProviderThemed iBlur3ColorProviderTabs;

    public void updateColors() {
        if (isSubButton) {
            int customBgCode = app.exteraless.appearance.AppearanceConfig.subFabColor();
            int defBg = Theme.getColor(Theme.key_windowBackgroundWhite);
            int defIcon = Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider);
            int customBg = app.exteraless.utils.UIUtil.resolveFabColor(customBgCode, 0);

            int customIconCode = app.exteraless.appearance.AppearanceConfig.subFabIconColor();
            int iconCol = app.exteraless.utils.UIUtil.resolveFabIconColor(customBgCode, customIconCode, defIcon);

            imageView.setColorFilter(iconCol, PorterDuff.Mode.SRC_IN);
            progressView.setProgressColor(iconCol);

            if (customBgCode != 0) {
                int rad = dp(app.exteraless.appearance.AppearanceConfig.squareFab() ? 10 : 18);
                int pressedColor = androidx.core.graphics.ColorUtils.blendARGB(customBg, 0xFF000000, 0.2f);
                setBackground(Theme.createSimpleSelectorRoundRectDrawable(rad, customBg, pressedColor));
            } else {
                iBlur3SourceColor.setColor(defBg);
                iBlur3ColorProviderTabs.updateColors();
                iBlur3Background.updateColors();
                invalidate();

                int rad = dp(app.exteraless.appearance.AppearanceConfig.squareFab() ? 10 : 18);
                int pressedColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);
                setBackground(Theme.createInsetRoundRectDrawable(pressedColor, rad, dp(6)));
            }
        } else {
            int mainBgCode = app.exteraless.appearance.AppearanceConfig.fabColor();
            int defBg = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            int defIcon = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);

            int mainColor = app.exteraless.utils.UIUtil.resolveFabColor(mainBgCode, defBg);

            int mainIconCode = app.exteraless.appearance.AppearanceConfig.fabIconColor();
            int iconCol = app.exteraless.utils.UIUtil.resolveFabIconColor(mainBgCode, mainIconCode, defIcon);

            imageView.setColorFilter(iconCol, PorterDuff.Mode.SRC_IN);
            progressView.setProgressColor(iconCol);

            int pressedColor;
            if (mainBgCode != 0) {
                pressedColor = androidx.core.graphics.ColorUtils.blendARGB(mainColor, 0xFF000000, 0.15f);
            } else {
                pressedColor = Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider);
            }
            int size = app.exteraless.appearance.AppearanceConfig.fabSize();
            setBackground(app.exteraless.utils.UIUtil.createFabSelectorDrawable(size, mainColor, pressedColor));
        }

        // Synchronize layout params with updated size settings
        int size = isSubButton ? app.exteraless.appearance.AppearanceConfig.subFabSize() : app.exteraless.appearance.AppearanceConfig.fabSize();
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            int curSize = dp(size);
            if (flp.width != curSize || flp.height != curSize) {
                FrameLayout.LayoutParams newLp = isSubButton ? createSubButtonLayoutParams() : createDefaultLayoutParams();
                setLayoutParams(newLp);
            }
        }
    }

    public static final int SIZE = 48;

    public static FrameLayout.LayoutParams createSubButtonLayoutParams() {
        int mainSize = app.exteraless.appearance.AppearanceConfig.fabSize();
        int subSize = app.exteraless.appearance.AppearanceConfig.subFabSize();
        int horizontalOffset = 20 + Math.max(0, (mainSize - subSize) / 2);
        return LayoutHelper.createFrame(subSize, subSize,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                horizontalOffset, 0, horizontalOffset, 14);
    }

    public static FrameLayout.LayoutParams createDefaultLayoutParams() {
        int size = app.exteraless.appearance.AppearanceConfig.fabSize();
        return LayoutHelper.createFrame(size, size,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                20, 0, 20, 14);
    }


    public static FrameLayout.LayoutParams createDefaultLayoutParamsBig() {
        return LayoutHelper.createFrame(56, 56,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                20 /*24*/, 0, 20 /*24*/, 14 /*16*/);
    }

    private float additionalTranslationY;
    private float internalTranslationY;
    private boolean setTranslationInternal;

    private void setAdditionalTranslationY(float translationY) {
        if (additionalTranslationY != translationY) {
            setTranslationInternal = true;
            super.setTranslationY(internalTranslationY + translationY);
            setTranslationInternal = false;
            additionalTranslationY = translationY;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (iBlur3Background != null) {
            iBlur3Background.setBounds(0, 0, w, h);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (iBlur3Background != null) {
            iBlur3Background.draw(canvas);
        }
        super.draw(canvas);
    }

    @Override
    public void setTranslationY(float translationY) {
        if (internalTranslationY != translationY) {
            setTranslationInternal = true;
            super.setTranslationY(translationY + additionalTranslationY);
            setTranslationInternal = false;
            internalTranslationY = translationY;
        }
    }

    @Override
    public float getTranslationY() {
        if (setTranslationInternal) {
            return super.getTranslationY();
        }

        return internalTranslationY;
    }

    public void addAdditionalView(View view) {
        if (additionalContentViews == null) {
            additionalContentViews = new ArrayList<>();
        }
        additionalContentViews.add(view);
        setAnimatedVisibility(view, 1f - animatorProgressVisible.getFloatValue());
    }

    public static void setAnimatedVisibility(@Nullable View v, float f) {
        if (v == null) {
            return;
        }

        v.setAlpha(f);
        v.setScaleX(lerp(0.4f, 1f, f));
        v.setScaleY(lerp(0.4f, 1f, f));
        v.setVisibility(f > 0 ? VISIBLE : GONE);
    }
}
