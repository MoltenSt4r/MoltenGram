/*
 * This is derived from exteraGram for Android (com.exteragram.messenger.utils.ui.MonetUtils),
 * adapted for MoltenGram with in-memory constant fallback colors and fast invalidated caching.
 */

package com.exteragram.messenger.utils.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.PatternMatcher
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.ui.ActionBar.Theme
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object MonetUtils {

    private val RES_MAP = HashMap<String, Int>(68)
    private val CONST_COLORS = HashMap<String, Int>(24)
    private val RESOLVED_CACHE = ConcurrentHashMap<String, Int>()
    private val ACCENT_PREFIXES = arrayOf("a1_", "a2_", "a3_")

    private val PARAM_PATTERN = Pattern.compile("^([^(]+)\\(([^)]+)\\)?$")

    private const val ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED"
    private val overlayChangeReceiver = OverlayChangeReceiver()

    private const val KEY_ALPHA = "a"
    private const val KEY_SATURATION = "s"
    private const val KEY_LIGHTNESS = "l"

    @Volatile
    private var harmonizeContextColor = 0

    @Volatile
    private var lastSystemSignature = 0L

    init {
        CONST_COLORS["mBlack"] = 0xFF000000.toInt()
        CONST_COLORS["black"] = 0xFF000000.toInt()
        CONST_COLORS["mWhite"] = 0xFFFFFFFF.toInt()
        CONST_COLORS["white"] = 0xFFFFFFFF.toInt()

        CONST_COLORS["mRed200"] = 0xFFEF9A9A.toInt()
        CONST_COLORS["mRed500"] = 0xFFF44336.toInt()
        CONST_COLORS["mRed800"] = 0xFFC62828.toInt()
        CONST_COLORS["mGreen200"] = 0xFFA5D6A7.toInt()
        CONST_COLORS["mGreen500"] = 0xFF4CAF50.toInt()
        CONST_COLORS["mGreen800"] = 0xFF2E7D32.toInt()

        CONST_COLORS["a1_0"] = 0xFFFFFFFF.toInt()
        CONST_COLORS["a2_0"] = 0xFFFFFFFF.toInt()
        CONST_COLORS["a3_0"] = 0xFFFFFFFF.toInt()
        CONST_COLORS["n1_0"] = 0xFFFFFFFF.toInt()
        CONST_COLORS["n2_0"] = 0xFFFFFFFF.toInt()
        CONST_COLORS["a1_1000"] = 0xFF000000.toInt()
        CONST_COLORS["a2_1000"] = 0xFF000000.toInt()
        CONST_COLORS["a3_1000"] = 0xFF000000.toInt()
        CONST_COLORS["n1_1000"] = 0xFF000000.toInt()
        CONST_COLORS["n2_1000"] = 0xFF000000.toInt()

        if (isSupported()) {
            initSystemColors()
        }
    }

    @JvmStatic
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @JvmStatic
    fun invalidateCache() {
        harmonizeContextColor = 0
        lastSystemSignature = 0L
        RESOLVED_CACHE.clear()
    }

    private fun ensureCacheFresh() {
        val ctx = ApplicationLoader.applicationContext ?: return
        if (!isSupported()) return
        try {
            val a = ctx.getColor(android.R.color.system_accent1_500)
            val n = ctx.getColor(android.R.color.system_neutral1_500)
            val sig = (a.toLong() shl 32) or (n.toLong() and 0xFFFFFFFFL)
            if (sig != lastSystemSignature) {
                harmonizeContextColor = 0
                RESOLVED_CACHE.clear()
                lastSystemSignature = sig
            }
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    @ColorInt
    fun getColor(colorString: String): Int {
        return getColorOrNull(colorString) ?: 0
    }

    @JvmStatic
    fun getColorOrNull(colorString: String?): Int? {
        if (colorString.isNullOrEmpty()) return null
        val trimmed = colorString.trim()
        if (trimmed.isEmpty()) return null

        ensureCacheFresh()
        RESOLVED_CACHE[trimmed]?.let { return it }

        try {
            var work = trimmed
            var harmonizeSuffix = false
            if (work.endsWith("h") || work.endsWith("!")) {
                val base = work.substring(0, work.length - 1).trim()
                if (base.startsWith("a") || base.startsWith("n") || base.startsWith("m")) {
                    harmonizeSuffix = true
                    work = base
                }
            }

            var colorKey = work
            var alpha = 100
            var saturation = 100
            var lightness = 100

            val matcher = PARAM_PATTERN.matcher(work)
            if (matcher.find()) {
                colorKey = matcher.group(1)?.trim() ?: work
                val params = matcher.group(2)
                if (params != null) {
                    val pairs = params.split(",")
                    for (pair in pairs) {
                        val kv = pair.split("=")
                        if (kv.size == 2) {
                            try {
                                val key = kv[0].trim()
                                val value = kv[1].trim().toInt()
                                when (key) {
                                    KEY_ALPHA -> alpha = value
                                    KEY_SATURATION -> saturation = value
                                    KEY_LIGHTNESS -> lightness = value
                                }
                            } catch (_: NumberFormatException) {
                            }
                        }
                    }
                }
            } else {
                // Support legacy underscore alpha suffix like n1_800_80
                val lastUnderscore = colorKey.lastIndexOf('_')
                if (lastUnderscore > 0 && colorKey.indexOf('_') != lastUnderscore) {
                    val maybeAlpha = colorKey.substring(lastUnderscore + 1).toIntOrNull()
                    if (maybeAlpha != null && maybeAlpha in 0..100) {
                        val candidateKey = colorKey.substring(0, lastUnderscore)
                        if (RES_MAP.containsKey(candidateKey) || CONST_COLORS.containsKey(candidateKey)) {
                            colorKey = candidateKey
                            alpha = maybeAlpha
                        }
                    }
                }
            }

            var colorValue = resolveRawColor(colorKey) ?: return null

            if (saturation != 100) {
                colorValue = ColorUtils.blendARGB(Color.WHITE, colorValue, (saturation / 100f).coerceIn(0f, 1f))
            }
            if (lightness != 100) {
                colorValue = ColorUtils.blendARGB(Color.BLACK, colorValue, (lightness / 100f).coerceIn(0f, 1f))
            }
            if (alpha != 100) {
                colorValue = ColorUtils.setAlphaComponent(colorValue, (alpha.coerceIn(0, 100) * 2.55f).toInt())
            }

            if (harmonizeSuffix || colorKey.startsWith("mR") || colorKey.startsWith("mG")) {
                colorValue = harmonize(colorValue)
            }

            RESOLVED_CACHE[trimmed] = colorValue
            return colorValue
        } catch (e: Exception) {
            FileLog.e(e)
            return null
        }
    }

    private fun resolveRawColor(colorKey: String): Int? {
        val resId = RES_MAP[colorKey]
        if (resId != null && resId != 0) {
            val ctx = ApplicationLoader.applicationContext
            if (ctx != null) {
                return try {
                    ctx.getColor(resId)
                } catch (_: Throwable) {
                    CONST_COLORS[colorKey]
                }
            }
        }
        return CONST_COLORS[colorKey]
    }

    @JvmStatic
    @ColorInt
    fun getSystemAccentColor(index: Int, isDark: Boolean): Int {
        if (!isSupported() || index !in ACCENT_PREFIXES.indices) {
            return 0
        }
        val tone = if (isDark) 200 else 600
        return getColor(ACCENT_PREFIXES[index] + tone)
    }

    @JvmStatic
    @ColorInt
    fun getSecondaryContainerColor(isDark: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ctx = ApplicationLoader.applicationContext
            if (ctx != null) {
                try {
                    return ctx.getColor(
                        if (isDark) android.R.color.system_secondary_container_dark
                        else android.R.color.system_secondary_container_light
                    )
                } catch (_: Throwable) {
                }
            }
        }
        return getColor(if (isDark) "a2_700" else "a2_100")
    }

    @JvmStatic
    @ColorInt
    fun getOnSecondaryContainerColor(isDark: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ctx = ApplicationLoader.applicationContext
            if (ctx != null) {
                try {
                    return ctx.getColor(
                        if (isDark) android.R.color.system_on_secondary_container_dark
                        else android.R.color.system_on_secondary_container_light
                    )
                } catch (_: Throwable) {
                }
            }
        }
        return getColor(if (isDark) "a2_100" else "a2_900")
    }

    @JvmStatic
    @ColorInt
    fun harmonize(@ColorInt color: Int): Int {
        val contextColor = getHarmonizeContextColor()
        if (contextColor == 0) {
            return color
        }
        return MaterialColors.harmonize(color, contextColor)
    }

    @JvmStatic
    @ColorInt
    fun getHarmonizeContextColor(): Int {
        if (!isSupported()) {
            return 0
        }
        ensureCacheFresh()
        var color = harmonizeContextColor
        if (color == 0) {
            val ctx = ApplicationLoader.applicationContext ?: return 0
            try {
                color = ctx.getColor(android.R.color.system_accent1_600)
                harmonizeContextColor = color
            } catch (_: Throwable) {
                return 0
            }
        }
        return color
    }

    @JvmStatic
    fun registerReceiver(context: Context) {
        try {
            invalidateCache()
            overlayChangeReceiver.register(context.applicationContext ?: context)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    @JvmStatic
    fun unregisterReceiver(context: Context) {
        try {
            overlayChangeReceiver.unregister(context.applicationContext ?: context)
        } catch (_: Exception) {
        }
    }

    private class OverlayChangeReceiver : BroadcastReceiver() {
        private var isRegistered = false

        fun register(context: Context) {
            if (isRegistered) return
            val packageFilter = IntentFilter(ACTION_OVERLAY_CHANGED)
            packageFilter.addDataScheme("package")
            packageFilter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL)
            ContextCompat.registerReceiver(context, this, packageFilter, ContextCompat.RECEIVER_EXPORTED)
            isRegistered = true
        }

        fun unregister(context: Context) {
            if (!isRegistered) return
            context.unregisterReceiver(this)
            isRegistered = false
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_OVERLAY_CHANGED == intent.action) {
                invalidateCache()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    tw.nekomimi.nekogram.helpers.MonetHelper.clearCachesOnly()
                }
                Theme.refreshMonetColors()
                if (Theme.isCurrentThemeMonet() || Theme.isCurrentAccentMonet()) {
                    val isNight = Theme.isCurrentThemeNight()
                    val active = Theme.getActiveTheme()
                    if (active != null) {
                        Theme.applyTheme(active, isNight)
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                            org.telegram.messenger.NotificationCenter.needSetDayNightTheme,
                            active, isNight, null, -1
                        )
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initSystemColors() {
        RES_MAP["a1_0"] = android.R.color.system_accent1_0
        RES_MAP["a1_10"] = android.R.color.system_accent1_10
        RES_MAP["a1_50"] = android.R.color.system_accent1_50
        RES_MAP["a1_100"] = android.R.color.system_accent1_100
        RES_MAP["a1_200"] = android.R.color.system_accent1_200
        RES_MAP["a1_300"] = android.R.color.system_accent1_300
        RES_MAP["a1_400"] = android.R.color.system_accent1_400
        RES_MAP["a1_500"] = android.R.color.system_accent1_500
        RES_MAP["a1_600"] = android.R.color.system_accent1_600
        RES_MAP["a1_700"] = android.R.color.system_accent1_700
        RES_MAP["a1_800"] = android.R.color.system_accent1_800
        RES_MAP["a1_900"] = android.R.color.system_accent1_900
        RES_MAP["a1_1000"] = android.R.color.system_accent1_1000

        RES_MAP["a2_0"] = android.R.color.system_accent2_0
        RES_MAP["a2_10"] = android.R.color.system_accent2_10
        RES_MAP["a2_50"] = android.R.color.system_accent2_50
        RES_MAP["a2_100"] = android.R.color.system_accent2_100
        RES_MAP["a2_200"] = android.R.color.system_accent2_200
        RES_MAP["a2_300"] = android.R.color.system_accent2_300
        RES_MAP["a2_400"] = android.R.color.system_accent2_400
        RES_MAP["a2_500"] = android.R.color.system_accent2_500
        RES_MAP["a2_600"] = android.R.color.system_accent2_600
        RES_MAP["a2_700"] = android.R.color.system_accent2_700
        RES_MAP["a2_800"] = android.R.color.system_accent2_800
        RES_MAP["a2_900"] = android.R.color.system_accent2_900
        RES_MAP["a2_1000"] = android.R.color.system_accent2_1000

        RES_MAP["a3_0"] = android.R.color.system_accent3_0
        RES_MAP["a3_10"] = android.R.color.system_accent3_10
        RES_MAP["a3_50"] = android.R.color.system_accent3_50
        RES_MAP["a3_100"] = android.R.color.system_accent3_100
        RES_MAP["a3_200"] = android.R.color.system_accent3_200
        RES_MAP["a3_300"] = android.R.color.system_accent3_300
        RES_MAP["a3_400"] = android.R.color.system_accent3_400
        RES_MAP["a3_500"] = android.R.color.system_accent3_500
        RES_MAP["a3_600"] = android.R.color.system_accent3_600
        RES_MAP["a3_700"] = android.R.color.system_accent3_700
        RES_MAP["a3_800"] = android.R.color.system_accent3_800
        RES_MAP["a3_900"] = android.R.color.system_accent3_900
        RES_MAP["a3_1000"] = android.R.color.system_accent3_1000

        RES_MAP["n1_0"] = android.R.color.system_neutral1_0
        RES_MAP["n1_10"] = android.R.color.system_neutral1_10
        RES_MAP["n1_50"] = android.R.color.system_neutral1_50
        RES_MAP["n1_100"] = android.R.color.system_neutral1_100
        RES_MAP["n1_200"] = android.R.color.system_neutral1_200
        RES_MAP["n1_300"] = android.R.color.system_neutral1_300
        RES_MAP["n1_400"] = android.R.color.system_neutral1_400
        RES_MAP["n1_500"] = android.R.color.system_neutral1_500
        RES_MAP["n1_600"] = android.R.color.system_neutral1_600
        RES_MAP["n1_700"] = android.R.color.system_neutral1_700
        RES_MAP["n1_800"] = android.R.color.system_neutral1_800
        RES_MAP["n1_900"] = android.R.color.system_neutral1_900
        RES_MAP["n1_1000"] = android.R.color.system_neutral1_1000

        RES_MAP["n2_0"] = android.R.color.system_neutral2_0
        RES_MAP["n2_10"] = android.R.color.system_neutral2_10
        RES_MAP["n2_50"] = android.R.color.system_neutral2_50
        RES_MAP["n2_100"] = android.R.color.system_neutral2_100
        RES_MAP["n2_200"] = android.R.color.system_neutral2_200
        RES_MAP["n2_300"] = android.R.color.system_neutral2_300
        RES_MAP["n2_400"] = android.R.color.system_neutral2_400
        RES_MAP["n2_500"] = android.R.color.system_neutral2_500
        RES_MAP["n2_600"] = android.R.color.system_neutral2_600
        RES_MAP["n2_700"] = android.R.color.system_neutral2_700
        RES_MAP["n2_800"] = android.R.color.system_neutral2_800
        RES_MAP["n2_900"] = android.R.color.system_neutral2_900
        RES_MAP["n2_1000"] = android.R.color.system_neutral2_1000
    }
}
