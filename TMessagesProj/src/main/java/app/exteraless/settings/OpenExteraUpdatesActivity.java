package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;

import java.util.Date;

import app.exteraless.ota.MoltenGramOtaManager;
import app.exteraless.shizuku.ShizukuConfig;
import app.exteraless.shizuku.ShizukuController;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class OpenExteraUpdatesActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private final MoltenGramOtaManager otaManager = MoltenGramOtaManager.getInstance();

    private int statusHeaderRow = -1;
    private int statusRow = -1;
    private int actionRow = -1;
    private int statusDividerRow = -1;

    private int aboutHeaderRow = -1;
    private int versionRow = -1;
    private int buildNumberRow = -1;
    private int changelogRow = -1;
    private int channelRow = -1;
    private int aboutDividerRow = -1;

    private int settingsHeaderRow = -1;
    private int includeCanaryRow = -1;
    private int autoCheckRow = -1;
    private int shizukuSilentRow = -1;
    private int autoDownloadRow = -1;
    private int settingsDividerRow = -1;

    @Override
    public boolean onFragmentCreate() {
        otaManager.dismissUpdateNotification();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.appUpdateAvailable);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);

        if (!otaManager.isUpdateAvailable() && !otaManager.isChecking() && !otaManager.isDownloading()) {
            otaManager.checkUpdates(false, getParentActivity(), (u, err) -> {
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.appUpdateAvailable);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appUpdateAvailable || id == NotificationCenter.didSetNewTheme) {
            AndroidUtilities.runOnUIThread(() -> {
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected String getKey() {
        return "ota_updates";
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExteraUpdates);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        statusHeaderRow = addRow("statusHeader");
        statusRow = addRow("status");
        actionRow = addRow("action");
        statusDividerRow = addRow();

        aboutHeaderRow = addRow("aboutHeader");
        versionRow = addRow("version");
        buildNumberRow = addRow("buildNumber");
        changelogRow = addRow("changelog");
        channelRow = addRow("channel");
        aboutDividerRow = addRow();

        settingsHeaderRow = addRow("settingsHeader");
        includeCanaryRow = addRow("includeCanary");
        autoCheckRow = addRow("autoCheck");
        shizukuSilentRow = addRow("shizukuSilent");
        autoDownloadRow = addRow("autoDownload");
        settingsDividerRow = addRow();
    }

    private String getCurrentVersionString() {
        String curCommitShort = !TextUtils.isEmpty(BuildConfig.BUILD_COMMIT_ID) ?
                (BuildConfig.BUILD_COMMIT_ID.length() > 7 ? BuildConfig.BUILD_COMMIT_ID.substring(0, 7) : BuildConfig.BUILD_COMMIT_ID) : "";
        return "v" + BuildVars.BUILD_VERSION_STRING + (!TextUtils.isEmpty(curCommitShort) ? "-" + curCommitShort : "") + ("staging".equals(BuildConfig.BUILD_TYPE) ? " (Canary)" : "");
    }

    private String getLastCheckString() {
        long time = otaManager.getLastCheckTime();
        if (time <= 0) {
            return getString(R.string.OpenExteraUpdatesLastCheckNever);
        }
        return DateFormat.format("dd.MM.yyyy HH:mm", new Date(time)).toString();
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

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == actionRow) {
            handleActionClick();
        } else if (position == changelogRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/MoltenSt4r/MoltenGram/releases");
        } else if (position == channelRow) {
            getMessagesController().openByUserName("MoltenGram", this, 1);
        } else if (position == includeCanaryRow) {
            boolean next = !otaManager.isIncludeCanary();
            otaManager.setIncludeCanary(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
            recheckUpdates();
        } else if (position == autoCheckRow) {
            boolean next = !otaManager.isOtaEnabled();
            otaManager.setOtaEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == shizukuSilentRow) {
            boolean next = !ShizukuConfig.getInstance().isSilentUpdatesEnabled();
            ShizukuConfig.getInstance().setSilentUpdatesEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
            if (next && !ShizukuController.getInstance().hasPermission()) {
                if (!ShizukuController.getInstance().isAvailable()) {
                    ShizukuController.getInstance().openShizukuApp(getParentActivity());
                } else {
                    ShizukuController.getInstance().requestPermission(ShizukuController.REQUEST_CODE_SHIZUKU);
                }
            }
        } else if (position == autoDownloadRow) {
            showAutoDownloadDialog();
        }
    }

    private void handleActionClick() {
        if (otaManager.isUpdateAvailable()) {
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
                        updateRows();
                        if (listAdapter != null) listAdapter.notifyDataSetChanged();
                        BulletinFactory.of(OpenExteraUpdatesActivity.this).createSimpleBulletin(R.raw.done,
                                getString(R.string.OpenExteraUpdatesInstallNow)).show();
                    }

                    @Override
                    public void onError(String error) {
                        updateRows();
                        if (listAdapter != null) listAdapter.notifyDataSetChanged();
                        if (!TextUtils.isEmpty(error)) {
                            BulletinFactory.of(OpenExteraUpdatesActivity.this).createErrorBulletin(error).show();
                        }
                    }
                });
                updateRows();
                if (listAdapter != null) listAdapter.notifyDataSetChanged();
            }
        } else {
            performCheckNow();
        }
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
        updateRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private void recheckUpdates() {
        otaManager.checkUpdates(true, getParentActivity(), (update, error) -> {
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        updateRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private void showAutoDownloadDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
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

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == statusHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraUpdates));
                    } else if (position == aboutHeaderRow) {
                        cell.setText("О клиенте");
                    } else if (position == settingsHeaderRow) {
                        cell.setText("Параметры обновления");
                    }
                    break;
                }
                case TYPE_DETAIL_SETTINGS: {
                    TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
                    if (position == statusRow) {
                        if (otaManager.isChecking()) {
                            cell.setTextAndValue("Проверка обновлений...", "Связь с сервером обновлений...", false);
                        } else if (otaManager.isUpdateAvailable()) {
                            MoltenGramOtaManager.OtaUpdate u = otaManager.getPendingUpdate();
                            String sizeStr = u != null && u.fileSize > 0 ? AndroidUtilities.formatFileSize(u.fileSize) : "";
                            String sub = !TextUtils.isEmpty(sizeStr) ? ("Размер: " + sizeStr) : "";
                            if (u != null && !TextUtils.isEmpty(u.changelog)) {
                                sub += (sub.isEmpty() ? "" : " • ") + u.changelog;
                            }
                            cell.setTextAndValue("Доступно обновление: MoltenGram " + (u != null ? u.version : ""), sub, false);
                        } else {
                            cell.setTextAndValue(getString(R.string.OpenExteraUpdatesLatest), "Сейчас установлена " + getCurrentVersionString(), false);
                        }
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setIcon(0);
                    if (position == versionRow) {
                        cell.setTextAndValue("Версия MoltenGram", getCurrentVersionString(), true);
                        cell.setIcon(R.drawable.msg_info);
                    } else if (position == buildNumberRow) {
                        cell.setTextAndValue("Номер сборки", BuildConfig.VERSION_CODE + " (Telegram " + BuildVars.BUILD_VERSION_STRING + ")", true);
                        cell.setIcon(R.drawable.msg_permissions);
                    } else if (position == autoDownloadRow) {
                        cell.setTextAndValue(getString(R.string.OpenExteraUpdatesAutoDownload), getAutoDownloadModeString(), false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == actionRow) {
                        if (otaManager.isUpdateAvailable()) {
                            if (otaManager.isDownloaded()) {
                                cell.setTextAndIcon(getString(R.string.OpenExteraUpdatesInstallNow), R.drawable.baseline_check_circle_24, false);
                            } else if (otaManager.isDownloading()) {
                                int pct = (int) (otaManager.getDownloadProgress() * 100);
                                cell.setTextAndIcon(formatString("OpenExteraUpdatesDownloading", R.string.OpenExteraUpdatesDownloading, pct) + " (отмена)", R.drawable.msg_retry, false);
                            } else {
                                MoltenGramOtaManager.OtaUpdate u = otaManager.getPendingUpdate();
                                String sizeStr = u != null && u.fileSize > 0 ? " (" + AndroidUtilities.formatFileSize(u.fileSize) + ")" : "";
                                cell.setTextAndIcon(getString(R.string.OpenExteraUpdatesDownloadNow) + sizeStr, R.drawable.msg_download, false);
                            }
                        } else {
                            cell.setTextAndValueAndIcon(getString(R.string.OpenExteraUpdatesCheckNow), getLastCheckString(), R.drawable.sync_outline_28, false);
                        }
                    } else if (position == changelogRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraUpdatesChangelog), "GitHub", R.drawable.msg_help, true);
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraUpdatesChannel), "@MoltenGram", R.drawable.msg_channel, false);
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == includeCanaryRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.OpenExteraUpdatesCanary),
                                getString(R.string.OpenExteraUpdatesCanaryDesc), otaManager.isIncludeCanary(), true, true);
                    } else if (position == autoCheckRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.OpenExteraUpdatesAutoCheck),
                                getString(R.string.OpenExteraUpdatesAutoCheckDesc), otaManager.isOtaEnabled(), true, true);
                    } else if (position == shizukuSilentRow) {
                        cell.setTextAndValueAndCheck("Тихая установка с Shizuku",
                                "Автоматически обновлять в фоне без участия пользователя",
                                ShizukuConfig.getInstance().isSilentUpdatesEnabled(), true, false);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == statusHeaderRow || position == aboutHeaderRow || position == settingsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == statusRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == versionRow || position == buildNumberRow || position == autoDownloadRow) {
                return TYPE_SETTINGS;
            } else if (position == actionRow || position == changelogRow || position == channelRow) {
                return TYPE_TEXT;
            } else if (position == includeCanaryRow || position == autoCheckRow || position == shizukuSilentRow) {
                return TYPE_CHECK;
            } else if (position == statusDividerRow || position == aboutDividerRow || position == settingsDividerRow) {
                return TYPE_SHADOW;
            }
            return TYPE_TEXT;
        }
    }
}
