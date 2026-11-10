package app.exteraless.appearance;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Region;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Превью закругления аватарок со слайдером «Квадрат ↔ Круг».
 * Порт AvatarCornersPreviewCell из exteraGram.
 */
@SuppressLint("ViewConstructor")
public class AvatarCornersPreviewCell extends FrameLayout {

    public interface OnChanged {
        void run();
    }

    private final FrameLayout preview;
    private final AvatarCornersSeekBar seekBar;

    private boolean needDivider;

    public AvatarCornersPreviewCell(Context context, OnChanged onChanged) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int start = 0, end = AppearanceConfig.AVATAR_CORNERS_MAX;
        seekBar = new AvatarCornersSeekBar(context, value -> {
            AppearanceConfig.avatarCorners.setConfigInt(value);
            invalidate();
            if (onChanged != null) {
                onChanged.run();
            }
        }, start, end,
                LocaleController.getString(R.string.OEAppearanceAvatarCorners),
                LocaleController.getString(R.string.OEAppearanceAvatarCornersLeft),
                LocaleController.getString(R.string.OEAppearanceAvatarCornersRight));
        seekBar.setValue(AppearanceConfig.avatarCorners());
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        preview = new FrameLayout(context) {
            private final Path onlinePath = new Path();

            @Override
            protected void onDraw(Canvas canvas) {
                float w = getMeasuredWidth();
                float h = getMeasuredHeight();

                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
                canvas.drawCircle(AndroidUtilities.dp(68), h / 2.0f + AndroidUtilities.dpf2(20.5f), AndroidUtilities.dp(7), Theme.dialogs_onlineCirclePaint);

                Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(true));
                canvas.drawRoundRect(AndroidUtilities.dp(92), h / 2.0f - AndroidUtilities.dpf2(15.5f),
                        w - AndroidUtilities.dp(90), h / 2.0f - AndroidUtilities.dpf2(7.5f),
                        w / 2.0f, w / 2.0f, Theme.dialogs_onlineCirclePaint);

                onlinePath.rewind();
                onlinePath.addCircle(AndroidUtilities.dp(68), h / 2.0f + AndroidUtilities.dpf2(20.5f), AndroidUtilities.dp(12), Path.Direction.CCW);
                canvas.save();
                canvas.clipPath(onlinePath, Region.Op.DIFFERENCE);

                Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(false));
                canvas.drawRoundRect(AndroidUtilities.dp(92), h / 2.0f + AndroidUtilities.dpf2(7.5f),
                        w - AndroidUtilities.dp(50), h / 2.0f + AndroidUtilities.dp(15.5f),
                        w / 2.0f, w / 2.0f, Theme.dialogs_onlineCirclePaint);

                // Аватарка у exteraGram рисуется ярким мок-цветом, а не тем же, что вторая строка
                // (AvatarCornersPreviewCell.java:188 — brightMockPaint).
                Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(true));
                float corners = AppearanceConfig.getAvatarCorners(AndroidUtilities.dp(56));
                canvas.drawRoundRect(AndroidUtilities.dp(20), h / 2.0f - AndroidUtilities.dp(28),
                        AndroidUtilities.dp(76), h / 2.0f + AndroidUtilities.dp(28),
                        corners, corners, Theme.dialogs_onlineCirclePaint);
                canvas.restore();
            }
        };
        preview.setWillNotDraw(false);
        // Подложка превью общая для всех экранов настроек.
        preview.setBackground(new PreviewBackgroundDrawable());
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER, 21, 130, 21, 21));
    }

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (preview != null) {
            preview.invalidate();
        }
        if (seekBar != null) {
            seekBar.invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1,
                    getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1,
                    Theme.dividerPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(130 + 89 + 21), MeasureSpec.EXACTLY)
        );
    }
}
