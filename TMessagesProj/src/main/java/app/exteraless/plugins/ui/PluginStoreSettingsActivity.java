package app.exteraless.plugins.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

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

    public static class ChannelSourceCell extends FrameLayout {
        private final BackupImageView avatarImageView;
        private final AvatarDrawable avatarDrawable;
        private final TextView nameTextView;
        private final TextView statusTextView;
        private final ImageView optionsImageView;
        private boolean needDivider;
        private String currentUsername;

        public ChannelSourceCell(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(58)));
            setBackground(Theme.getSelectorDrawable(false));

            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(20));
            addView(avatarImageView, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setScaleSize(0.85f);

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(Gravity.CENTER_VERTICAL);
            addView(textCol, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 70, 0, 48, 0));

            nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTextView.setTypeface(AndroidUtilities.bold());
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            statusTextView = new TextView(context);
            statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            statusTextView.setSingleLine(true);
            statusTextView.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            optionsImageView = new ImageView(context);
            optionsImageView.setImageResource(R.drawable.ic_ab_other);
            optionsImageView.setScaleType(ImageView.ScaleType.CENTER);
            optionsImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            addView(optionsImageView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            setWillNotDraw(false);
        }

        public void setChannel(String rawUsername, boolean isCustom, boolean divider, int currentAccount, Runnable onResolved) {
            this.needDivider = divider;
            String clean = rawUsername != null ? rawUsername.trim() : "";
            if (clean.startsWith("@")) clean = clean.substring(1);
            this.currentUsername = clean;

            TLObject obj = MessagesController.getInstance(currentAccount).getUserOrChat(clean);
            if (obj == null) {
                for (TLRPC.Chat c : MessagesController.getInstance(currentAccount).getChats().values()) {
                    if (c != null && clean.equalsIgnoreCase(c.username)) {
                        obj = c;
                        break;
                    }
                }
            }

            if (obj instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) obj;
                nameTextView.setText(chat.title != null ? chat.title : "@" + clean);
                statusTextView.setText("@" + clean);
                avatarDrawable.setInfo(currentAccount, chat);
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            } else if (obj instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) obj;
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
                statusTextView.setText("@" + clean);
                avatarDrawable.setInfo(currentAccount, user);
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            } else {
                nameTextView.setText("@" + clean);
                statusTextView.setText("@" + clean);
                avatarDrawable.setInfo(5, clean, null);
                avatarImageView.setImageDrawable(avatarDrawable);

                final String requestUsername = clean;
                TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                req.username = requestUsername;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                        TLRPC.TL_contacts_resolvedPeer peer = (TLRPC.TL_contacts_resolvedPeer) res;
                        MessagesController.getInstance(currentAccount).putUsers(peer.users, false);
                        MessagesController.getInstance(currentAccount).putChats(peer.chats, false);
                        if (requestUsername.equalsIgnoreCase(this.currentUsername)) {
                            if (!peer.chats.isEmpty()) {
                                TLRPC.Chat resolvedChat = peer.chats.get(0);
                                nameTextView.setText(resolvedChat.title != null ? resolvedChat.title : "@" + requestUsername);
                                avatarDrawable.setInfo(currentAccount, resolvedChat);
                                avatarImageView.setForUserOrChat(resolvedChat, avatarDrawable);
                            } else if (!peer.users.isEmpty()) {
                                TLRPC.User resolvedUser = peer.users.get(0);
                                nameTextView.setText(ContactsController.formatName(resolvedUser.first_name, resolvedUser.last_name));
                                avatarDrawable.setInfo(currentAccount, resolvedUser);
                                avatarImageView.setForUserOrChat(resolvedUser, avatarDrawable);
                            }
                        }
                        if (onResolved != null) {
                            onResolved.run();
                        }
                    }
                }));
            }
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawLine(AndroidUtilities.dp(70), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private class ListAdapter extends BaseListAdapter {

        private static final int TYPE_CHANNEL_SOURCE = 999;

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_CHANNEL_SOURCE) {
                ChannelSourceCell cell = new ChannelSourceCell(mContext);
                cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(cell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == searchHeaderRow || position == sourcesHeaderRow || position == actionsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == hideOldVersionsRow || position == deepSearchRow || position == autoUpdateRow) {
                return TYPE_CHECK;
            } else if (position == pageSizeRow) {
                return TYPE_SETTINGS;
            } else if (position >= channelsStartRow && position < channelsEndRow) {
                return TYPE_CHANNEL_SOURCE;
            } else if (position == addChannelRow || position == clearCacheRow) {
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
                case TYPE_CHANNEL_SOURCE: {
                    ChannelSourceCell cell = (ChannelSourceCell) holder.itemView;
                    int idx = position - channelsStartRow;
                    if (idx >= 0 && idx < currentChannels.size()) {
                        String ch = currentChannels.get(idx);
                        boolean isCustom = config.isCustomChannel(ch);
                        boolean divider = (idx != currentChannels.size() - 1);
                        cell.setChannel(ch, isCustom, divider, currentAccount, () -> {
                            if (listAdapter != null) {
                                listAdapter.notifyItemChanged(position);
                            }
                        });
                    }
                    break;
                }
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
