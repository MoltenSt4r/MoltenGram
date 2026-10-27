package app.exteraless.clip;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Locale;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * Settings screen for OpenCLIP on-device multimodal semantic search.
 * Provides master switch, model downloader & lifecycle manager card,
 * precision threshold slider (0.35 - 0.80), and automatic indexing toggles.
 */
public class OpenClipSettingsActivity extends BaseNekoSettingsActivity implements OpenClipManager.DownloadListener, ClipAutoIndexer.IndexerListener {

    private static final int TYPE_THRESHOLD_SLIDER = 101;
    private static final int TYPE_INDEXING_PROGRESS = 102;

    private int masterSwitchRow;
    private int masterInfoRow;

    private int modelHeaderRow;
    private int modelCardRow;
    private int modelActionRow;
    private int modelInfoRow;

    private int thresholdHeaderRow;
    private int thresholdSliderRow;
    private int thresholdInfoRow;

    private int indexingHeaderRow;
    private int autoIndexEnabledRow;
    private int autoIndexPhotosRow;
    private int autoIndexTextRow;
    private int indexAllChatsRow;
    private int chooseChatsRow;
    private int indexingProgressRow = -1;
    private int indexingInfoRow;

    @Override
    public boolean onFragmentCreate() {
        OpenClipManager.getInstance().addListener(this);
        ClipAutoIndexer.getInstance().addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        OpenClipManager.getInstance().removeListener(this);
        ClipAutoIndexer.getInstance().removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        masterSwitchRow = addRow("openClipMasterSwitch");
        masterInfoRow = addRow();

        modelHeaderRow = addRow("openClipModelHeader");
        modelCardRow = addRow("openClipModelCard");
        modelActionRow = addRow("openClipModelAction");
        modelInfoRow = addRow();

        thresholdHeaderRow = addRow("openClipThresholdHeader");
        thresholdSliderRow = addRow("openClipThresholdSlider");
        thresholdInfoRow = addRow();

        indexingHeaderRow = addRow("openClipIndexingHeader");
        autoIndexEnabledRow = addRow("openClipAutoIndexEnabled");
        autoIndexPhotosRow = addRow("openClipAutoIndexPhotos");
        autoIndexTextRow = addRow("openClipAutoIndexText");
        indexAllChatsRow = addRow("openClipIndexAllChats");
        chooseChatsRow = addRow("openClipChooseChats");
        if (ClipAutoIndexer.getInstance().isIndexing()) {
            indexingProgressRow = addRow("openClipIndexingProgress");
        } else {
            indexingProgressRow = -1;
        }
        indexingInfoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenClipSettingsTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new OpenClipAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterSwitchRow) {
            boolean enabled = !OpenClipConfig.isEnabled();
            OpenClipConfig.setEnabled(enabled);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            safeNotifyDataSetChanged();
            return;
        }
        if (!OpenClipConfig.isEnabled()) {
            return;
        }
        if (position == autoIndexEnabledRow) {
            boolean autoIndex = !OpenClipConfig.isAutoIndexEnabled();
            OpenClipConfig.setAutoIndexEnabled(autoIndex);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(autoIndex);
            }
        } else if (position == autoIndexPhotosRow) {
            boolean photos = !OpenClipConfig.isAutoIndexPhotos();
            OpenClipConfig.setAutoIndexPhotos(photos);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(photos);
            }
        } else if (position == autoIndexTextRow) {
            boolean text = !OpenClipConfig.isAutoIndexText();
            OpenClipConfig.setAutoIndexText(text);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(text);
            }
        } else if (position == indexAllChatsRow) {
            if (ClipAutoIndexer.getInstance().isIndexing()) {
                return;
            }
            if (!OpenClipManager.getInstance().isModelInstalled()) {
                if (BulletinFactory.canShowBulletin(this)) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.OpenClipModelRequired)).show();
                }
                return;
            }
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                ArrayList<Long> allDialogIds = new ArrayList<>();
                SQLiteCursor cursor = null;
                try {
                    SQLiteDatabase db = MessagesStorage.getInstance(currentAccount).getDatabase();
                    if (db != null) {
                        cursor = db.queryFinalized("SELECT did FROM dialogs");
                        while (cursor.next()) {
                            long did = cursor.longValue(0);
                            allDialogIds.add(did);
                        }
                    }
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (!allDialogIds.isEmpty()) {
                        ClipAutoIndexer.getInstance().startIndexing(currentAccount, allDialogIds);
                        updateRows();
                        safeNotifyDataSetChanged();
                    }
                });
            });
        } else if (position == chooseChatsRow) {
            if (ClipAutoIndexer.getInstance().isIndexing()) {
                return;
            }
            if (!OpenClipManager.getInstance().isModelInstalled()) {
                if (BulletinFactory.canShowBulletin(this)) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.OpenClipModelRequired)).show();
                }
                return;
            }
            android.os.Bundle args = new android.os.Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("checkCanWrite", false);
            args.putBoolean("allowGlobalSearch", false);
            DialogsActivity activity = new DialogsActivity(args);
            activity.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                activity.finishFragment();
                if (dids != null && !dids.isEmpty()) {
                    ArrayList<Long> dialogIds = new ArrayList<>();
                    for (int i = 0; i < dids.size(); i++) {
                        dialogIds.add(dids.get(i).dialogId);
                    }
                    ClipAutoIndexer.getInstance().startIndexing(currentAccount, dialogIds);
                    updateRows();
                    safeNotifyDataSetChanged();
                }
                return true;
            });
            presentFragment(activity);
        } else if (position == modelCardRow || position == modelActionRow) {
            handleModelAction();
        }
    }

    private void safeNotifyItemChanged(int row) {
        if (listAdapter == null || row == -1) return;
        if (listView != null && listView.isComputingLayout()) {
            listView.post(() -> {
                if (listAdapter != null && row != -1) {
                    listAdapter.notifyItemChanged(row);
                }
            });
        } else {
            listAdapter.notifyItemChanged(row);
        }
    }

    private void safeNotifyItemChanged(int row, Object payload) {
        if (listAdapter == null || row == -1) return;
        if (listView != null && listView.isComputingLayout()) {
            listView.post(() -> {
                if (listAdapter != null && row != -1) {
                    listAdapter.notifyItemChanged(row, payload);
                }
            });
        } else {
            listAdapter.notifyItemChanged(row, payload);
        }
    }

    private void safeNotifyDataSetChanged() {
        if (listView != null) {
            AndroidUtilities.notifyDataSetChanged(listView);
        } else if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void handleModelAction() {
        OpenClipManager manager = OpenClipManager.getInstance();
        if (manager.isDownloading()) {
            manager.cancelDownload();
            safeNotifyItemChanged(modelCardRow);
            safeNotifyItemChanged(modelActionRow);
            return;
        }

        if (manager.isModelInstalled()) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString(R.string.OpenClipDeleteModel));
            builder.setMessage(getString(R.string.OpenClipDeleteConfirm));
            builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                manager.deleteModel();
                safeNotifyItemChanged(modelCardRow);
                safeNotifyItemChanged(modelActionRow);
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button instanceof TextView) {
                ((TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
            return;
        }

        manager.startDownload(this);
        safeNotifyItemChanged(modelCardRow);
        safeNotifyItemChanged(modelActionRow);
    }

    private String getModelStatusText() {
        OpenClipManager manager = OpenClipManager.getInstance();
        if (manager.isDownloading()) {
            int percent = (int) (manager.getDownloadProgress() * 100);
            return String.format(Locale.getDefault(), getString(R.string.OpenClipModelStatusDownloading), percent);
        } else if (manager.isModelInstalled()) {
            return getString(R.string.OpenClipModelStatusReady);
        } else {
            return getString(R.string.OpenClipModelStatusNotInstalled);
        }
    }

    private String getModelActionText() {
        OpenClipManager manager = OpenClipManager.getInstance();
        if (manager.isDownloading()) {
            return getString(R.string.Cancel);
        } else if (manager.isModelInstalled()) {
            return getString(R.string.OpenClipDeleteModel);
        } else {
            return getString(R.string.OpenClipDownloadModel);
        }
    }

    @Override
    public void onProgress(long done, long total) {
        AndroidUtilities.runOnUIThread(() -> safeNotifyItemChanged(modelCardRow, PARTIAL));
    }

    @Override
    public void onProgress(long dialogId, String title, int currentInDialog, int totalInDialog, float overallProgress) {
        AndroidUtilities.runOnUIThread(() -> {
            if (indexingProgressRow == -1) {
                updateRows();
                safeNotifyDataSetChanged();
                return;
            }
            if (listView != null) {
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(indexingProgressRow);
                if (holder != null && holder.itemView instanceof IndexingProgressCell) {
                    ((IndexingProgressCell) holder.itemView).updateProgress(title, currentInDialog, totalInDialog, overallProgress);
                }
            }
        });
    }

    @Override
    public void onFinished(int totalIndexed) {
        AndroidUtilities.runOnUIThread(() -> {
            if (BulletinFactory.canShowBulletin(this)) {
                String text = String.format(Locale.US, getString(R.string.OpenClipIndexingFinished), totalIndexed);
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.magic_stick_solar, text).show();
            }
            updateRows();
            safeNotifyDataSetChanged();
        });
    }

    @Override
    public void onError(String error) {
        AndroidUtilities.runOnUIThread(() -> {
            if (BulletinFactory.canShowBulletin(this) && error != null) {
                BulletinFactory.of(this).createErrorBulletin(error).show();
            }
            updateRows();
            safeNotifyDataSetChanged();
        });
    }

    @Override
    public void onFinished(boolean success, String error) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!success && error != null && !error.toLowerCase(Locale.ROOT).contains("cancel")) {
                if (BulletinFactory.canShowBulletin(this)) {
                    BulletinFactory.of(this).createErrorBulletin(error).show();
                }
            }
            safeNotifyDataSetChanged();
        });
    }

    private class OpenClipAdapter extends BaseListAdapter {

        public OpenClipAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterSwitchRow || position == autoIndexEnabledRow || position == autoIndexPhotosRow || position == autoIndexTextRow) {
                return TYPE_CHECK;
            } else if (position == modelHeaderRow || position == thresholdHeaderRow || position == indexingHeaderRow) {
                return TYPE_HEADER;
            } else if (position == modelCardRow || position == indexAllChatsRow || position == chooseChatsRow) {
                return TYPE_SETTINGS;
            } else if (position == modelActionRow) {
                return TYPE_TEXT;
            } else if (position == thresholdSliderRow) {
                return TYPE_THRESHOLD_SLIDER;
            } else if (position == indexingProgressRow) {
                return TYPE_INDEXING_PROGRESS;
            } else if (position == masterInfoRow || position == modelInfoRow || position == thresholdInfoRow || position == indexingInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int pos = holder.getAdapterPosition();
            if (!OpenClipConfig.isEnabled()) {
                return pos == masterSwitchRow;
            }
            if (ClipAutoIndexer.getInstance().isIndexing()) {
                if (pos == indexAllChatsRow || pos == chooseChatsRow) {
                    return false;
                }
            }
            return pos == masterSwitchRow || pos == autoIndexEnabledRow || pos == autoIndexPhotosRow || pos == autoIndexTextRow
                    || pos == modelCardRow || pos == modelActionRow || pos == indexAllChatsRow || pos == chooseChatsRow;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_THRESHOLD_SLIDER) {
                PrecisionSliderCell sliderCell = new PrecisionSliderCell(mContext);
                sliderCell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                sliderCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(sliderCell);
            } else if (viewType == TYPE_INDEXING_PROGRESS) {
                IndexingProgressCell cell = new IndexingProgressCell(mContext);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(cell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            if (viewType == TYPE_CHECK) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                if (position == masterSwitchRow) {
                    checkCell.setTextAndCheck(getString(R.string.OpenClipEnable), OpenClipConfig.isEnabled(), false);
                } else if (position == autoIndexEnabledRow) {
                    checkCell.setTextAndValueAndCheck(getString(R.string.OpenClipAutoIndexEnabled), getString(R.string.OpenClipAutoIndexEnabledDesc), OpenClipConfig.isAutoIndexEnabled(), true, true);
                } else if (position == autoIndexPhotosRow) {
                    checkCell.setTextAndCheck(getString(R.string.OpenClipAutoIndexPhotos), OpenClipConfig.isAutoIndexPhotos(), true);
                } else if (position == autoIndexTextRow) {
                    checkCell.setTextAndCheck(getString(R.string.OpenClipAutoIndexText), OpenClipConfig.isAutoIndexText(), false);
                }
            } else if (viewType == TYPE_HEADER) {
                HeaderCell header = (HeaderCell) holder.itemView;
                if (position == modelHeaderRow) {
                    header.setText(getString(R.string.OpenClipModelHeader));
                } else if (position == thresholdHeaderRow) {
                    header.setText(getString(R.string.OpenClipThresholdHeader));
                } else if (position == indexingHeaderRow) {
                    header.setText(getString(R.string.OpenClipAutoIndexHeader));
                }
            } else if (viewType == TYPE_SETTINGS) {
                TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                if (position == modelCardRow) {
                    settingsCell.setTextAndValue(getString(R.string.OpenClipModelName), getModelStatusText(), true);
                } else if (position == indexAllChatsRow) {
                    settingsCell.setTextAndValue(getString(R.string.OpenClipIndexAllChats), getString(R.string.OpenClipIndexAllChatsSubtitle), true);
                } else if (position == chooseChatsRow) {
                    settingsCell.setTextAndValue(getString(R.string.OpenClipChooseChats), getString(R.string.OpenClipChooseChatsSubtitle), true);
                }
            } else if (viewType == TYPE_INDEXING_PROGRESS) {
                IndexingProgressCell cell = (IndexingProgressCell) holder.itemView;
                ClipAutoIndexer indexer = ClipAutoIndexer.getInstance();
                cell.updateProgress(indexer.getCurrentChatTitle(), indexer.getCurrentItemCount(), indexer.getTotalItemCount(), indexer.getProgress());
            } else if (viewType == TYPE_TEXT) {
                TextCell textCell = (TextCell) holder.itemView;
                if (position == modelActionRow) {
                    OpenClipManager manager = OpenClipManager.getInstance();
                    boolean isInstalled = manager.isModelInstalled();
                    textCell.setText(getModelActionText(), false);
                    textCell.setColors(isInstalled ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlueText2,
                            isInstalled ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlueText2);
                }
            } else if (viewType == TYPE_THRESHOLD_SLIDER) {
                PrecisionSliderCell sliderCell = (PrecisionSliderCell) holder.itemView;
                sliderCell.updateValue();
            } else if (viewType == TYPE_INFO_PRIVACY) {
                TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                if (position == masterInfoRow) {
                    infoCell.setText(getString(R.string.OpenClipEnableInfo));
                } else if (position == thresholdInfoRow) {
                    infoCell.setText(getString(R.string.OpenClipThresholdInfo));
                } else if (position == indexingInfoRow) {
                    infoCell.setText(getString(R.string.OpenClipAutoIndexPhotosInfo));
                } else {
                    infoCell.setText("");
                }
            }
        }
    }

    /**
     * Precision / Similarity Threshold Slider Cell.
     * Reuses SeekBarView pattern from AiSettingsActivity:691-752 (TemperatureCell).
     */
    private class PrecisionSliderCell extends FrameLayout {

        private final SeekBarView seekBar;
        private final TextView valueView;
        private boolean sliderTouched = false;

        public PrecisionSliderCell(Context context) {
            super(context);
            setWillNotDraw(false);

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            title.setText(getString(R.string.OpenClipThresholdHeader));
            header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            valueView.setTypeface(AndroidUtilities.bold());
            valueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            valueView.setPadding(AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2), AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2));
            valueView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(4),
                    Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.15f)));
            header.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

            addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 15, 21, 0));

            FrameLayout edges = new FrameLayout(context);
            TextView left = new TextView(context);
            left.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            left.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            left.setText(getString(R.string.OpenClipThresholdBroad));
            edges.addView(left, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            TextView right = new TextView(context);
            right.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            right.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            right.setText(getString(R.string.OpenClipThresholdStrict));
            edges.addView(right, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            addView(edges, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 48, 21, 0));

            seekBar = new SeekBarView(context);
            seekBar.setReportChanges(true);
            seekBar.setSeparatorsCount(46); // 0.35 to 0.80 range

            float current = OpenClipConfig.getThreshold();
            float initialProgress = (current - OpenClipConfig.MIN_THRESHOLD) / (OpenClipConfig.MAX_THRESHOLD - OpenClipConfig.MIN_THRESHOLD);
            seekBar.setProgress(initialProgress);
            seekBar.setDelegate((stop, progress) -> {
                float threshold = OpenClipConfig.MIN_THRESHOLD + progress * (OpenClipConfig.MAX_THRESHOLD - OpenClipConfig.MIN_THRESHOLD);
                OpenClipConfig.setThreshold(threshold);
                updateValue();
            });
            addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38,
                    Gravity.LEFT | Gravity.TOP, 9, 72, 9, 0));

            updateValue();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                float y = event.getY();
                if (y >= seekBar.getTop() - AndroidUtilities.dp(8) && y <= seekBar.getBottom() + AndroidUtilities.dp(8)) {
                    sliderTouched = true;
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                sliderTouched = false;
            }
            return super.dispatchTouchEvent(event);
        }

        public void updateValue() {
            float val = OpenClipConfig.getThreshold();
            valueView.setText(String.format(Locale.US, "%.2f", val));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(
                            MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(118), MeasureSpec.EXACTLY));
        }
    }

    private class IndexingProgressCell extends FrameLayout {
        private final TextView titleView;
        private final TextView statusView;
        private final LineProgressView progressBar;
        private final TextView cancelBtn;

        public IndexingProgressCell(Context context) {
            super(context);
            setWillNotDraw(false);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            container.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setText(getString(R.string.OpenClipIndexingProgress));
            header.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            cancelBtn = new TextView(context);
            cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            cancelBtn.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            cancelBtn.setText(getString(R.string.Cancel));
            cancelBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
            cancelBtn.setOnClickListener(v -> {
                ClipAutoIndexer.getInstance().cancelIndexing();
                safeNotifyDataSetChanged();
            });
            header.addView(cancelBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            statusView = new TextView(context);
            statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            statusView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            container.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

            progressBar = new LineProgressView(context);
            progressBar.setProgressColor(getThemedColor(Theme.key_featuredStickers_addButton));
            progressBar.setBackColor(getThemedColor(Theme.key_featuredStickers_addButton) & 0x33ffffff);
            container.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4));
        }

        public void updateProgress(String chatTitle, int current, int total, float progress) {
            String title = TextUtils.isEmpty(chatTitle) ? "" : chatTitle;
            statusView.setText(String.format(Locale.US, "%s (%d / %d)", title, current, Math.max(current, total)));
            progressBar.setProgress(Math.max(0.02f, progress), true);
        }
    }
}
