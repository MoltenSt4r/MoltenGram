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
        return tone(top, bottom, 0.82f)
    }

    @JvmStatic
    fun tonalForeground(top: Int, bottom: Int, dark: Boolean): Int {
        return tone(top, bottom, if (dark) 0.34f else 0.28f)
    }

    private fun tone(top: Int, bottom: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(ColorUtils.blendARGB(top, bottom, 0.5f), hsl)
        hsl[1] = hsl[1].coerceIn(0.55f, 1f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }
}
