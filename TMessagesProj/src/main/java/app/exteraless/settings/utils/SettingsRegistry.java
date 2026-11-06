package app.exteraless.settings.utils;

import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import app.exteraless.settings.OpenExteraAppNavigationActivity;
import app.exteraless.settings.OpenExteraAppearanceActivity;
import app.exteraless.settings.OpenExteraChatsActivity;
import app.exteraless.settings.OpenExteraGeneralActivity;
import app.exteraless.settings.OpenExteraOtherActivity;
import app.exteraless.settings.OpenExteraSettingsActivity;
import app.exteraless.settings.OpenExteraUpdatesActivity;

/**
 * Реестр пунктов настроек под именем {@code com.exteragram.messenger.preferences.utils.SettingsRegistry}.
 *
 * Плагины каталога дёргают его рефлексией: зовут {@code createEntriesIfNeeded},
 * читают приватное поле {@code entriesStringAlias}, вешают хук на {@code createEntries}
 * и открывают экран по алиасу через {@link #handleLink}. Имена и форма хранилища
 * повторяют эталон именно поэтому.
 *
 * Наши экраны настроек построены на строках NagramX, а не на UItem, поэтому сами
 * себя в реестр не добавляют — заполняют его плагины через {@link #addSearchEntry}
 * и {@link #addLinkAliasForOption}.
 */
public class SettingsRegistry {

    public static final class Entry {

        private final int guid;
        private final int itemId;
        private final String title;
        private final String subtext;
        private final int icon;
        private final Class<? extends BaseFragment> fragmentClass;

        private Entry(int guid, int itemId, String title, String subtext, int icon,
                      Class<? extends BaseFragment> fragmentClass) {
            this.guid = guid;
            this.itemId = itemId;
            this.title = title;
            this.subtext = subtext;
            this.icon = icon;
            this.fragmentClass = fragmentClass;
        }

        public static Entry fromUItem(BaseFragment fragment, UItem item) {
            Class<? extends BaseFragment> owner = fragment.getClass();
            CharSequence text = item.text;
            return new Entry(generateGUIDForUItem(owner, item), item.id,
                    text == null ? null : String.valueOf(text),
                    titleOf(fragment),
                    getInstance().getCategoryIcon(owner), owner);
        }

        private static String titleOf(BaseFragment fragment) {
            org.telegram.ui.ActionBar.ActionBar actionBar = fragment.getActionBar();
            CharSequence title = actionBar == null ? null : actionBar.getTitle();
            return title == null ? null : String.valueOf(title);
        }

        public int getGuid() {
            return guid;
        }

        public int getItemId() {
            return itemId;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtext() {
            return subtext;
        }

        public int getIcon() {
            return icon;
        }

        public Class<? extends BaseFragment> getFragmentClass() {
            return fragmentClass;
        }

        public ProfileActivity.SearchAdapter.SearchResult toSearchResult(
                ProfileActivity.SearchAdapter adapter) {
            return new ProfileActivity.SearchAdapter.SearchResult(guid, title,
                    String.valueOf(itemId), subtext, icon,
                    () -> getInstance().openActivity(fragmentClass, itemId));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Entry)) {
                return false;
            }
            Entry entry = (Entry) other;
            return guid == entry.guid && itemId == entry.itemId && icon == entry.icon
                    && Objects.equals(title, entry.title)
                    && Objects.equals(subtext, entry.subtext)
                    && Objects.equals(fragmentClass, entry.fragmentClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(guid, itemId, icon, title, subtext, fragmentClass);
        }

        @Override
        public String toString() {
            return "Entry[guid=" + guid + ", itemId=" + itemId + ", title=" + title
                    + ", subtext=" + subtext + ", icon=" + icon
                    + ", fragmentClass=" + fragmentClass + "]";
        }
    }

    private static final class SingletonHolder {
        private static final SettingsRegistry INSTANCE = new SettingsRegistry();
    }

    private static final Map<Class<? extends BaseFragment>, Integer> categoriesIcons = buildIcons();

    public static List<String> newFeatures = new ArrayList<>(Arrays.asList(
            "customSavePath", "zoomSlider", "widePosts", "aiFeatures", "hideDialogsSearchBar",
            "glassOutlineStyle", "glassMessageMenu", "chat3dScroll"));

    private final ConcurrentHashMap<Integer, Entry> preparedEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Entry> entriesStringAlias = new ConcurrentHashMap<>();

    private boolean entriesFetched;
    private String entriesLangCode;

