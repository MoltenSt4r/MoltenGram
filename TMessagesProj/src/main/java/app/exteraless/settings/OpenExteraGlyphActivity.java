package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import app.exteraless.glyph.GlyphConfig;
import app.exteraless.glyph.GlyphController;
import app.exteraless.glyph.GlyphDevicePreviewCell;
import app.exteraless.glyph.NothingPhoneEasterEgg;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Экран «Nothing Glyph» раздела exteraless: подсветка задней панели Nothing Phone
 * на события клиента с поддержкой различных стилей анимаций для звонков,
 * голосовых сообщений и входящих уведомлений.
 * Внизу экрана расположен интерактивный виджет предпросмотра текущего устройства и его глифов.
 */
public class OpenExteraGlyphActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_DEVICE_PREVIEW = 101;

    private int glyphHeaderRow;
    private int glyphEnableRow;
    private int glyphNewMessageRow;
    private int glyphCallsRow;
    private int glyphRecordingRow;
    private int glyphScreenOffRow;
    private int glyphDividerRow;
    private int glyphPreviewRow;
    private int glyphDevicePreviewRow;

    private String selectedDeviceModel;

    public OpenExteraGlyphActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        GlyphConfig.init();
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        glyphHeaderRow = addRow("glyphHeader");
        glyphEnableRow = addRow(GlyphConfig.enabled.getKey());
        if (GlyphConfig.enabled()) {
            glyphNewMessageRow = addRow(GlyphConfig.onNewMessage.getKey());
            glyphCallsRow = addRow(GlyphConfig.onCall.getKey());
            glyphRecordingRow = addRow(GlyphConfig.onRecording.getKey());
            glyphScreenOffRow = addRow(GlyphConfig.screenOffOnly.getKey());
            glyphDividerRow = addRow();
            glyphPreviewRow = addRow("glyphPreview");
        } else {
            glyphNewMessageRow = -1;
            glyphCallsRow = -1;
            glyphRecordingRow = -1;
            glyphScreenOffRow = -1;
            glyphDividerRow = addRow();
            glyphPreviewRow = -1;
        }

        // Dedicated bottom preview showing current phone and its glyphs (or centered for CMF)
        glyphDevicePreviewRow = addRow("glyphDevicePreview");
    }

    private void update() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEGlyphTitle);
    }

    @Override
    public int getSearchGuid() {
        return 25000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.deproko_baseline_lamp_filled_24;
    }

    @Override
    public String getSearchPrefix() {
        return "OEGlyph";
    }

    @Override
    protected String getKey() {
        return "exteraless_glyph";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == glyphEnableRow) {
            boolean enabled = GlyphConfig.enabled.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            if (enabled) {
                GlyphController.getInstance().init();
            } else {
                GlyphController.getInstance().shutdown();
            }
            update();
        } else if (position == glyphNewMessageRow) {
            boolean enabled = GlyphConfig.onNewMessage.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == glyphCallsRow) {
            boolean enabled = GlyphConfig.onCall.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == glyphRecordingRow) {
            boolean enabled = GlyphConfig.onRecording.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == glyphScreenOffRow) {
            boolean enabled = GlyphConfig.screenOffOnly.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == glyphPreviewRow) {
            if (!GlyphController.getInstance().isSupported()) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, getString(R.string.OEGlyphUnsupported))
                        .show();
                return;
            }
            if (getParentActivity() == null) {
                return;
            }
            CharSequence[] previewItems = new CharSequence[]{
                    getString(R.string.OEGlyphNewMessage),
                    getString(R.string.OEGlyphCalls),
                    getString(R.string.OEGlyphRecording)
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString(R.string.OEGlyphPreview));
            builder.setItems(previewItems, (dialog, which) -> {
                if (which == 0) {
                    GlyphController.getInstance().previewMessage();
                } else if (which == 1) {
                    GlyphController.getInstance().previewCall();
                } else if (which == 2) {
                    GlyphController.getInstance().previewRecording();
                }
                notifyPreviewCell(which);
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
            showDialog(builder.create());
        } else if (position == glyphDevicePreviewRow) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if (view instanceof GlyphDevicePreviewCell) {
                ((GlyphDevicePreviewCell) view).triggerPreviewAnimation(0);
            }
            String model = selectedDeviceModel != null ? selectedDeviceModel : NothingPhoneEasterEgg.getDeviceDisplayName();
            NothingPhoneEasterEgg.playBurstForModel(getParentActivity() != null ? getParentActivity() : (Activity) getContext(), this, model);
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position == glyphDevicePreviewRow) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showDeviceBurstChooserDialog();
            return true;
        }
        return super.onItemLongClick(view, position, x, y);
    }

    private void notifyPreviewCell(int type) {
        if (listView == null) return;
        int count = listView.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = listView.getChildAt(i);
            if (child instanceof GlyphDevicePreviewCell) {
                ((GlyphDevicePreviewCell) child).triggerPreviewAnimation(type);
                break;
            }
        }
    }

    private void showDeviceBurstChooserDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEGlyphBurst));
        builder.setItems(NothingPhoneEasterEgg.ALL_MODELS, (dialog, which) -> {
            selectedDeviceModel = NothingPhoneEasterEgg.ALL_MODELS[which];
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            NothingPhoneEasterEgg.playBurstForModel(getParentActivity(), this, selectedDeviceModel);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_DEVICE_PREVIEW) {
                GlyphDevicePreviewCell cell = new GlyphDevicePreviewCell(mContext);
                cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == glyphHeaderRow) {
                        cell.setText(getString(R.string.OEGlyphTitle));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == glyphEnableRow) {
                        cell.setTextAndCheck(getString(R.string.OEGlyphEnable),
                                GlyphConfig.enabled(), true);
                    } else if (position == glyphNewMessageRow) {
                        cell.setTextAndCheck(getString(R.string.OEGlyphNewMessage),
                                GlyphConfig.onNewMessage(), true);
                    } else if (position == glyphCallsRow) {
                        cell.setTextAndCheck(getString(R.string.OEGlyphCalls),
                                GlyphConfig.onCall(), true);
                    } else if (position == glyphRecordingRow) {
                        cell.setTextAndCheck(getString(R.string.OEGlyphRecording),
                                GlyphConfig.onRecording(), true);
                    } else if (position == glyphScreenOffRow) {
                        cell.setTextAndCheck(getString(R.string.OEGlyphScreenOff),
                                GlyphConfig.screenOffOnly(), false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == glyphPreviewRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon,
                                Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.OEGlyphPreview),
                                R.drawable.deproko_baseline_lamp_filled_24, true);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == glyphDividerRow) {
                        cell.setText(getString(R.string.OEGlyphInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case TYPE_DEVICE_PREVIEW: {
                    GlyphDevicePreviewCell cell = (GlyphDevicePreviewCell) holder.itemView;
                    cell.setModel(selectedDeviceModel != null ? selectedDeviceModel : NothingPhoneEasterEgg.getDeviceDisplayName());
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == glyphHeaderRow) {
                return TYPE_HEADER;
            } else if (position == glyphPreviewRow) {
                return TYPE_TEXT;
            } else if (position == glyphDividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == glyphDevicePreviewRow) {
                return TYPE_DEVICE_PREVIEW;
            }
            return TYPE_CHECK;
        }
    }
}
