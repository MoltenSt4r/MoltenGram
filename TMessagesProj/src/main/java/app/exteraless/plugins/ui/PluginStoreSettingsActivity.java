package app.exteraless.plugins.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class PluginStoreSettingsActivity extends BaseNekoSettingsActivity {

    private int searchHeaderRow = -1;
    private int hideOldVersionsRow = -1;
    private int deepSearchRow = -1;
    private int autoUpdateRow = -1;
    private int pageSizeRow = -1;
    private int searchDividerRow = -1;

    private int sourcesHeaderRow = -1;
    private int addChannelRow = -1;
    private int channelsStartRow = -1;
    private int channelsEndRow = -1;
    private int sourcesDividerRow = -1;

    private int actionsHeaderRow = -1;
    private int clearCacheRow = -1;
    private int actionsDividerRow = -1;
    private int infoRow = -1;

    private final PluginStoreConfig config = PluginStoreConfig.getInstance();
    private final List<String> currentChannels = new ArrayList<>();

    @Override
    protected String getKey() {
        return "plugin_store_settings";
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        searchHeaderRow = addRow("searchHeader");
        hideOldVersionsRow = addRow("hideOldVersions");
        deepSearchRow = addRow("deepSearch");
        autoUpdateRow = addRow("autoUpdate");
        pageSizeRow = addRow("pageSize");
        searchDividerRow = addRow();

        sourcesHeaderRow = addRow("sourcesHeader");
        addChannelRow = addRow("addChannel");

        currentChannels.clear();
        currentChannels.addAll(config.getSourceChannels());
        if (!currentChannels.isEmpty()) {
            channelsStartRow = rowCount;
            for (String ch : currentChannels) {
                addRow("ch_" + ch);
            }
            channelsEndRow = rowCount;
        } else {
            channelsStartRow = -1;
            channelsEndRow = -1;
        }
        sourcesDividerRow = addRow();

        actionsHeaderRow = addRow("actionsHeader");
        clearCacheRow = addRow("clearCache");
        actionsDividerRow = addRow();
        infoRow = addRow("info");
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.PluginsStoreSettings);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == hideOldVersionsRow) {
            boolean next = !config.isHideOldVersionsEnabled();
            config.setHideOldVersionsEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == deepSearchRow) {
            boolean next = !config.isDeepSearchEnabled();
            config.setDeepSearchEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == autoUpdateRow) {
            boolean next = !config.isAutoUpdateEnabled();
            config.setAutoUpdateEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == pageSizeRow) {
            showPageSizeDialog();
        } else if (position == addChannelRow) {
            showAddChannelDialog();
        } else if (position >= channelsStartRow && position < channelsEndRow) {
            int idx = position - channelsStartRow;
            if (idx >= 0 && idx < currentChannels.size()) {
                String ch = currentChannels.get(idx);
                showChannelOptionsDialog(ch);
            }
        } else if (position == clearCacheRow) {
            config.clearCache();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.PluginsStoreSettingsClearCacheDone)).show();
        }
    }

    private void showPageSizeDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.PluginsStoreSettingsPageSize));
        builder.setMessage(getString(R.string.PluginsStoreSettingsPageSizePrompt));

        final EditText editText = new EditText(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint("1–100");
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setSingleLine(true);
        editText.setText(String.valueOf(config.getPageSize()));
        editText.setSelection(editText.getText().length());

        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);

        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            String input = editText.getText().toString().trim();
            try {
                int val = Integer.parseInt(input);
                val = Math.max(PluginStoreConfig.MIN_PAGE_SIZE, Math.min(PluginStoreConfig.MAX_PAGE_SIZE, val));
                config.setPageSize(val);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(pageSizeRow);
                }
            } catch (Exception ignored) {
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showAddChannelDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.PluginsStoreSettingsAddChannel));
        builder.setMessage(getString(R.string.PluginsStoreSettingsAddChannelPrompt));

        final EditText editText = new EditText(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint("@channel_username");
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setSingleLine(true);

        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);

        builder.setPositiveButton(getString(R.string.Add), (dialog, which) -> {
            String input = editText.getText().toString().trim();
            if (config.addSourceChannel(input)) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.PluginsStoreSettingsAddChannelSuccess)).show();
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            } else {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.PluginsStoreSettingsAddChannelError)).show();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showChannelOptionsDialog(String channel) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("@" + channel);

        CharSequence[] items = new CharSequence[]{
                getString(R.string.PluginsStoreOpenChannel),
                getString(R.string.Delete)
        };

        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                String cleanChannel = channel != null ? channel.trim() : "";
                if (cleanChannel.startsWith("@")) {
                    cleanChannel = cleanChannel.substring(1);
                }
                final String targetUsername = cleanChannel;
                org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername req = new org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername();
                req.username = targetUsername;
                org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                    if (res instanceof org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer) {
                        org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer peer = (org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer) res;
                        MessagesController.getInstance(currentAccount).putUsers(peer.users, false);
                        MessagesController.getInstance(currentAccount).putChats(peer.chats, false);
                        if (!peer.chats.isEmpty()) {
                            android.os.Bundle args = new android.os.Bundle();
                            args.putLong("chat_id", peer.chats.get(0).id);
                            presentFragment(new org.telegram.ui.ChatActivity(args));
                            return;
                        }
                    }
                    BulletinFactory.of(this)
                            .createSimpleBulletin(R.drawable.msg_info, "Канал @" + targetUsername + " переехал или недоступен")
                            .show();
                }));
            } else if (which == 1) {
                config.removeSourceChannel(channel);
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
        showDialog(builder.create());
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == searchHeaderRow || position == sourcesHeaderRow || position == actionsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == hideOldVersionsRow || position == deepSearchRow || position == autoUpdateRow) {
                return TYPE_CHECK;
            } else if (position == pageSizeRow) {
                return TYPE_SETTINGS;
            } else if (position == addChannelRow || (position >= channelsStartRow && position < channelsEndRow) || position == clearCacheRow) {
                return TYPE_TEXT;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == searchDividerRow || position == sourcesDividerRow || position == actionsDividerRow) {
                return TYPE_SHADOW;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == searchHeaderRow) {
                        cell.setText(getString(R.string.Search));
                    } else if (position == sourcesHeaderRow) {
                        cell.setText(getString(R.string.PluginsStoreSettingsSources));
                    } else if (position == actionsHeaderRow) {
                        cell.setText(getString(R.string.Actions));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == addChannelRow) {
                        cell.setTextAndIcon(getString(R.string.PluginsStoreSettingsAddChannel), R.drawable.msg_add, true);
                    } else if (position >= channelsStartRow && position < channelsEndRow) {
                        int idx = position - channelsStartRow;
                        if (idx >= 0 && idx < currentChannels.size()) {
                            String ch = currentChannels.get(idx);
                            boolean isCustom = config.isCustomChannel(ch);
                            cell.setTextAndValueAndIcon("@" + ch, isCustom ? getString(R.string.Custom) : null, R.drawable.msg_channel, idx != currentChannels.size() - 1);
                        }
                    } else if (position == clearCacheRow) {
                        cell.setTextAndIcon(getString(R.string.PluginsStoreSettingsClearCache), R.drawable.msg_delete, false);
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == hideOldVersionsRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.PluginsStoreSettingsHideOld), getString(R.string.PluginsStoreSettingsHideOldDesc), config.isHideOldVersionsEnabled(), true, true);
                    } else if (position == deepSearchRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.PluginsStoreSettingsDeepSearch), getString(R.string.PluginsStoreSettingsDeepSearchDesc), config.isDeepSearchEnabled(), true, true);
                    } else if (position == autoUpdateRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.PluginsStoreSettingsAutoUpdate), getString(R.string.PluginsStoreSettingsAutoUpdateDesc), config.isAutoUpdateEnabled(), false, true);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == pageSizeRow) {
                        cell.setTextAndValue(getString(R.string.PluginsStoreSettingsPageSize), String.valueOf(config.getPageSize()), false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == infoRow) {
                        cell.setText(getString(R.string.PluginsStoreSettingsDeepSearchDesc) + "\n\n" + getString(R.string.PluginsStoreSettingsHideOldDesc));
                    }
                    break;
                }
            }
        }
    }
}
