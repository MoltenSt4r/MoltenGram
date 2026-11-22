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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class FragmentFloatingButton extends FrameLayout implements FactorAnimator.Target, Theme.Colorable {
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
    private boolean isMorphedToActive;
    private final Path shapePath = new Path();
    private final Paint fabBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fabPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapeGlassStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapeGlassGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapeGlassSeparatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapeGlassFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subGlassBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int cachedStrokeW, cachedStrokeH;
    private boolean cachedStrokeDark;
    private int cachedStrokeConfigVer = -1;
    private int lastGlassConfigVersion = -1;
    private boolean cachedSubGlassDefaultFill;
    private boolean isFabPressed;
    private int currentIconColor = 0;

    public FragmentFloatingButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, false);
    }

    public FragmentFloatingButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, boolean isSubButton) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        this.isSubButton = isSubButton;
        setWillNotDraw(false);
        shapeGlassStrokePaint.setStyle(Paint.Style.STROKE);
        shapeGlassStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        shapeGlassStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        shapeGlassGlowPaint.setStyle(Paint.Style.STROKE);
        shapeGlassGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        shapeGlassGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        shapeGlassSeparatorPaint.setStyle(Paint.Style.STROKE);
        shapeGlassSeparatorPaint.setStrokeJoin(Paint.Join.ROUND);
        shapeGlassSeparatorPaint.setStrokeCap(Paint.Cap.ROUND);
        shapeGlassFillPaint.setStyle(Paint.Style.FILL);
        subGlassBorderPaint.setStyle(Paint.Style.STROKE);
        subGlassBorderPaint.setStrokeJoin(Paint.Join.ROUND);
        subGlassBorderPaint.setStrokeCap(Paint.Cap.ROUND);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        progressView.setSize(dp(18));
        progressView.setStrokeWidth(2);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setAnimatedVisibility(progressView, 0);

        ScaleStateListAnimator.apply(this);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                final int w = view.getWidth();
                final int h = view.getHeight();
                if (w <= 0 || h <= 0) {
                    return;
                }
                final int shape = effectiveShapeId();
                if (shape == 0 && (isSubButton || shapeMorphProgress >= 1f)) {
                    outline.setOval(0, 0, w, h);
                } else if (shape == 1 && (isSubButton || shapeMorphProgress >= 1f)) {
                    final float rad = Math.min(w, h) * 0.30f;
                    outline.setRoundRect(0, 0, w, h, rad);
                } else {
                    updateShapePath(w, h);
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        try {
                            outline.setPath(shapePath);
                            return;
                        } catch (Throwable ignored) {
                        }
                    }
                    final float rad = Math.min(w, h) * 0.30f;
                    outline.setRoundRect(0, 0, w, h, rad);
                }
            }
        });
        if (!isSubButton) {
            setTranslationZ(dpf2(0.5f));
        }
        setClipToOutline(false);

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

    private BlurredBackgroundDrawable fabGlassDrawable;

    public void setGlassFactory(org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory factory) {
        if (factory == null) {
            return;
        }
        fabGlassDrawable = factory.create(this, org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
        configureFabGlassDrawable(getWidth(), getHeight());
        updateColors();
        invalidate();
    }

    private int effectiveShapeId() {
        if (isSubButton) {
            return app.exteraless.appearance.AppearanceConfig.subFabShape();
        }
        final int id = currentShapeId >= 0 ? currentShapeId : app.exteraless.appearance.AppearanceConfig.fabShape();
        if (id == 0 && !isMorphedToActive && app.exteraless.appearance.MainTabsUiHelper.isExpressiveToolbar()) {
            return 1; // MD3 Expressive rounded square FAB next to floating toolbar
        }
        return id;
    }

    private void configureFabGlassDrawable(int w, int h) {
        if (fabGlassDrawable == null || w <= 0 || h <= 0) {
            return;
        }
        fabGlassDrawable.setBounds(0, 0, w, h);
        fabGlassDrawable.setPadding(0);
        fabGlassDrawable.setShadowAlpha(0f);
        // Always disable the shader's fixed rounded-rect/circle rim on FABs so main & sub FABs share one unified shapePath glass contour across all 44 shapes and morphs
        fabGlassDrawable.solegramSetStrokeHidden(true);
        fabGlassDrawable.setClipToOutline(false);
        fabGlassDrawable.setRadius(Math.min(w, h) / 2f);
    }

    private boolean isGlassDarkSurface() {
        int bgCol = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        if (bgCol == 0) {
            return Theme.isCurrentThemeDark();
        }
        return Theme.isCurrentThemeDark() || androidx.core.graphics.ColorUtils.calculateLuminance(bgCol | 0xFF000000) < 0.45;
    }

    private float getGlassRimStrength() {
        final boolean glassOn = app.exteraless.glass.LiquidGlassConfig.glassOn;
        if (!glassOn) {
            return 1f;
        }
        return Math.max(0f, app.exteraless.glass.LiquidGlassConfig.glassRim / 100f);
    }

    private float getGlassHighlightStrength() {
        final boolean glassOn = app.exteraless.glass.LiquidGlassConfig.glassOn;
        if (!glassOn) {
            return 1f;
        }
        return Math.max(0f, app.exteraless.glass.LiquidGlassConfig.glassHighlight / 100f);
    }

    private void ensureShapeGlassShaders(int w, int h, float rimStrength, float highlightStrength) {
        final boolean dark = isGlassDarkSurface();
        final int tintCol = app.exteraless.glass.SolegramGlass.tintColor(resourcesProvider);
        final int cfgVer = app.exteraless.glass.LiquidGlassConfig.version * 31 + tintCol;
        if (shapeGlassStrokePaint.getShader() != null && cachedStrokeW == w && cachedStrokeH == h
                && cachedStrokeDark == dark && cachedStrokeConfigVer == cfgVer) {
            return;
        }
        cachedStrokeW = w;
        cachedStrokeH = h;
        cachedStrokeDark = dark;
        cachedStrokeConfigVer = cfgVer;

        final float s = Math.min(2.0f, Math.max(0f, rimStrength));
        final float sClamped = Math.min(1f, s);
        final float hl = Math.min(3.0f, Math.max(0f, highlightStrength));

        // Keep glass surface veil ultra-light so fabGlassDrawable refraction, index, dispersion and opacity stay crisp
        final int fillTopAlpha = s > 0.01f ? Math.round(255f * (dark ? 0.025f * sClamped : 0.035f * sClamped)) : 0;
        int fillTop = androidx.core.graphics.ColorUtils.setAlphaComponent(0xFFFFFFFF, fillTopAlpha);
        int fillBot = 0;
        shapeGlassFillPaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{fillTop, fillBot}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));

        // Soft inner bevel glow along the glass edge, driven directly by glassRim
        final int glowTopAlpha = s <= 0.01f ? 0 : (dark
                ? Math.min(115, Math.round(255f * (0.04f + 0.16f * s)))
                : Math.min(150, Math.round(255f * (0.08f + 0.22f * s))));
        final int glowMidAlpha = Math.round(glowTopAlpha * 0.35f);
        final int glowBotAlpha = Math.round(glowTopAlpha * 0.72f);
        shapeGlassGlowPaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{
                        androidx.core.graphics.ColorUtils.setAlphaComponent(0xFFFFFFFF, glowTopAlpha),
                        androidx.core.graphics.ColorUtils.setAlphaComponent(0xFFFFFFFF, glowMidAlpha),
                        androidx.core.graphics.ColorUtils.setAlphaComponent(0xFFFFFFFF, glowBotAlpha)
                },
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        // Crisp 45-deg specular rim contour following the vector shapePath, driven by glassHighlight
        final int topA = hl <= 0.01f ? 0 : (dark
                ? Math.min(245, Math.round(255f * (0.14f + 0.26f * Math.min(hl, 2f))))
                : Math.min(248, Math.round(255f * (0.36f + 0.30f * Math.min(hl, 2f)))));
        final int midA = Math.round(topA * 0.36f);
        final int botA = Math.round(topA * 0.72f);
        shapeGlassStrokePaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{
                        androidx.core.graphics.ColorUtils.setAlphaComponent(0xFFFFFFFF, topA),
                        androidx.core.graphics.ColorUtils.setAlphaComponent(0xFFFFFFFF, midA),
                        androidx.core.graphics.ColorUtils.setAlphaComponent(0xFFFFFFFF, botA)
                },
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));

        if (!dark && hl > 0.01f) {
            final int sepTop = Math.round(255f * (0.08f * Math.min(hl, 1.5f)));
            final int sepBot = Math.round(255f * (0.14f * Math.min(hl, 1.5f)));
            shapeGlassSeparatorPaint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{
                            androidx.core.graphics.ColorUtils.setAlphaComponent(0xFF000000, sepTop),
                            androidx.core.graphics.ColorUtils.setAlphaComponent(0xFF000000, sepBot)
                    },
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        } else {
            shapeGlassSeparatorPaint.setShader(null);
        }
    }

    private void drawGlassSurfaceSeparation(Canvas canvas, int w, int h) {
        final float rimStrength = getGlassRimStrength();
        if (rimStrength <= 0.01f) {
            return;
        }
        ensureShapeGlassShaders(w, h, rimStrength, getGlassHighlightStrength());
        canvas.drawPath(shapePath, shapeGlassFillPaint);
    }

    private void drawShapeGlassOutline(Canvas canvas, int w, int h) {
        final float rimStrength = getGlassRimStrength();
        final float highlightStrength = getGlassHighlightStrength();
        if (rimStrength <= 0.01f && highlightStrength <= 0.01f) {
            return;
        }
        ensureShapeGlassShaders(w, h, rimStrength, highlightStrength);
        final boolean dark = isGlassDarkSurface();
        if (highlightStrength > 0.01f && !dark && shapeGlassSeparatorPaint.getShader() != null) {
            shapeGlassSeparatorPaint.setStrokeWidth(dpf2(0.85f) * 2f);
            canvas.drawPath(shapePath, shapeGlassSeparatorPaint);
        }
        if (rimStrength > 0.01f) {
            shapeGlassGlowPaint.setStrokeWidth(dpf2(2.6f) * 2f);
            canvas.drawPath(shapePath, shapeGlassGlowPaint);
        }
        if (highlightStrength > 0.01f) {
            final float baseStroke = dpf2(1.1f);
            shapeGlassStrokePaint.setStrokeWidth(Math.max(dpf2(0.8f), app.exteraless.glass.SolegramGlass.highlightWidth(baseStroke, true)) * 2f);
            canvas.drawPath(shapePath, shapeGlassStrokePaint);
        }
    }

    private boolean isGlassFabActive() {
        return fabGlassDrawable != null
                && app.exteraless.glass.LiquidGlassConfig.isLiquidGlassEnabled()
                && app.exteraless.glass.LiquidGlassConfig.glassFab;
    }

    private int resolveGlassFabIconColor(int iconColorCode, int defIcon) {
        return iconColorCode == 0
                ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider)
                : app.exteraless.utils.UIUtil.resolveFabIconColor(0, iconColorCode, defIcon);
    }

    private Drawable themedBackground;
    private int themedIconColor;
    private boolean themedIconColorSet;
    private int themedBackgroundColor, themedBackgroundPressedColor;

    @Override
    public void updateColors() {
        if (imageView == null || progressView == null) {
            return;
        }
        lastGlassConfigVersion = app.exteraless.glass.LiquidGlassConfig.version;
        cachedStrokeConfigVer = -1;
        final boolean recolorBackground = themedBackground != null && getBackground() == themedBackground;
        if (isSubButton) {
            int customBgCode = app.exteraless.appearance.AppearanceConfig.subFabColor();
            int defBg = Theme.getColor(Theme.key_windowBackgroundWhite);
            int defIcon = Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider);
            int customBg = app.exteraless.utils.UIUtil.resolveFabColor(customBgCode, 0);

            int customIconCode = app.exteraless.appearance.AppearanceConfig.subFabIconColor();
            boolean useGlassSub = isGlassFabActive();
            int iconCol = useGlassSub
                    ? resolveGlassFabIconColor(customIconCode, defIcon)
                    : app.exteraless.utils.UIUtil.resolveFabIconColor(customBgCode, customIconCode, defIcon);
            currentIconColor = iconCol;

            imageView.setColorFilter(iconCol, PorterDuff.Mode.SRC_IN);
            progressView.setProgressColor(iconCol);

            if (useGlassSub) {
                fabGlassDrawable.setLiquidGlassEffectAllowed(true);
                fabGlassDrawable.setShadowAlpha(0f);
                fabGlassDrawable.updateColors();
                fabGlassDrawable.invalidateDisplayList();
                cachedSubGlassDefaultFill = false;
                setTranslationZ(0f);
                setClipToOutline(false);
                invalidateOutline();
                setBackground(null);
                configureFabGlassDrawable(getWidth(), getHeight());
                invalidate();
            } else {
                if (fabGlassDrawable != null) {
                    fabGlassDrawable.setLiquidGlassEffectAllowed(false);
                    fabGlassDrawable.invalidateDisplayList();
                }
                setTranslationZ(dpf2(0.5f));
                setClipToOutline(false);
                invalidateOutline();
                final boolean dark = isGlassDarkSurface();
                final int fillColor;
                final int pressedColor;
                if (customBgCode != 0) {
                    fillColor = customBg;
                    pressedColor = androidx.core.graphics.ColorUtils.blendARGB(customBg, 0xFF000000, 0.2f);
                } else {
                    fillColor = dark
                            ? androidx.core.graphics.ColorUtils.blendARGB(defBg, 0xFFFFFFFF, 0.08f)
                            : defBg;
                    pressedColor = androidx.core.graphics.ColorUtils.blendARGB(fillColor, dark ? 0xFFFFFFFF : 0xFF000000, 0.12f);
                }
                cachedSubGlassDefaultFill = false;
                fabBgPaint.setColor(fillColor);
                fabPressedPaint.setColor(pressedColor);
                setBackground(null);
                invalidate();
            }
        } else {
            if (currentShapeId < 0 || (!isMorphedToActive && (shapeMorphAnimator == null || !shapeMorphAnimator.isRunning()))) {
                currentShapeId = app.exteraless.appearance.AppearanceConfig.fabShape();
                fromShapeId = currentShapeId;
                targetShapeId = currentShapeId;
                shapeMorphProgress = 1f;
            }
            int mainBgCode = app.exteraless.appearance.AppearanceConfig.fabColor();
            int defBg = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            int defIcon = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);

            int mainColor = app.exteraless.utils.UIUtil.resolveFabColor(mainBgCode, defBg);

            int mainIconCode = app.exteraless.appearance.AppearanceConfig.fabIconColor();
            boolean useGlassFab = isGlassFabActive();
            int iconCol = useGlassFab
                    ? resolveGlassFabIconColor(mainIconCode, defIcon)
                    : app.exteraless.utils.UIUtil.resolveFabIconColor(mainBgCode, mainIconCode, defIcon);
            if (fabGlassDrawable != null) {
                fabGlassDrawable.setLiquidGlassEffectAllowed(useGlassFab);
                fabGlassDrawable.setShadowAlpha(0f);
                fabGlassDrawable.updateColors();
                fabGlassDrawable.invalidateDisplayList();
                configureFabGlassDrawable(getWidth(), getHeight());
            }
            cachedSubGlassDefaultFill = false;
            setTranslationZ(useGlassFab ? 0f : dpf2(0.5f));
            setClipToOutline(false);
            invalidateOutline();
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
        if (isFabPressed != pressed) {
            isFabPressed = pressed;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (lastGlassConfigVersion != app.exteraless.glass.LiquidGlassConfig.version) {
            updateColors();
        }
        final boolean glassFab = isGlassFabActive();
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas);
            return;
        }

        updateShapePath(w, h);
        if (glassFab) {
            configureFabGlassDrawable(w, h);
            canvas.save();
            canvas.clipPath(shapePath);
            fabGlassDrawable.draw(canvas);
            drawGlassSurfaceSeparation(canvas, w, h);
            if (isFabPressed) {
                canvas.drawColor(isGlassDarkSurface() ? 0x22FFFFFF : 0x18000000);
            }
            super.dispatchDraw(canvas);
            drawShapeGlassOutline(canvas, w, h);
            canvas.restore();
            return;
        }
        canvas.drawPath(shapePath, isFabPressed ? fabPressedPaint : fabBgPaint);

        canvas.save();
        canvas.clipPath(shapePath);
        super.dispatchDraw(canvas);
        if (cachedSubGlassDefaultFill) {
            canvas.drawPath(shapePath, subGlassBorderPaint);
        }
        canvas.restore();
    }

    private void updateShapePath(float w, float h) {
        shapePath.reset();
        int shape = effectiveShapeId();
        if (isSubButton || shapeMorphProgress >= 1f || fromShapeId == targetShapeId) {
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
        final boolean useGlassFab = !isSubButton && isGlassFabActive();
        final int targetIconColor = useGlassFab
                ? resolveGlassFabIconColor(targetIconCode, defIcon)
                : app.exteraless.utils.UIUtil.resolveFabIconColor(targetColorCode, targetIconCode, defIcon);

        final int startColor = fabBgPaint.getColor() != 0 ? fabBgPaint.getColor() : targetColor;
        final int startIconColor = currentIconColor != 0 ? currentIconColor : targetIconColor;

        int curShape = currentShapeId >= 0 ? currentShapeId : app.exteraless.appearance.AppearanceConfig.fabShape();
        final int restingSizePx = dp(app.exteraless.appearance.AppearanceConfig.fabSize());
        final int startSize = getLayoutParams() != null && getLayoutParams().width > 0 ? getLayoutParams().width : restingSizePx;
        final int endSize = dp(targetSizeDp);

        final float startShiftY = activeTranslationY;
        final float targetShiftY = -(restingSizePx - endSize) / 2f;
        final boolean isLeft = app.exteraless.appearance.AppearanceConfig.fabAlignment() == app.exteraless.appearance.AppearanceConfig.FAB_ALIGN_LEFT;
        final float startShiftX = activeTranslationX;
        final float targetShiftX = isLeft ? (restingSizePx - endSize) / 2f : -(restingSizePx - endSize) / 2f;

        if (!animated) {
            if (shapeMorphAnimator != null) {
                shapeMorphAnimator.cancel();
                shapeMorphAnimator = null;
            }
            fromShapeId = targetShape;
            targetShapeId = targetShape;
            currentShapeId = targetShape;
            shapeMorphProgress = 1f;
            isMorphedToActive = targetShape != app.exteraless.appearance.AppearanceConfig.fabShape()
                    || targetSizeDp != app.exteraless.appearance.AppearanceConfig.fabSize();
            fabBgPaint.setColor(targetColor);
            imageView.setColorFilter(targetIconColor, PorterDuff.Mode.SRC_IN);
            progressView.setProgressColor(targetIconColor);
            currentIconColor = targetIconColor;
            setTranslationZ(useGlassFab ? 0f : dpf2(0.5f));
            setClipToOutline(false);
            invalidateOutline();
            applyFabSize(targetSizeDp);
            configureFabGlassDrawable(getWidth(), getHeight());
            setActiveTranslationY(targetShiftY);
            setActiveTranslationX(targetShiftX);
            invalidate();
            return;
        }

        isMorphedToActive = targetShape != app.exteraless.appearance.AppearanceConfig.fabShape()
                || targetSizeDp != app.exteraless.appearance.AppearanceConfig.fabSize();
        if (curShape == targetShape && shapeMorphProgress >= 1f && fabBgPaint.getColor() == targetColor && currentIconColor == targetIconColor && startSize == endSize && activeTranslationY == targetShiftY && activeTranslationX == targetShiftX) {
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
            if (startShiftX != targetShiftX) {
                setActiveTranslationX(startShiftX + (targetShiftX - startShiftX) * shapeMorphProgress);
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
                setTranslationZ(useGlassFab ? 0f : dpf2(0.5f));
                setClipToOutline(false);
                invalidateOutline();
                applyFabSize(targetSizeDp);
                setActiveTranslationY(targetShiftY);
                setActiveTranslationX(targetShiftX);
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

    private float activeTranslationX;
    private float activeTranslationY;
    private float additionalTranslationY;
    private float internalTranslationY;
    private boolean setTranslationInternal;

    private void updateCombinedTranslationY() {
        setTranslationInternal = true;
        super.setTranslationY(internalTranslationY + additionalTranslationY + activeTranslationY);
        setTranslationInternal = false;
    }

    public void setActiveTranslationX(float translationX) {
        if (activeTranslationX != translationX) {
            activeTranslationX = translationX;
            super.setTranslationX(activeTranslationX);
        }
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
        configureFabGlassDrawable(w, h);
        invalidateOutline();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (!isClickable() || !isEnabled() || getVisibility() != VISIBLE || getAlpha() < 0.05f) {
            return false;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
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
