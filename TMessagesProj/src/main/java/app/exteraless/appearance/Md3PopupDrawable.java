package app.exteraless.appearance;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;

public class Md3PopupDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF innerRect = new RectF();
    private final Theme.ResourcesProvider resourcesProvider;

    private int shadowPad = AndroidUtilities.dp(8);
    private float cornerRadius = AndroidUtilities.dp(20);
    private ColorFilter externalColorFilter;

    public Md3PopupDrawable(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
        shadowPaint.setColor(0x35000000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            shadowPaint.setShadowLayer(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(3.5f), 0x4D000000);
        }
    }

    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidateSelf();
    }

    @Override
    public boolean getPadding(@NonNull Rect padding) {
        padding.set(shadowPad, shadowPad, shadowPad, shadowPad);
        return true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;

        innerRect.set(bounds.left + shadowPad, bounds.top + shadowPad, bounds.right - shadowPad, bounds.bottom - shadowPad);
        if (innerRect.width() <= 0 || innerRect.height() <= 0) return;

        // Shadow
        canvas.drawRoundRect(innerRect, cornerRadius, cornerRadius, shadowPaint);

        // Fill color
        int bg = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider);
        if (bg == 0) {
            bg = 0xFF24262B;
        }

        boolean isLiquidGlass = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) && AppearanceConfig.INSTANCE.liquidGlassElements();

        if (isLiquidGlass) {
            float intensity = AppearanceConfig.INSTANCE.liquidGlassIntensityFloat();
            int alpha = (int) (255 * (0.86f - 0.16f * intensity));
            bg = ColorUtils.setAlphaComponent(bg, Math.max(160, Math.min(255, alpha)));
        }

        fillPaint.setColor(bg);
        if (externalColorFilter != null) {
            fillPaint.setColorFilter(externalColorFilter);
        } else {
            fillPaint.setColorFilter(null);
        }

        canvas.drawRoundRect(innerRect, cornerRadius, cornerRadius, fillPaint);

        // Specular highlight / border stroke
        if (isLiquidGlass) {
            Shader rimShader = new LinearGradient(
                    innerRect.left, innerRect.top,
                    innerRect.right, innerRect.bottom,
                    new int[]{0x40FFFFFF, 0x18FFFFFF, 0x05FFFFFF},
                    new float[]{0f, 0.40f, 1f},
                    Shader.TileMode.CLAMP
            );
            strokePaint.setShader(rimShader);
        } else {
            strokePaint.setShader(null);
            int itemColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider);
            strokePaint.setColor(Theme.multAlpha(itemColor != 0 ? itemColor : 0xFFFFFFFF, 0.10f));
        }
        canvas.drawRoundRect(innerRect, cornerRadius, cornerRadius, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        shadowPaint.setAlpha((int) (alpha * 0.35f));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.externalColorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
