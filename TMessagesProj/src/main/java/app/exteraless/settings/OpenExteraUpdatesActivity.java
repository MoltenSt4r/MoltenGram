package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Date;

import app.exteraless.ota.MoltenGramOtaManager;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class OpenExteraUpdatesActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private static final int TYPE_ABOUT = 100;

    private int headerRow = -1;

    private int updateCardHeaderRow = -1;
    private int updateCardVersionRow = -1;
    private int updateCardActionRow = -1;
    private int updateCardChangelogRow = -1;
    private int updateCardDividerRow = -1;

    private int settingsHeaderRow = -1;
    private int autoCheckRow = -1;
    private int includeCanaryRow = -1;
    private int autoDownloadRow = -1;
    private int settingsDividerRow = -1;

    private int actionsHeaderRow = -1;
    private int checkNowRow = -1;
    private int changelogRow = -1;
    private int channelRow = -1;
    private int actionsDividerRow = -1;

    private final MoltenGramOtaManager otaManager = MoltenGramOtaManager.getInstance();

    @Override
    public boolean onFragmentCreate() {
        otaManager.dismissUpdateNotification();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.appUpdateAvailable);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.appUpdateAvailable);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appUpdateAvailable) {
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        headerRow = addRow("header");

        if (otaManager.isUpdateAvailable()) {
            updateCardHeaderRow = addRow("updateCardHeader");
            updateCardVersionRow = addRow("updateCardVersion");
            updateCardActionRow = addRow("updateCardAction");
            if (!TextUtils.isEmpty(otaManager.getPendingUpdate().changelog)) {
                updateCardChangelogRow = addRow("updateCardChangelog");
            } else {
                updateCardChangelogRow = -1;
            }
            updateCardDividerRow = addRow();
        } else {
            updateCardHeaderRow = -1;
            updateCardVersionRow = -1;
            updateCardActionRow = -1;
            updateCardChangelogRow = -1;
            updateCardDividerRow = -1;
        }

        settingsHeaderRow = addRow("settingsHeader");
        autoCheckRow = addRow("autoCheck");
        includeCanaryRow = addRow("includeCanary");
        autoDownloadRow = addRow("autoDownload");
        settingsDividerRow = addRow();

        actionsHeaderRow = addRow("actionsHeader");
        checkNowRow = addRow("checkNow");
        changelogRow = addRow("changelog");
        channelRow = addRow("channel");
        actionsDividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExteraUpdates);
    }

    @Override
    protected String getKey() {
        return "ota_updates";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private String getAutoDownloadModeString() {
        int mode = otaManager.getAutoDownloadMode();
        switch (mode) {
            case MoltenGramOtaManager.AUTO_DOWNLOAD_WIFI:
                return getString(R.string.OpenExteraUpdatesAutoDownloadWifi);
            case MoltenGramOtaManager.AUTO_DOWNLOAD_ALWAYS:
                return getString(R.string.OpenExteraUpdatesAutoDownloadAlways);
            case MoltenGramOtaManager.AUTO_DOWNLOAD_NEVER:
            default:
                return getString(R.string.OpenExteraUpdatesAutoDownloadAsk);
        }
    }

    private String getLastCheckString() {
        long time = otaManager.getLastCheckTime();
        if (time <= 0) {
            return getString(R.string.OpenExteraUpdatesLastCheckNever);
        }
        return DateFormat.format("dd.MM.yyyy HH:mm", new Date(time)).toString();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == autoCheckRow) {
            boolean next = !otaManager.isOtaEnabled();
            otaManager.setOtaEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == includeCanaryRow) {
            boolean next = !otaManager.isIncludeCanary();
            otaManager.setIncludeCanary(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == autoDownloadRow) {
            showAutoDownloadDialog();
        } else if (position == checkNowRow) {
            performCheckNow();
        } else if (position == updateCardActionRow) {
            handleUpdateCardAction();
        } else if (position == changelogRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/MoltenSt4r/MoltenGram/releases");
        } else if (position == channelRow) {
            getMessagesController().openByUserName("MoltenGram", this, 1);
        }
    }

    private void handleUpdateCardAction() {
        if (!otaManager.isUpdateAvailable()) {
            return;
        }
        if (otaManager.isDownloaded()) {
            otaManager.installUpdate(getParentActivity());
        } else if (otaManager.isDownloading()) {
            otaManager.cancelDownload();
            updateRows();
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        } else {
            otaManager.startDownload(new MoltenGramOtaManager.DownloadCallback() {
                @Override
                public void onProgress(float progress) {
                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                }

                @Override
                public void onComplete(java.io.File file) {
                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                    BulletinFactory.of(OpenExteraUpdatesActivity.this).createSimpleBulletin(R.raw.done,
                            getString(R.string.OpenExteraUpdatesInstallNow)).show();
                }

                @Override
                public void onError(String error) {
                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                    if (!TextUtils.isEmpty(error)) {
                        BulletinFactory.of(OpenExteraUpdatesActivity.this).createErrorBulletin(error).show();
                    }
                }
            });
            updateRows();
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        }
    }

    private void showAutoDownloadDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        builder.setTitle(getString(R.string.OpenExteraUpdatesAutoDownload));

        CharSequence[] items = new CharSequence[]{
                getString(R.string.OpenExteraUpdatesAutoDownloadAsk),
                getString(R.string.OpenExteraUpdatesAutoDownloadWifi),
                getString(R.string.OpenExteraUpdatesAutoDownloadAlways)
        };

        builder.setItems(items, (dialog, which) -> {
            otaManager.setAutoDownloadMode(which);
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void performCheckNow() {
        BulletinFactory.of(this).createSimpleBulletin(R.raw.dots_loading,
                getString(R.string.OpenExteraUpdatesChecking)).show();

        otaManager.checkUpdates(true, getParentActivity(), (update, error) -> {
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }

            if (!TextUtils.isEmpty(error)) {
                BulletinFactory.of(OpenExteraUpdatesActivity.this).createErrorBulletin(error).show();
            } else if (update != null) {
                BulletinFactory.of(OpenExteraUpdatesActivity.this).createSimpleBulletin(R.raw.ic_download,
                        formatString("OpenExteraUpdatesAvailable", R.string.OpenExteraUpdatesAvailable) + ": " + update.version).show();
            } else {
                BulletinFactory.of(OpenExteraUpdatesActivity.this).createSimpleBulletin(R.raw.done,
                        getString(R.string.OpenExteraUpdatesLatest)).show();
            }
        });
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_ABOUT) {
                View view = new AboutHeaderCell(mContext);
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
                    if (position == updateCardHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraUpdatesAvailable));
                    } else if (position == settingsHeaderRow) {
                        cell.setText(getString(R.string.AutoCheckUpdateSwitch));
                    } else if (position == actionsHeaderRow) {
                        cell.setText(getString(R.string.CheckUpdate));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == autoCheckRow) {
                        cell.setTextAndCheck(getString(R.string.OpenExteraUpdatesAutoCheck),
                                otaManager.isOtaEnabled(), true);
                    } else if (position == includeCanaryRow) {
                        cell.setTextAndCheck(getString(R.string.OpenExteraUpdatesCanary),
                                otaManager.isIncludeCanary(), false);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == autoDownloadRow) {
                        cell.setTextAndValue(getString(R.string.OpenExteraUpdatesAutoDownload),
                                getAutoDownloadModeString(), false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == updateCardVersionRow) {
                        MoltenGramOtaManager.OtaUpdate u = otaManager.getPendingUpdate();
                        String sizeStr = u != null && u.fileSize > 0 ? AndroidUtilities.formatFileSize(u.fileSize) : "";
                        cell.setTextAndValueAndIcon(u != null ? u.title : "", sizeStr, R.drawable.msg_download, true);
                    } else if (position == updateCardActionRow) {
                        if (otaManager.isDownloaded()) {
                            cell.setTextAndIcon(getString(R.string.OpenExteraUpdatesInstallNow), R.drawable.baseline_check_circle_24, false);
                        } else if (otaManager.isDownloading()) {
                            int pct = (int) (otaManager.getDownloadProgress() * 100);
                            cell.setTextAndIcon(formatString("OpenExteraUpdatesDownloading", R.string.OpenExteraUpdatesDownloading, pct), R.drawable.msg_retry, false);
                        } else {
                            cell.setTextAndIcon(getString(R.string.OpenExteraUpdatesDownloadNow), R.drawable.msg_download, false);
                        }
                    } else if (position == checkNowRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraUpdatesCheckNow),
                                getLastCheckString(), R.drawable.sync_outline_28, true);
                    } else if (position == changelogRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraUpdatesChangelog),
                                "GitHub", R.drawable.msg_help, true);
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraUpdatesChannel),
                                "@MoltenGram", R.drawable.msg_channel, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == updateCardChangelogRow) {
                        MoltenGramOtaManager.OtaUpdate u = otaManager.getPendingUpdate();
                        cell.setText(u != null && !TextUtils.isEmpty(u.changelog) ? u.changelog : null);
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == actionsDividerRow) {
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
            int pos = holder.getAdapterPosition();
            if (pos == updateCardVersionRow || pos == updateCardChangelogRow) {
                return false;
            }
            return super.isEnabled(holder);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return TYPE_ABOUT;
            } else if (position == updateCardHeaderRow || position == settingsHeaderRow || position == actionsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == autoCheckRow || position == includeCanaryRow) {
                return TYPE_CHECK;
            } else if (position == autoDownloadRow) {
                return TYPE_SETTINGS;
            } else if (position == updateCardVersionRow || position == updateCardActionRow || position == checkNowRow || position == changelogRow || position == channelRow) {
                return TYPE_TEXT;
            } else if (position == updateCardChangelogRow || position == actionsDividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == updateCardDividerRow || position == settingsDividerRow) {
                return TYPE_SHADOW;
            }
            return TYPE_TEXT;
        }
    }
}
