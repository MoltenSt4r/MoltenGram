package app.exteraless.appearance

import androidx.core.graphics.ColorUtils

object M3ListItems {

    @JvmStatic
    fun enabled(): Boolean = AppearanceConfig.m3ListItems()

    @JvmStatic
    fun rowHeight(stockDp: Int): Int = if (stockDp == 50 && enabled()) 56 else stockDp

    @JvmStatic
    fun detailRowHeight(stockDp: Int): Int = if (stockDp == 64 && enabled()) 72 else stockDp

    @JvmStatic
    fun shadowHeight(nextIsHeader: Boolean, stockDp: Int): Int {
        if (!enabled()) return stockDp
        return if (nextIsHeader) 10 else 16
    }

    @JvmStatic
    fun tonalBackground(top: Int, bottom: Int): Int = tonalBackground(top, bottom, false)

    @JvmStatic
    fun tonalForeground(top: Int, bottom: Int): Int = tonalForeground(top, bottom, false)

    @JvmStatic
    fun tonalBackground(top: Int, bottom: Int, dark: Boolean): Int {
        return tone(top, bottom, if (dark) 0.76f else 0.88f, if (dark) 0.85f else 0.65f)
    }

    @JvmStatic
    fun tonalForeground(top: Int, bottom: Int, dark: Boolean): Int {
        return tone(top, bottom, if (dark) 0.22f else 0.30f, if (dark) 0.90f else 0.85f)
    }

    private fun tone(top: Int, bottom: Int, lightness: Float, saturation: Float = 0.70f): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(ColorUtils.blendARGB(top, bottom, 0.5f), hsl)
        val origSat = hsl[1]
        hsl[1] = if (origSat < 0.1f) origSat else saturation.coerceIn(0.2f, 1f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }
}
