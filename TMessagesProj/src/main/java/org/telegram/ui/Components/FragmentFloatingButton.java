package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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

    private int currentShapeId = -1;
    private int fromShapeId = 0;
    private int targetShapeId = 0;
    private float shapeMorphProgress = 1f;
    private ValueAnimator shapeMorphAnimator;
    private final Path shapePath = new Path();
    private final Paint fabBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fabPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean isFabPressed;
    private int currentIconColor = 0;

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
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    int shape = currentShapeId >= 0 ? currentShapeId : app.exteraless.appearance.AppearanceConfig.fabShape();
                    if (shape == 0) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    } else {
                        int rad = dp(app.exteraless.appearance.AppearanceConfig.fabCornerRadius(Math.min(view.getWidth(), view.getHeight())));
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad);
                    }
                }
            });
            setTranslationZ(dpf2(0.5f));
            setClipToOutline(false);
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
            currentIconColor = iconCol;

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
            currentIconColor = iconCol;

            imageView.setColorFilter(iconCol, PorterDuff.Mode.SRC_IN);
            progressView.setProgressColor(iconCol);

            int pressedColor;
            if (mainBgCode != 0) {
                pressedColor = androidx.core.graphics.ColorUtils.blendARGB(mainColor, 0xFF000000, 0.15f);
            } else {
                pressedColor = Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider);
            }

            fabBgPaint.setColor(mainColor);
            fabPressedPaint.setColor(pressedColor);
            setBackground(null);

            if (currentShapeId < 0) {
                currentShapeId = app.exteraless.appearance.AppearanceConfig.fabShape();
                fromShapeId = currentShapeId;
                targetShapeId = currentShapeId;
            }
            invalidate();
        }

        // Synchronize layout params with updated size and alignment settings
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            FrameLayout.LayoutParams targetLp = isSubButton ? createSubButtonLayoutParams() : createDefaultLayoutParams();
            if (flp.width != targetLp.width || flp.height != targetLp.height || flp.gravity != targetLp.gravity
                    || flp.leftMargin != targetLp.leftMargin || flp.rightMargin != targetLp.rightMargin) {
                setLayoutParams(targetLp);
            }
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (!isSubButton && isFabPressed != pressed) {
            isFabPressed = pressed;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (isSubButton) {
            super.dispatchDraw(canvas);
            return;
        }

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas);
            return;
        }

        updateShapePath(w, h);
        canvas.drawPath(shapePath, isFabPressed ? fabPressedPaint : fabBgPaint);

        canvas.save();
        canvas.clipPath(shapePath);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    private void updateShapePath(float w, float h) {
        shapePath.reset();
        int shape = currentShapeId >= 0 ? currentShapeId : app.exteraless.appearance.AppearanceConfig.fabShape();
        if (shapeMorphProgress >= 1f || fromShapeId == targetShapeId) {
            shapePath.set(app.exteraless.appearance.Md3AvatarShapes.getShapePath(shape, 0, 0, w, h));
        } else {
            shapePath.set(app.exteraless.appearance.Md3AvatarShapes.getMorphPath(fromShapeId, targetShapeId, shapeMorphProgress, 0, 0, w, h));
        }
    }

    public void morphTo(int targetShape, int targetSizeDp, boolean animated) {
        morphTo(targetShape, targetSizeDp, app.exteraless.appearance.AppearanceConfig.fabColor(), animated);
    }

    public void morphTo(int targetShape, int targetSizeDp, int targetColorCode, boolean animated) {
        if (isSubButton) return;
        if (targetShape < 0 || targetShape >= app.exteraless.appearance.Md3AvatarShapes.SHAPES_COUNT) {
            targetShape = app.exteraless.appearance.AppearanceConfig.fabShape();
        }

        int defBg = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        int defIcon = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
        final int targetColor = app.exteraless.utils.UIUtil.resolveFabColor(targetColorCode, defBg);
        int targetIconCode = (targetColorCode == app.exteraless.appearance.AppearanceConfig.fabActiveColor())
                ? app.exteraless.appearance.AppearanceConfig.fabActiveIconColor()
                : app.exteraless.appearance.AppearanceConfig.fabIconColor();
        final int targetIconColor = app.exteraless.utils.UIUtil.resolveFabIconColor(targetColorCode, targetIconCode, defIcon);

        final int startColor = fabBgPaint.getColor() != 0 ? fabBgPaint.getColor() : targetColor;
        final int startIconColor = currentIconColor != 0 ? currentIconColor : targetIconColor;

        int curShape = currentShapeId >= 0 ? currentShapeId : app.exteraless.appearance.AppearanceConfig.fabShape();
        final int restingSizePx = dp(app.exteraless.appearance.AppearanceConfig.fabSize());
        final int startSize = getLayoutParams() != null && getLayoutParams().width > 0 ? getLayoutParams().width : restingSizePx;
        final int endSize = dp(targetSizeDp);

        final float startShiftY = activeTranslationY;
        final float targetShiftY = endSize < restingSizePx ? -(restingSizePx - endSize) : 0f;

        if (!animated) {
            if (shapeMorphAnimator != null) {
                shapeMorphAnimator.cancel();
                shapeMorphAnimator = null;
            }
            fromShapeId = targetShape;
            targetShapeId = targetShape;
            currentShapeId = targetShape;
            shapeMorphProgress = 1f;
            fabBgPaint.setColor(targetColor);
            imageView.setColorFilter(targetIconColor, PorterDuff.Mode.SRC_IN);
            progressView.setProgressColor(targetIconColor);
            currentIconColor = targetIconColor;
            applyFabSize(targetSizeDp);
            setActiveTranslationY(targetShiftY);
            invalidate();
            return;
        }

        if (curShape == targetShape && shapeMorphProgress >= 1f && fabBgPaint.getColor() == targetColor && currentIconColor == targetIconColor && startSize == endSize && activeTranslationY == targetShiftY) {
            applyFabSize(targetSizeDp);
            return;
        }

        if (shapeMorphAnimator != null) {
            shapeMorphAnimator.cancel();
        }

        fromShapeId = (shapeMorphProgress < 1f) ? fromShapeId : curShape;
        targetShapeId = targetShape;
        currentShapeId = targetShape;
        shapeMorphProgress = 0f;

        shapeMorphAnimator = ValueAnimator.ofFloat(0f, 1f);
        shapeMorphAnimator.setDuration(260);
        shapeMorphAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        shapeMorphAnimator.addUpdateListener(anim -> {
            shapeMorphProgress = (Float) anim.getAnimatedValue();
            if (startSize != endSize) {
                int curSize = (int) (startSize + (endSize - startSize) * shapeMorphProgress);
                applyFabDimension(curSize);
            }
            if (startShiftY != targetShiftY) {
                setActiveTranslationY(startShiftY + (targetShiftY - startShiftY) * shapeMorphProgress);
            }
            if (startColor != targetColor) {
                int blended = androidx.core.graphics.ColorUtils.blendARGB(startColor, targetColor, shapeMorphProgress);
                fabBgPaint.setColor(blended);
            }
            if (startIconColor != targetIconColor) {
                int blendedIcon = androidx.core.graphics.ColorUtils.blendARGB(startIconColor, targetIconColor, shapeMorphProgress);
                imageView.setColorFilter(blendedIcon, PorterDuff.Mode.SRC_IN);
                progressView.setProgressColor(blendedIcon);
            }
            invalidate();
        });
        shapeMorphAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fromShapeId = targetShapeId;
                currentShapeId = targetShapeId;
                shapeMorphProgress = 1f;
                fabBgPaint.setColor(targetColor);
                imageView.setColorFilter(targetIconColor, PorterDuff.Mode.SRC_IN);
                progressView.setProgressColor(targetIconColor);
                currentIconColor = targetIconColor;
                applyFabSize(targetSizeDp);
                setActiveTranslationY(targetShiftY);
                invalidate();
            }
        });
        shapeMorphAnimator.start();
    }

    private void applyFabSize(int sizeDp) {
        applyFabDimension(dp(sizeDp));
    }

    private void applyFabDimension(int sizePx) {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && (lp.width != sizePx || lp.height != sizePx)) {
            lp.width = sizePx;
            lp.height = sizePx;
            setLayoutParams(lp);
        }
    }

    public static final int SIZE = 48;

    public static FrameLayout.LayoutParams createSubButtonLayoutParams() {
        int mainSize = app.exteraless.appearance.AppearanceConfig.fabSize();
        int subSize = app.exteraless.appearance.AppearanceConfig.subFabSize();
        boolean isLeft = app.exteraless.appearance.AppearanceConfig.fabAlignment() == app.exteraless.appearance.AppearanceConfig.FAB_ALIGN_LEFT;
        boolean isEdge = app.exteraless.appearance.AppearanceConfig.subFabAlignment() == app.exteraless.appearance.AppearanceConfig.SUB_FAB_ALIGN_EDGE;

        int horizontalOffset = isEdge ? 20 : (20 + Math.max(0, (mainSize - subSize) / 2));
        int gravity = (isLeft ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM;
        return LayoutHelper.createFrame(subSize, subSize, gravity,
                isLeft ? horizontalOffset : 0, 0, isLeft ? 0 : horizontalOffset, 14);
    }

    public static FrameLayout.LayoutParams createDefaultLayoutParams() {
        int size = app.exteraless.appearance.AppearanceConfig.fabSize();
        boolean isLeft = app.exteraless.appearance.AppearanceConfig.fabAlignment() == app.exteraless.appearance.AppearanceConfig.FAB_ALIGN_LEFT;
        int gravity = (isLeft ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM;
        return LayoutHelper.createFrame(size, size, gravity,
                isLeft ? 20 : 0, 0, isLeft ? 0 : 20, 14);
    }

    public static FrameLayout.LayoutParams createDefaultLayoutParamsBig() {
        boolean isLeft = app.exteraless.appearance.AppearanceConfig.fabAlignment() == app.exteraless.appearance.AppearanceConfig.FAB_ALIGN_LEFT;
        int gravity = (isLeft ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM;
        return LayoutHelper.createFrame(56, 56, gravity,
                isLeft ? 20 : 0, 0, isLeft ? 0 : 20, 14);
    }

    private float activeTranslationY;
    private float additionalTranslationY;
    private float internalTranslationY;
    private boolean setTranslationInternal;

    private void updateCombinedTranslationY() {
        setTranslationInternal = true;
        super.setTranslationY(internalTranslationY + additionalTranslationY + activeTranslationY);
        setTranslationInternal = false;
    }

    public void setActiveTranslationY(float translationY) {
        if (activeTranslationY != translationY) {
            activeTranslationY = translationY;
            updateCombinedTranslationY();
        }
    }

    private void setAdditionalTranslationY(float translationY) {
        if (additionalTranslationY != translationY) {
            additionalTranslationY = translationY;
            updateCombinedTranslationY();
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
            internalTranslationY = translationY;
            updateCombinedTranslationY();
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
