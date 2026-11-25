package com.exteragram.messenger.messageexport.render;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public final class MessageExportWatermark {

    private static final int TOP_GAP = 8;
    private static final int BOTTOM_GAP = 12;
    private static final int PILL_HEIGHT = 26;
    private static final int PILL_PADDING = 10;
    private static final int CONTENT_PADDING = 8;
    private static final String WATERMARK_TEXT = "MoltenGram";

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();

    public MessageExportWatermark(Context context) {
        textPaint.setTextSize(dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());
    }

    public static boolean isEnabled() {
        return ExteraConfig.getMessageExportWatermark();
    }

    public static int getBottomInset() {
        return dp(isEnabled() ? TOP_GAP + PILL_HEIGHT + BOTTOM_GAP : CONTENT_PADDING);
    }

    public void draw(Canvas canvas, int width, float contentBottom, Theme.ResourcesProvider provider) {
        if (!isEnabled()) {
            pill.setEmpty();
            return;
        }
        float left = (width - getPillWidth()) / 2f;
        float top = Math.round(contentBottom) + dp(TOP_GAP);
        if (provider != null && provider.hasGradientService()) {
            canvas.save();
            canvas.translate(left, top);
            provider.applyServiceShaderMatrix(width, 0, left, top);
            drawPill(canvas, 0, 0, provider);
            canvas.restore();
            pill.offset(left, top);
        } else {
            drawPill(canvas, left, top, provider);
        }
    }

    public void drawPill(Canvas canvas, float left, float top, Theme.ResourcesProvider provider) {
        int color = Theme.getColor(Theme.key_chat_serviceText, provider);
        textPaint.setColor(color);
        int pillHeight = getPillHeight();
        pill.set(left, top, left + getPillWidth(), top + pillHeight);
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, Theme.getThemePaint(Theme.key_paint_chatActionBackground, provider));
        float textX = pill.left + dp(PILL_PADDING);
        float textY = pill.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(WATERMARK_TEXT, textX, textY, textPaint);
    }

    public RectF getBounds() {
        return pill;
    }

    public int getPillWidth() {
        return Math.round(textPaint.measureText(WATERMARK_TEXT)) + dp(PILL_PADDING) * 2;
    }

    public int getPillHeight() {
        return dp(PILL_HEIGHT);
    }
}
