package app.exteraless.drawer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.exteraless.appearance.AppearanceConfig;

import org.telegram.ui.MainTabsLayout;

/**
 * Порядок и видимость пунктов главного меню.
 *
 * exteraGram хранит два JSON-массива, {@code mainMenuLayout} и {@code mainMenuHiddenItems}
 * ({@code com/exteragram/messenger/ExteraConfig.java:1333-1380}, сохранение — {@code :1474}).
 * У нас координатор завёл один строковый ключ {@code OEAppearanceMainMenuLayout}, поэтому оба
 * списка лежат в нём: {@code "видимые;скрытые"}, id через запятую. Пустая строка — «настройку
 * не трогали», тогда берётся дефолт из {@link #getDefaultLayout()}.
 *
 * Формат намеренно текстовый: его читает и пишет ещё и экран-редактор пунктов.
 */
public final class MainMenuLayout {

    private static final String SECTION_SEPARATOR = ";";
    private static final String ID_SEPARATOR = ",";

    private MainMenuLayout() {
    }

    /**
     * Дефолтная раскладка: состав зависит от того, скрыта ли нижняя панель. Когда она
     * видна, «Профиль», «Контакты» и «Настройки» уже лежат во вкладках и в меню не
     * дублируются. Порядок пунктов — как в {@code ExteraConfig.getDefaultMainMenuLayout()}.
     */
    public static List<Integer> getDefaultLayout() {
        final boolean bottomBarHidden = isBottomNavigationBarHidden();
        final ArrayList<Integer> layout = new ArrayList<>();
        layout.add(MainMenuItem.ARCHIVE.getId());
        if (bottomBarHidden) {
            layout.add(MainMenuItem.PROFILE.getId());
        }
        layout.add(MainMenuItem.NEW_GROUP.getId());
        if (bottomBarHidden) {
            layout.add(MainMenuItem.CONTACTS.getId());
        }
        layout.add(MainMenuItem.SAVED.getId());
        layout.add(MainMenuItem.WALLET.getId());
        layout.add(MainMenuItem.FEED.getId());
        layout.add(MainMenuItem.BOTS.getId());
        if (bottomBarHidden) {
            layout.add(MainMenuItem.SETTINGS.getId());
        }
        return layout;
    }

    /** Видимые пункты в порядке показа. Разделители ({@link MainMenuItem#DIVIDER}) тоже здесь. */
    public static List<Integer> getLayout() {
        return parse()[0];
    }

    /** Спрятанные пункты — их показывает только экран-редактор. */
    public static List<Integer> getHiddenItems() {
        return parse()[1];
    }

    /**
     * Записывает раскладку. Перед записью прогоняется
     * {@link #ensureSettingsVisibility(List, List)}: «Настройки» нельзя спрятать, когда
     * нижняя панель выключена — иначе до них не добраться.
     */
    public static void save(List<Integer> layout, List<Integer> hidden) {
        final ArrayList<Integer> visibleCopy = new ArrayList<>(layout);
        final ArrayList<Integer> hiddenCopy = new ArrayList<>(hidden);
        ensureSettingsVisibility(visibleCopy, hiddenCopy);
        AppearanceConfig.mainMenuLayout.setConfigString(serialize(visibleCopy, hiddenCopy));
    }

    /** Сбрасывает раскладку в дефолт (пустая строка = «не настраивали»). */
    public static void reset() {
        AppearanceConfig.mainMenuLayout.setConfigString("");
    }

    /**
     * Настраивал ли пользователь раскладку. Пока нет — потребители обязаны показывать
     * ровно то, что показывали до появления настройки (правило «дефолт = как было»).
     */
    public static boolean isCustomized() {
        AppearanceConfig.ensureLoaded();
        final String raw = AppearanceConfig.mainMenuLayout.String();
        return raw != null && !raw.isEmpty();
    }

    /** Все известные пункты, кроме разделителя, — для экрана-редактора. */
    public static List<Integer> getAllItemIds() {
        final ArrayList<Integer> ids = new ArrayList<>();
        for (MainMenuItem item : MainMenuItem.values()) {
            if (item != MainMenuItem.DIVIDER) {
                ids.add(item.getId());
            }
        }
        return ids;
    }

    // ---- внутреннее ----

    private static boolean isBottomNavigationBarHidden() {
        try {
            // Тот же смысл, что у BottomNavigationBar.hidden().
            return MainTabsLayout.isBottomNavigationHidden();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Возвращает {@code [видимые, скрытые]}. Неизвестные id выкидываются, новые
     * (появившиеся с обновлением) дописываются в скрытые — как в exteraGram при миграции.
     */
    private static List<Integer>[] parse() {
        AppearanceConfig.ensureLoaded();
        final String raw = AppearanceConfig.mainMenuLayout.String();

        final ArrayList<Integer> visible = new ArrayList<>();
        final ArrayList<Integer> hidden = new ArrayList<>();

        if (raw == null || raw.isEmpty()) {
            visible.addAll(getDefaultLayout());
        } else {
            final String[] sections = raw.split(SECTION_SEPARATOR, -1);
            readIds(sections.length > 0 ? sections[0] : "", visible, true);
            readIds(sections.length > 1 ? sections[1] : "", hidden, false);
        }

        // Пункты, которых нет ни там, ни там, — скрыты.
        for (Integer id : getAllItemIds()) {
            if (!visible.contains(id) && !hidden.contains(id)) {
                hidden.add(id);
            }
        }
        ensureSettingsVisibility(visible, hidden);

        @SuppressWarnings("unchecked") final List<Integer>[] result = new List[]{
                Collections.unmodifiableList(visible),
                Collections.unmodifiableList(hidden)
        };
        return result;
    }

    private static void readIds(String section, ArrayList<Integer> out, boolean allowDivider) {
        if (section == null || section.isEmpty()) {
            return;
        }
        for (String part : section.split(ID_SEPARATOR)) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int id;
            try {
                id = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                continue;
            }
            if (id == MainMenuItem.DIVIDER.getId()) {
                if (allowDivider) {
                    out.add(id);
                }
                continue;
            }
            if (MainMenuItem.getById(id) != null && !out.contains(id)) {
                out.add(id);
            }
        }
    }

    private static void ensureSettingsVisibility(List<Integer> visible, List<Integer> hidden) {
        if (!isBottomNavigationBarHidden()) {
            return;
        }
        final Integer settings = MainMenuItem.SETTINGS.getId();
        if (visible.contains(settings)) {
            return;
        }
        hidden.remove(settings);
        visible.add(settings);
    }

    private static String serialize(List<Integer> visible, List<Integer> hidden) {
        return join(visible) + SECTION_SEPARATOR + join(hidden);
    }

    private static String join(List<Integer> ids) {
        final StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) {
                sb.append(ID_SEPARATOR);
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /** Утилита для редактора: раскладка «как сейчас», но уже изменяемая. */
    public static ArrayList<Integer> getLayoutMutable() {
        return new ArrayList<>(getLayout());
    }

    /** Утилита для редактора: скрытые пункты, но уже изменяемые. */
    public static ArrayList<Integer> getHiddenItemsMutable() {
        return new ArrayList<>(getHiddenItems());
    }

    /** Для тестов и отладки: разбор произвольной строки в том же формате. */
    static List<Integer> parseIds(String section) {
        final ArrayList<Integer> out = new ArrayList<>();
        readIds(section, out, true);
        return out;
    }
}
