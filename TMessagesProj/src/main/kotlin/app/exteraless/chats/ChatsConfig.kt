package app.exteraless.chats

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem
import xyz.nextalone.nagram.NaConfig

/**
 * Настройки экрана «Chats», перенесённые из exteraGram.
 *
 * Здесь только то, чего в NagramX нет: остальное экран берёт из [tw.nekomimi.nekogram.NekoConfig]
 * и [xyz.nextalone.nagram.NaConfig], чтобы настройка не задваивалась.
 *
 * Схема повторяет [app.exteraless.OpenExteraConfig]: те же SharedPreferences, тот же [ConfigItem],
 * ключи с префиксом OEChats. Конфиг подгружается лениво (см. [ensureLoaded]), потому что
 * ApplicationLoader не трогаем.
 */
object ChatsConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = NekoConfig.getPreferences()

    // ---- Стикеры ----

    /** Форма стикеров: 0 — как есть, 1 — скруглённая, 2 — как сообщение. */
    @JvmField
    val stickerShape = addConfig("OEChatsStickerShape", ConfigItem.configTypeInt, 1)

    // ---- Material 3 Поле ввода ----

    @JvmField
    val md3ChatInput = addConfig("OEChatsMd3ChatInput", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun md3ChatInput(): Boolean {
        ensureLoaded()
        return md3ChatInput.Bool()
    }

    // ---- Анимация сообщений ----

    const val MESSAGE_ANIM_DEFAULT = 0
    const val MESSAGE_ANIM_NONE = 1
    const val MESSAGE_ANIM_FADE = 2
    const val MESSAGE_ANIM_SLIDE = 3
    const val MESSAGE_ANIM_SCALE = 4
    const val MESSAGE_ANIM_POP = 5

    @JvmField
    val incomingMessageAnimation = addConfig("OEChatsIncomingMessageAnimation", ConfigItem.configTypeInt, MESSAGE_ANIM_DEFAULT)

    @JvmField
    val outgoingMessageAnimation = addConfig("OEChatsOutgoingMessageAnimation", ConfigItem.configTypeInt, MESSAGE_ANIM_DEFAULT)

    @JvmStatic
    fun incomingMessageAnimation(): Int {
        ensureLoaded()
        val anim = incomingMessageAnimation.Int()
        return if (anim in MESSAGE_ANIM_DEFAULT..MESSAGE_ANIM_POP) anim else MESSAGE_ANIM_DEFAULT
    }

    @JvmStatic
    fun outgoingMessageAnimation(): Int {
        ensureLoaded()
        val anim = outgoingMessageAnimation.Int()
        return if (anim in MESSAGE_ANIM_DEFAULT..MESSAGE_ANIM_POP) anim else MESSAGE_ANIM_DEFAULT
    }

    // ---- Ответы ----

    /** Цветной фон блока ответа. */
    @JvmField
    val replyColors = addConfig("OEChatsReplyColors", ConfigItem.configTypeBool, true)

    /** Эмодзи-иконка в блоке ответа. */
    @JvmField
    val replyEmoji = addConfig("OEChatsReplyEmoji", ConfigItem.configTypeBool, true)

    /** Подложка под блоком ответа. */
    @JvmField
    val replyBackground = addConfig("OEChatsReplyBackground", ConfigItem.configTypeBool, true)

    @JvmField
    val bottomButton = addConfig("OEChatsBottomButton", ConfigItem.configTypeInt, 1)

    const val BOTTOM_BUTTON_HIDE = 0
    const val BOTTOM_BUTTON_MUTE = 1
    const val BOTTOM_BUTTON_DISCUSS = 2

    @JvmStatic
    fun bottomButton(): Int {
        ensureLoaded()
        val value = bottomButton.Int()
        return if (value in BOTTOM_BUTTON_HIDE..BOTTOM_BUTTON_DISCUSS) value else BOTTOM_BUTTON_MUTE
    }

    // ---- Анимация ввода текста ----

    @JvmField
    val textAnimationEnabled = addConfig("OEChatsTextAnimationEnabled", ConfigItem.configTypeBool, false)
    @JvmField
    val textAnimationDuration = addConfig("OEChatsTextAnimationDuration", ConfigItem.configTypeInt, 300)
    @JvmField
    val textAnimationBlurEnabled = addConfig("OEChatsTextAnimationBlurEnabled", ConfigItem.configTypeBool, true)
    @JvmField
    val textAnimationBlurDuration = addConfig("OEChatsTextAnimationBlurDuration", ConfigItem.configTypeInt, 300)
    @JvmField
    val textAnimationBlurRadius = addConfig("OEChatsTextAnimationBlurRadius", ConfigItem.configTypeInt, 10)
    @JvmField
    val textAnimationBlurTextDelay = addConfig("OEChatsTextAnimationBlurTextDelay", ConfigItem.configTypeInt, 20)
    @JvmField
    val textAnimationSlideEnabled = addConfig("OEChatsTextAnimationSlideEnabled", ConfigItem.configTypeBool, true)
    @JvmField
    val textAnimationSlideDist = addConfig("OEChatsTextAnimationSlideDist", ConfigItem.configTypeInt, 20)
    @JvmField
    val textAnimationScaleEnabled = addConfig("OEChatsTextAnimationScaleEnabled", ConfigItem.configTypeBool, false)
    @JvmField
    val textAnimationScaleStart = addConfig("OEChatsTextAnimationScaleStart", ConfigItem.configTypeInt, 0)
    @JvmField
    val textAnimationRotateEnabled = addConfig("OEChatsTextAnimationRotateEnabled", ConfigItem.configTypeBool, false)
    @JvmField
    val textAnimationRotateAngle = addConfig("OEChatsTextAnimationRotateAngle", ConfigItem.configTypeInt, -15)
    @JvmField
    val textAnimationDeleteEnabled = addConfig("OEChatsTextAnimationDeleteEnabled", ConfigItem.configTypeBool, true)
    @JvmField
    val textAnimationParticleStyle = addConfig("OEChatsTextAnimationParticleStyle", ConfigItem.configTypeInt, 0)
    @JvmField
    val textAnimationParticleCount = addConfig("OEChatsTextAnimationParticleCount", ConfigItem.configTypeInt, 5)
    @JvmField
    val textAnimationParticleSpeed = addConfig("OEChatsTextAnimationParticleSpeed", ConfigItem.configTypeInt, 50)
    @JvmField
    val textAnimationParticleSpread = addConfig("OEChatsTextAnimationParticleSpread", ConfigItem.configTypeInt, 50)
    @JvmField
    val textAnimationParticleSize = addConfig("OEChatsTextAnimationParticleSize", ConfigItem.configTypeInt, 50)
    @JvmField
    val textAnimationCursorEnabled = addConfig("OEChatsTextAnimationCursorEnabled", ConfigItem.configTypeBool, true)
    @JvmField
    val textAnimationCursorSpeed = addConfig("OEChatsTextAnimationCursorSpeed", ConfigItem.configTypeInt, 25)
    @JvmField
    val textAnimationCursorWidth = addConfig("OEChatsTextAnimationCursorWidth", ConfigItem.configTypeInt, 5)
    @JvmField
    val textAnimationLiquidCursorEnabled = addConfig("OEChatsTextAnimationLiquidCursorEnabled", ConfigItem.configTypeBool, false)
    @JvmField
    val textAnimationLiquidStretch = addConfig("OEChatsTextAnimationLiquidStretch", ConfigItem.configTypeInt, 60)
    @JvmField
    val textAnimationIgnoreSpaces = addConfig("OEChatsTextAnimationIgnoreSpaces", ConfigItem.configTypeBool, true)
    @JvmField
    val textAnimationAnimateAllLines = addConfig("OEChatsTextAnimationAnimateAllLines", ConfigItem.configTypeBool, false)

    // ---- Камера ----

    /**
     * Чем снимать круглые видеосообщения: 0 — системная (Camera1/Camera2 по решению
     * приложения), 1 — Camera2, 2 — CameraX ([app.exteraless.camera.CameraXSession]).
     *
     * exteraGram держит здесь enum CameraType и по умолчанию ставит CameraX на быстрых
     * устройствах. У нас по умолчанию 0: движок CameraX новый, и менять им камеру
     * всем сразу — без спроса — неправильно.
     */
    @JvmField
    val cameraType = addConfig("OEChatsCameraType", ConfigItem.configTypeInt, 0)

    /** Зеркалить фронтальную камеру. Применяется только на CameraX. */
    @JvmField
    val cameraMirrorMode = addConfig("OEChatsCameraMirrorMode", ConfigItem.configTypeBool, true)

    /** Начинать с широкоугольной линзы. Только CameraX. */
    @JvmField
    val startWithWideAngleCamera =
        addConfig("OEChatsStartWithWideAngleCamera", ConfigItem.configTypeBool, false)

    /** Стабилизация видео. */
    @JvmField
    val cameraStabilization = addConfig("OEChatsCameraStabilization", ConfigItem.configTypeBool, false)

    /** Расширенный диапазон FPS. */
    @JvmField
    val extendedFramesPerSecond = addConfig("OEChatsExtendedFps", ConfigItem.configTypeBool, false)

    /** Показывать плашку зума под камерой кружков. */
    @JvmField
    val zoomSlider = addConfig("OEChatsZoomSlider", ConfigItem.configTypeBool, true)

    /** Статичный зум: уровень не сбрасывается после отпускания пальца. */
    @JvmField
    val staticZoom = addConfig("OEChatsStaticZoom", ConfigItem.configTypeBool, false)

    // ---- Фото ----

    /**
     * Тёплость вспышки при записи кружка, проценты (50 = 0.5, как FloatPref(0.5f) exteraGram).
     * ConfigItem не умеет float, поэтому целое.
     */
    @JvmField
    val flashWarmth = addConfig("OEChatsFlashWarmth", ConfigItem.configTypeInt, 50)

    /** Яркость вспышки при записи кружка, проценты (100 = 1.0, FloatPref(1.0f) exteraGram). */
    @JvmField
    val flashIntensity = addConfig("OEChatsFlashIntensity", ConfigItem.configTypeInt, 100)

    @JvmStatic
    fun flashWarmth(): Float = flashWarmth.Int() / 100f

    @JvmStatic
    fun flashIntensity(): Float = flashIntensity.Int() / 100f

    /** Скрепка при наборе остаётся скрепкой, а не «тремя точками» с меню. */
    @JvmField
    val keepAttachButton = addConfig("OEChatsKeepAttachButton", ConfigItem.configTypeBool, false)

    /** Скрыть плитку камеры в шторке вложений. */
    @JvmField
    val hideCameraTile = addConfig("OEChatsHideCameraTile", ConfigItem.configTypeBool, false)

    @JvmField
    val inlineMathResult = addConfig("OEChatsInlineMathResult", ConfigItem.configTypeBool, true)

    @JvmField
    val inlineMathCurrency = addConfig("OEChatsInlineMathCurrency", ConfigItem.configTypeBool, true)

    /**
     * Отправлять фото в высоком качестве по умолчанию. Дефолт true, как в exteraGram.
     * При включённом бейдж на превью инвертируется: помечается не «HD», а «SD» —
     * то есть те кадры, которые уйдут в обычном качестве.
     */
    @JvmField
    val alwaysSendInHD = addConfig("OEChatsAlwaysSendInHD", ConfigItem.configTypeBool, true)

    @JvmField
    val hdrPhotos = addConfig("OEChatsHdrPhotos", ConfigItem.configTypeBool, true)

    // ---- Стикеры и эмодзи ----

    /** Скрывать реакции — каналы / группы / личные (группа-мультивыбор, только UI). */
    @JvmField
    val hideReactionsInChannels = addConfig("OEChatsHideReactionsChannels", ConfigItem.configTypeBool, false)
    @JvmField
    val hideReactionsInGroups = addConfig("OEChatsHideReactionsGroups", ConfigItem.configTypeBool, false)
    @JvmField
    val hideReactionsInPrivate = addConfig("OEChatsHideReactionsPrivate", ConfigItem.configTypeBool, false)

    // ---- Чаты ----

    @JvmField
    val wideChannelPosts = addConfig("OEChatsWideChannelPosts", ConfigItem.configTypeBool, false)

    @JvmField
    val wideFeedPosts = addConfig("OEChatsWideFeedPosts", ConfigItem.configTypeBool, false)

    @JvmField
    val showFeedInChatMenu = addConfig("OEChatsShowFeedInChatMenu", ConfigItem.configTypeBool, true)

    /** Результаты вычислений: показывать результат после «=» и вставлять его пробелом. */
    @JvmField
    val mathResults = addConfig("OEChatsMathResults", ConfigItem.configTypeBool, true)

    /** Убрать «хвостик» пузыря (только UI). */
    @JvmField
    val removeMessageTail = addConfig("OEChatsRemoveMessageTail", ConfigItem.configTypeBool, true)

    /** Показывать результаты опроса до голосования (только UI). */
    @JvmField
    val showResultsBeforeVoting = addConfig("OEChatsShowResultsBeforeVoting", ConfigItem.configTypeBool, false)

    @JvmField
    val hideChannelSearchButton = addConfig("OEChatsHideChannelSearchButton", ConfigItem.configTypeBool, false)

    @JvmField
    val translateInSheet = addConfig("OEChatsTranslateInSheet", ConfigItem.configTypeBool, false)

    @JvmField
    val stripTrackingOnOpen = addConfig("OEChatsStripTrackingOnOpen", ConfigItem.configTypeBool, true)

    @JvmField
    val stripTrackingOnPaste = addConfig("OEChatsStripTrackingOnPaste", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun stripTrackingOnOpen(): Boolean {
        ensureLoaded()
        return stripTrackingOnOpen.Bool()
    }

    @JvmStatic
    fun stripTrackingOnPaste(): Boolean {
        ensureLoaded()
        return stripTrackingOnPaste.Bool()
    }

    // ---- Камера (расширенные) ----

    /** Запоминать последнюю использованную камеру (только UI). */
    @JvmField
    val rememberLastUsedCamera = addConfig("OEChatsRememberLastUsedCamera", ConfigItem.configTypeBool, false)

    // ---- Воспроизведение ----

    /** Предпочитать оригинальное качество (только UI). */
    @JvmField
    val preferOriginalQuality = addConfig("OEChatsPreferOriginalQuality", ConfigItem.configTypeBool, false)

    /**
     * Автопауза — голос / кружки (группа-мультивыбор, только UI).
     * Видео берётся из рабочего [NekoConfig.autoPauseVideo].
     */
    @JvmField
    val pauseOnMinimizeVoice = addConfig("OEChatsPauseVoice", ConfigItem.configTypeBool, false)
    @JvmField
    val pauseOnMinimizeRound = addConfig("OEChatsPauseRound", ConfigItem.configTypeBool, false)

    // ---- Воспроизведение ----

    /** Индекс в [SEEK_DURATIONS]: время перемотки двойным тапом. */
    @JvmField
    val doubleTapSeekDuration = addConfig("OEChatsDoubleTapSeekDuration", ConfigItem.configTypeInt, 1)

    /** Свайп вниз по видео — в режим «картинка в картинке». */
    @JvmField
    val swipeToPip = addConfig("OEChatsSwipeToPip", ConfigItem.configTypeBool, false)

    /** Кнопки громкости снимают звук с видео вместо изменения громкости. */
    @JvmField
    val unmuteWithVolumeButtons = addConfig("OEChatsUnmuteWithVolumeButtons", ConfigItem.configTypeBool, false)

    /** Варианты времени перемотки двойным тапом, секунды. */
    @JvmField
    val SEEK_DURATIONS = intArrayOf(5, 10, 15, 30)

    const val CAMERA_TYPE_SYSTEM = 0
    const val CAMERA_TYPE_CAMERA_2 = 1
    const val CAMERA_TYPE_CAMERA_X = 2
    const val CAMERA_TYPE_TELEGRAM = 3

    // ---- Статические геттеры для горячих мест в Java ----

    /** Кнопки громкости снимают немоту с видео вместо изменения громкости. */
    @JvmStatic
    fun unmuteWithVolumeButtons(): Boolean {
        ensureLoaded()
        return unmuteWithVolumeButtons.Bool()
    }

    /** Ставить голосовое на паузу при сворачивании приложения. */
    @JvmStatic
    fun pauseOnMinimizeVoice(): Boolean {
        ensureLoaded()
        return pauseOnMinimizeVoice.Bool()
    }

    /** Ставить кружок на паузу при сворачивании приложения. */
    @JvmStatic
    fun pauseOnMinimizeRound(): Boolean {
        ensureLoaded()
        return pauseOnMinimizeRound.Bool()
    }

    /** Тип камеры для кружков: 0 системная, 1 Camera2, 2 CameraX, 3 новый рекордер Telegram. */
    @JvmStatic
    fun cameraType(): Int {
        ensureLoaded()
        val type = cameraType.Int()
        return if (type in CAMERA_TYPE_SYSTEM..CAMERA_TYPE_TELEGRAM) type else CAMERA_TYPE_SYSTEM
    }

    @JvmStatic
    fun stickerShape(): Int {
        ensureLoaded()
        return stickerShape.Int()
    }

    @JvmStatic
    fun wideChannelPosts(): Boolean {
        ensureLoaded()
        return wideChannelPosts.Bool()
    }

    @JvmStatic
    fun wideFeedPosts(): Boolean {
        ensureLoaded()
        return wideFeedPosts.Bool()
    }

    @JvmStatic
    fun showFeedInChatMenu(): Boolean {
        ensureLoaded()
        return showFeedInChatMenu.Bool()
    }

    @JvmStatic
    fun seekDurationSeconds(): Int {
        ensureLoaded()
        val index = doubleTapSeekDuration.Int()
        return SEEK_DURATIONS[if (index in SEEK_DURATIONS.indices) index else 1]
    }

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    /** Ленивая инициализация: до готовности ApplicationLoader остаются значения по умолчанию. */
    @JvmStatic
    fun ensureLoaded() {
        if (configLoaded) return
        loadConfig(false)
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

                        ConfigItem.configTypeLong ->
                            item.value = preferences.getLong(item.key, item.defaultValue as Long)

                        ConfigItem.configTypeFloat ->
                            item.value = preferences.getFloat(item.key, item.defaultValue as Float)

                        ConfigItem.configTypeString ->
                            item.value = preferences.getString(item.key, item.defaultValue as String?)
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
            configLoaded = true
        }
        migrateLegacyKeys()
    }

    private fun migrateLegacyKeys() {
        val legacyHidden = getPreferences().getBoolean("DisableChannelMuteButton", false)
        if (legacyHidden && bottomButton.Int() != BOTTOM_BUTTON_HIDE) {
            bottomButton.setConfigInt(BOTTOM_BUTTON_HIDE)
        } else if (!legacyHidden && bottomButton.Int() == BOTTOM_BUTTON_HIDE) {
            NaConfig.disableChannelMuteButton.setConfigBool(true)
        }
        if (NaConfig.hideReactions.Bool()) {
            hideReactionsInChannels.setConfigBool(true)
            hideReactionsInGroups.setConfigBool(true)
            hideReactionsInPrivate.setConfigBool(true)
            NaConfig.hideReactions.setConfigBool(false)
        }
        if (!NaConfig.messageColoredBackground.Bool()) {
            replyBackground.setConfigBool(false)
            NaConfig.messageColoredBackground.setConfigBool(true)
        }
        if (NaConfig.defaultHlsVideoQuality.Int() == 1) {
            preferOriginalQuality.setConfigBool(true)
            NaConfig.defaultHlsVideoQuality.setConfigInt(0)
        }
    }

    /** Сбрасывает настройки экрана «Chats» к значениям по умолчанию. */
    @JvmStatic
    fun reset() {
        synchronized(sync) {
            val editor = getPreferences().edit()
            for (item in configs) {
                editor.remove(item.key)
                item.value = item.defaultValue
            }
            editor.apply()
        }
    }
}
