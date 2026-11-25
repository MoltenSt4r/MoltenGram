package com.exteragram.messenger.preferences;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * Базовый экран настроек exteraGram — для dex-модулей плагинов.
 *
 * Плагины каталога подгружают собственный dex, скомпилированный против классов
 * exteraGram, и наследуют этот экран напрямую. Подстановка имён из
 * `extera_utils/class_aliases.py` работает только для Python, поэтому имя пакета
 * здесь настоящее, а не наше.
 */
public abstract class BasePreferencesActivity extends BaseFragment {

    protected UniversalRecyclerView listView;
    protected LinearLayoutManager layoutManager;

    public abstract String getTitle();

    public abstract void fillItems(ArrayList<UItem> items, UniversalAdapter adapter);

    public abstract void onClick(UItem item, View view, int position, float x, float y);

    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    public boolean hasWhiteActionBar() {
        return true;
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        layoutManager = (LinearLayoutManager) listView.getLayoutManager();
        contentView.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = contentView;
        return fragmentView;
    }

    @Override
    protected org.telegram.ui.Components.RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            org.telegram.utils.glass.BaseFragmentSimpleGlass.setListViewParams(listView, top, bottom, ActionBar.getCurrentActionBarHeight(), 0);
            listView.setClipToPadding(false);
        }
    }
}
