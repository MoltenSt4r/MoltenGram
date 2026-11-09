package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Раздел «Инструменты» (Tools):
 * Объединяет Voice Changer, AyuMoments, Tor VPN, Device Spoofing и Shizuku.
 */
public class OpenExteraToolsActivity extends BaseNekoSettingsActivity {

    private int toolsHeaderRow;
    private int voiceChangerRow;
    private int ayuMomentsRow;
    private int torRow;
    private int deviceSpoofRow;
    private int shizukuRow;
    private int toolsDividerRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        toolsHeaderRow = addRow("toolsHeader");
        voiceChangerRow = addRow("voiceChanger");
        ayuMomentsRow = addRow("ayuMoments");
        torRow = addRow("tor");
        deviceSpoofRow = addRow("deviceSpoof");
        shizukuRow = addRow("shizuku");
        toolsDividerRow = addRow();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null && listView != null && !listView.isComputingLayout()) {
            if (voiceChangerRow != -1) {
                listAdapter.notifyItemChanged(voiceChangerRow);
            }
            if (deviceSpoofRow != -1) {
                listAdapter.notifyItemChanged(deviceSpoofRow);
            }
            if (torRow != -1) {
                listAdapter.notifyItemChanged(torRow);
            }
            if (shizukuRow != -1) {
                listAdapter.notifyItemChanged(shizukuRow);
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExteraTools);
    }

    @Override
    public int getSearchGuid() {
        return 28000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.menu_premium_tools;
    }

    @Override
    public String getSearchPrefix() {
        return "OETools";
    }

    @Override
    protected String getKey() {
        return "exteraless_tools";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == voiceChangerRow) {
            presentFragment(new OpenExteraVoiceChangerActivity());
        } else if (position == ayuMomentsRow) {
            presentFragment(new OpenExteraAyuMomentsActivity());
        } else if (position == torRow) {
            presentFragment(new app.exteraless.tor.TorSettingsActivity());
        } else if (position == deviceSpoofRow) {
            presentFragment(new app.exteraless.spoof.DeviceSpoofActivity());
        } else if (position == shizukuRow) {
            presentFragment(new app.exteraless.shizuku.ShizukuSettingsActivity());
        }
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
                    if (position == toolsHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraTools));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    if (position == voiceChangerRow) {
                        boolean vcEnabled = app.exteraless.chats.ChatsConfig.voiceChangerEnabled.Bool();
                        cell.setTextAndValueAndIcon(getString(R.string.OEVoiceChanger),
                                getString(vcEnabled ? R.string.NotificationsOn : R.string.OEVoiceChangerOff),
                                R.drawable.msg_voicechat, true);
                    } else if (position == ayuMomentsRow) {
                        cell.setTextAndIcon(getString(R.string.OEGeneralAyuMoments), R.drawable.msg_secret_solar, true);
                    } else if (position == torRow) {
                        int torStatus = app.exteraless.tor.TorController.getInstance().getStatus();
                        String statusStr;
                        if (torStatus == app.exteraless.tor.TorController.STATUS_CONNECTED) {
                            statusStr = getString(R.string.TorConnected);
                        } else if (torStatus == app.exteraless.tor.TorController.STATUS_CONNECTING) {
                            statusStr = getString(R.string.TorStarting);
                        } else if (torStatus == app.exteraless.tor.TorController.STATUS_STOPPING) {
                            statusStr = getString(R.string.TorStopping);
                        } else {
                            statusStr = getString(R.string.TorDisabled);
                        }
                        cell.setTextAndValueAndIcon(getString(R.string.TorSettingsTitle), statusStr, R.drawable.ic_orbot, true);
                    } else if (position == deviceSpoofRow) {
                        boolean spoofing = app.exteraless.spoof.DeviceSpoofManager.getInstance().isSpoofingEnabled();
                        String val = spoofing ? app.exteraless.spoof.DeviceSpoofManager.getInstance().getDeviceModel() : null;
                        if (val != null) {
                            cell.setTextAndValueAndIcon(getString(R.string.DeviceSpoofing), val, R.drawable.msg_devices_solar, true);
                        } else {
                            cell.setTextAndIcon(getString(R.string.DeviceSpoofing), R.drawable.msg_devices_solar, true);
                        }
                    } else if (position == shizukuRow) {
                        String statusStr = app.exteraless.shizuku.ShizukuController.getInstance().hasPermission()
                                ? getString(R.string.ShizukuStatusConnected)
                                : (app.exteraless.shizuku.ShizukuController.getInstance().isAvailable()
                                ? getString(R.string.ShizukuStatusPermissionRequired)
                                : getString(R.string.ShizukuStatusDisconnected));
                        cell.setTextAndValueAndIcon(getString(R.string.ShizukuSettings), statusStr, R.drawable.msg_permissions, false);
                    }
                    cell.setImageLeft(21);
                    cell.setOffsetFromImage(71);
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
            if (position == toolsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == toolsDividerRow) {
                return TYPE_SHADOW;
            }
            return TYPE_TEXT;
        }
    }
}
