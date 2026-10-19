package app.exteraless.shizuku;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import rikka.shizuku.Shizuku;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class ShizukuSettingsActivity extends BaseNekoSettingsActivity implements ShizukuController.Listener {

    private int statusHeaderRow = -1;
    private int statusRow = -1;
    private int actionRow = -1;
    private int statusDividerRow = -1;

    private int featuresHeaderRow = -1;
    private int silentUpdatesRow = -1;
    private int silentApkRow = -1;
    private int unrestrictedBgRow = -1;
    private int notificationBoostRow = -1;
    private int silentPluginsRow = -1;
    private int featuresDividerRow = -1;

    private int systemHeaderRow = -1;
    private int trimCachesRow = -1;
    private int optimizeDexRow = -1;
    private int systemDividerRow = -1;
    private int infoRow = -1;

    private final ShizukuController shizuku = ShizukuController.getInstance();
    private final ShizukuConfig config = ShizukuConfig.getInstance();

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        AndroidUtilities.runOnUIThread(() -> {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                shizuku.setUnrestrictedBackground();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ShizukuOptimizationsApplied)).show();
            }
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
    };

    @Override
    public boolean onFragmentCreate() {
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener);
            shizuku.addListener(this);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
            shizuku.removeListener(this);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public void onShizukuStateChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
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
        return "shizuku_settings";
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        statusHeaderRow = addRow("statusHeader");
        statusRow = addRow("status");
        actionRow = addRow("action");
        statusDividerRow = addRow();

        featuresHeaderRow = addRow("featuresHeader");
        silentUpdatesRow = addRow("silentUpdates");
        silentApkRow = addRow("silentApk");
        unrestrictedBgRow = addRow("unrestrictedBg");
        notificationBoostRow = addRow("notificationBoost");
        silentPluginsRow = addRow("silentPlugins");
        featuresDividerRow = addRow();

        systemHeaderRow = addRow("systemHeader");
        trimCachesRow = addRow("trimCaches");
        optimizeDexRow = addRow("optimizeDex");
        systemDividerRow = addRow();
        infoRow = addRow("info");
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.ShizukuSettings);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == actionRow) {
            if (!shizuku.isAvailable()) {
                shizuku.openShizukuApp(getParentActivity());
            } else if (!shizuku.hasPermission()) {
                shizuku.requestPermission(ShizukuController.REQUEST_CODE_SHIZUKU);
            } else {
                shizuku.openShizukuApp(getParentActivity());
            }
        } else if (position == silentUpdatesRow) {
            boolean next = !config.isSilentUpdatesEnabled();
            config.setSilentUpdatesEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == silentApkRow) {
            boolean next = !config.isSilentApkEnabled();
            config.setSilentApkEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == unrestrictedBgRow) {
            boolean next = !config.isUnrestrictedBackgroundEnabled();
            config.setUnrestrictedBackgroundEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
            if (next && shizuku.hasPermission()) {
                Utilities.globalQueue.postRunnable(() -> {
                    shizuku.setUnrestrictedBackground();
                    AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ShizukuOptimizationsApplied)).show());
                });
            }
        } else if (position == notificationBoostRow) {
            boolean next = !config.isNotificationBoostEnabled();
            config.setNotificationBoostEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
            if (next && shizuku.hasPermission()) {
                Utilities.globalQueue.postRunnable(() -> {
                    shizuku.boostNotificationPerformance();
                    AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.OEGeneralShizukuNotificationBoostApplied)).show());
                });
            }
        } else if (position == silentPluginsRow) {
            boolean next = !config.isSilentPluginUpdatesEnabled();
            config.setSilentPluginUpdatesEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == trimCachesRow) {
            if (!shizuku.hasPermission()) {
                shizuku.requestPermission(ShizukuController.REQUEST_CODE_SHIZUKU);
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                shizuku.trimCaches();
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ShizukuTrimCachesDone)).show());
            });
        } else if (position == optimizeDexRow) {
            if (!shizuku.hasPermission()) {
                shizuku.requestPermission(ShizukuController.REQUEST_CODE_SHIZUKU);
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                shizuku.optimizeDex();
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ShizukuOptimizationsApplied)).show());
            });
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == statusHeaderRow || position == featuresHeaderRow || position == systemHeaderRow) {
                return TYPE_HEADER;
            } else if (position == silentUpdatesRow || position == silentApkRow || position == unrestrictedBgRow || position == notificationBoostRow || position == silentPluginsRow) {
                return TYPE_CHECK;
            } else if (position == statusRow || position == actionRow || position == trimCachesRow || position == optimizeDexRow) {
                return TYPE_TEXT;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == statusDividerRow || position == featuresDividerRow || position == systemDividerRow) {
                return TYPE_SHADOW;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == statusHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.AuthAnotherClient));
                    } else if (position == featuresHeaderRow) {
                        cell.setText(getString(R.string.Features));
                    } else if (position == systemHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.StorageUsage));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == statusRow) {
                        String statusText;
                        if (shizuku.hasPermission()) {
                            statusText = getString(R.string.ShizukuStatusConnected);
                        } else if (shizuku.isAvailable()) {
                            statusText = getString(R.string.ShizukuStatusPermissionRequired);
                        } else {
                            statusText = getString(R.string.ShizukuStatusDisconnected);
                        }
                        cell.setTextAndValueAndIcon("Shizuku", statusText, R.drawable.msg_permissions, true);
                    } else if (position == actionRow) {
                        if (!shizuku.isAvailable()) {
                            cell.setTextAndIcon(getString(R.string.ShizukuOpenApp), R.drawable.msg_link2_solar, false);
                        } else if (!shizuku.hasPermission()) {
                            cell.setTextAndIcon(getString(R.string.ShizukuRequestPermission), R.drawable.msg_permissions, false);
                        } else {
                            cell.setTextAndIcon(getString(R.string.ShizukuOpenApp), R.drawable.msg_link2_solar, false);
                        }
                    } else if (position == trimCachesRow) {
                        cell.setTextAndIcon(getString(R.string.ShizukuTrimCaches), R.drawable.msg_delete, true);
                    } else if (position == optimizeDexRow) {
                        cell.setTextAndIcon(getString(R.string.Speed), R.drawable.msg_speed, false);
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == silentUpdatesRow) {
                        cell.setTextAndCheck(getString(R.string.ShizukuSilentUpdates), config.isSilentUpdatesEnabled(), true);
                    } else if (position == silentApkRow) {
                        cell.setTextAndCheck(getString(R.string.ShizukuSilentApk), config.isSilentApkEnabled(), true);
                    } else if (position == unrestrictedBgRow) {
                        cell.setTextAndCheck(getString(R.string.ShizukuUnrestrictedBackground), config.isUnrestrictedBackgroundEnabled(), true);
                    } else if (position == notificationBoostRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralShizukuNotificationBoost), config.isNotificationBoostEnabled(), true);
                    } else if (position == silentPluginsRow) {
                        cell.setTextAndCheck(getString(R.string.ShizukuSilentPlugins), config.isSilentPluginUpdatesEnabled(), false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == infoRow) {
                        cell.setText(getString(R.string.ShizukuSilentUpdatesDesc) + "\n\n" + getString(R.string.ShizukuUnrestrictedBackgroundDesc));
                    }
                    break;
                }
            }
        }
    }
}
