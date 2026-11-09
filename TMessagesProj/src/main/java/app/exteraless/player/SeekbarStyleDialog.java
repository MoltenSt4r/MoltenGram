package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import app.exteraless.appearance.AppearanceConfig;

/**
 * 4-card Seekbar Style selection modal dialog matching MetroList design (Screenshot 112554).
 */
public class SeekbarStyleDialog extends Dialog {

    private final Utilities.Callback<Integer> onSelected;

    public SeekbarStyleDialog(Context context, Utilities.Callback<Integer> onSelected) {
        super(context);
        this.onSelected = onSelected;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        int currentStyle = AppearanceConfig.playerSeekbarStyle();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(16));

        // Background container
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(Theme.getColor(Theme.key_dialogBackground));
        root.setBackground(bg);

        // 2x2 Grid of preview cards
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(2);
        grid.setRowCount(2);

        int primaryColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (primaryColor == 0 || primaryColor == -1) {
            primaryColor = 0xff00d2b4; // MetroList accent teal
        }

        String[] titles = {
                LocaleController.getString(R.string.OEAppearancePlayerSeekbarDefault),
                LocaleController.getString(R.string.OEAppearancePlayerSeekbarWavy),
                LocaleController.getString(R.string.OEAppearancePlayerSeekbarSlim),
                LocaleController.getString(R.string.OEAppearancePlayerSeekbarWavy)
        };

        for (int i = 0; i < 4; i++) {
            final int styleIndex = i;
            boolean isSelected = (currentStyle == i);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(12), dp(16), dp(12), dp(12));

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(dp(16));
            cardBg.setColor(0x18ffffff);
            if (isSelected) {
                cardBg.setStroke(dp(2), primaryColor);
            } else {
                cardBg.setStroke(dp(1), 0x2affffff);
            }
            card.setBackground(cardBg);

