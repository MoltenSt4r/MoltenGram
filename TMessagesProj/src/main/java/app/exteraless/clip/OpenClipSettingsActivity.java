package app.exteraless.clip;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.util.Locale;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * Settings screen for OpenCLIP on-device multimodal semantic search.
 * Provides master switch, model downloader & lifecycle manager card,
 * precision threshold slider (0.35 - 0.80), and automatic indexing toggles.
 */
public class OpenClipSettingsActivity extends BaseNekoSettingsActivity implements OpenClipManager.DownloadListener {

    private static final int TYPE_THRESHOLD_SLIDER = 101;

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
    private int autoIndexPhotosRow;
    private int autoIndexTextRow;
    private int indexingInfoRow;

    @Override
    public boolean onFragmentCreate() {
        OpenClipManager.getInstance().addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        OpenClipManager.getInstance().removeListener(this);
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
        autoIndexPhotosRow = addRow("openClipAutoIndexPhotos");
        autoIndexTextRow = addRow("openClipAutoIndexText");
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
        } else if (position == modelCardRow || position == modelActionRow) {
            handleModelAction();
        }
    }

    private void handleModelAction() {
        OpenClipManager manager = OpenClipManager.getInstance();
        if (manager.isDownloading()) {
            manager.cancelDownload();
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(modelCardRow);
                listAdapter.notifyItemChanged(modelActionRow);
            }
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
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(modelCardRow);
                    listAdapter.notifyItemChanged(modelActionRow);
                }
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
        if (listAdapter != null) {
            listAdapter.notifyItemChanged(modelCardRow);
            listAdapter.notifyItemChanged(modelActionRow);
        }
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
        AndroidUtilities.runOnUIThread(() -> {
            if (listAdapter != null && modelCardRow != -1) {
                listAdapter.notifyItemChanged(modelCardRow, PARTIAL);
            }
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
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    private class OpenClipAdapter extends BaseListAdapter {

        public OpenClipAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterSwitchRow || position == autoIndexPhotosRow || position == autoIndexTextRow) {
                return TYPE_CHECK;
            } else if (position == modelHeaderRow || position == thresholdHeaderRow || position == indexingHeaderRow) {
                return TYPE_HEADER;
            } else if (position == modelCardRow) {
                return TYPE_SETTINGS;
            } else if (position == modelActionRow) {
                return TYPE_TEXT;
            } else if (position == thresholdSliderRow) {
                return TYPE_THRESHOLD_SLIDER;
            } else if (position == masterInfoRow || position == modelInfoRow || position == thresholdInfoRow || position == indexingInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int pos = holder.getAdapterPosition();
            return pos == masterSwitchRow || pos == autoIndexPhotosRow || pos == autoIndexTextRow
                    || pos == modelCardRow || pos == modelActionRow;
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
                }
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
}
