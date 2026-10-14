package app.exteraless.plugins.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

final class PluginUiItem extends UItem {

    private PluginUiItem(int viewType) {
        super(viewType, false);
    }

    static PluginUiItem check(int id, CharSequence text, int iconResId) {
        PluginUiItem item = new PluginUiItem(UniversalAdapter.VIEW_TYPE_CHECK);
        item.id = id;
        item.text = text;
        item.iconResId = iconResId;
        return item;
    }

    static UItem engineToggle(int id, CharSequence text, boolean checked) {
        return EngineToggleFactory.of(id, text, checked);
    }

    static UItem storeBanner(int id, CharSequence title, CharSequence desc) {
        return StoreBannerFactory.of(id, title, desc);
    }

    static PluginUiItem fullscreen(View view, int minusHeight, boolean minusPadding) {
        PluginUiItem item = new PluginUiItem(UniversalAdapter.VIEW_TYPE_FULLSCREEN_CUSTOM);
        item.view = view;
        item.intValue = minusHeight;
        item.flags = minusPadding ? 1 : 0;
        item.transparent = true;
        return item;
    }

    @Override
    protected boolean contentsEquals(AdapterWithDiffUtils.Item candidate) {
        if (!(candidate instanceof PluginUiItem)) {
            return false;
        }
        PluginUiItem item = (PluginUiItem) candidate;
        if (viewType != item.viewType || id != item.id || enabled != item.enabled) {
            return false;
        }
        if (viewType == UniversalAdapter.VIEW_TYPE_CHECK) {
            return checked == item.checked
                    && iconResId == item.iconResId
                    && multiline == item.multiline
                    && TextUtils.equals(text, item.text)
                    && TextUtils.equals(textValue, item.textValue);
        }
        if (viewType == UniversalAdapter.VIEW_TYPE_FULLSCREEN_CUSTOM) {
            return view == item.view
                    && intValue == item.intValue
                    && flags == item.flags
                    && transparent == item.transparent;
        }
        return super.contentsEquals(candidate);
    }

    private static final class EngineToggleFactory extends UItem.UItemFactory<TextCheckCell> {
        static {
            setup(new EngineToggleFactory());
        }

        @Override
        public TextCheckCell createView(Context context, RecyclerListView listView,
                                        int currentAccount, int classGuid,
                                        Theme.ResourcesProvider resourcesProvider) {
            TextCheckCell cell = new TextCheckCell(context, resourcesProvider);
            cell.setDrawCheckRipple(true);
            cell.setTypeface(AndroidUtilities.bold());
            cell.setHeight(56);
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            TextCheckCell cell = (TextCheckCell) view;
            cell.setEnabled(item.enabled, null);
            cell.setIcon(0);
            cell.setTextAndCheck(item.text, item.checked, divider);
        }

        @Override
        public boolean equals(UItem first, UItem second) {
            return first.id == second.id;
        }

        @Override
        public boolean contentsEquals(UItem first, UItem second) {
            return first.id == second.id
                    && first.checked == second.checked
                    && first.enabled == second.enabled
                    && TextUtils.equals(first.text, second.text);
        }

        static UItem of(int id, CharSequence text, boolean checked) {
            UItem item = UItem.ofFactory(EngineToggleFactory.class);
            item.id = id;
            item.text = text;
            item.checked = checked;
            return item;
        }
    }

    public static final class StoreBannerCell extends FrameLayout {
        private final TextView titleView;
        private final TextView descView;

        public StoreBannerCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setMinimumHeight(AndroidUtilities.dp(64));
            setBackground(Theme.getSelectorDrawable(false));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            FrameLayout iconContainer = new FrameLayout(context);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(AndroidUtilities.dp(12));
            iconBg.setColor(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 0.14f));
            iconContainer.setBackground(iconBg);

            ImageView iconView = new ImageView(context);
            iconView.setImageResource(R.drawable.menu_shop);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), PorterDuff.Mode.SRC_IN));
            iconContainer.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
            row.addView(iconContainer, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 14, 0, 8, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            descView = new TextView(context);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            descView.setMaxLines(2);
            descView.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            ImageView arrowView = new ImageView(context);
            arrowView.setImageResource(R.drawable.msg_arrowright);
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            row.addView(arrowView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL));
        }

        public void set(CharSequence title, CharSequence desc) {
            titleView.setText(title);
            descView.setText(desc);
        }
    }

    private static final class StoreBannerFactory extends UItem.UItemFactory<StoreBannerCell> {
        static {
            setup(new StoreBannerFactory());
        }

        @Override
        public StoreBannerCell createView(Context context, RecyclerListView listView,
                                          int currentAccount, int classGuid,
                                          Theme.ResourcesProvider resourcesProvider) {
            return new StoreBannerCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            if (view instanceof StoreBannerCell) {
                ((StoreBannerCell) view).set(item.text, item.subtext);
            }
        }

        @Override
        public boolean equals(UItem first, UItem second) {
            return first.id == second.id;
        }

        @Override
        public boolean contentsEquals(UItem first, UItem second) {
            return first.id == second.id
                    && TextUtils.equals(first.text, second.text)
                    && TextUtils.equals(first.subtext, second.subtext);
        }

        static UItem of(int id, CharSequence title, CharSequence desc) {
            UItem item = UItem.ofFactory(StoreBannerFactory.class);
            item.id = id;
            item.text = title;
            item.subtext = desc;
            return item;
        }
    }
}