            // Miniature Seekbar Preview
            CardPreview preview = new CardPreview(context, i, primaryColor);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp(110), dp(56));
            previewLp.gravity = Gravity.CENTER;
            card.addView(preview, previewLp);

            // Label
            TextView label = new TextView(context);
            label.setText(titles[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            label.setTextColor(0xffeeeeee);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(8);
            card.addView(label, labelLp);

            card.setOnClickListener(v -> {
                AppearanceConfig.playerSeekbarStyle.setConfigInt(styleIndex);
                AppearanceConfig.playerWavy.setConfigBool(styleIndex == 1 || styleIndex == 3);
                if (onSelected != null) {
                    onSelected.run(styleIndex);
                }
                if (PlayerSheet.instance != null) {
                    PlayerSheet.instance.applyStyle(true);
                }
                PlayerMiniView.styleChanged();
                dismiss();
            });

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = dp(132);
            glp.height = dp(120);
            glp.setMargins(dp(6), dp(6), dp(6), dp(6));
            grid.addView(card, glp);
        }

        root.addView(grid);

        // Cancel button
        TextView cancelBtn = new TextView(context);
        cancelBtn.setText(LocaleController.getString(R.string.Cancel));
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelBtn.setTextColor(primaryColor);
        cancelBtn.setPadding(dp(16), dp(12), dp(12), dp(8));
        cancelBtn.setGravity(Gravity.RIGHT);
        cancelBtn.setOnClickListener(v -> dismiss());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(12);
        btnLp.gravity = Gravity.RIGHT;
        root.addView(cancelBtn, btnLp);

        setContentView(root);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static class CardPreview extends View {
        private final int style;
        private final int primaryColor;
        private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        CardPreview(Context context, int style, int primaryColor) {
            super(context);
            this.style = style;
            this.primaryColor = primaryColor;

            activePaint.setColor(primaryColor);
            activePaint.setStyle(Paint.Style.STROKE);
            activePaint.setStrokeCap(Paint.Cap.ROUND);

            inactivePaint.setColor(0x55888888);
            inactivePaint.setStyle(Paint.Style.STROKE);
            inactivePaint.setStrokeCap(Paint.Cap.ROUND);

            thumbPaint.setColor(primaryColor);
            thumbPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float cy = getHeight() / 2f;
            float left = dp(10);
            float right = w - dp(10);
            float totalW = right - left;

            if (style == 0) {
                // Default: thick bar + vertical pill thumb + trailing dot
                float strokeW = dp(8);
                activePaint.setStrokeWidth(strokeW);
                inactivePaint.setStrokeWidth(strokeW);

                float p = 0.38f;
                float thumbX = left + totalW * p;
                float gap = dp(4);
                float waveEnd = thumbX - gap;
                float restX = thumbX + gap;

                if (waveEnd > left) {
                    canvas.drawLine(left, cy, waveEnd, cy, activePaint);
                }
                if (right - dp(4) > restX) {
                    canvas.drawLine(restX, cy, right - dp(4), cy, inactivePaint);
                }
                canvas.drawCircle(right, cy, dp(2.5f), activePaint);

                rect.set(thumbX - dp(2.5f), cy - dp(14), thumbX + dp(2.5f), cy + dp(14));
                canvas.drawRoundRect(rect, dpf2(2), dpf2(2), thumbPaint);

            } else if (style == 1) {
                // Wavy: sine wave + round circle thumb + trailing dot
                float strokeW = dp(3.5f);
                activePaint.setStrokeWidth(strokeW);
                inactivePaint.setStrokeWidth(strokeW);

                float p = 0.5f;
                float thumbX = left + totalW * p;
                float waveEnd = thumbX - dp(8);

                path.rewind();
                path.moveTo(left, cy);
                float lambda = dp(24);
                for (float x = left; x <= waveEnd; x += dp(2)) {
                    float y = cy + dp(3.5f) * (float) Math.sin((x - left) / lambda * Math.PI * 2);
                    path.lineTo(x, y);
                }
                canvas.drawPath(path, activePaint);

                if (right - dp(4) > thumbX + dp(8)) {
                    canvas.drawLine(thumbX + dp(8), cy, right - dp(4), cy, inactivePaint);
                }
                canvas.drawCircle(right, cy, dp(2), inactivePaint);
                canvas.drawCircle(thumbX, cy, dp(6.5f), thumbPaint);

            } else if (style == 2) {
                // Slim: flat smooth line, no thumb
                float strokeW = dp(4);
                activePaint.setStrokeWidth(strokeW);
                inactivePaint.setStrokeWidth(strokeW);

                float p = 0.65f;
                float midX = left + totalW * p;
                canvas.drawLine(left, cy, midX, cy, activePaint);
                canvas.drawLine(midX, cy, right, cy, inactivePaint);

            } else {
                // Squiggly (style 3): sine wave + vertical bar thumb + trailing dot
                float strokeW = dp(3.5f);
                activePaint.setStrokeWidth(strokeW);
                inactivePaint.setStrokeWidth(strokeW);

                float p = 0.5f;
                float thumbX = left + totalW * p;
                float waveEnd = thumbX - dp(5);

                path.rewind();
                path.moveTo(left, cy);
                float lambda = dp(24);
                for (float x = left; x <= waveEnd; x += dp(2)) {
                    float y = cy + dp(3.5f) * (float) Math.sin((x - left) / lambda * Math.PI * 2);
                    path.lineTo(x, y);
                }
                canvas.drawPath(path, activePaint);

                if (right - dp(4) > thumbX + dp(5)) {
                    canvas.drawLine(thumbX + dp(5), cy, right - dp(4), cy, inactivePaint);
                }
                canvas.drawCircle(right, cy, dp(2), inactivePaint);

                rect.set(thumbX - dp(2.5f), cy - dp(9), thumbX + dp(2.5f), cy + dp(9));
                canvas.drawRoundRect(rect, dpf2(1.5f), dpf2(1.5f), thumbPaint);
            }
        }
    }
}
