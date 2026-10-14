package app.exteraless.plugins.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_chatlists;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.exteraless.plugins.Plugin;
import app.exteraless.plugins.PluginAutoConverter;
import app.exteraless.plugins.PluginsController;

/**
 * Built-in Plugin Store for exteraGram / exteraless.
 * Fetches verified open-source community plugins from community channels.
 */
public class PluginStoreActivity extends BaseFragment {

    private static final String CACHE_FILE_NAME = "packit_plugins_cache.json";
    private static final String CHANNELS_FOLDER_SLUG = "MsGEKKJgoGlkOGM0";

    private static final int MENU_SEARCH = 0;
    private static final int MENU_SORT = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int MENU_REFRESH = 3;
    private static final int MENU_MORE = 4;

    private static final int SUB_SORT_DATE_DESC = 10;
    private static final int SUB_SORT_DATE_ASC = 11;
    private static final int SUB_SORT_NAME_ASC = 12;
    private static final int SUB_SORT_NAME_DESC = 13;
    private static final int SUB_SORT_SIZE_DESC = 14;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_OPEN_SOURCE = 1;
    private static final int FILTER_UI = 2;
    private static final int FILTER_TWEAKS = 3;
    private static final int FILTER_TOOLS = 4;
    private static final int FILTER_MEDIA = 5;
    private static final int FILTER_CHATS = 6;
    private static final int FILTER_DEV = 7;

    public static class StorePlugin {
        public String id;
        public String name;
        public String author;
        public String sourceChannel;
        public String channelPost;
        public String version;
        public String icon;
        public String link;
        public String size;
        public String description;
        public String updateDate;
        public String format = ".plugin";
        public boolean isOpenSource = true;
        public List<String> tags = new ArrayList<>();
        public boolean downloading = false;
        public TLRPC.Document document;
        public TLRPC.Message message;
        public TLRPC.Chat chat;
        public final List<StorePlugin> versions = new ArrayList<>();

        public boolean isInstalled() {
            String norm = normalizePluginId(id);
            if (PluginsController.getInstance().getPlugin(id) != null) return true;
            for (Plugin p : PluginsController.getInstance().getPlugins().values()) {
                if (p != null && normalizePluginId(p.getId()).equalsIgnoreCase(norm)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasUpdate() {
            String norm = normalizePluginId(id);
            Plugin installed = PluginsController.getInstance().getPlugin(id);
            if (installed == null) {
                for (Plugin p : PluginsController.getInstance().getPlugins().values()) {
                    if (p != null && normalizePluginId(p.getId()).equalsIgnoreCase(norm)) {
                        installed = p;
                        break;
                    }
                }
            }
            if (installed == null) return false;
            if (installed.version == null || version == null) return false;
            return compareVersions(version, installed.version) > 0;
        }
    }

    private UniversalRecyclerView listView;
    private LinearLayout chipsLayout;
    private final List<StorePlugin> allPlugins = new ArrayList<>();
    private final List<NotificationCenter.NotificationCenterDelegate> activeObservers = new ArrayList<>();
    private final PluginStoreConfig config = PluginStoreConfig.getInstance();
    private final List<TLRPC.Chat> activeSourceChats = new ArrayList<>();
    private final Map<Long, Integer> channelMinMsgId = new HashMap<>();
    private final Set<Long> channelsAtEnd = new HashSet<>();
    private final Set<String> autoUpdatedPluginIds = new HashSet<>();
    private String searchQuery;
    private int currentFilter = FILTER_ALL;
    private boolean loading = false;
    private boolean loadingNextPage = false;
    private Runnable deepSearchRunnable;
    private ActionBarMenuItem searchItem;

    private final StorePluginDelegate pluginDelegate = new StorePluginDelegate() {
        @Override
        public void onInstallClick(StorePlugin plugin) {
            downloadAndInstall(plugin);
        }

        @Override
        public void onInstalledClick(StorePlugin plugin) {
            showInstalledOptions(plugin);
        }

        @Override
        public void onAuthorClick(StorePlugin plugin) {
            String target = !TextUtils.isEmpty(plugin.channelPost) ? plugin.channelPost :
                    (!TextUtils.isEmpty(plugin.sourceChannel) ? plugin.sourceChannel : plugin.author);
            if (!TextUtils.isEmpty(target)) {
                String clean = target.trim();
                if (clean.startsWith("http://") || clean.startsWith("https://")) {
                    Browser.openUrl(getParentActivity(), clean);
                } else if (clean.startsWith("@")) {
                    Browser.openUrl(getParentActivity(), "https://t.me/" + clean.substring(1));
                } else {
                    Browser.openUrl(getParentActivity(), "https://t.me/" + clean);
                }
            }
        }

        @Override
        public void onCardClick(StorePlugin plugin) {
            new PluginDetailsBottomSheet(getParentActivity(), plugin, PluginStoreActivity.this, allPlugins).show();
        }
    };

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.PluginsStoreTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new PluginStoreSettingsActivity());
                } else if (id == MENU_REFRESH) {
                    BulletinFactory.of(PluginStoreActivity.this)
                            .createSimpleBulletin(R.drawable.msg_retry, getString(R.string.PluginsStoreLoading))
                            .show();
                    channelMinMsgId.clear();
                    channelsAtEnd.clear();
                    checkFolderChannelsOnline();
                } else if (id == SUB_SORT_DATE_DESC) {
                    config.setSortMode(PluginStoreConfig.SORT_DATE_DESC);
                    updateRows();
                } else if (id == SUB_SORT_DATE_ASC) {
                    config.setSortMode(PluginStoreConfig.SORT_DATE_ASC);
                    updateRows();
                } else if (id == SUB_SORT_NAME_ASC) {
                    config.setSortMode(PluginStoreConfig.SORT_NAME_ASC);
                    updateRows();
                } else if (id == SUB_SORT_NAME_DESC) {
                    config.setSortMode(PluginStoreConfig.SORT_NAME_DESC);
                    updateRows();
                } else if (id == SUB_SORT_SIZE_DESC) {
                    config.setSortMode(PluginStoreConfig.SORT_SIZE_DESC);
                    updateRows();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(MENU_SEARCH, R.drawable.ic_ab_search_solar);
        searchItem.setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchCollapse() {
                        searchQuery = null;
                        updateRows();
                    }

                    @Override
                    public void onTextChanged(android.widget.EditText editText) {
                        searchQuery = editText.getText().toString();
                        updateRows();
                        scheduleDeepSearch(searchQuery);
                    }
                });
        searchItem.setSearchFieldHint(getString(R.string.PluginsStoreSearch));

        ActionBarMenuItem sortItem = menu.addItem(MENU_SORT, R.drawable.contacts_sort_time);
        sortItem.addSubItem(SUB_SORT_DATE_DESC, R.drawable.contacts_sort_time, getString(R.string.PluginsStoreSortDateDesc));
        sortItem.addSubItem(SUB_SORT_DATE_ASC, R.drawable.contacts_sort_time, getString(R.string.PluginsStoreSortDateAsc));
        sortItem.addSubItem(SUB_SORT_NAME_ASC, R.drawable.contacts_sort_name, getString(R.string.PluginsStoreSortNameAsc));
        sortItem.addSubItem(SUB_SORT_NAME_DESC, R.drawable.contacts_sort_name, getString(R.string.PluginsStoreSortNameDesc));
        sortItem.addSubItem(SUB_SORT_SIZE_DESC, R.drawable.msg_media, getString(R.string.PluginsStoreSortSizeDesc));

        ActionBarMenuItem moreItem = menu.addItem(MENU_MORE, R.drawable.ic_ab_other);
        moreItem.addSubItem(MENU_REFRESH, R.drawable.msg_retry, getString(R.string.PluginsStoreRetry));
        moreItem.addSubItem(MENU_SETTINGS, R.drawable.msg_settings_old, getString(R.string.PluginsStoreSettings));

