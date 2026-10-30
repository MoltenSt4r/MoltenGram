package app.exteraless.glyph

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem

/**
 * Настройки интеграции с Nothing Glyph (подсветка задней панели Nothing Phone).
 *
 * Схема повторяет [app.exteraless.OpenExteraConfig]: те же SharedPreferences,
 * тот же [ConfigItem]. Ключи с префиксом OEGlyph.
 */
object GlyphConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = NekoConfig.getPreferences()

    /** Мастер-переключатель: без него контроллер не биндится к системному сервису глифов. */
    @JvmField
    val enabled = addConfig("OEGlyphEnabled", ConfigItem.configTypeBool, false)

    /** Вспышка глифов на входящее сообщение. */
    @JvmField
    val onNewMessage = addConfig("OEGlyphNewMessage", ConfigItem.configTypeBool, true)

    /** «Дыхание» глифов, пока идёт запись голосового. */
    @JvmField
    val onRecording = addConfig("OEGlyphRecording", ConfigItem.configTypeBool, true)

    /** «Дыхание» глифов, пока звонит входящий VoIP-звонок. */
    @JvmField
    val onCall = addConfig("OEGlyphCalls", ConfigItem.configTypeBool, true)

    /** Реагировать на сообщения только при выключенном экране. */
    @JvmField
    val screenOffOnly = addConfig("OEGlyphScreenOff", ConfigItem.configTypeBool, false)

    /** Показывать иконку (с контрастной инверсией поверх колец/эффектов) на матрице Glyph Matrix. */
    @JvmField
    val matrixShowIcon = addConfig("OEGlyphMatrixShowIcon", ConfigItem.configTypeBool, true)

    /** Свечение глифов при записи кружков (видеосообщений) и видео. */
    @JvmField
    val onVideoRecording = addConfig("OEGlyphVideoRecording", ConfigItem.configTypeBool, true)

    /** Включать красный индикатор записи (Rec. Light) на поддерживаемых моделях Nothing Phone при записи кружка или видео. */
    @JvmField
    val redRecordingIndicator = addConfig("OEGlyphRedIndicator", ConfigItem.configTypeBool, true)

    const val CALL_ANIM_PULSE = 0
    const val CALL_ANIM_WAVE = 1
    const val CALL_ANIM_BREATHING = 2

    const val RECORDING_ANIM_BREATHING = 0
    const val RECORDING_ANIM_ACCENT_RING = 1
    const val RECORDING_ANIM_HEARTBEAT = 2

    const val MESSAGE_ANIM_DOUBLE_FLASH = 0
    const val MESSAGE_ANIM_STROBE = 1
    const val MESSAGE_ANIM_SOFT_PULSE = 2
    const val MESSAGE_ANIM_ACCENT_RING = 3

    /** Стиль анимации при звонке: 0 - пульс, 1 - волна, 2 - дыхание. */
    @JvmField
    val callAnimationStyle = addConfig("OEGlyphCallAnim", ConfigItem.configTypeInt, CALL_ANIM_PULSE)

    /** Стиль анимации при записи: 0 - дыхание, 1 - кольцо, 2 - пульс. */
    @JvmField
    val recordingAnimationStyle = addConfig("OEGlyphRecordAnim", ConfigItem.configTypeInt, RECORDING_ANIM_BREATHING)

    /** Стиль вспышки при сообщении: 0 - двойная, 1 - стробоскоп, 2 - мягкая, 3 - кольцо. */
    @JvmField
    val messageAnimationStyle = addConfig("OEGlyphMessageAnim", ConfigItem.configTypeInt, MESSAGE_ANIM_DOUBLE_FLASH)

    @JvmStatic
    fun enabled(): Boolean = enabled.Bool()

    @JvmStatic
    fun onNewMessage(): Boolean = onNewMessage.Bool()

    @JvmStatic
    fun onRecording(): Boolean = onRecording.Bool()

    @JvmStatic
    fun onCall(): Boolean = onCall.Bool()

    @JvmStatic
    fun screenOffOnly(): Boolean = screenOffOnly.Bool()

    @JvmStatic
    fun matrixShowIcon(): Boolean = matrixShowIcon.Bool()

    @JvmStatic
    fun onVideoRecording(): Boolean = onVideoRecording.Bool()

    @JvmStatic
    fun redRecordingIndicator(): Boolean = redRecordingIndicator.Bool()

    @JvmStatic
    fun callAnimationStyle(): Int = callAnimationStyle.Int()

    @JvmStatic
    fun recordingAnimationStyle(): Int = recordingAnimationStyle.Int()

    @JvmStatic
    fun messageAnimationStyle(): Int = messageAnimationStyle.Int()

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    @JvmStatic
    fun init() {
        loadConfig(false)
    }

    @JvmStatic
    fun loadConfig(force: Boolean) {
        synchronized(sync) {
            if (configLoaded && !force) return
            if (ApplicationLoader.applicationContext == null) return
            val preferences = getPreferences()
            for (item in configs) {
                try {
                    when (item.type) {
                        ConfigItem.configTypeBool ->
                            item.value = preferences.getBoolean(item.key, item.defaultValue as Boolean)
                        ConfigItem.configTypeInt ->
                            item.value = preferences.getInt(item.key, item.defaultValue as Int)
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
            configLoaded = true
        }
    }
}
