package app.exteraless.appearance

import android.content.SharedPreferences
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.ui.Components.blur3.GlassOutlineStyle
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.NekoXConfig
import tw.nekomimi.nekogram.config.ConfigItem
import xyz.nextalone.nagram.NaConfig

/**
 * Настройки экрана «Appearance», перенесённые из exteraGram.
 *
 * Здесь живут ТОЛЬКО те настройки, аналогов которых нет в NagramX
 * ([xyz.nextalone.nagram.NaConfig] / [tw.nekomimi.nekogram.NekoConfig]).
 * Всё остальное экран берёт из существующих конфигов, чтобы не плодить дубли.
 *
 * Схема повторяет [app.exteraless.OpenExteraConfig]: те же SharedPreferences, тот же [ConfigItem],
 * собственный список. Ключи с префиксом OEAppearance.
 *
 * Загрузка ленивая: [ensureLoaded] вызывается из геттеров, потому что ApplicationLoader
 * трогать нельзя. После первой загрузки проверка стоит один volatile-read.
 */
object AppearanceConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    /** Максимум слайдера закругления аватарок: радиус = половина стороны, то есть круг. */
    const val AVATAR_CORNERS_MAX = 28

    /** Темы Monet на ролях Telemone — то, чем набор стал 19.08.2026. */
    const val MONET_STYLE_TELEMONE = 0

    /** Прежний набор Monet, пришедший вместе с базой форка (токены вида `a1_100`). */
    const val MONET_STYLE_CLASSIC = 1

    @JvmStatic
    fun getPreferences(): SharedPreferences = NekoConfig.getPreferences()

    // ---- Аватары ----

    /** Закругление аватарок: 0 — квадрат, [AVATAR_CORNERS_MAX] — круг. */
    @JvmField
    val avatarCorners =
        addConfig("OEAppearanceAvatarCorners", ConfigItem.configTypeInt, AVATAR_CORNERS_MAX)

    /** Единое закругление: форумы получают ту же форму аватарки, что и обычные чаты. */
    @JvmField
    val singleCornerRadius =
        addConfig("OEAppearanceSingleCornerRadius", ConfigItem.configTypeBool, false)

    // ---- MD3 Avatar Shapes per chat type ----

    @JvmField
    val avatarShapeUsers =
        addConfig("OEAppearanceAvatarShapeUsers", ConfigItem.configTypeInt, 0)

    @JvmField
    val avatarShapeBots =
        addConfig("OEAppearanceAvatarShapeBots", ConfigItem.configTypeInt, 0)

    @JvmField
    val avatarShapeGroups =
        addConfig("OEAppearanceAvatarShapeGroups", ConfigItem.configTypeInt, 0)

    @JvmField
    val avatarShapeChannels =
        addConfig("OEAppearanceAvatarShapeChannels", ConfigItem.configTypeInt, 0)

    @JvmField
    val avatarShapeSelf =
        addConfig("OEAppearanceAvatarShapeSelf", ConfigItem.configTypeInt, 0)

    /** Отображать группы фотографий/видео как карусель Material 3. */
    @JvmField
    val mediaGroupCarousel =
        addConfig("OEAppearanceMediaGroupCarousel", ConfigItem.configTypeBool, false)

    // ---- Список чатов ----

    /** Мини-аватарки отправителей в списке чатов. */
    @JvmField
    val senderMiniAvatars =
        addConfig("OEAppearanceSenderMiniAvatars", ConfigItem.configTypeBool, true)

    /** Прятать эмодзи-статус рядом с заголовком шапки. Дефолт false, как в exteraGram. */
    @JvmField
    val hideActionBarStatus =
        addConfig("OEAppearanceHideActionBarStatus", ConfigItem.configTypeBool, false)

    /**
     * Текст заголовка списка чатов: 0 — имя приложения, 1 — username, 2 — имя,
     * 3 — «Чаты». Только UI.
     */
    @JvmField
    val titleText =
        addConfig("OEAppearanceTitleText", ConfigItem.configTypeInt, 0)

    /**
     * Какой набор темы Monet берут «Monet Light/Dark/AMOLED».
     *
     * Наборы отличаются словарём токенов, а не механикой: [MONET_STYLE_TELEMONE] собран
     * из ролей Material 3, [MONET_STYLE_CLASSIC] — из тональных палитр базы форка. На части
     * прошивок роли M3 система отдаёт со своими поправками, и второй набор выглядит ровнее.
     */
    @JvmField
    val monetStyle =
        addConfig("OEAppearanceMonetStyle", ConfigItem.configTypeInt, MONET_STYLE_TELEMONE)

    /** Суффикс файла темы для выбранного набора: пустой для Telemone. */
    @JvmStatic
    fun monetAssetSuffix(): String =
        if (monetStyle.Int() == MONET_STYLE_CLASSIC) "_gram" else ""

    // ---- Только UI: аналогов в NagramX нет, визуальный эффект пока не подключён ----

    /**
     * Квадратная («squircle») плавающая кнопка вместо круглой. Дефолт true, как в exteraGram —
     * одно из самых заметных отличий экстеры из коробки.
     * Радиус считается как ceil(size * 16 / 56) dp, то есть 16 dp при кнопке 56 dp.
     */
    @JvmField
    val squareFab = addConfig("OEAppearanceSquareFab", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun squareFab(): Boolean {
        ensureLoaded()
        return squareFab.Bool()
    }

    /** Радиус скругления кнопки со стороной [size] dp. */
    @JvmStatic
    fun fabCornerRadius(size: Int): Int =
        if (squareFab()) Math.ceil((size * 16) / 56.0).toInt() else size / 2

    /** Заголовок ActionBar по центру. Дефолт false, как в exteraGram (BooleanPref(0)). */
    private val centerTitle = addConfig("OEAppearanceCenterTitle", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun centerTitle(): Boolean {
        ensureLoaded()
        return NaConfig.centerActionBarTitle.Bool()
    }

    @JvmStatic
    fun setCenterTitle(value: Boolean) {
        NaConfig.centerActionBarTitle.setConfigBool(value)
        NaConfig.centerActionBarTitleType.setConfigInt(if (value) 1 else 0)
    }

    /** «Gooey»-анимация аватарки при оттягивании шапки профиля. Дефолт true, как в exteraGram. */
    @JvmField
    val gooeyAvatarAnimation =
        addConfig("OEAppearanceGooeyAvatarAnimation", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun gooeyAvatarAnimation(): Boolean {
        ensureLoaded()
        return gooeyAvatarAnimation.Bool()
    }

    /**
     * Индивидуальные темы и обои в чатах.
     *
     * Выключено — приложение не применяет тему и обои, выставленные для
     * конкретного диалога, и везде остаётся общая тема. exteraGram гейтит этим
     * ключом две точки: ChatActivity.setupChatTheme (12.9.0:12501) и
     * ChatThemeController.getDialogWallpaper (12.9.0:950).
     *
     * По умолчанию включено — как в exteraGram: иначе обновление приложения
     * молча погасило бы у всех уже настроенные темы чатов.
     */
    @JvmField
    val customThemes =
        addConfig("OEAppearanceCustomThemes", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun customThemes(): Boolean {
        ensureLoaded()
        return customThemes.Bool()
    }

    /** Радиус карточек-секций, dp. Применяется ко всем спискам через RecyclerListView.setSections(). */
    @JvmField
    val sectionRadius =
        addConfig("OEAppearanceSectionRadius", ConfigItem.configTypeInt, 20)

    /** Отдельные заголовки секций. Только UI. */
    @JvmField
    val separateHeaders =
        addConfig("OEAppearanceSeparateHeaders", ConfigItem.configTypeBool, true)

    /** Стиль разделителя: 0 — скрыт, 1 — линия, 2 — сегменты. 0/1 привязаны к NaConfig.hideDividers, 2 — только UI. */
    @JvmField
    val dividerStyle =
        addConfig("OEAppearanceDividerStyle", ConfigItem.configTypeInt, 1)

    /** Стиль стеклянного контура: 0 — блик, 1 — сплошной, 2 — скрыт. Только UI. */
    @JvmField
    val glassOutlineStyle =
        addConfig(object : ConfigItem("OEAppearanceGlassOutlineStyle", ConfigItem.configTypeInt, 0) {
            override fun setConfigInt(v: Int) {
                val changed = Int() != v
                super.setConfigInt(v)
                if (changed) GlassOutlineStyle.dispatchChange()
            }
        })

    /** Стеклянное меню сообщения. Дефолт true, как в exteraGram (BooleanPref(1)). */
    @JvmField
    val glassMessageMenu =
        addConfig("OEAppearanceGlassMessageMenu", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun glassMessageMenu(): Boolean {
        ensureLoaded()
        return glassMessageMenu.Bool()
    }

    @JvmStatic
    fun glassOutlineStyle(): Int {
        ensureLoaded()
        return glassOutlineStyle.Int()
    }

    // ---- Папки-чипсы Material 3 ----

    @JvmField
    val chipFoldersEnabled = addConfig("OEAppearanceChipFoldersEnabled", ConfigItem.configTypeBool, false)
    @JvmField
    val chipFoldersStyle = addConfig("OEAppearanceChipFoldersStyle", ConfigItem.configTypeInt, 0)
    @JvmField
    val chipFoldersShape = addConfig("OEAppearanceChipFoldersShape", ConfigItem.configTypeInt, 5)
    @JvmField
    val chipFoldersSize = addConfig("OEAppearanceChipFoldersSize", ConfigItem.configTypeInt, 1)
    @JvmField
    val chipFoldersSpacing = addConfig("OEAppearanceChipFoldersSpacing", ConfigItem.configTypeInt, 1)
    @JvmField
    val chipFoldersAnim = addConfig("OEAppearanceChipFoldersAnim", ConfigItem.configTypeInt, 0)
    @JvmField
    val chipFoldersAnimSpeed = addConfig("OEAppearanceChipFoldersAnimSpeed", ConfigItem.configTypeInt, 100)
    @JvmField
    val chipFoldersMd3Colors = addConfig("OEAppearanceChipFoldersMd3Colors", ConfigItem.configTypeBool, true)
    @JvmField
    val chipFoldersScrollDivider = addConfig("OEAppearanceChipFoldersScrollDivider", ConfigItem.configTypeBool, true)
    @JvmField
    val chipFoldersRadiusActive = addConfig("OEAppearanceChipFoldersRadiusActive", ConfigItem.configTypeInt, 20)
    @JvmField
    val chipFoldersRadiusInactive = addConfig("OEAppearanceChipFoldersRadiusInactive", ConfigItem.configTypeInt, 20)
    @JvmField
    val chipFoldersRadiusCustomActive = addConfig("OEAppearanceChipFoldersRadiusCustomActive", ConfigItem.configTypeInt, 11)
    @JvmField
    val chipFoldersRadiusOuter = addConfig("OEAppearanceChipFoldersRadiusOuter", ConfigItem.configTypeInt, 20)
    @JvmField
    val chipFoldersRadiusInner = addConfig("OEAppearanceChipFoldersRadiusInner", ConfigItem.configTypeInt, 8)
    @JvmField
    val chipFoldersRadiusAltActive = addConfig("OEAppearanceChipFoldersRadiusAltActive", ConfigItem.configTypeInt, 20)
    @JvmField
    val chipFoldersRadiusAltOuter = addConfig("OEAppearanceChipFoldersRadiusAltOuter", ConfigItem.configTypeInt, 20)
    @JvmField
    val chipFoldersCustomHeight = addConfig("OEAppearanceChipFoldersCustomHeight", ConfigItem.configTypeInt, 48)
    @JvmField
    val chipFoldersCustomSpacing = addConfig("OEAppearanceChipFoldersCustomSpacing", ConfigItem.configTypeInt, 6)
    @JvmField
    val chipFoldersBarBottomPadding = addConfig("OEAppearanceChipFoldersBarBottomPadding", ConfigItem.configTypeInt, 0)
    @JvmField
    val chipFoldersListTopPadding = addConfig("OEAppearanceChipFoldersListTopPadding", ConfigItem.configTypeInt, 5)
    @JvmField
    val chipFoldersColorActive = addConfig("OEAppearanceChipFoldersColorActive", ConfigItem.configTypeInt, 0)
    @JvmField
    val chipFoldersColorInactive = addConfig("OEAppearanceChipFoldersColorInactive", ConfigItem.configTypeInt, 0)
    @JvmField
    val chipFoldersColorTextActive = addConfig("OEAppearanceChipFoldersColorTextActive", ConfigItem.configTypeInt, 0)
    @JvmField
    val chipFoldersColorTextInactive = addConfig("OEAppearanceChipFoldersColorTextInactive", ConfigItem.configTypeInt, 0)

    // ---- Material Design 3 ----
    // switchStyle и sliderStyle уже есть у NagramX (NaConfig, дефолт 2 = MD3) — не дублируем.

    /** M3-индикаторы загрузки. Дефолт true, как в exteraGram. */
    @JvmField
    val newLoadingStyle =
        addConfig("OEAppearanceNewLoadingStyle", ConfigItem.configTypeBool, true)

    /** M3-шапка чата. Дефолт false, как в exteraGram. */
    @JvmField
    val newChatHeaderStyle =
        addConfig("OEAppearanceNewChatHeaderStyle", ConfigItem.configTypeBool, false)

    /** M3-нижняя панель вкладок. Дефолт false, как в exteraGram. */
    @JvmField
    val newNavigationBarStyle =
        addConfig("OEAppearanceNewNavigationBarStyle", ConfigItem.configTypeBool, false)

    @JvmField
    val m3ListItems =
        addConfig("OEAppearanceM3ListItems", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun m3ListItems(): Boolean {
        ensureLoaded()
        return m3ListItems.Bool()
    }

    @JvmStatic
    fun newLoadingStyle(): Boolean {
        ensureLoaded()
        return newLoadingStyle.Bool()
    }

    @JvmStatic
    fun newChatHeaderStyle(): Boolean {
        ensureLoaded()
        return newChatHeaderStyle.Bool()
    }

    @JvmStatic
    fun newNavigationBarStyle(): Boolean {
        ensureLoaded()
        return newNavigationBarStyle.Bool()
    }

    @JvmField
    val md3Player =
        addConfig("OEAppearanceMd3Player", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun md3Player(): Boolean {
        ensureLoaded()
        return md3Player.Bool()
    }

    @JvmField
    val md3MiniPlayer =
        addConfig("OEAppearanceMd3MiniPlayer", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun md3MiniPlayer(): Boolean {
        ensureLoaded()
        return md3MiniPlayer.Bool()
    }

    @JvmField
    val lrclibAllowed =
        addConfig("OEPlayerLrclibAllowed", ConfigItem.configTypeBool, false)

    // ---- Расширенные настройки плеера (MetroList / Material 3) ----

    /** Стиль фона плеера: 0 — Следовать теме / Цвет обложки, 1 — Размытая обложка, 2 — Градиент. */
    @JvmField
    val playerBackgroundStyle =
        addConfig("OEAppearancePlayerBackgroundStyle", ConfigItem.configTypeInt, 0)

    @JvmStatic
    fun playerBackgroundStyle(): Int {
        ensureLoaded()
        return playerBackgroundStyle.Int()
    }

    /** Стиль ползунка: 0 — Волнистый (M3/Android 13+), 1 — Прямой. */
    @JvmField
    val playerSeekbarStyle =
        addConfig("OEAppearancePlayerSeekbarStyle", ConfigItem.configTypeInt, 0)

    @JvmStatic
    fun playerSeekbarStyle(): Int {
        ensureLoaded()
        return playerSeekbarStyle.Int()
    }

    /** Стиль кнопок плеера: 0 — Цвета обложки (динамический), 1 — Акцент темы (Monet). */
    @JvmField
    val playerColorStyle =
        addConfig("OEAppearancePlayerColorStyle", ConfigItem.configTypeInt, 0)

    @JvmStatic
    fun playerColorStyle(): Int {
        ensureLoaded()
        return playerColorStyle.Int()
    }

    /** Свайп по обложке влево/вправо для переключения треков. */
    @JvmField
    val playerSwipeTrack =
        addConfig("OEAppearancePlayerSwipeTrack", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerSwipeTrack(): Boolean {
        ensureLoaded()
        return playerSwipeTrack.Bool()
    }

    /** Обрезать обложку под квадратное соотношение (квадрат с радиусом). */
    @JvmField
    val playerCropCover =
        addConfig("OEAppearancePlayerCropCover", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerCropCover(): Boolean {
        ensureLoaded()
        return playerCropCover.Bool()
    }

    /** Не выключать экран при развернутом плеере (FLAG_KEEP_SCREEN_ON). */
    @JvmField
    val playerKeepScreenOn =
        addConfig("OEAppearancePlayerKeepScreenOn", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerKeepScreenOn(): Boolean {
        ensureLoaded()
        return playerKeepScreenOn.Bool()
    }

    const val COVER_SHAPE_DISABLED = -1
    const val COVER_SHAPE_CYCLE = -2
    const val COVER_SHAPE_RANDOM = -3
    const val COVER_SHAPE_FOLLOW = -4

    /** Форма обложки альбома в плеере: -1 (выключено), -2 (циклически), -3 (случайно) или ID фигуры >= 0. */
    @JvmField
    val playerCoverShape =
        addConfig("OEAppearancePlayerCoverShape", ConfigItem.configTypeInt, COVER_SHAPE_CYCLE)

    @JvmStatic
    fun playerCoverShape(): Int {
        ensureLoaded()
        return playerCoverShape.Int()
    }

    /** Форма обложки в мини-плеере: -4 (как в основном плеере), -1 (выключено), -2 (циклически), -3 (случайно) или ID фигуры >= 0. */
    @JvmField
    val playerMiniCoverShape =
        addConfig("OEAppearancePlayerMiniCoverShape", ConfigItem.configTypeInt, COVER_SHAPE_FOLLOW)

    @JvmStatic
    fun playerMiniCoverShape(): Int {
        ensureLoaded()
        return playerMiniCoverShape.Int()
    }

    /** Заменять обложку на видео при обнаружении (Canvas). */
    @JvmField
    val playerVideoCover =
        addConfig("OEAppearancePlayerVideoCover", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerVideoCover(): Boolean {
        ensureLoaded()
        return playerVideoCover.Bool()
    }

    /** Останавливать воспроизведение при отключении звука (громкость 0). */
    @JvmField
    val playerPauseOnMute =
        addConfig("OEAppearancePlayerPauseOnMute", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerPauseOnMute(): Boolean {
        ensureLoaded()
        return playerPauseOnMute.Bool()
    }

    /** Возобновлять воспроизведение при подключении Bluetooth. */
    @JvmField
    val playerResumeOnBluetooth =
        addConfig("OEAppearancePlayerResumeOnBluetooth", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerResumeOnBluetooth(): Boolean {
        ensureLoaded()
        return playerResumeOnBluetooth.Bool()
    }

    /** Плавный переход (кроссфейд) между музыкальными треками. */
    @JvmField
    val playerCrossfade =
        addConfig("OEAppearancePlayerCrossfade", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerCrossfade(): Boolean {
        ensureLoaded()
        return playerCrossfade.Bool()
    }

    /** Длительность кроссфейда в секундах (1..12, по умолчанию 5). */
    @JvmField
    val playerCrossfadeDuration =
        addConfig("OEAppearancePlayerCrossfadeDuration", ConfigItem.configTypeInt, 5)

    @JvmStatic
    fun playerCrossfadeDuration(): Int {
        ensureLoaded()
        val d = playerCrossfadeDuration.Int()
        return if (d in 1..12) d else 5
    }

    /** Размер шрифта текста песни: 0 — Нормальный (22sp), 1 — Крупный (26sp), 2 — Огромный (32sp). */
    @JvmField
    val playerLyricsTextSize =
        addConfig("OEAppearancePlayerLyricsTextSize", ConfigItem.configTypeInt, 1)

    @JvmStatic
    fun playerLyricsTextSize(): Int {
        ensureLoaded()
        return playerLyricsTextSize.Int()
    }

    /** Размытие неактивных строк текста (Blur inactive lines, эффект караоке). */
    @JvmField
    val playerLyricsBlur =
        addConfig("OEAppearancePlayerLyricsBlur", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsBlur(): Boolean {
        ensureLoaded()
        return playerLyricsBlur.Bool()
    }

    /** Автоматическая прокрутка текста песни к активной строке. */
    @JvmField
    val playerLyricsAutoScroll =
        addConfig("OEAppearancePlayerLyricsAutoScroll", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsAutoScroll(): Boolean {
        ensureLoaded()
        return playerLyricsAutoScroll.Bool()
    }

    /** Экспериментальный текст (MetroList / Enhanced animation). */
    @JvmField
    val playerLyricsExperimental =
        addConfig("OEAppearancePlayerLyricsExperimental", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsExperimental(): Boolean {
        ensureLoaded()
        return playerLyricsExperimental.Bool()
    }

    /** Расположение текста: 0 — По центру, 1 — Слева. */
    @JvmField
    val playerLyricsAlignment =
        addConfig("OEAppearancePlayerLyricsAlignment", ConfigItem.configTypeInt, 0)

    @JvmStatic
    fun playerLyricsAlignment(): Int {
        ensureLoaded()
        return playerLyricsAlignment.Int()
    }

    /** Разделять по ролям (бэк-вокал). */
    @JvmField
    val playerLyricsSplitRoles =
        addConfig("OEAppearancePlayerLyricsSplitRoles", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsSplitRoles(): Boolean {
        ensureLoaded()
        return playerLyricsSplitRoles.Bool()
    }

    /** Менять текст песни по касанию (Seek to line on tap). */
    @JvmField
    val playerLyricsTapToSeek =
        addConfig("OEAppearancePlayerLyricsTapToSeek", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsTapToSeek(): Boolean {
        ensureLoaded()
        return playerLyricsTapToSeek.Bool()
    }

    /** Скрывать строку состояния в полноэкранном режиме текста. */
    @JvmField
    val playerLyricsHideStatusBar =
        addConfig("OEAppearancePlayerLyricsHideStatusBar", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsHideStatusBar(): Boolean {
        ensureLoaded()
        return playerLyricsHideStatusBar.Bool()
    }

    /** Романизация текста (транслитерация кириллицы/иероглифов). */
    @JvmField
    val playerLyricsRomanize =
        addConfig("OEAppearancePlayerLyricsRomanize", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerLyricsRomanize(): Boolean {
        ensureLoaded()
        return playerLyricsRomanize.Bool()
    }

    // ---- Провайдеры текстов (MetroList) ----

    @JvmField
    val playerLyricsLrcLib =
        addConfig("OEAppearancePlayerLyricsLrcLib", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsLrcLib(): Boolean {
        ensureLoaded()
        return playerLyricsLrcLib.Bool()
    }

    @JvmField
    val playerLyricsKuGou =
        addConfig("OEAppearancePlayerLyricsKuGou", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsKuGou(): Boolean {
        ensureLoaded()
        return playerLyricsKuGou.Bool()
    }

    @JvmField
    val playerLyricsBetterLyrics =
        addConfig("OEAppearancePlayerLyricsBetterLyrics", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsBetterLyrics(): Boolean {
        ensureLoaded()
        return playerLyricsBetterLyrics.Bool()
    }

    @JvmField
    val playerLyricsPaxsenix =
        addConfig("OEAppearancePlayerLyricsPaxsenix", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsPaxsenix(): Boolean {
        ensureLoaded()
        return playerLyricsPaxsenix.Bool()
    }

    @JvmField
    val playerLyricsLyricsPlus =
        addConfig("OEAppearancePlayerLyricsLyricsPlus", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun playerLyricsLyricsPlus(): Boolean {
        ensureLoaded()
        return playerLyricsLyricsPlus.Bool()
    }

    @JvmField
    val playerLyricsZemer =
        addConfig("OEAppearancePlayerLyricsZemer", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerLyricsZemer(): Boolean {
        ensureLoaded()
        return playerLyricsZemer.Bool()
    }

    @JvmField
    val playerLyricsProviderOrder =
        addConfig("OEAppearancePlayerLyricsProviderOrder", ConfigItem.configTypeString, "BetterLyrics,LrcLib,Paxsenix,LyricsPlus,KuGou,Zemer")

    @JvmStatic
    fun playerLyricsProviderOrder(): String {
        ensureLoaded()
        val s = playerLyricsProviderOrder.String()
        return if (s.isNullOrBlank()) "BetterLyrics,LrcLib,Paxsenix,LyricsPlus,KuGou,Zemer" else s
    }

    @JvmStatic
    fun hasOnlineLyricsProvider(): Boolean {
        ensureLoaded()
        return playerLyricsLrcLib.Bool() ||
                playerLyricsBetterLyrics.Bool() ||
                playerLyricsPaxsenix.Bool() ||
                playerLyricsLyricsPlus.Bool() ||
                playerLyricsKuGou.Bool() ||
                playerLyricsZemer.Bool()
    }

    // ---- Перевод текста песни (привязан к переводчику чатов) ----

    @JvmField
    val playerLyricsTranslate =
        addConfig("OEAppearancePlayerLyricsTranslate", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerLyricsTranslate(): Boolean {
        ensureLoaded()
        return playerLyricsTranslate.Bool()
    }

    @JvmField
    val profileMusicCard =
        addConfig("OEAppearanceProfileMusicCard", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun profileMusicCard(): Boolean {
        ensureLoaded()
        return profileMusicCard.Bool()
    }

    /** Широкая нижняя панель как в Telegram iOS. Уступает M3-панели, если включены обе. */
    @JvmField
    val iosNavigationBarStyle =
        addConfig("OEAppearanceIosNavigationBarStyle", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun iosNavigationBarStyle(): Boolean {
        ensureLoaded()
        return iosNavigationBarStyle.Bool()
    }

    @JvmField
    val iosFirstFolderOnTabTap =
        addConfig("OEAppearanceIosFirstFolderOnTabTap", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun iosFirstFolderOnTabTap(): Boolean {
        ensureLoaded()
        return iosFirstFolderOnTabTap.Bool()
    }

    @JvmField
    val iosChatHeader =
        addConfig("OEAppearanceIosChatHeader", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun iosChatHeader(): Boolean {
        ensureLoaded()
        return iosChatHeader.Bool()
    }

    // ---- AI-функции Telegram ----

    /** Прячет кнопку AI-редактора в поле ввода, вложениях и подписи к медиа. */
    @JvmField
    val hideAiEditor =
        addConfig("OEAppearanceHideAiEditor", ConfigItem.configTypeBool, false)

    /** Прячет кнопку «саммари» на сообщении. */
    @JvmField
    val hideMessageSummary =
        addConfig("OEAppearanceHideMessageSummary", ConfigItem.configTypeBool, false)

    /** Прячет блок «Cocoon AI Summary» в Instant View. */
    @JvmField
    val hideIvSummary =
        addConfig("OEAppearanceHideIvSummary", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun hideAiEditor(): Boolean {
        ensureLoaded()
        return hideAiEditor.Bool()
    }

    @JvmStatic
    fun hideMessageSummary(): Boolean {
        ensureLoaded()
        return hideMessageSummary.Bool()
    }

    @JvmStatic
    fun hideIvSummary(): Boolean {
        ensureLoaded()
        return hideIvSummary.Bool()
    }

    // ---- Боковое меню ----

    /** Своя шторка бокового меню вместо стоковой. Дефолт false, как в exteraGram. */
    @JvmField
    val navigationDrawer =
        addConfig("OEAppearanceNavigationDrawer", ConfigItem.configTypeBool, false)

    /** Иммерсивная анимация открытия шторки. Дефолт false, как в exteraGram. */
    @JvmField
    val immersiveDrawerAnimation =
        addConfig("OEAppearanceImmersiveDrawer", ConfigItem.configTypeBool, false)

    /** Порядок и видимость пунктов бокового меню, сериализованный список id. */
    @JvmField
    val mainMenuLayout =
        addConfig("OEAppearanceMainMenuLayout", ConfigItem.configTypeString, "")

    // ---- Лента ----

    /**
     * Показывать ленту нижней вкладкой — на месте вкладки «Контакты».
     * Дефолт false, как в exteraGram (BooleanPref(0)).
     */
    @JvmField
    val showFeedTab = addConfig("OEAppearanceShowFeedTab", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun showFeedTab(): Boolean {
        ensureLoaded()
        return showFeedTab.Bool()
    }

    /** Счётчик непрочитанных постов на вкладке ленты. */
    @JvmField
    val showFeedUnreadCounter =
        addConfig("OEAppearanceFeedUnreadCounter", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun showFeedUnreadCounter(): Boolean {
        ensureLoaded()
        return showFeedUnreadCounter.Bool()
    }

    @JvmStatic
    fun navigationDrawer(): Boolean {
        ensureLoaded()
        return navigationDrawer.Bool()
    }

    @JvmStatic
    fun immersiveDrawerAnimation(): Boolean {
        ensureLoaded()
        return immersiveDrawerAnimation.Bool()
    }

    // ---- Геттеры для Java (в том числе для горячих мест отрисовки) ----

    @JvmStatic
    fun avatarCorners(): Int {
        ensureLoaded()
        return avatarCorners.Int()
    }

    @JvmStatic
    fun singleCornerRadius(): Boolean {
        ensureLoaded()
        return singleCornerRadius.Bool()
    }

    @JvmStatic
    fun avatarShapeUsers(): Int {
        ensureLoaded()
        return avatarShapeUsers.Int()
    }

    @JvmStatic
    fun avatarShapeBots(): Int {
        ensureLoaded()
        return avatarShapeBots.Int()
    }

    @JvmStatic
    fun avatarShapeGroups(): Int {
        ensureLoaded()
        return avatarShapeGroups.Int()
    }

    @JvmStatic
    fun avatarShapeChannels(): Int {
        ensureLoaded()
        return avatarShapeChannels.Int()
    }

    @JvmStatic
    fun avatarShapeSelf(): Int {
        ensureLoaded()
        return avatarShapeSelf.Int()
    }

    @JvmStatic
    fun mediaGroupCarousel(): Boolean {
        ensureLoaded()
        return mediaGroupCarousel.Bool()
    }

    @JvmStatic
    fun getAvatarShape(peerType: Int): Int {
        ensureLoaded()
        return when (peerType) {
            Md3AvatarShapes.PEER_TYPE_BOT -> avatarShapeBots.Int()
            Md3AvatarShapes.PEER_TYPE_GROUP -> avatarShapeGroups.Int()
            Md3AvatarShapes.PEER_TYPE_CHANNEL -> avatarShapeChannels.Int()
            Md3AvatarShapes.PEER_TYPE_SELF -> avatarShapeSelf.Int()
            else -> avatarShapeUsers.Int()
        }
    }

    @JvmStatic
    fun hasAnyCustomAvatarShape(): Boolean {
        ensureLoaded()
        return avatarShapeUsers.Int() != 0 ||
                avatarShapeBots.Int() != 0 ||
                avatarShapeGroups.Int() != 0 ||
                avatarShapeChannels.Int() != 0 ||
                avatarShapeSelf.Int() != 0
    }

    @JvmStatic
    fun senderMiniAvatars(): Boolean {
        ensureLoaded()
        return senderMiniAvatars.Bool()
    }

    /** Текст заголовка списка чатов: 0 — имя приложения, 1 — username, 2 — имя, 3 — «Чаты». */
    @JvmStatic
    fun titleText(): Int {
        ensureLoaded()
        return titleText.Int()
    }

    const val TITLE_TEXT_CUSTOM = 4

    private const val TITLE_TEXT_MIGRATED = "OEAppearanceTitleTextMigrated"

    @JvmStatic
    fun titleTextScrolls(): Boolean = when (titleText()) {
        1, 2, TITLE_TEXT_CUSTOM -> true
        else -> false
    }

    @JvmStatic
    fun folderNameAsTitle(): Boolean = NekoConfig.tabsTitleType.Int() == NekoXConfig.TITLE_TYPE_ICON

    /** Прятать ли эмодзи-статус в шапке списка чатов. */
    @JvmStatic
    fun hideActionBarStatus(): Boolean {
        ensureLoaded()
        return hideActionBarStatus.Bool()
    }

    /** true, если аватарки должны остаться обычными кругами — быстрый выход из хот-пути. */
    @JvmStatic
    fun avatarCornersDefault(): Boolean = avatarCorners() >= AVATAR_CORNERS_MAX

    /**
     * Квадратность аватарки: 0 — круг, 1 — квадрат.
     *
     * Обратная величина к [avatarCorners] и ровно то, что exteraGram зовёт
     * `getAvatarSquareness()`. Нужна там, где геометрия зависит от формы —
     * например, онлайн-точку на квадратной аватарке надо уводить в угол,
     * иначе она наползает на картинку.
     */
    @JvmStatic
    fun avatarSquareness(): Float =
        1f - (avatarCorners().coerceIn(0, AVATAR_CORNERS_MAX).toFloat() / AVATAR_CORNERS_MAX)

    /**
     * Смещение онлайн-точки от края аватарки.
     * У круга — заданное значение, у квадрата — по диагонали от угла.
     */
    @JvmStatic
    fun onlineDotOffset(base: Float, radius: Float): Float =
        base + ((radius / Math.sqrt(2.0)).toFloat() - base) * avatarSquareness()

    // ---- Секции настроек ----

    /** Радиус скругления карточек-секций, dp. 0 — острые углы (как сток). */
    @JvmStatic
    fun sectionRadius(): Int {
        ensureLoaded()
        return sectionRadius.Int()
    }

    /** Отдельные заголовки секций (заголовок — своя карточка). */
    @JvmStatic
    fun separateHeaders(): Boolean {
        ensureLoaded()
        return separateHeaders.Bool()
    }

    /**
     * Выносить ли заголовки секций из карточки — с учётом стиля разделителя.
     *
     * В режиме «Сегменты» каждая строка сама по себе карточка, и заголовок
     * внутри неё выглядел бы чужеродно, поэтому exteraGram включает вынос
     * принудительно (ExteraConfig.getSectionsSeparatedHeaders: SEGMENTS || pref).
     */
    @JvmStatic
    fun sectionsSeparatedHeaders(): Boolean {
        return separateHeaders() || dividerStyle() == DIVIDER_SEGMENTS || m3ListItems()
    }

    @JvmStatic
    fun sectionsSeparatedHeadersForced(): Boolean {
        return dividerStyle() == DIVIDER_SEGMENTS || m3ListItems()
    }

    /** Стиль разделителя внутри карточки: 0 — скрыт, 1 — линия, 2 — сегменты. */
    @JvmStatic
    fun dividerStyle(): Int {
        ensureLoaded()
        return dividerStyle.Int()
    }

    const val DIVIDER_HIDDEN = 0
    const val DIVIDER_LINE = 1
    const val DIVIDER_SEGMENTS = 2

    /**
     * Кэш для [dividerHidden]. `Theme.getColor` — самый горячий путь отрисовки,
     * ходить туда через [ensureLoaded] на каждый вызов нельзя.
     * Обновляется из [invalidateDividerStyle] при смене настройки.
     */
    @Volatile
    private var dividerHiddenCache: Boolean? = null

    /**
     * Гасить ли `key_divider` целиком. У exteraGram прозрачный цвет отдаётся во всех режимах,
     * кроме [DIVIDER_LINE] — поэтому стоковые `drawLine(..., Theme.dividerPaint)`
     * перестают рисовать без правки самих ячеек
     */
    @JvmStatic
    fun dividerHidden(): Boolean {
        val cached = dividerHiddenCache
        if (cached != null) return cached
        val value = try {
            dividerStyle() != DIVIDER_LINE || m3ListItems()
        } catch (e: Exception) {
            false
        }
        dividerHiddenCache = value
        return value
    }

    /** Звать при смене стиля разделителя. */
    @JvmStatic
    fun invalidateDividerStyle() {
        dividerHiddenCache = null
    }

    /**
     * Радиус закругления для аватарки со стороной [size] пикселей.
     * При максимуме слайдера возвращает size / 2, то есть круг.
     */
    @JvmStatic
    @JvmOverloads
    fun getAvatarCorners(size: Float, cornerType: Int = CORNER_TYPE_DEFAULT, hasStories: Boolean = false): Int {
        val corners = avatarCorners()
        if (corners <= 0) return 0
        var value = size * corners / (AVATAR_CORNERS_MAX * 2.0)
        if (hasStories) {
            value -= AndroidUtilities.dpf2(2.5f)
        }
        if (!singleCornerRadius()) {
            when (cornerType) {
                CORNER_TYPE_FORUM -> value = value.toInt() * 42.0 / 64.0
                CORNER_TYPE_COMMUNITY -> value = value * 40.0 / 72.0
            }
        }
        if (value <= 0) return 0
        return Math.ceil(value).toInt()
    }

    const val CORNER_TYPE_DEFAULT = 0
    const val CORNER_TYPE_FORUM = 1
    const val CORNER_TYPE_COMMUNITY = 2

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    private fun addConfig(item: ConfigItem): ConfigItem {
        configs.add(item)
        return item
    }

    @JvmStatic
    fun ensureLoaded() {
        if (!configLoaded) loadConfig(false)
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
        val legacyHidden = getPreferences().getBoolean("HideDividers", false)
        if (legacyHidden && dividerStyle.Int() != DIVIDER_HIDDEN) {
            dividerStyle.setConfigInt(DIVIDER_HIDDEN)
        } else if (!legacyHidden && dividerStyle.Int() == DIVIDER_HIDDEN) {
            NaConfig.hideDividers.setConfigBool(true)
        }
        migrateLegacyTitleName()
        migrateCustomTitle()
        migrateCenterTitle()
        migrateIosChatHeader()
        migrateModernStyles()
        migrateDecorations()
    }

    private fun migrateCenterTitle() {
        if (centerTitle.Bool()) {
            setCenterTitle(true)
            centerTitle.setConfigBool(false)
        }
        val type = NaConfig.centerActionBarTitleType.Int()
        if (type == 2 || type == 3) {
            NaConfig.centerActionBarTitleType.setConfigInt(1)
        }
    }

    private fun migrateIosChatHeader() {
        if (getPreferences().contains(iosChatHeader.key)) return
        iosChatHeader.setConfigBool(NaConfig.centerActionBarTitle.Bool())
    }

    private fun migrateModernStyles() {
        if (NaConfig.switchStyle.Int() == 1) {
            NaConfig.switchStyle.setConfigInt(2)
        }
        if (NaConfig.sliderStyle.Int() == 1) {
            NaConfig.sliderStyle.setConfigInt(2)
        }
    }

    private fun migrateDecorations() {
        val actionBar = NekoConfig.actionBarDecoration.Int()
        val chat = NaConfig.chatDecoration.Int()
        val snow = if (actionBar == 1 || chat == 1) 1 else 0
        if (actionBar != snow) {
            NekoConfig.actionBarDecoration.setConfigInt(snow)
        }
        if (chat != snow) {
            NaConfig.chatDecoration.setConfigInt(snow)
        }
    }

    private const val LEGACY_DEFAULT_TITLE = "Nagram X"

    private fun migrateLegacyTitleName() {
        if (NaConfig.customTitle.String() != LEGACY_DEFAULT_TITLE) return
        NaConfig.customTitle.setConfigString(NaConfig.customTitle.defaultValue as String)
        if (titleText.Int() == TITLE_TEXT_CUSTOM) {
            titleText.setConfigInt(0)
        }
    }

    private fun migrateCustomTitle() {
        val preferences = getPreferences()
        if (preferences.getBoolean(TITLE_TEXT_MIGRATED, false)) return
        if (titleText.Int() == 0) {
            if (NaConfig.customTitleUserName.Bool()) {
                titleText.setConfigInt(2)
            } else if (NaConfig.customTitle.String() != NaConfig.customTitle.defaultValue) {
                titleText.setConfigInt(TITLE_TEXT_CUSTOM)
            }
        }
        NaConfig.customTitleUserName.setConfigBool(false)
        preferences.edit().putBoolean(TITLE_TEXT_MIGRATED, true).apply()
    }

    /** Сбрасывает настройки экрана Appearance к значениям по умолчанию. */
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
        GlassOutlineStyle.dispatchChange()
    }
}
