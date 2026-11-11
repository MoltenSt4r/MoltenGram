package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;


import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Корневой экран раздела exteraless.
 * Структура повторяет настройки exteraGram: About-шапка + категории с иконками.
 * Открывается из главных настроек (SettingsActivity, id 102).
 */
public class OpenExteraSettingsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_CLOUD = 1;
    private static final int MENU_SEARCH = 2;

    private static final int TYPE_ABOUT = 100;

    private int aboutRow;

    private int categoriesHeaderRow;
    private int generalRow;
    private int appearanceRow;
    private int chatsRow;
    private int pluginsRow;
    private int toolsRow;
    private int otherRow;
    private int categoriesDividerRow;

    private int linksHeaderRow;
    private int channelRow;
    private int sourceRow;
    private int linksDividerRow;

    private int creditsHeaderRow;
    private int creditsExteralessRow;
    private int creditsNagramXRow;
    private int creditsDividerRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        aboutRow = addRow("about");

        categoriesHeaderRow = addRow("categoriesHeader");
        generalRow = addRow("general");
        appearanceRow = addRow("appearance");
        chatsRow = addRow("chats");
        pluginsRow = addRow("plugins");
        toolsRow = addRow("tools");
        otherRow = addRow("other");
        categoriesDividerRow = addRow();

        linksHeaderRow = addRow("linksHeader");
        channelRow = addRow("channel");
        sourceRow = addRow("source");
        linksDividerRow = addRow();

        creditsHeaderRow = addRow("creditsHeader");
        creditsExteralessRow = addRow("creditsExteraless");
        creditsNagramXRow = addRow("creditsNagramX");
        creditsDividerRow = addRow();
    }

    /**
     * У экстеры корневой экран устроен так: actionBar не занимает места и прозрачен,
     * пока список не прокручен, поэтому логотип стоит выше (в 12.9.0 верх логотипа
     * y=242 при низе actionBar 283). Заголовок проявляется вместе с фоном шапки.
     */
    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        getMessagesController().getContentSettings(null);
        if (actionBar != null && fragmentView instanceof android.widget.FrameLayout) {
            // 1:1 с MainPreferencesActivity.createView из 12.9.0.
            actionBar.setBackground(null);
            actionBar.setCastShadows(false);
            actionBar.setAddToContainer(false);
            if (actionBar.getTitleTextView() != null) {
                actionBar.getTitleTextView().setAlpha(0f);
            }
            ((android.widget.FrameLayout) fragmentView).addView(actionBar,
                    org.telegram.ui.Components.LayoutHelper.createFrame(
                            org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                            org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                            android.view.Gravity.TOP));
        }
        if (actionBar != null) {
            final org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(MENU_SEARCH, R.drawable.outline_header_search, getResourceProvider());
            menu.addItem(MENU_CLOUD, R.drawable.cloud_sync, getResourceProvider());
            actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == MENU_CLOUD) {
                        presentFragment(new OpenExteraCloudActivity());
                    } else if (id == MENU_SEARCH) {
                        showSettingsSearchDialog();
                    }
                }
            });
        }
        return view;
    }

    /**
     * Базовый класс в onInsets обнуляет верхний отступ списка. Нам он нужен: actionBar
     * прозрачный и не занимает места, поэтому без отступа контент уезжает под строку
     * состояния и логотип срезает.
     */
    /**
     * У экстеры на корневом экране адаптивный фон НЕ включается вовсе:
     * {@code BasePreferencesActivity.createView} вызывает {@code setAdaptiveBackground}
     * только при {@code !hasHeaderCell()}, а у {@code MainPreferencesActivity}
     * {@code hasHeaderCell()} возвращает true. Шапка остаётся прозрачной всегда,
     * заголовок не проявляется даже при прокрутке.
     */
    @Override
    protected void setupAdaptiveBackground() {
    }

    /** Блюр-подложка NagramX закрывает верх логотипа; у экстеры её нет. */
    @Override
    protected boolean needActionBarBlur() {
        return false;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(0, top, 0, bottom);
            listView.setClipToPadding(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExtera);
    }

    @Override
    public int getSearchGuid() {
        return 24000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_settings_old;
    }

    @Override
    public String getSearchPrefix() {
        return "OpenExtera";
    }

    @Override
    protected String getKey() {
        return "exteraless";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == generalRow) {
            presentFragment(new OpenExteraGeneralActivity());
        } else if (position == appearanceRow) {
            presentFragment(new OpenExteraAppearanceActivity());
        } else if (position == chatsRow) {
            presentFragment(new OpenExteraChatsActivity());
        } else if (position == pluginsRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        } else if (position == toolsRow) {
            presentFragment(new OpenExteraToolsActivity());
        } else if (position == otherRow) {
            presentFragment(new OpenExteraOtherActivity());
        } else if (position == channelRow) {
            getMessagesController().openByUserName("MoltenGram", this, 1);
        } else if (position == sourceRow) {
            org.telegram.messenger.browser.Browser.openUrl(getParentActivity(),
                    "https://github.com/MoltenSt4r/MoltenGram");
        } else if (position == creditsExteralessRow) {
            getMessagesController().openByUserName("exteraless", this, 1);
        } else if (position == creditsNagramXRow) {
            getMessagesController().openByUserName("NagramX", this, 1);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_ABOUT) {
                View view = new AboutHeaderCell(mContext);
                // У экстеры шапка лежит на фоне окна, а не в карточке-секции.
                view.setTag(RecyclerListView.TAG_NOT_SECTION);
                view.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == creditsHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraCreditsSection));
                    } else if (position == categoriesHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraCategories));
                    } else if (position == linksHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraLinks));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == generalRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraGeneral), R.drawable.msg_media, true);
                    } else if (position == appearanceRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraAppearance), R.drawable.msg_theme, true);
                    } else if (position == chatsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraChats), R.drawable.msg_discussion, true);
                    } else if (position == pluginsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraPlugins), R.drawable.msg_plugins, true);
                    } else if (position == toolsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraTools), R.drawable.baseline_build_outline_24, true);
                    } else if (position == otherRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraOther), R.drawable.msg_fave, false);
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.ProfileChannel),
                                "@MoltenGram", R.drawable.msg_channel, true);
                    } else if (position == sourceRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraSource),
                                "GitHub", R.drawable.msg_language, false);
                    } else if (position == creditsExteralessRow) {
                        cell.setTextAndValueAndIcon("exteraless",
                                "@exteraless", R.drawable.msg_channel, true);
                    } else if (position == creditsNagramXRow) {
                        cell.setTextAndValueAndIcon("NagramX",
                                "@NagramX", R.drawable.msg_channel, false);
                    }
                    // ВАЖНО: только после setTextAndIcon* — они сбрасывают imageLeft в 16dp.
                    // Метрики сняты с 12.9.0 (420 dpi): иконка 88px от края экрана, текст 219px,
                    // то есть 21dp и 71dp вместо дефолтных 16dp и 58dp.
                    cell.setImageLeft(21);
                    cell.setOffsetFromImage(71);
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == creditsDividerRow) {
                        cell.setText(null);
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_TEXT;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == aboutRow) {
                return TYPE_ABOUT;
            } else if (position == categoriesHeaderRow || position == linksHeaderRow
                    || position == creditsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == categoriesDividerRow || position == linksDividerRow) {
                // Промежуток между секциями — тень фиксированной высоты. TextInfoPrivacyCell
                // здесь держал высоту под подпись, которой нет, и оставлял пустое поле.
                return TYPE_SHADOW;
            } else if (position == creditsDividerRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }
    }
}