    private static Map<Class<? extends BaseFragment>, Integer> buildIcons() {
        Map<Class<? extends BaseFragment>, Integer> icons = new HashMap<>();
        icons.put(OpenExteraSettingsActivity.class, R.drawable.msg_settings);
        icons.put(OpenExteraGeneralActivity.class, R.drawable.msg_media);
        icons.put(OpenExteraAppearanceActivity.class, R.drawable.msg_theme);
        icons.put(OpenExteraChatsActivity.class, R.drawable.msg_discussion);
        icons.put(OpenExteraOtherActivity.class, R.drawable.msg_fave);
        icons.put(OpenExteraAppNavigationActivity.class, R.drawable.msg_list);
        icons.put(OpenExteraUpdatesActivity.class, R.drawable.sync_outline_28);
        return icons;
    }

    public static SettingsRegistry getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static int generateGUIDForUItem(Class<?> owner, UItem item) {
        return Objects.hash(owner.getName(), item.id);
    }

    public int getCategoryIcon(Class<? extends BaseFragment> owner) {
        Integer icon = categoriesIcons.get(owner);
        return icon == null ? 0 : icon;
    }

    private void createEntriesIfNeeded() {
        LocaleController.LocaleInfo locale = LocaleController.getInstance().getCurrentLocaleInfo();
        String key = locale == null ? "" : locale.getKey();
        if (entriesFetched && TextUtils.equals(entriesLangCode, key)) {
            return;
        }
        if (entriesFetched) {
            preparedEntries.clear();
            entriesStringAlias.clear();
        }
        for (Class<? extends BaseFragment> owner : categoriesIcons.keySet()) {
            initiateFragment(owner);
        }
        entriesFetched = true;
        entriesLangCode = key;
    }

    public BaseFragment initiateFragment(Class<? extends BaseFragment> owner) {
        try {
            return owner.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            FileLog.e("SettingsRegistry: cannot instantiate " + owner.getName(), t);
            return null;
        }
    }

    public boolean isValidForSearch(UItem item) {
        return item != null && item.id > 0 && !TextUtils.isEmpty(item.text);
    }

    public boolean isValidForLinkAliases(UItem item) {
        return isValidForSearch(item);
    }

    public void addSearchEntry(BaseFragment fragment, UItem item) {
        if (fragment == null || !isValidForSearch(item)) {
            return;
        }
        Entry entry = Entry.fromUItem(fragment, item);
        preparedEntries.put(entry.guid, entry);
    }

    public void addLinkAliasForOption(String alias, BaseFragment fragment, UItem item) {
        if (TextUtils.isEmpty(alias) || fragment == null || !isValidForLinkAliases(item)) {
            return;
        }
        entriesStringAlias.put(alias, Entry.fromUItem(fragment, item));
    }

    public String getFirstSettingLink(Class<? extends BaseFragment> owner, UItem item) {
        int guid = generateGUIDForUItem(owner, item);
        for (Map.Entry<String, Entry> alias : entriesStringAlias.entrySet()) {
            if (alias.getValue().guid == guid) {
                return alias.getKey();
            }
        }
        return null;
    }

    public boolean markAsNewFeature(String key) {
        return key != null && newFeatures.contains(key);
    }

    public void openActivity(Class<? extends BaseFragment> owner, Integer itemId) {
        BaseFragment fragment = initiateFragment(owner);
        if (fragment == null) {
            return;
        }
        LaunchActivity activity = LaunchActivity.instance;
        if (activity != null) {
            activity.presentFragment(fragment);
        }
    }

    public void handleLink(String alias, String pluginId) {
        createEntriesIfNeeded();
        Entry entry = alias == null ? null : entriesStringAlias.get(alias);
        if (entry == null) {
            onSettingNotFound();
            return;
        }
        openActivity(entry.fragmentClass, entry.itemId);
    }

    public void onSettingNotFound() {
        onSettingNotFound(null);
    }

    public void onSettingNotFound(BaseFragment fragment) {
        FileLog.d("SettingsRegistry: setting link not found");
    }

    public ProfileActivity.SearchAdapter.SearchResult[] getSearchResults(
            ProfileActivity.SearchAdapter adapter) {
        createEntriesIfNeeded();
        List<ProfileActivity.SearchAdapter.SearchResult> results = new ArrayList<>();
        for (Entry entry : preparedEntries.values()) {
            results.add(entry.toSearchResult(adapter));
        }
        return results.toArray(new ProfileActivity.SearchAdapter.SearchResult[0]);
    }
}