        LinearLayout contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        // Chips bar
        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setClipToPadding(false);
        chipsScroll.setPadding(dp(12), dp(8), dp(12), dp(8));
        chipsScroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        chipsLayout = new LinearLayout(context);
        chipsLayout.setOrientation(LinearLayout.HORIZONTAL);
        chipsScroll.addView(chipsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        contentView.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        buildChips(context);

        // RecyclerView
        listView = new UniversalRecyclerView(this, this::fillItems, null, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(4), 0, dp(88));
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && !loading && !loadingNextPage) {
                    RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                    if (lm instanceof LinearLayoutManager) {
                        int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                        int count = recyclerView.getAdapter() != null ? recyclerView.getAdapter().getItemCount() : 0;
                        if (count > 0 && last >= count - 8) {
                            loadNextPage();
                        }
                    }
                }
            }
        });
        contentView.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        FrameLayout rootLayout = new FrameLayout(context);
        rootLayout.addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Aurora Store squircle search FAB
        FrameLayout fab = new FrameLayout(context);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setCornerRadius(dp(16));
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        fabBg.setColor(accent);
        fab.setBackground(fabBg);

        ImageView fabIcon = new ImageView(context);
        fabIcon.setImageResource(R.drawable.ic_ab_search_solar);
        fabIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fabIcon.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
        fab.addView(fabIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

        fab.setOnClickListener(v -> {
            if (searchItem != null) {
                searchItem.openSearch(true);
            }
        });
        rootLayout.addView(fab, LayoutHelper.createFrame(56, 56, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 16, 0, 16, 20));
        loadInitialCatalog();

        fragmentView = rootLayout;
        return fragmentView;
    }

    private void buildChips(Context context) {
        chipsLayout.removeAllViews();
        int[] filterIds = {
                FILTER_ALL,
                FILTER_OPEN_SOURCE,
                FILTER_UI,
                FILTER_TWEAKS,
                FILTER_TOOLS,
                FILTER_MEDIA,
                FILTER_CHATS,
                FILTER_DEV
        };
        int[] titleResIds = {
                R.string.PluginsStoreFilterAll,
                R.string.PluginsStoreFilterOpenSource,
                R.string.PluginsStoreFilterUi,
                R.string.PluginsStoreFilterTweaks,
                R.string.PluginsStoreFilterTools,
                R.string.PluginsStoreFilterMedia,
                R.string.PluginsStoreFilterChats,
                R.string.PluginsStoreFilterDev
        };

        for (int i = 0; i < filterIds.length; i++) {
            final int fId = filterIds[i];
            TextView chip = new TextView(context);
            chip.setText(getString(titleResIds[i]));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(6), dp(14), dp(6));

            boolean active = (currentFilter == fId);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            if (active) {
                int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
                if (accent == 0) accent = 0xff00d2b4;
                bg.setColor(accent);
                chip.setTextColor(0xffffffff);
            } else {
                bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
            chip.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setOnClickListener(v -> {
                if (currentFilter != fId) {
                    currentFilter = fId;
                    buildChips(context);
                    updateRows();
                }
            });
            chipsLayout.addView(chip, lp);
        }
    }

    private void loadInitialCatalog() {
        Activity act = getParentActivity();
        if (act == null) return;

        // 1. Try disk cache
        File cache = new File(act.getCacheDir(), CACHE_FILE_NAME);
        if (cache.exists() && cache.length() > 0) {
            try (FileInputStream in = new FileInputStream(cache)) {
                String json = readStream(in);
                mergeCatalogJson(json);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        // 2. If still empty, load bundled community plugins from assets
        if (allPlugins.isEmpty()) {
            try (InputStream in = act.getAssets().open("packit_plugins.json")) {
                String json = readStream(in);
                mergeCatalogJson(json);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        updateRows();

        // 3. Scan folder channels (https://t.me/addlist/MsGEKKJgoGlkOGM0) for latest community plugins
        checkFolderChannelsOnline();
    }

    private void checkFolderChannelsOnline() {
        TL_chatlists.TL_chatlists_checkChatlistInvite req = new TL_chatlists.TL_chatlists_checkChatlistInvite();
        req.slug = CHANNELS_FOLDER_SLUG;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            ArrayList<TLRPC.Chat> chats = null;
            if (response instanceof TL_chatlists.chatlist_ChatlistInvite) {
                TL_chatlists.chatlist_ChatlistInvite inv = (TL_chatlists.chatlist_ChatlistInvite) response;
                if (inv instanceof TL_chatlists.TL_chatlists_chatlistInvite) {
                    chats = ((TL_chatlists.TL_chatlists_chatlistInvite) inv).chats;
                } else if (inv instanceof TL_chatlists.TL_chatlists_chatlistInviteAlready) {
                    chats = ((TL_chatlists.TL_chatlists_chatlistInviteAlready) inv).chats;
                }
            }

            final ArrayList<TLRPC.Chat> folderChats = chats != null ? new ArrayList<>(chats) : new ArrayList<>();
            AndroidUtilities.runOnUIThread(() -> {
                int delay = 0;
                for (TLRPC.Chat c : folderChats) {
                    if (c != null && !containsChat(activeSourceChats, c)) {
                        activeSourceChats.add(c);
                        final TLRPC.Chat chatToSearch = c;
                        AndroidUtilities.runOnUIThread(() -> searchChatBatch(chatToSearch, 0, null, null), delay);
                        delay += 120;
                    }
                }
                AndroidUtilities.runOnUIThread(this::resolveKnownChannels, delay + 150);
            });
        });
    }

    private static boolean containsChat(List<TLRPC.Chat> list, TLRPC.Chat chat) {
        for (TLRPC.Chat c : list) {
            if (c != null && chat != null && c.id == chat.id) return true;
        }
        return false;
    }

    private void resolveKnownChannels() {
        List<String> sourceChannels = config.getSourceChannels();
        for (String username : sourceChannels) {
            boolean already = false;
            for (TLRPC.Chat c : activeSourceChats) {
                if (c != null && c.username != null && c.username.equalsIgnoreCase(username)) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                req.username = username;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                    if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                        TLRPC.TL_contacts_resolvedPeer r = (TLRPC.TL_contacts_resolvedPeer) res;
                        if (r != null && !r.chats.isEmpty()) {
                            TLRPC.Chat c = r.chats.get(0);
                            AndroidUtilities.runOnUIThread(() -> {
                                if (!containsChat(activeSourceChats, c)) {
                                    activeSourceChats.add(c);
                                    searchChatBatch(c, 0, null, null);
                                }
                            });
                        }
                    }
                });
            }
        }
    }

    private void searchChatBatch(TLRPC.Chat chat, int offsetId, String query, Runnable onComplete) {
        if (chat == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        TLRPC.InputPeer inputPeer = MessagesController.getInstance(currentAccount).getInputPeer(chat);
        if (inputPeer == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        TLRPC.TL_messages_search sReq = new TLRPC.TL_messages_search();
        sReq.peer = inputPeer;
        sReq.filter = new TLRPC.TL_inputMessagesFilterDocument();
        sReq.q = query != null ? query : "";
        sReq.offset_id = offsetId;
        sReq.limit = 100;

        ConnectionsManager.getInstance(currentAccount).sendRequest(sReq, (res, err) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages mRes = (TLRPC.messages_Messages) res;
                    int minId = Integer.MAX_VALUE;
                    List<StorePlugin> discovered = new ArrayList<>();

                    for (TLRPC.Message msg : mRes.messages) {
                        if (msg == null) continue;
                        if (msg.id < minId) minId = msg.id;

                        if (msg.media instanceof TLRPC.TL_messageMediaDocument) {
                            TLRPC.Document doc = msg.media.document;
                            String fName = FileLoader.getDocumentFileName(doc);
                            if (fName != null && (fName.endsWith(".plugin") || fName.endsWith(".py") || fName.endsWith(".eaf"))) {
                                String id = fName.replaceAll("\\.[^.]+$", "").toLowerCase(Locale.ROOT).replace(" ", "_");
                                StorePlugin sp = new StorePlugin();
                                sp.id = id;
                                sp.name = cleanPluginName(fName);
                                String authorName = !TextUtils.isEmpty(chat.username) ? "@" + chat.username : (chat.title != null ? chat.title : "");
                                sp.author = authorName;
                                sp.sourceChannel = authorName;
                                sp.channelPost = !TextUtils.isEmpty(chat.username) ? "https://t.me/" + chat.username + "/" + msg.id : null;
                                sp.format = fName.endsWith(".py") ? ".py" : (fName.endsWith(".eaf") ? ".eaf" : ".plugin");
                                sp.isOpenSource = !sp.format.endsWith(".eaf");
                                sp.description = msg.message;
                                sp.size = AndroidUtilities.formatFileSize(doc.size);
                                sp.version = extractVersion(fName, msg.message);
                                sp.document = doc;
                                sp.message = msg;
                                sp.chat = chat;
                                sp.icon = parseIconFromText(msg.message);
                                sp.tags.add(sp.isOpenSource ? "OpenSource" : "Binary");
                                categorizePlugin(sp);
                                discovered.add(sp);
                            }
                        }
                    }

                    if (query == null) {
                        if (minId != Integer.MAX_VALUE && minId > 1) {
                            channelMinMsgId.put(chat.id, minId);
                        }
                        if (mRes.messages.size() < 20) {
                            channelsAtEnd.add(chat.id);
                        }
                    }

                    if (!discovered.isEmpty()) {
                        mergePlugins(discovered);
                        scheduleSaveCache();
                    }
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    private void loadNextPage() {
        if (loadingNextPage || activeSourceChats.isEmpty()) return;
        List<TLRPC.Chat> pendingChats = new ArrayList<>();
        for (TLRPC.Chat c : activeSourceChats) {
            if (!channelsAtEnd.contains(c.id)) {
                pendingChats.add(c);
            }
        }
        if (pendingChats.isEmpty()) return;

        loadingNextPage = true;
        final int[] remaining = new int[]{pendingChats.size()};
        for (TLRPC.Chat chat : pendingChats) {
            int offset = channelMinMsgId.containsKey(chat.id) ? channelMinMsgId.get(chat.id) : 0;
            searchChatBatch(chat, offset, null, () -> {
                remaining[0]--;
                if (remaining[0] <= 0) {
                    loadingNextPage = false;
                }
            });
        }
    }

    private void scheduleDeepSearch(String query) {
        if (deepSearchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(deepSearchRunnable);
            deepSearchRunnable = null;
        }
        if (TextUtils.isEmpty(query) || query.trim().length() < 2 || !config.isDeepSearchEnabled()) {
            return;
        }
        final String q = query.trim();
        deepSearchRunnable = () -> performDeepSearch(q);
        AndroidUtilities.runOnUIThread(deepSearchRunnable, 400);
    }

    private void performDeepSearch(String query) {
        if (activeSourceChats.isEmpty() || TextUtils.isEmpty(query)) return;
        List<String> terms = getSearchQueryExpansions(query);
        for (TLRPC.Chat chat : activeSourceChats) {
            for (String term : terms) {
                searchChatBatch(chat, 0, term, null);
            }
        }
    }

    public static List<String> getSearchQueryExpansions(String query) {
        List<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(query)) return list;
        String q = query.trim().toLowerCase(Locale.ROOT);
        list.add(q);

        List<String> aliases = getAliases(q);
        for (String a : aliases) {
            if (!list.contains(a)) {
                list.add(a);
            }
        }

        String translit = transliterateToLatin(q);
        if (!TextUtils.isEmpty(translit) && !list.contains(translit)) {
            list.add(translit);
        }

        if (list.size() > 4) {
            return list.subList(0, 4);
        }
        return list;
    }

    public static boolean matchPluginFuzzy(StorePlugin p, String query) {
        if (p == null || TextUtils.isEmpty(query)) return true;
        String rawQuery = query.toLowerCase(Locale.ROOT).trim();
        if (rawQuery.isEmpty()) return true;

        String name = p.name != null ? p.name.toLowerCase(Locale.ROOT) : "";
        String cleanName = cleanPluginName(p.name).toLowerCase(Locale.ROOT);
        String id = p.id != null ? p.id.toLowerCase(Locale.ROOT) : "";
        String normId = normalizePluginId(p.id != null ? p.id : "").toLowerCase(Locale.ROOT);
        String author = p.author != null ? p.author.toLowerCase(Locale.ROOT) : "";
        String channel = p.sourceChannel != null ? p.sourceChannel.toLowerCase(Locale.ROOT) : "";
        String desc = p.description != null ? p.description.toLowerCase(Locale.ROOT) : "";
        StringBuilder tagsBuilder = new StringBuilder();
        if (p.tags != null) {
            for (String t : p.tags) {
                tagsBuilder.append(t).append(" ");
            }
        }
        String tags = tagsBuilder.toString().toLowerCase(Locale.ROOT);

        String fullText = name + " " + cleanName + " " + id + " " + normId + " " + author + " " + channel + " " + desc + " " + tags;
        String compactFull = toCompact(fullText);
        String compactQuery = toCompact(rawQuery);

        if (compactQuery.length() >= 2 && compactFull.contains(compactQuery)) {
            return true;
        }

        if (fullText.contains(rawQuery)) {
            return true;
        }

        String[] tokens = rawQuery.split("\\s+");
        List<String> meaningfulTokens = new ArrayList<>();
        for (String t : tokens) {
            String trimmed = t.trim();
            if (trimmed.isEmpty()) continue;
            if (tokens.length > 1 && isStopWord(trimmed)) continue;
            meaningfulTokens.add(trimmed);
        }
        if (meaningfulTokens.isEmpty()) {
            meaningfulTokens.addAll(Arrays.asList(tokens));
        }

        String[] wordsInPlugin = fullText.split("[^a-zA-Z0-9а-яА-ЯёЁ]+");

        for (String token : meaningfulTokens) {
            if (!matchSingleToken(token, compactFull, wordsInPlugin)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchSingleToken(String token, String compactFull, String[] wordsInPlugin) {
        String tokenCompact = toCompact(token);
        if (tokenCompact.isEmpty()) return true;

        if (compactFull.contains(tokenCompact)) {
            return true;
        }

        String translitLatin = transliterateToLatin(token);
        String compactLatin = toCompact(translitLatin);
        if (!compactLatin.isEmpty() && compactFull.contains(compactLatin)) {
            return true;
        }

        String translitCyr = transliterateToCyrillic(token);
        String compactCyr = toCompact(translitCyr);
        if (!compactCyr.isEmpty() && compactFull.contains(compactCyr)) {
            return true;
        }

        List<String> aliases = getAliases(token);
        for (String alias : aliases) {
            String compactAlias = toCompact(alias);
            if (!compactAlias.isEmpty() && compactFull.contains(compactAlias)) {
                return true;
            }
        }

        for (String word : wordsInPlugin) {
            if (word.isEmpty()) continue;
            if (word.startsWith(token) || (token.length() >= 4 && token.startsWith(word))) {
                return true;
            }
            if (!compactLatin.isEmpty() && (word.startsWith(compactLatin) || (compactLatin.length() >= 4 && compactLatin.startsWith(word)))) {
                return true;
            }
            if (!compactCyr.isEmpty() && (word.startsWith(compactCyr) || (compactCyr.length() >= 4 && compactCyr.startsWith(word)))) {
                return true;
            }

            if (token.length() >= 4) {
                int maxDist = token.length() <= 5 ? 1 : 2;
                if (Math.abs(word.length() - token.length()) <= maxDist) {
                    if (levenshteinDistance(word, token) <= maxDist) {
                        return true;
                    }
                }
                if (!compactLatin.isEmpty() && Math.abs(word.length() - compactLatin.length()) <= maxDist) {
                    if (levenshteinDistance(word, compactLatin) <= maxDist) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static String toCompact(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^a-z0-9а-я]", "");
    }

    private static boolean isStopWord(String word) {
        switch (word.toLowerCase(Locale.ROOT)) {
            case "in":
            case "the":
            case "for":
            case "to":
            case "a":
            case "an":
            case "of":
            case "and":
            case "by":
            case "with":
            case "on":
            case "в":
            case "на":
            case "с":
            case "для":
            case "и":
            case "к":
            case "по":
            case "из":
            case "о":
            case "об":
            case "от":
            case "до":
                return true;
            default:
                return false;
        }
    }

    public static List<String> getAliases(String token) {
        List<String> res = new ArrayList<>();
        String t = token.toLowerCase(Locale.ROOT).replace('ё', 'е');

        if (t.contains("плеер") || t.contains("плейер") || t.contains("музык") || t.contains("песн") || t.contains("аудио") || t.contains("звук")) {
            res.addAll(Arrays.asList("player", "music", "audio", "sound", "track", "mp3"));
        }
        if (t.contains("player") || t.contains("music") || t.contains("audio") || t.contains("sound") || t.contains("track")) {
            res.addAll(Arrays.asList("плеер", "музыка", "аудио", "звук", "песня", "трек"));
        }
        if (t.contains("перевод") || t.contains("транслейт")) {
            res.addAll(Arrays.asList("translate", "translator", "deepl", "translation", "google"));
        }
        if (t.contains("translat") || t.contains("deepl")) {
            res.addAll(Arrays.asList("перевод", "переводчик"));
        }
        if (t.contains("шрифт")) {
            res.addAll(Arrays.asList("font", "fonts", "typography", "typeface"));
        }
        if (t.contains("font")) {
            res.addAll(Arrays.asList("шрифт", "шрифты"));
        }
        if (t.contains("кастом") || t.contains("настройк") || t.contains("стил") || t.contains("тем") || t.contains("вид") || t.contains("интерфейс")) {
            res.addAll(Arrays.asList("custom", "customize", "customization", "style", "theme", "appearance", "ui", "interface", "settings"));
        }
        if (t.contains("custom") || t.contains("theme") || t.contains("style") || t.contains("appear") || t.contains("interface") || t.contains("ui")) {
            res.addAll(Arrays.asList("кастом", "настройки", "стиль", "тема", "вид", "интерфейс"));
        }
        if (t.contains("скач") || t.contains("загруз") || t.contains("сохран")) {
            res.addAll(Arrays.asList("download", "downloader", "save", "saver"));
        }
        if (t.contains("download") || t.contains("save")) {
            res.addAll(Arrays.asList("скачать", "загрузка", "сохранить"));
        }
        if (t.contains("чат") || t.contains("диалог") || t.contains("сообщен")) {
            res.addAll(Arrays.asList("chat", "dialog", "message", "msg"));
        }
        if (t.contains("chat") || t.contains("message")) {
            res.addAll(Arrays.asList("чат", "сообщение", "диалог"));
        }
        if (t.contains("скрыт") || t.contains("невидим") || t.contains("призрак")) {
            res.addAll(Arrays.asList("hide", "hidden", "stealth", "ghost", "invisible"));
        }
        if (t.contains("ghost") || t.contains("hide") || t.contains("stealth")) {
            res.addAll(Arrays.asList("скрыть", "невидимка", "призрак"));
        }
        if (t.contains("вангард")) {
            res.addAll(Arrays.asList("vanguard", "vg"));
        }
        if (t.contains("vanguard")) {
            res.addAll(Arrays.asList("вангард"));
        }
        if (t.contains("пакит") || t.contains("пак")) {
            res.addAll(Arrays.asList("packit", "pack"));
        }
        if (t.contains("packit") || t.contains("pack")) {
            res.addAll(Arrays.asList("пакит", "пак"));
        }
        if (t.contains("экстера")) {
            res.addAll(Arrays.asList("extera", "exteragram"));
        }
        if (t.contains("неко")) {
            res.addAll(Arrays.asList("neko", "nekogram"));
        }
        if (t.contains("стикер")) {
            res.addAll(Arrays.asList("sticker", "stickers"));
        }
        if (t.contains("эмодзи") || t.contains("смайл")) {
            res.addAll(Arrays.asList("emoji", "smile", "emoticon"));
        }
        if (t.contains("реакц")) {
            res.addAll(Arrays.asList("reaction", "reactions"));
        }
        if (t.contains("кнопк")) {
            res.addAll(Arrays.asList("button", "btn"));
        }
        if (t.contains("бот")) {
            res.addAll(Arrays.asList("bot", "bots"));
        }
        if (t.contains("анимац")) {
            res.addAll(Arrays.asList("animation", "anim", "lottie"));
        }
        if (t.contains("заметк")) {
            res.addAll(Arrays.asList("note", "notes"));
        }
        if (t.contains("шпион") || t.contains("слежк")) {
            res.addAll(Arrays.asList("spy", "stalker"));
        }
        if (t.contains("кэш") || t.contains("кеш")) {
            res.addAll(Arrays.asList("cache", "cleaner"));
        }
        return res;
    }

    public static String transliterateToLatin(String s) {
        if (TextUtils.isEmpty(s)) return "";
        StringBuilder sb = new StringBuilder();
        String lower = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'а': sb.append("a"); break;
                case 'б': sb.append("b"); break;
                case 'в': sb.append("v"); break;
                case 'г': sb.append("g"); break;
                case 'д': sb.append("d"); break;
                case 'е':
                case 'ё': sb.append("e"); break;
                case 'ж': sb.append("zh"); break;
                case 'з': sb.append("z"); break;
                case 'и': sb.append("i"); break;
                case 'й': sb.append("y"); break;
                case 'к': sb.append("k"); break;
                case 'л': sb.append("l"); break;
                case 'м': sb.append("m"); break;
                case 'н': sb.append("n"); break;
                case 'о': sb.append("o"); break;
                case 'п': sb.append("p"); break;
                case 'р': sb.append("r"); break;
                case 'с': sb.append("s"); break;
                case 'т': sb.append("t"); break;
                case 'у': sb.append("u"); break;
                case 'ф': sb.append("f"); break;
                case 'х': sb.append("h"); break;
                case 'ц': sb.append("ts"); break;
                case 'ч': sb.append("ch"); break;
                case 'ш': sb.append("sh"); break;
                case 'щ': sb.append("shch"); break;
                case 'ъ': break;
                case 'ы': sb.append("y"); break;
                case 'ь': break;
                case 'э': sb.append("e"); break;
                case 'ю': sb.append("yu"); break;
                case 'я': sb.append("ya"); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    public static String transliterateToCyrillic(String s) {
        if (TextUtils.isEmpty(s)) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        lower = lower.replace("shch", "щ")
                .replace("ch", "ч")
                .replace("sh", "ш")
                .replace("zh", "ж")
                .replace("ts", "ц")
                .replace("yu", "ю")
                .replace("ya", "я")
                .replace("ph", "ф")
                .replace("th", "т");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'a': sb.append('а'); break;
                case 'b': sb.append('б'); break;
                case 'c': sb.append('к'); break;
                case 'd': sb.append('д'); break;
                case 'e': sb.append('е'); break;
                case 'f': sb.append('ф'); break;
                case 'g': sb.append('г'); break;
                case 'h': sb.append('х'); break;
                case 'i': sb.append('и'); break;
                case 'j': sb.append('ж'); break;
                case 'k': sb.append('к'); break;
                case 'l': sb.append('л'); break;
                case 'm': sb.append('м'); break;
                case 'n': sb.append('н'); break;
                case 'o': sb.append('о'); break;
                case 'p': sb.append('п'); break;
                case 'q': sb.append('к'); break;
                case 'r': sb.append('р'); break;
                case 's': sb.append('с'); break;
                case 't': sb.append('т'); break;
                case 'u': sb.append('у'); break;
                case 'v': sb.append('в'); break;
                case 'w': sb.append('в'); break;
                case 'x': sb.append("кс"); break;
                case 'y': sb.append('й'); break;
                case 'z': sb.append('з'); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    public static int levenshteinDistance(String s1, String s2) {
        if (s1.equals(s2)) return 0;
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) prev[j] = j;

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, len2 + 1);
        }
        return prev[len2];
    }

    public static String cleanPluginName(String fName) {
        if (TextUtils.isEmpty(fName)) return "";
        String base = fName.replaceAll("\\.[^.]+$", "").trim();
        if (base.contains("_") || base.contains("-")) {
            base = base.replaceAll("[-_]", " ").trim();
            String[] words = base.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty()) continue;
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            return sb.toString();
        }
        return base;
    }

    public static String parseIconFromText(String text) {
        if (TextUtils.isEmpty(text)) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:__icon__|icon)\\s*[=:]\\s*[\"']?([a-zA-Z0-9_]+/[0-9]+)[\"']?").matcher(text);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void categorizePlugin(StorePlugin sp) {
        String text = (sp.name + " " + (sp.description != null ? sp.description : "")).toLowerCase(Locale.ROOT);
        if (text.contains("ui") || text.contains("интерфейс") || text.contains("вид") || text.contains("style") || text.contains("theme") || text.contains("дизайн") || text.contains("стил") || text.contains("drawer") || text.contains("icon")) {
            if (!sp.tags.contains("Interface")) sp.tags.add("Interface");
        }
        if (text.contains("tool") || text.contains("инструмент") || text.contains("download") || text.contains("скач") || text.contains("viewer") || text.contains("log") || text.contains("bot") || text.contains("бот") || text.contains("api") || text.contains("утилит")) {
            if (!sp.tags.contains("Tools")) sp.tags.add("Tools");
        }
        if (text.contains("tweak") || text.contains("настройк") || text.contains("mod") || text.contains("custom") || text.contains("кастомизац") || text.contains("hide") || text.contains("скры")) {
            if (!sp.tags.contains("Tweaks")) sp.tags.add("Tweaks");
        }
        if (text.contains("media") || text.contains("плеер") || text.contains("player") || text.contains("audio") || text.contains("видео") || text.contains("video") || text.contains("музык") || text.contains("music") || text.contains("sound") || text.contains("voice") || text.contains("звук") || text.contains("mp3") || text.contains("stream")) {
            if (!sp.tags.contains("Media")) sp.tags.add("Media");
        }
        if (text.contains("chat") || text.contains("сообщен") || text.contains("чат") || text.contains("диалог") || text.contains("msg") || text.contains("message") || text.contains("перевод") || text.contains("translate") || text.contains("forward") || text.contains("spam") || text.contains("reply")) {
            if (!sp.tags.contains("Chats")) sp.tags.add("Chats");
        }
        if (text.contains("dev") || text.contains("отладк") || text.contains("hook") || text.contains("dex") || text.contains("script") || text.contains("debug") || text.contains("eval") || text.contains("разработ")) {
            if (!sp.tags.contains("Dev")) sp.tags.add("Dev");
        }
    }

    public static String normalizePluginId(String fName) {
        if (TextUtils.isEmpty(fName)) return "";
        String base = fName.replaceAll("\\.[^.]+$", "").trim().toLowerCase(Locale.ROOT);
        base = base.replaceAll("(?:_|-|\\s+)(?:v|rel|ver)?\\s*[0-9]+(?:\\.[0-9]+)*(?:_rel_[0-9.]+)?.*$", "");
        base = base.replaceAll("\\s*\\([0-9]+\\)$", "").trim();
        base = base.replace(" ", "_");
        if (base.isEmpty()) {
            base = fName.replaceAll("\\.[^.]+$", "").toLowerCase(Locale.ROOT).replace(" ", "_");
        }
        return base;
    }

    public static String extractVersion(String fName, String msgText) {
        if (!TextUtils.isEmpty(msgText)) {
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:version|версия)\\s*[:=]\\s*[\"']?([0-9]+(?:\\.[0-9]+)+)[\"']?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(msgText);
                if (m.find()) {
                    return m.group(1);
                }
            } catch (Exception ignored) {}
        }
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:v|ver)?([0-9]+(?:\\.[0-9]+)+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(fName);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static int compareVersions(String v1, String v2) {
        if (v1 == null) return v2 == null ? 0 : -1;
        if (v2 == null) return 1;
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int num1 = 0, num2 = 0;
            if (i < parts1.length) {
                try { num1 = Integer.parseInt(parts1[i].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            if (i < parts2.length) {
                try { num2 = Integer.parseInt(parts2[i].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    private static boolean isNewer(StorePlugin candidate, StorePlugin existing) {
        if (!TextUtils.isEmpty(candidate.version) && !TextUtils.isEmpty(existing.version)) {
            int cmp = compareVersions(candidate.version, existing.version);
            if (cmp > 0) return true;
            if (cmp < 0) return false;
        }
        int candDate = candidate.message != null ? candidate.message.date : 0;
        int existDate = existing.message != null ? existing.message.date : 0;
        if (candDate > 0 && existDate > 0) {
            return candDate > existDate;
        }
        if (candidate.chat != null && existing.chat != null && candidate.chat.id == existing.chat.id) {
            int candId = candidate.message != null ? candidate.message.id : 0;
            int existId = existing.message != null ? existing.message.id : 0;
            return candId > existId;
        }
        return false;
    }

    private void mergePlugins(List<StorePlugin> newPlugins) {
        if (newPlugins == null || newPlugins.isEmpty()) return;
        boolean changed = false;
        boolean hideOld = config.isHideOldVersionsEnabled();

        synchronized (allPlugins) {
            for (StorePlugin np : newPlugins) {
                if (np == null || TextUtils.isEmpty(np.id)) continue;
                String normNpId = normalizePluginId(np.id);
                boolean found = false;

                for (int i = 0; i < allPlugins.size(); i++) {
                    StorePlugin existing = allPlugins.get(i);
                    String normExistingId = normalizePluginId(existing.id);

                    if (existing.id.equalsIgnoreCase(np.id) || (hideOld && normExistingId.equalsIgnoreCase(normNpId))) {
                        found = true;
                        if (isNewer(np, existing)) {
                            if (!containsVersion(np.versions, existing)) {
                                np.versions.add(existing);
                            }
                            for (StorePlugin v : existing.versions) {
                                if (!containsVersion(np.versions, v)) {
                                    np.versions.add(v);
                                }
                            }
                            allPlugins.set(i, np);
                            changed = true;
                        } else {
                            if (!containsVersion(existing.versions, np)) {
                                existing.versions.add(np);
                            }
                            if (existing.document == null && np.document != null) existing.document = np.document;
                            if (existing.message == null && np.message != null) existing.message = np.message;
                            if (existing.chat == null && np.chat != null) existing.chat = np.chat;
                            if (TextUtils.isEmpty(existing.icon) && !TextUtils.isEmpty(np.icon)) existing.icon = np.icon;
                            if (TextUtils.isEmpty(existing.channelPost) && !TextUtils.isEmpty(np.channelPost)) existing.channelPost = np.channelPost;
                            if (TextUtils.isEmpty(existing.description) && !TextUtils.isEmpty(np.description)) existing.description = np.description;
                        }
                        break;
                    }
                }
                if (!found) {
                    allPlugins.add(np);
                    changed = true;
                }
            }
        }
        if (changed) {
            updateRows();
        }

        if (config.isAutoUpdateEnabled()) {
            for (StorePlugin np : newPlugins) {
                if (np != null && np.isInstalled() && np.hasUpdate() && np.document != null) {
                    if (!autoUpdatedPluginIds.contains(np.id)) {
                        autoUpdatedPluginIds.add(np.id);
                        downloadAndInstall(np);
                    }
                }
            }
        }
    }

    public List<StorePlugin> getPluginVersions(StorePlugin plugin) {
        List<StorePlugin> list = new ArrayList<>();
        if (plugin == null) return list;
        list.add(plugin);
        for (StorePlugin v : plugin.versions) {
            if (v != null && !containsVersion(list, v)) {
                list.add(v);
            }
        }
        String normId = normalizePluginId(plugin.id);
        synchronized (allPlugins) {
            for (StorePlugin p : allPlugins) {
                if (p != null && normalizePluginId(p.id).equalsIgnoreCase(normId)) {
                    if (!containsVersion(list, p)) {
                        list.add(p);
                    }
                    for (StorePlugin pv : p.versions) {
                        if (pv != null && !containsVersion(list, pv)) {
                            list.add(pv);
                        }
                    }
                }
            }
        }
        list.sort((a, b) -> {
            if (isNewer(a, b)) return -1;
            if (isNewer(b, a)) return 1;
            return 0;
        });
        return list;
    }

    private static boolean containsVersion(List<StorePlugin> list, StorePlugin item) {
        if (list == null || item == null) return false;
        for (StorePlugin p : list) {
            if (p == item) return true;
            if (p.message != null && item.message != null && p.message.id == item.message.id && p.chat != null && item.chat != null && p.chat.id == item.chat.id) return true;
            if (!TextUtils.isEmpty(p.version) && !TextUtils.isEmpty(item.version) && p.version.equalsIgnoreCase(item.version) && TextUtils.equals(p.link, item.link)) return true;
        }
        return false;
    }

    private void mergeCatalogJson(String jsonStr) {
        if (TextUtils.isEmpty(jsonStr)) return;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray arr = root.optJSONArray("plugins");
            if (arr == null) return;

            List<StorePlugin> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;

                StorePlugin p = new StorePlugin();
                p.id = obj.optString("id");
                p.name = obj.optString("name");
                p.author = obj.optString("author");
                p.sourceChannel = obj.optString("source_channel", p.author);
                p.channelPost = obj.optString("channel_post");
                p.format = obj.optString("format", ".plugin");
                p.isOpenSource = obj.optBoolean("is_open_source", !p.format.endsWith(".eaf") && !p.format.endsWith(".elyx"));
                p.version = obj.optString("version");
                p.icon = obj.optString("icon");
                p.link = obj.optString("link");
                p.size = obj.optString("size");
                p.description = obj.optString("description");
                p.updateDate = obj.optString("update_date");

                JSONArray tagsArr = obj.optJSONArray("tags");
                if (tagsArr != null) {
                    for (int j = 0; j < tagsArr.length(); j++) {
                        Object tagObj = tagsArr.opt(j);
                        if (tagObj instanceof JSONArray) {
                            JSONArray sub = (JSONArray) tagObj;
                            if (sub.length() > 0) p.tags.add(sub.optString(0));
                        } else if (tagObj instanceof String) {
                            p.tags.add((String) tagObj);
                        }
                    }
                }
                list.add(p);
            }

            mergePlugins(list);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private Runnable saveCacheRunnable;
    private void scheduleSaveCache() {
        if (saveCacheRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(saveCacheRunnable);
        }
        saveCacheRunnable = this::saveCache;
        AndroidUtilities.runOnUIThread(saveCacheRunnable, 1200);
    }

    private void saveCache() {
        Activity act = getParentActivity();
        if (act == null || allPlugins.isEmpty()) return;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject root = new JSONObject();
                JSONArray arr = new JSONArray();
                List<StorePlugin> snapshot;
                synchronized (allPlugins) {
                    snapshot = new ArrayList<>(allPlugins);
                }
                for (StorePlugin sp : snapshot) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", sp.id);
                    obj.put("name", sp.name);
                    obj.put("author", sp.author);
                    obj.put("source_channel", sp.sourceChannel);
                    obj.put("channel_post", sp.channelPost);
                    obj.put("format", sp.format);
                    obj.put("is_open_source", sp.isOpenSource);
                    obj.put("version", sp.version != null ? sp.version : "");
                    obj.put("size", sp.size != null ? sp.size : "");
                    obj.put("description", sp.description != null ? sp.description : "");
                    if (!TextUtils.isEmpty(sp.icon)) obj.put("icon", sp.icon);
                    if (!TextUtils.isEmpty(sp.link)) obj.put("link", sp.link);
                    JSONArray tags = new JSONArray();
                    for (String t : sp.tags) tags.put(t);
                    obj.put("tags", tags);
                    arr.put(obj);
                }
                root.put("plugins", arr);
                File cache = new File(act.getCacheDir(), CACHE_FILE_NAME);
                try (FileOutputStream out = new FileOutputStream(cache)) {
                    out.write(root.toString().getBytes("UTF-8"));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private boolean hasTag(StorePlugin p, String... targets) {
        if (p.tags == null) return false;
        for (String t : p.tags) {
            for (String target : targets) {
                if (t.equalsIgnoreCase(target)) return true;
            }
        }
        return false;
    }

    private List<StorePlugin> getFilteredPlugins() {
        List<StorePlugin> result = new ArrayList<>();
        String q = (searchQuery != null) ? searchQuery.toLowerCase(Locale.ROOT).trim() : null;

        List<StorePlugin> listCopy;
        synchronized (allPlugins) {
            listCopy = new ArrayList<>(allPlugins);
        }

        for (StorePlugin p : listCopy) {
            // Check filter
            if (currentFilter == FILTER_OPEN_SOURCE) {
                if (!p.isOpenSource) continue;
            } else if (currentFilter == FILTER_UI) {
                if (!hasTag(p, "UI", "Interface", "Player", "Customization", "Appearance")) continue;
            } else if (currentFilter == FILTER_TWEAKS) {
                if (!hasTag(p, "Tweaks", "Customization", "Tweak")) continue;
            } else if (currentFilter == FILTER_TOOLS) {
                if (!hasTag(p, "Tools", "Utility", "Library")) continue;
            } else if (currentFilter == FILTER_MEDIA) {
                if (!hasTag(p, "Media", "Player", "Audio", "Music")) continue;
            } else if (currentFilter == FILTER_CHATS) {
                if (!hasTag(p, "Chats", "Messages", "Chat", "Translate")) continue;
            } else if (currentFilter == FILTER_DEV) {
                if (!hasTag(p, "Dev", "DevTools", "Debug", "Dex")) continue;
            }

            // Check search query
            if (!TextUtils.isEmpty(q)) {
                if (!matchPluginFuzzy(p, q)) {
                    continue;
                }
            }

            result.add(p);
        }

        int sortMode = config.getSortMode();
        Collections.sort(result, (p1, p2) -> {
            switch (sortMode) {
                case PluginStoreConfig.SORT_DATE_ASC: {
                    int d1 = p1.message != null ? p1.message.date : 0;
                    int d2 = p2.message != null ? p2.message.date : 0;
                    return Integer.compare(d1, d2);
                }
                case PluginStoreConfig.SORT_NAME_ASC: {
                    return p1.name.compareToIgnoreCase(p2.name);
                }
                case PluginStoreConfig.SORT_NAME_DESC: {
                    return p2.name.compareToIgnoreCase(p1.name);
                }
                case PluginStoreConfig.SORT_SIZE_DESC: {
                    long s1 = p1.document != null ? p1.document.size : 0;
                    long s2 = p2.document != null ? p2.document.size : 0;
                    return Long.compare(s2, s1);
                }
                case PluginStoreConfig.SORT_DATE_DESC:
                default: {
                    int d1 = p1.message != null ? p1.message.date : 0;
                    int d2 = p2.message != null ? p2.message.date : 0;
                    return Integer.compare(d2, d1);
                }
            }
        });

        return result;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) {
            context = getParentActivity();
        }
        if (context == null) {
            return;
        }

        List<StorePlugin> list = getFilteredPlugins();
        if (list.isEmpty()) {
            if (loading) {
                items.add(UItem.asShadow(getString(R.string.PluginsStoreLoading)));
            } else {
                View emptyView = createAuroraEmptyCell(context);
                if (emptyView != null) {
                    items.add(UItem.asCustom(emptyView));
                }
            }
            return;
        }

        for (StorePlugin p : list) {
            items.add(StorePluginItemFactory.of(p, pluginDelegate));
        }
    }

    private View createAuroraEmptyCell(Context context) {
        if (context == null) {
            return null;
        }
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(32), dp(64), dp(32), dp(64));

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.msg_retry);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
        layout.addView(iconView, LayoutHelper.createLinear(56, 56, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setText(!TextUtils.isEmpty(searchQuery) ? getString(R.string.PluginsNotFound) : "Нет доступных обновлений");
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        TextView button = new TextView(context);
        button.setText(!TextUtils.isEmpty(searchQuery) ? "Сбросить поиск" : "Проверить обновления");
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setTextColor(0xffffffff);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(dp(20));
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        btnBg.setColor(accent);
        button.setBackground(btnBg);
        button.setPadding(dp(24), dp(10), dp(24), dp(10));
        button.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(searchQuery)) {
                searchQuery = null;
                if (actionBar != null) {
                    actionBar.closeSearchField(true);
                }
                updateRows();
            } else {
                channelMinMsgId.clear();
                channelsAtEnd.clear();
                checkFolderChannelsOnline();
            }
        });
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_HORIZONTAL));

        return layout;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item != null && item.object instanceof StorePlugin) {
            new PluginDetailsBottomSheet(getParentActivity(), (StorePlugin) item.object, PluginStoreActivity.this, allPlugins).show();
        }
    }

    void downloadAndInstall(StorePlugin plugin) {
        Activity activity = getParentActivity();
        if (activity == null) return;

        // 1. If plugin was directly fetched from a telegram channel
        if (plugin.document != null) {
            File localFile = FileLoader.getInstance(currentAccount).getPathToAttach(plugin.document, true);
            if (localFile != null && localFile.exists() && localFile.length() > 0) {
                installPluginFile(localFile);
                return;
            }
            if (plugin.message != null) {
                File msgFile = FileLoader.getInstance(currentAccount).getPathToMessage(plugin.message);
                if (msgFile != null && msgFile.exists() && msgFile.length() > 0) {
                    installPluginFile(msgFile);
                    return;
                }
            }

            plugin.downloading = true;
            updateRows();

            final String attachName = FileLoader.getAttachFileName(plugin.document);
            final String docName = FileLoader.getDocumentFileName(plugin.document);

            NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
                @Override
                public void didReceivedNotification(int id, int account, Object... args) {
                    if (id == NotificationCenter.fileLoaded) {
                        String name = (String) args[0];
                        File f = (File) args[1];
                        if (matches(name, f)) {
                            cleanup();
                            File target = (f != null && f.exists() && f.length() > 0) ? f :
                                    FileLoader.getInstance(currentAccount).getPathToAttach(plugin.document, true);
                            if (target == null || !target.exists() || target.length() == 0) {
                                if (plugin.message != null) {
                                    target = FileLoader.getInstance(currentAccount).getPathToMessage(plugin.message);
                                }
                            }
                            if (target != null && target.exists() && target.length() > 0) {
                                installPluginFile(target);
                            } else {
                                BulletinFactory.of(PluginStoreActivity.this)
                                        .createSimpleBulletin(R.drawable.msg_delete, getString(R.string.PluginsInstallReadError))
                                        .show();
                            }
                        }
                    } else if (id == NotificationCenter.fileLoadFailed) {
                        String name = (String) args[0];
                        if (matches(name, null)) {
                            cleanup();
                            BulletinFactory.of(PluginStoreActivity.this)
                                    .createSimpleBulletin(R.drawable.msg_delete, getString(R.string.PluginsInstallReadError))
                                    .show();
                        }
                    }
                }

                private boolean matches(String name, File f) {
                    if (TextUtils.equals(name, attachName) || TextUtils.equals(name, docName)) {
                        return true;
                    }
                    if (f != null && (TextUtils.equals(f.getName(), attachName) || TextUtils.equals(f.getName(), docName))) {
                        return true;
                    }
                    return false;
                }

                private void cleanup() {
                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadFailed);
                    activeObservers.remove(this);
                    plugin.downloading = false;
                    updateRows();
                }
            };

            activeObservers.add(observer);
            NotificationCenter.getInstance(currentAccount).addObserver(observer, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(observer, NotificationCenter.fileLoadFailed);

            MessageObject msgObj = plugin.message != null ? new MessageObject(currentAccount, plugin.message, false, false) : null;
            FileLoader.getInstance(currentAccount).loadFile(plugin.document, msgObj, FileLoader.PRIORITY_HIGH, 0);
            return;
        }

        // 2. If it's a telegram post link (e.g. https://t.me/PESSDES_Plugins/91)
        String postUrl = !TextUtils.isEmpty(plugin.channelPost) ? plugin.channelPost : plugin.link;
        if (!TextUtils.isEmpty(postUrl) && postUrl.startsWith("https://t.me/")) {
            downloadTelegramPost(plugin, postUrl);
            return;
        }

        // 3. Otherwise download via HTTP
        if (TextUtils.isEmpty(plugin.link)) {
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_info, "Download link not found").show();
            return;
        }

        plugin.downloading = true;
        updateRows();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                URL url = new URL(plugin.link);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "exteraless/" + BuildVars.BUILD_VERSION_STRING);
                conn.connect();

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new Exception("HTTP " + code);
                }

                String ext = plugin.format != null ? plugin.format : ".plugin";
                String linkLower = plugin.link.toLowerCase(Locale.ROOT);
                if (linkLower.endsWith(".eaf")) ext = ".eaf";
                else if (linkLower.endsWith(".py")) ext = ".py";
                else if (linkLower.endsWith(".plugin")) ext = ".plugin";

                File file = new File(activity.getCacheDir(), plugin.id + "_" + plugin.version + ext);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                } finally {
                    conn.disconnect();
                }

                AndroidUtilities.runOnUIThread(() -> {
                    plugin.downloading = false;
                    updateRows();
                    installPluginFile(file);
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    plugin.downloading = false;
                    updateRows();
                    BulletinFactory.of(PluginStoreActivity.this)
                            .createSimpleBulletin(R.drawable.msg_delete, getString(R.string.PluginsInstallError) + ": " + e.getMessage())
                            .show();
                });
            }
        });
    }

    private void downloadTelegramPost(StorePlugin plugin, String postUrl) {
        try {
            Uri uri = Uri.parse(postUrl);
            List<String> segments = uri.getPathSegments();
            if (segments != null && segments.size() >= 2) {
                String username = segments.get(0);
                int msgId = Integer.parseInt(segments.get(1));

                plugin.downloading = true;
                updateRows();

                TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                req.username = username;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                    if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                        TLRPC.TL_contacts_resolvedPeer rPeer = (TLRPC.TL_contacts_resolvedPeer) res;
                        TLRPC.Chat chat = (!rPeer.chats.isEmpty()) ? rPeer.chats.get(0) : null;
                        if (chat != null) {
                            TLRPC.TL_channels_getMessages gReq = new TLRPC.TL_channels_getMessages();
                            gReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat);
                            gReq.id.add(msgId);
                            final TLRPC.Chat finalChat = chat;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(gReq, (mRes, mErr) -> {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (mRes instanceof TLRPC.messages_Messages) {
                                        TLRPC.messages_Messages messages = (TLRPC.messages_Messages) mRes;
                                        if (!messages.messages.isEmpty()) {
                                            TLRPC.Message msg = messages.messages.get(0);
                                            if (msg != null && msg.media instanceof TLRPC.TL_messageMediaDocument) {
                                                plugin.document = msg.media.document;
                                                plugin.message = msg;
                                                plugin.chat = finalChat;
                                                plugin.downloading = false;
                                                downloadAndInstall(plugin);
                                                return;
                                            }
                                        }
                                    }
                                    plugin.downloading = false;
                                    updateRows();
                                    Browser.openUrl(getParentActivity(), postUrl);
                                });
                            });
                            return;
                        }
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        plugin.downloading = false;
                        updateRows();
                        Browser.openUrl(getParentActivity(), postUrl);
                    });
                });
                return;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        Browser.openUrl(getParentActivity(), postUrl);
    }

    private void installPluginFile(File file) {
        if (file == null || !file.exists() || file.length() == 0) return;
        File toInstall = file;
        if (PluginsController.getInstance().isBetaImportPyEnabled() && file.getName().toLowerCase(Locale.ROOT).endsWith(".py")) {
            toInstall = PluginAutoConverter.autoConvertIfNeeded(file);
        }
        final String path = toInstall.getAbsolutePath();
        AndroidUtilities.runOnUIThread(() -> {
            PluginsController.getInstance().showInstallDialog(PluginStoreActivity.this, path, false);
        });
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (deepSearchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(deepSearchRunnable);
            deepSearchRunnable = null;
        }
        if (saveCacheRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(saveCacheRunnable);
            saveCacheRunnable = null;
        }
        for (NotificationCenter.NotificationCenterDelegate obs : activeObservers) {
            NotificationCenter.getInstance(currentAccount).removeObserver(obs, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(obs, NotificationCenter.fileLoadFailed);
        }
        activeObservers.clear();
    }

    private void showInstalledOptions(StorePlugin plugin) {
        Activity act = getParentActivity();
        if (act == null) return;

        CharSequence[] items = new CharSequence[]{
                getString(R.string.PluginSettingsTitle),
                getString(R.string.PluginPermissions),
                getString(R.string.PluginsInstallAction)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setTitle(plugin.name != null ? plugin.name : plugin.id);
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                Plugin p = PluginsController.getInstance().getPlugin(plugin.id);
                if (p == null) {
                    String norm = normalizePluginId(plugin.id);
                    for (Plugin pl : PluginsController.getInstance().getPlugins().values()) {
                        if (pl != null && normalizePluginId(pl.getId()).equalsIgnoreCase(norm)) {
                            p = pl;
                            break;
                        }
                    }
                }
                if (p != null) {
                    app.exteraless.plugins.PythonPluginsEngine.getInstance().openPluginSettings(p, this);
                }
            } else if (which == 1) {
                String targetId = plugin.id;
                if (PluginsController.getInstance().getPlugin(targetId) == null) {
                    String norm = normalizePluginId(targetId);
                    for (Plugin pl : PluginsController.getInstance().getPlugins().values()) {
                        if (pl != null && normalizePluginId(pl.getId()).equalsIgnoreCase(norm)) {
                            targetId = pl.getId();
                            break;
                        }
                    }
                }
                presentFragment(new PluginPermissionsActivity(targetId));
            } else if (which == 2) {
                downloadAndInstall(plugin);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void updateRows() {
        if (getContext() == null && getParentActivity() == null) {
            return;
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        resolveKnownChannels();
        updateRows();
    }

    private static String readStream(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) > 0) {
            out.write(buf, 0, read);
        }
        return out.toString("UTF-8");
    }

    public interface StorePluginDelegate {
        void onInstallClick(StorePlugin plugin);
        void onInstalledClick(StorePlugin plugin);
        void onAuthorClick(StorePlugin plugin);
        void onCardClick(StorePlugin plugin);
    }

    private static final class StorePluginItemFactory extends UItem.UItemFactory<StorePluginCell> {
        static {
            setup(new StorePluginItemFactory());
        }

        @Override
        public StorePluginCell createView(Context context, RecyclerListView listView,
                                          int currentAccount, int classGuid,
                                          Theme.ResourcesProvider resourcesProvider) {
            return new StorePluginCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            if (view instanceof StorePluginCell && item.object instanceof StorePlugin) {
                StorePluginCell cell = (StorePluginCell) view;
                StorePlugin p = (StorePlugin) item.object;
                cell.bind(p, (StorePluginDelegate) item.tag);
            }
        }

        @Override
        public boolean equals(UItem first, UItem second) {
            return first.id == second.id;
        }

        @Override
        public boolean contentsEquals(UItem first, UItem second) {
            if (first.object instanceof StorePlugin && second.object instanceof StorePlugin) {
                StorePlugin p1 = (StorePlugin) first.object;
                StorePlugin p2 = (StorePlugin) second.object;
                return p1.downloading == p2.downloading
                        && TextUtils.equals(p1.version, p2.version)
                        && TextUtils.equals(p1.name, p2.name)
                        && TextUtils.equals(p1.description, p2.description);
            }
            return false;
        }

        static UItem of(StorePlugin p, StorePluginDelegate delegate) {
            UItem item = ofFactory(StorePluginItemFactory.class);
            item.object = p;
            item.tag = delegate;
            item.id = p.id != null ? p.id.hashCode() : 0;
            return item;
        }
    }

    public static class StorePluginCell extends FrameLayout {
        private final LinearLayout card;
        private final BackupImageView iconView;
        private final ImageView defaultIconView;
        private final TextView nameView;
        private final TextView typeBadge;
        private final TextView subView;
        private final TextView descView;
        private final TextView tagsView;
        private final TextView actionButton;
        private final TextView openButton;
        private StorePlugin plugin;
        private StorePluginDelegate delegate;
        private boolean expanded = false;

        public StorePluginCell(Context context) {
            super(context);
            setPadding(dp(12), dp(4), dp(12), dp(6));

            card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            int radius = app.exteraless.appearance.AppearanceConfig.sectionRadius();
            card.setBackground(Theme.createRoundRectDrawable(dp(radius), Theme.getColor(Theme.key_windowBackgroundWhite)));
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setOnClickListener(v -> {
                if (delegate != null && plugin != null) {
                    delegate.onCardClick(plugin);
                }
            });
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // Header row
            LinearLayout headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            FrameLayout iconContainer = new FrameLayout(context);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(dp(14));
            iconBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            iconContainer.setBackground(iconBg);

            defaultIconView = new ImageView(context);
            defaultIconView.setImageResource(R.drawable.baseline_extension_24);
            defaultIconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            defaultIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
            iconContainer.addView(defaultIconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            iconView = new BackupImageView(context);
            iconView.setRoundRadius(dp(14));
            iconView.setVisibility(GONE);
            iconContainer.addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            headerRow.addView(iconContainer, LayoutHelper.createLinear(50, 50));

            LinearLayout titleCol = new LinearLayout(context);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            titleCol.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 8, 0));

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            titleCol.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            typeBadge = new TextView(context);
            typeBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            typeBadge.setTypeface(AndroidUtilities.bold());
            typeBadge.setPadding(dp(7), dp(2), dp(7), dp(2));
            titleCol.addView(typeBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

            subView = new TextView(context);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subView.setSingleLine(true);
            subView.setEllipsize(TextUtils.TruncateAt.END);
            titleCol.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            // Description
            descView = new TextView(context);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            descView.setLineSpacing(0, 1.15f);
            descView.setMaxLines(3);
            descView.setEllipsize(TextUtils.TruncateAt.END);
            descView.setOnClickListener(v -> {
                expanded = !expanded;
                descView.setMaxLines(expanded ? Integer.MAX_VALUE : 3);
            });
            card.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

            // Tags
            tagsView = new TextView(context);
            tagsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            tagsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            card.addView(tagsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

            // Actions row
            LinearLayout actionsRow = new LinearLayout(context);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

            openButton = new TextView(context);
            openButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            openButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            openButton.setSingleLine(true);
            openButton.setEllipsize(TextUtils.TruncateAt.END);
            openButton.setPadding(0, dp(4), dp(8), dp(4));
            actionsRow.addView(openButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            actionButton = new TextView(context);
            actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            actionButton.setTypeface(AndroidUtilities.bold());
            actionButton.setGravity(Gravity.CENTER);
            actionButton.setPadding(dp(16), dp(7), dp(16), dp(7));
            actionsRow.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 34));
        }

        public void bind(StorePlugin p, StorePluginDelegate d) {
            this.plugin = p;
            this.delegate = d;
            nameView.setText(p.name != null ? p.name : p.id);

            GradientDrawable tbBg = new GradientDrawable();
            tbBg.setCornerRadius(dp(8));
            if (p.isOpenSource) {
                tbBg.setColor(0x2200c853);
                typeBadge.setTextColor(0xff00c853);
                typeBadge.setText(getString(R.string.PluginsStoreOpenSource) + " (" + (p.format != null ? p.format : ".plugin") + ")");
            } else {
                tbBg.setColor(0x22888888);
                typeBadge.setTextColor(0xff888888);
                typeBadge.setText(p.format != null ? p.format : ".eaf");
            }
            typeBadge.setBackground(tbBg);

            StringBuilder sub = new StringBuilder();
            if (!TextUtils.isEmpty(p.author)) sub.append(p.author);
            if (!TextUtils.isEmpty(p.version)) {
                if (sub.length() > 0) sub.append(" • ");
                sub.append("v").append(p.version);
            }
            if (!TextUtils.isEmpty(p.size)) {
                if (sub.length() > 0) sub.append(" • ");
                sub.append(p.size);
            }
            subView.setText(sub.toString());

            descView.setText(p.description != null ? p.description : "");
            descView.setVisibility(TextUtils.isEmpty(p.description) ? GONE : VISIBLE);

            if (p.tags != null && !p.tags.isEmpty()) {
                StringBuilder tb = new StringBuilder();
                for (String t : p.tags) {
                    if (tb.length() > 0) tb.append("  ");
                    tb.append("#").append(t);
                }
                tagsView.setText(tb.toString());
                tagsView.setVisibility(VISIBLE);
            } else {
                tagsView.setVisibility(GONE);
            }

            String source = !TextUtils.isEmpty(p.sourceChannel) ? p.sourceChannel : p.author;
            if (!TextUtils.isEmpty(source)) {
                openButton.setText(source + (!TextUtils.isEmpty(p.channelPost) ? " ↗" : ""));
                openButton.setVisibility(VISIBLE);
                openButton.setOnClickListener(v -> {
                    if (delegate != null) delegate.onAuthorClick(p);
                });
            } else {
                openButton.setVisibility(GONE);
            }

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(dp(17));
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accent == 0) accent = 0xff00d2b4;

            if (p.downloading) {
                btnBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                actionButton.setBackground(btnBg);
                actionButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                actionButton.setText(getString(R.string.PluginsStoreDownloading));
                actionButton.setEnabled(false);
            } else if (p.isInstalled()) {
                if (p.hasUpdate()) {
                    btnBg.setColor(accent);
                    actionButton.setBackground(btnBg);
                    actionButton.setTextColor(0xffffffff);
                    actionButton.setText(getString(R.string.PluginsStoreUpdate));
                    actionButton.setEnabled(true);
                    actionButton.setOnClickListener(v -> {
                        if (delegate != null) delegate.onInstallClick(p);
                    });
                } else {
                    btnBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    actionButton.setBackground(btnBg);
                    actionButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    actionButton.setText(getString(R.string.PluginsStoreInstalled));
                    actionButton.setEnabled(true);
                    actionButton.setOnClickListener(v -> {
                        if (delegate != null) delegate.onInstalledClick(p);
                    });
                }
            } else {
                btnBg.setColor(accent);
                actionButton.setBackground(btnBg);
                actionButton.setTextColor(0xffffffff);
                actionButton.setText(getString(R.string.PluginsStoreInstall));
                actionButton.setEnabled(true);
                actionButton.setOnClickListener(v -> {
                    if (delegate != null) delegate.onInstallClick(p);
                });
            }

            iconView.setTag(null);
            iconView.setImageDrawable(null);

            boolean iconApplied = false;
            if (!TextUtils.isEmpty(p.icon)) {
                iconApplied = PluginIcons.apply(iconView, p.icon, () -> {
                    iconView.setVisibility(VISIBLE);
                    defaultIconView.setVisibility(GONE);
                });
            }

            if (!iconApplied) {
                if (p.document != null && p.document.thumbs != null && !p.document.thumbs.isEmpty()) {
                    TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(p.document.thumbs, 120);
                    if (thumbSize != null) {
                        iconView.setImage(ImageLocation.getForDocument(thumbSize, p.document), "50_50", null, null, p.document);
                        iconView.setVisibility(VISIBLE);
                        defaultIconView.setVisibility(GONE);
                        iconApplied = true;
                    }
                }
            }

            if (!iconApplied) {
                TLRPC.Chat chat = p.chat;
                if (chat == null && !TextUtils.isEmpty(p.sourceChannel)) {
                    String u = p.sourceChannel.startsWith("@") ? p.sourceChannel.substring(1) : p.sourceChannel;
                    TLObject obj = MessagesController.getInstance(UserConfig.selectedAccount).getUserOrChat(u);
                    if (obj instanceof TLRPC.Chat) {
                        chat = (TLRPC.Chat) obj;
                        p.chat = chat;
                    }
                }

                AvatarDrawable avatar = new AvatarDrawable();
                avatar.setRoundRadius(dp(14));
                avatar.setTextSize(dp(18));
                if (chat != null) {
                    avatar.setInfo(chat);
                    iconView.setForUserOrChat(chat, avatar);
                } else {
                    int seed = p.id != null ? p.id.hashCode() : (p.name != null ? p.name.hashCode() : 0);
                    avatar.setInfo(seed, p.name != null ? p.name : "Plugin", null);
                    iconView.setImage(null, null, avatar, null);
                }
                iconView.setVisibility(VISIBLE);
                defaultIconView.setVisibility(GONE);
            }
        }
    }
}
