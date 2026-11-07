package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.SchemeTonalSpot;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import app.exteraless.appearance.AppearanceConfig;

/**
 * Exact port of InterTune's {@code GlassSample} composable from {@code ThemeFrag.kt} (lines 465-628).
 *
 * A 124dp strip of made-up artwork (linear gradient of primary, tertiary, secondary + 3 circles +
 * 3dp diagonal stripes spaced by 18dp) with two rows of controls floating on it:
 * - Row 1: The player's 5 buttons (Shuffle, Prev, Play, Next, Repeat), either inside one 46dp glass
 *   pill bar ({@code grouped = true}) or each on its own ({@code grouped = false}).
 * - Row 2: A "Play" pill button with {@code buttonTint()}, a 40dp circle skip-next button, and a
 *   20dp glass seekbar track with 45% primary fill.
 */
public class LiquidGlassSampleCell extends View {

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final Path cardClipPath = new Path();
    private final RectF cardRect = new RectF();
    private final RectF itemRect = new RectF();

    private final PlayerIcon shuffleIcon = PlayerIcon.stroke(PlayerIcon.SHUFFLE, 22);
    private final PlayerIcon prevIcon = PlayerIcon.fill(PlayerIcon.PREV, 22);
    private final PlayerIcon playIcon = PlayerIcon.fill(PlayerIcon.PLAY, 22);
    private final PlayerIcon playSmallIcon = PlayerIcon.fill(PlayerIcon.PLAY, 20);
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 22);
    private final PlayerIcon repeatIcon = PlayerIcon.stroke(PlayerIcon.REPEAT, 22);
    private final PlayerIcon[] row1Icons = new PlayerIcon[]{shuffleIcon, prevIcon, playIcon, nextIcon, repeatIcon};

    private final LiquidGlassHelper.GlassPane groupedBarPane = new LiquidGlassHelper.GlassPane();
    private final LiquidGlassHelper.GlassPane[] buttonPanes = new LiquidGlassHelper.GlassPane[5];
    private final LiquidGlassHelper.GlassPane playPillPane = new LiquidGlassHelper.GlassPane();
    private final LiquidGlassHelper.GlassPane circleBtnPane = new LiquidGlassHelper.GlassPane();
    private final LiquidGlassHelper.GlassPane seekTrackPane = new LiquidGlassHelper.GlassPane();

    private LinearGradient bgGradient;
    private int lastGradW = -1;
    private int lastGradH = -1;
    private int lastPrimary = 0;

    // Theme scheme colors
    private int primary;
    private int secondary;
    private int tertiary;
    private int primaryContainer;
    private int secondaryContainer;
    private int tertiaryContainer;
    private int onPrimary;
    private int onPrimaryContainer;
    private int onSurface;
    private int surface;
    private int surfaceAtElevation;

    public LiquidGlassSampleCell(Context context) {
        this(context, null);
    }

    public LiquidGlassSampleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        for (int i = 0; i < buttonPanes.length; i++) {
            buttonPanes[i] = new LiquidGlassHelper.GlassPane();
        }
        stripePaint.setStyle(Paint.Style.STROKE);
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, context.getResources().getDisplayMetrics()));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private void updateSchemeColors() {
        boolean dark = PlayerColors.isDark(resourcesProvider);
        int seed = PlayerColors.noCoverSeed(resourcesProvider);
        DynamicScheme s = new SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0);
        primary = s.getPrimary();
        secondary = s.getSecondary();
        tertiary = s.getTertiary();
        primaryContainer = s.getPrimaryContainer();
        secondaryContainer = s.getSecondaryContainer();
        tertiaryContainer = s.getTertiaryContainer();
        onPrimary = s.getOnPrimary();
        onPrimaryContainer = s.getOnPrimaryContainer();
        onSurface = s.getOnSurface();
        surface = s.getSurface();
        surfaceAtElevation = s.getSurfaceContainerHigh();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = dp(124) + dp(12); // 4dp top + 8dp bottom padding like ThemeFrag.kt
        setMeasuredDimension(w, h);
    }

    private void drawSampleBackdrop(@NonNull Canvas canvas, int width, int height) {
        if (bgGradient == null || lastGradW != width || lastGradH != height || lastPrimary != primary) {
            lastGradW = width;
            lastGradH = height;
            lastPrimary = primary;
            bgGradient = new LinearGradient(
                    0f, 0f, width, height,
                    new int[]{primary, tertiary, secondary},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
        }
        bgPaint.setShader(bgGradient);
        canvas.drawRect(0f, 0f, width, height, bgPaint);

        float h = height;
        circlePaint.setColor(tertiaryContainer);
        canvas.drawCircle(width * 0.12f, h * 0.1f, h * 0.5f, circlePaint);

        circlePaint.setColor(primaryContainer);
        canvas.drawCircle(width * 0.52f, h * 1.02f, h * 0.42f, circlePaint);

        circlePaint.setColor(secondaryContainer);
        canvas.drawCircle(width * 0.84f, h * 0.2f, h * 0.34f, circlePaint);

        stripePaint.setColor(ColorUtils.setAlphaComponent(onPrimary, Math.round(255f * 0.3f)));
        stripePaint.setStrokeWidth(dpf2(3f));
        float gap = dpf2(18f);
        float x = -h;
        while (x < width) {
            canvas.drawLine(x, h, x + h, 0f, stripePaint);
            x += gap;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        updateSchemeColors();

        float padLeft = dpf2(16f);
        float padRight = dpf2(16f);
        float padTop = dpf2(4f);
        float padBottom = dpf2(8f);

        float cardW = getWidth() - padLeft - padRight;
        float cardH = getHeight() - padTop - padBottom;
        if (cardW <= 0 || cardH <= 0) return;

        int cardWInt = Math.round(cardW);
        int cardHInt = Math.round(cardH);

        cardRect.set(0f, 0f, cardW, cardH);
        cardClipPath.rewind();
        cardClipPath.addRoundRect(cardRect, dpf2(16f), dpf2(16f), Path.Direction.CW);

        canvas.save();
        canvas.translate(padLeft, padTop);
        canvas.clipPath(cardClipPath);

        // 1. Draw the vibrant geometric artwork backdrop onto the card itself
        drawSampleBackdrop(canvas, cardWInt, cardHInt);

        boolean liquidGlass = AppearanceConfig.playerLiquidGlass() && LiquidGlassHelper.isSupported();
        boolean grouped = liquidGlass && AppearanceConfig.playerGroupedControls();
        float intensity = AppearanceConfig.playerGlassIntensityFloat();
        boolean still = LiquidGlassHelper.isBatterySaverStill();
        LiquidGlassHelper.GlassSpec glass = liquidGlass ? new LiquidGlassHelper.GlassSpec(intensity, still) : null;
        int flatColor = ColorUtils.setAlphaComponent(surfaceAtElevation, Math.round(255f * 0.88f));

        LiquidGlassHelper.BackdropDrawer backdropDrawer = this::drawSampleBackdrop;

        float innerLeft = dpf2(12f);
        float innerRight = cardW - dpf2(12f);
        float innerW = innerRight - innerLeft;

        // Vertical layout: 2 rows (46dp and 40dp) inside 124dp with 6dp vertical padding and SpaceEvenly
        float row1H = dpf2(46f);
        float row2H = dpf2(40f);
        float availV = cardH - dpf2(12f) - row1H - row2H;
        float gapV = Math.max(dpf2(4f), availV / 3f);
        float row1Top = dpf2(6f) + gapV;
        float row2Top = row1Top + row1H + gapV;

        // --- Row 1: Five transport buttons ---
        if (grouped && glass != null) {
            groupedBarPane.draw(
                    canvas,
                    innerLeft, row1Top, innerRight, row1Top + row1H,
                    row1H * 0.5f,
                    backdropDrawer, 0f, 0f, cardWInt, cardHInt,
                    true,
                    glass.blurPx,
                    dpf2(12f) * glass.lensT,
                    dpf2(24f) * glass.lensT,
                    true,
                    false,
                    true,
                    glass.floatingTint(surfaceAtElevation),
                    glass.stillFill(surfaceAtElevation),
                    still
            );
        }

        float row1PadH = dpf2(6f);
        float row1ButtonsW = innerW - row1PadH * 2f;
        float totalBtnW = dpf2(36f) * 4f + dpf2(38f);
        float btnSpace = (row1ButtonsW - totalBtnW) / 6f;
        float bx = innerLeft + row1PadH + btnSpace;

        for (int i = 0; i < 5; i++) {
            boolean isPlay = (i == 2);
            float bSize = dpf2(isPlay ? 38f : 36f);
            float by = row1Top + (row1H - bSize) * 0.5f;
            float bRadius = bSize * 0.5f;

            if (isPlay && glass != null && !grouped) {
                buttonPanes[i].draw(
                        canvas,
                        bx, by, bx + bSize, by + bSize,
                        bRadius,
                        backdropDrawer, 0f, 0f, cardWInt, cardHInt,
                        true,
                        glass.blurPx,
                        dpf2(12f) * glass.lensT,
                        dpf2(24f) * glass.lensT,
                        true,
                        false,
                        true,
                        glass.buttonTint(surface, primaryContainer),
                        glass.stillFill(surfaceAtElevation),
                        still
                );
            } else if (isPlay) {
                flatPaint.setColor(primaryContainer);
                itemRect.set(bx, by, bx + bSize, by + bSize);
                canvas.drawRoundRect(itemRect, bRadius, bRadius, flatPaint);
            } else if (grouped && glass != null) {
                // Inside grouped glass bar: no individual button background
            } else if (glass != null) {
                buttonPanes[i].draw(
                        canvas,
                        bx, by, bx + bSize, by + bSize,
                        bRadius,
                        backdropDrawer, 0f, 0f, cardWInt, cardHInt,
                        true,
                        glass.blurPx,
                        dpf2(12f) * glass.lensT,
                        dpf2(24f) * glass.lensT,
                        true,
                        false,
                        true,
                        glass.floatingTint(surfaceAtElevation),
                        glass.stillFill(surfaceAtElevation),
                        still
                );
            } else {
                flatPaint.setColor(flatColor);
                itemRect.set(bx, by, bx + bSize, by + bSize);
                canvas.drawRoundRect(itemRect, bRadius, bRadius, flatPaint);
            }

            PlayerIcon icon = row1Icons[i];
            icon.setColor(isPlay ? onPrimaryContainer : onSurface);
            int iconSz = dp(22);
            int ix = Math.round(bx + (bSize - iconSz) * 0.5f);
            int iy = Math.round(by + (bSize - iconSz) * 0.5f);
            icon.setBounds(ix, iy, ix + iconSz, iy + iconSz);
            icon.draw(canvas);

            bx += bSize + btnSpace;
        }

        // --- Row 2: Play pill + 40dp skip icon button + 20dp seekbar track ---
        String playLabel = getString(R.string.AccActionPlay);
        if (playLabel == null || playLabel.isEmpty()) {
            playLabel = "Play";
        }
        float playTextW = textPaint.measureText(playLabel);
        float pillW = dpf2(12f) + dpf2(20f) + dpf2(6f) + playTextW + dpf2(16f);
        float pillLeft = innerLeft;
        float pillRight = pillLeft + pillW;

        if (glass != null) {
            playPillPane.draw(
                    canvas,
                    pillLeft, row2Top, pillRight, row2Top + row2H,
                    row2H * 0.5f,
                    backdropDrawer, 0f, 0f, cardWInt, cardHInt,
                    true,
                    glass.blurPx,
                    dpf2(12f) * glass.lensT,
                    dpf2(24f) * glass.lensT,
                    true,
                    false,
                    true,
                    glass.buttonTint(surface, primaryContainer),
                    glass.stillFill(surfaceAtElevation),
                    still
            );
        } else {
            flatPaint.setColor(primaryContainer);
            itemRect.set(pillLeft, row2Top, pillRight, row2Top + row2H);
            canvas.drawRoundRect(itemRect, row2H * 0.5f, row2H * 0.5f, flatPaint);
        }

        playSmallIcon.setColor(onPrimaryContainer);
        int pIconSz = dp(20);
        int pIx = Math.round(pillLeft + dpf2(12f));
        int pIy = Math.round(row2Top + (row2H - pIconSz) * 0.5f);
        playSmallIcon.setBounds(pIx, pIy, pIx + pIconSz, pIy + pIconSz);
        playSmallIcon.draw(canvas);

        textPaint.setColor(onPrimaryContainer);
        float textX = pillLeft + dpf2(12f) + pIconSz + dpf2(6f);
        float textY = row2Top + row2H * 0.5f - (textPaint.descent() + textPaint.ascent()) * 0.5f;
        canvas.drawText(playLabel, textX, textY, textPaint);

        // 40dp circle button
        float cBtnLeft = pillRight + dpf2(10f);
        float cBtnSize = dpf2(40f);
        float cBtnRight = cBtnLeft + cBtnSize;
        if (glass != null) {
            circleBtnPane.draw(
                    canvas,
                    cBtnLeft, row2Top, cBtnRight, row2Top + row2H,
                    cBtnSize * 0.5f,
                    backdropDrawer, 0f, 0f, cardWInt, cardHInt,
                    true,
                    glass.blurPx,
                    dpf2(12f) * glass.lensT,
                    dpf2(24f) * glass.lensT,
                    true,
                    false,
                    true,
                    glass.floatingTint(surfaceAtElevation),
                    glass.stillFill(surfaceAtElevation),
                    still
            );
        } else {
            flatPaint.setColor(flatColor);
            itemRect.set(cBtnLeft, row2Top, cBtnRight, row2Top + row2H);
            canvas.drawRoundRect(itemRect, cBtnSize * 0.5f, cBtnSize * 0.5f, flatPaint);
        }
        nextIcon.setColor(onSurface);
        int nIconSz = dp(22);
        int nIx = Math.round(cBtnLeft + (cBtnSize - nIconSz) * 0.5f);
        int nIy = Math.round(row2Top + (row2H - nIconSz) * 0.5f);
        nextIcon.setBounds(nIx, nIy, nIx + nIconSz, nIy + nIconSz);
        nextIcon.draw(canvas);

        // 20dp tall seekbar track
        float seekLeft = cBtnRight + dpf2(10f);
        float seekRight = innerRight;
        float seekH = dpf2(20f);
        float seekTop = row2Top + (row2H - seekH) * 0.5f;
        if (seekRight > seekLeft + dpf2(24f)) {
            if (glass != null) {
                seekTrackPane.draw(
                        canvas,
                        seekLeft, seekTop, seekRight, seekTop + seekH,
                        seekH * 0.5f,
                        backdropDrawer, 0f, 0f, cardWInt, cardHInt,
                        true,
                        glass.blurPx,
                        dpf2(12f) * glass.lensT,
                        dpf2(24f) * glass.lensT,
                        true,
                        false,
                        true,
                        glass.floatingTint(surfaceAtElevation),
                        glass.stillFill(surfaceAtElevation),
                        still
                );
            } else {
                flatPaint.setColor(flatColor);
                itemRect.set(seekLeft, seekTop, seekRight, seekTop + seekH);
                canvas.drawRoundRect(itemRect, seekH * 0.5f, seekH * 0.5f, flatPaint);
            }

            float trackPad = dpf2(6f);
            float fillLeft = seekLeft + trackPad;
            float fillTop = seekTop + trackPad;
            float fillBottom = seekTop + seekH - trackPad;
            float fillMaxW = Math.max(0f, (seekRight - seekLeft) - trackPad * 2f);
            float fillW = fillMaxW * 0.45f;
            float fillRadius = (fillBottom - fillTop) * 0.5f;
            flatPaint.setColor(primary);
            itemRect.set(fillLeft, fillTop, fillLeft + fillW, fillBottom);
            canvas.drawRoundRect(itemRect, fillRadius, fillRadius, flatPaint);
        }

        canvas.restore();
    }
}
