package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.chats.ChatsConfig;
import app.exteraless.utils.UIUtil;
import app.exteraless.voice.VoiceChangerController;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Встроенный войсченджер MoltenGram:
 * - Управление тональностью (Pitch)
 * - Freqcrush (дискретизация)
 * - Bitcrush (разрядность)
 * - Эхо (задержка и сила)
 * - Случайные глитчи/заикания («перегрев/зависание ПК»)
 * - Быстрые пресеты и тест микрофона
 */
public class OpenExteraVoiceChangerActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_TEST_MIC_CARD = 100;
    private static final int TYPE_PRESETS = 101;
    private static final int TYPE_SLIDER_ROW = 102;

    private int masterHeaderRow;
    private int masterEnableRow;
    private int testMicRow;
    private int masterDividerRow;

    private int presetsHeaderRow;
    private int presetsRow;
    private int presetsDividerRow;

    private int effectsHeaderRow;
    private int pitchSliderRow;
    private int freqCrushSliderRow;
    private int bitCrushSliderRow;
    private int effectsDividerRow;

    private int echoHeaderRow;
    private int echoDelaySliderRow;
    private int echoFeedbackSliderRow;
    private int echoDividerRow;

    private int glitchHeaderRow;
    private int glitchRateSliderRow;
    private int glitchRepeatsSliderRow;
    private int glitchDividerRow;

    private static final int TEST_STATE_IDLE = 0;
    private static final int TEST_STATE_RECORDING = 1;
    private static final int TEST_STATE_PLAYING = 2;
    private int testState = TEST_STATE_IDLE;
    private volatile boolean stopRecordingEarly = false;
    private AudioTrack activeTestTrack = null;

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEVoiceChanger);
    }

    @Override
    public int getSearchGuid() {
        return 27000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_voicechat;
    }

    @Override
    public String getSearchPrefix() {
        return "OEVoiceChanger";
    }

    @Override
    public String getSearchTitle() {
        return getString(R.string.OEVoiceChanger);
    }

    @Override
    public void buildRowsForSearch() {
        getSearchRows().clear();
        int r = 0;
        getSearchRows().put(r++, "masterEnable");
        getSearchRows().put(r++, "presets");
        getSearchRows().put(r++, "pitchSlider");
        getSearchRows().put(r++, "freqCrushSlider");
        getSearchRows().put(r++, "bitCrushSlider");
        getSearchRows().put(r++, "echoDelaySlider");
        getSearchRows().put(r++, "echoFeedbackSlider");
        getSearchRows().put(r++, "glitchRateSlider");
        getSearchRows().put(r++, "glitchRepeatsSlider");
        getSearchRows().put(r++, "testMic");
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        boolean enabled = ChatsConfig.voiceChangerEnabled.Bool();

        masterHeaderRow = addRow("masterHeader");
        masterEnableRow = addRow("masterEnable");
        testMicRow = enabled ? addRow("testMic") : -1;
        masterDividerRow = addRow();

        if (enabled) {
            presetsHeaderRow = addRow("presetsHeader");
            presetsRow = addRow("presets");
            presetsDividerRow = addRow();

            effectsHeaderRow = addRow("effectsHeader");
            pitchSliderRow = addRow("pitchSlider");
            freqCrushSliderRow = addRow("freqCrushSlider");
            bitCrushSliderRow = addRow("bitCrushSlider");
            effectsDividerRow = addRow();

            echoHeaderRow = addRow("echoHeader");
            echoDelaySliderRow = addRow("echoDelaySlider");
            echoFeedbackSliderRow = addRow("echoFeedbackSlider");
            echoDividerRow = addRow();

            glitchHeaderRow = addRow("glitchHeader");
            glitchRateSliderRow = addRow("glitchRateSlider");
            glitchRepeatsSliderRow = addRow("glitchRepeatsSlider");
            glitchDividerRow = addRow();
        } else {
            presetsHeaderRow = -1;
            presetsRow = -1;
            presetsDividerRow = -1;
            effectsHeaderRow = -1;
            pitchSliderRow = -1;
            freqCrushSliderRow = -1;
            bitCrushSliderRow = -1;
            effectsDividerRow = -1;
            echoHeaderRow = -1;
            echoDelaySliderRow = -1;
            echoFeedbackSliderRow = -1;
            echoDividerRow = -1;
            glitchHeaderRow = -1;
            glitchRateSliderRow = -1;
            glitchRepeatsSliderRow = -1;
            glitchDividerRow = -1;
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterEnableRow) {
            boolean next = !ChatsConfig.voiceChangerEnabled.Bool();
            ChatsConfig.voiceChangerEnabled.setConfigBool(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
            if (!next && testState != TEST_STATE_IDLE) {
                stopAudioTest();
            }
            VoiceChangerController.getInstance().resetState();
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void handleTestMicClick() {
        if (testState == TEST_STATE_RECORDING) {
            finishRecordingEarly();
        } else if (testState == TEST_STATE_PLAYING) {
            stopAudioTest();
        } else {
            startAudioTest();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        stopAudioTest();
    }

    private void applyPreset(int preset) {
        switch (preset) {
            case 0: // Default (Off)
                ChatsConfig.voiceChangerPitch.setConfigInt(0);
                ChatsConfig.voiceChangerFreqCrush.setConfigInt(48000);
                ChatsConfig.voiceChangerBitCrush.setConfigInt(16);
                ChatsConfig.voiceChangerEchoDelay.setConfigInt(0);
                ChatsConfig.voiceChangerEchoFeedback.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRate.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(4);
                break;
            case 1: // PC Glitch / Overheat
                ChatsConfig.voiceChangerPitch.setConfigInt(0);
                ChatsConfig.voiceChangerFreqCrush.setConfigInt(48000);
                ChatsConfig.voiceChangerBitCrush.setConfigInt(16);
                ChatsConfig.voiceChangerEchoDelay.setConfigInt(40);
                ChatsConfig.voiceChangerEchoFeedback.setConfigInt(45);
                ChatsConfig.voiceChangerGlitchRate.setConfigInt(75);
                ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(5);
                break;
            case 2: // 8-bit Bitcrush
                ChatsConfig.voiceChangerPitch.setConfigInt(0);
                ChatsConfig.voiceChangerFreqCrush.setConfigInt(8000);
                ChatsConfig.voiceChangerBitCrush.setConfigInt(4);
                ChatsConfig.voiceChangerEchoDelay.setConfigInt(0);
                ChatsConfig.voiceChangerEchoFeedback.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRate.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(4);
                break;
            case 3: // Deep Demon
                ChatsConfig.voiceChangerPitch.setConfigInt(-6);
                ChatsConfig.voiceChangerFreqCrush.setConfigInt(48000);
                ChatsConfig.voiceChangerBitCrush.setConfigInt(16);
                ChatsConfig.voiceChangerEchoDelay.setConfigInt(120);
                ChatsConfig.voiceChangerEchoFeedback.setConfigInt(35);
                ChatsConfig.voiceChangerGlitchRate.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(4);
                break;
            case 4: // Chipmunk
                ChatsConfig.voiceChangerPitch.setConfigInt(8);
                ChatsConfig.voiceChangerFreqCrush.setConfigInt(48000);
                ChatsConfig.voiceChangerBitCrush.setConfigInt(16);
                ChatsConfig.voiceChangerEchoDelay.setConfigInt(0);
                ChatsConfig.voiceChangerEchoFeedback.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRate.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(4);
                break;
            case 5: // Echo Chamber
                ChatsConfig.voiceChangerPitch.setConfigInt(0);
                ChatsConfig.voiceChangerFreqCrush.setConfigInt(48000);
                ChatsConfig.voiceChangerBitCrush.setConfigInt(16);
                ChatsConfig.voiceChangerEchoDelay.setConfigInt(280);
                ChatsConfig.voiceChangerEchoFeedback.setConfigInt(65);
                ChatsConfig.voiceChangerGlitchRate.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(4);
                break;
            case 6: // Walkie-Talkie Lo-Fi
                ChatsConfig.voiceChangerPitch.setConfigInt(1);
                ChatsConfig.voiceChangerFreqCrush.setConfigInt(6000);
                ChatsConfig.voiceChangerBitCrush.setConfigInt(6);
                ChatsConfig.voiceChangerEchoDelay.setConfigInt(0);
                ChatsConfig.voiceChangerEchoFeedback.setConfigInt(0);
                ChatsConfig.voiceChangerGlitchRate.setConfigInt(15);
                ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(3);
                break;
        }
    }

    private void startAudioTest() {
        if (testState != TEST_STATE_IDLE) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && getParentActivity() != null
                && getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
            return;
        }
        testState = TEST_STATE_RECORDING;
        stopRecordingEarly = false;
        notifyTestMicChanged();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.OEVoiceChangerRecordTest)).show();

        Utilities.globalQueue.postRunnable(() -> {
            AudioRecord record = null;
            byte[] recordedData = null;
            int totalBytes = 0;
            int sampleRate = 48000;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize <= 0) bufferSize = 4096;

            try {
                record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
                if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording();
                    int maxRecordSeconds = 3;
                    int maxBytes = sampleRate * 2 * maxRecordSeconds;
                    recordedData = new byte[maxBytes];

                    long start = System.currentTimeMillis();
                    while (!stopRecordingEarly && testState == TEST_STATE_RECORDING && System.currentTimeMillis() - start < 3000 && totalBytes + bufferSize <= maxBytes) {
                        int read = record.read(recordedData, totalBytes, bufferSize);
                        if (read > 0) {
                            totalBytes += read;
                        } else {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (record != null) {
                    try {
                        record.stop();
                        record.release();
                    } catch (Exception ignored) {}
                }
            }

            final byte[] finalData = recordedData;
            final int finalTotal = totalBytes;

            AndroidUtilities.runOnUIThread(() -> {
                if (testState == TEST_STATE_IDLE || finalData == null || finalTotal <= 0) {
                    stopAudioTest();
                    return;
                }
                testState = TEST_STATE_PLAYING;
                notifyTestMicChanged();

                // Process recorded audio through Voice Changer
                VoiceChangerController.getInstance().resetState();
                ByteBuffer buf = ByteBuffer.wrap(finalData, 0, finalTotal).order(ByteOrder.LITTLE_ENDIAN);
                VoiceChangerController.getInstance().process(buf, finalTotal);

                // Playback processed audio
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        int minOutBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                        activeTestTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                                Math.max(minOutBuf, finalTotal), AudioTrack.MODE_STREAM);
                        activeTestTrack.play();
                        activeTestTrack.write(finalData, 0, finalTotal);

                        long playDurationMs = (long) (finalTotal / 2.0 / sampleRate * 1000L) + 300L;
                        long playStart = System.currentTimeMillis();
                        while (testState == TEST_STATE_PLAYING && System.currentTimeMillis() - playStart < playDurationMs) {
                            Thread.sleep(50);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        stopAudioTest();
                    }
                });
            });
        });
    }

    private void finishRecordingEarly() {
        stopRecordingEarly = true;
    }

    private void stopAudioTest() {
        testState = TEST_STATE_IDLE;
        stopRecordingEarly = false;
        if (activeTestTrack != null) {
            try {
                activeTestTrack.stop();
                activeTestTrack.release();
            } catch (Exception ignored) {}
            activeTestTrack = null;
        }
        AndroidUtilities.runOnUIThread(this::notifyTestMicChanged);
    }

    private void notifyTestMicChanged() {
        if (listAdapter != null && testMicRow >= 0) {
            listAdapter.notifyItemChanged(testMicRow);
        }
    }

    private int resolveMd3Accent() {
        int fallback = getThemedColor(Theme.key_featuredStickers_addButton);
        if (fallback == 0) {
            fallback = getThemedColor(Theme.key_windowBackgroundWhiteBlueText);
        }
        return AppearanceConfig.resolveThemeToken("primary", fallback);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected boolean isSectionContent(int viewType) {
            if (viewType == TYPE_TEST_MIC_CARD || viewType == TYPE_PRESETS || viewType == TYPE_SLIDER_ROW) {
                return true;
            }
            return super.isSectionContent(viewType);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == TYPE_TEST_MIC_CARD || type == TYPE_PRESETS || type == TYPE_SLIDER_ROW) {
                return false;
            }
            return super.isEnabled(holder);
        }

        private void refreshPresetChips() {
            if (presetsRow >= 0) {
                notifyItemChanged(presetsRow);
            }
        }

        private void refreshSliders() {
            int[] sliderRows = {
                    pitchSliderRow, freqCrushSliderRow, bitCrushSliderRow,
                    echoDelaySliderRow, echoFeedbackSliderRow,
                    glitchRateSliderRow, glitchRepeatsSliderRow
            };
            for (int row : sliderRows) {
                if (row >= 0) {
                    notifyItemChanged(row);
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_TEST_MIC_CARD) {
                MicTestCard card = new MicTestCard(mContext);
                card.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                card.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(card);
            } else if (viewType == TYPE_PRESETS) {
                PresetChipsView chips = new PresetChipsView(mContext);
                chips.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                chips.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(chips);
            } else if (viewType == TYPE_SLIDER_ROW) {
                VoiceSliderCell slider = new VoiceSliderCell(mContext);
                slider.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(slider);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == masterHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChanger));
                    } else if (position == presetsHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChangerPresets));
                    } else if (position == effectsHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChangerEffects));
                    } else if (position == echoHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChangerEchoDelay));
                    } else if (position == glitchHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChangerGlitchRate));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == masterEnableRow) {
                        cell.setTextAndCheck(getString(R.string.OEVoiceChangerEnable),
                                ChatsConfig.voiceChangerEnabled.Bool(), testMicRow >= 0);
                    }
                    break;
                }
                case TYPE_TEST_MIC_CARD: {
                    ((MicTestCard) holder.itemView).updateState(testState);
                    break;
                }
                case TYPE_PRESETS: {
                    ((PresetChipsView) holder.itemView).updateSelection();
                    break;
                }
                case TYPE_SLIDER_ROW: {
                    VoiceSliderCell cell = (VoiceSliderCell) holder.itemView;
                    bindSliderRow(cell, position);
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == masterDividerRow) {
                        cell.setText(getString(R.string.OEVoiceChangerEnableInfo));
                    } else if (position == glitchDividerRow) {
                        cell.setText(getString(R.string.OEVoiceChangerGlitchRateInfo));
                    }
                    break;
                }
            }
        }

        private void bindSliderRow(VoiceSliderCell cell, int position) {
            if (position == pitchSliderRow) {
                cell.bind(
                        getString(R.string.OEVoiceChangerPitch),
                        -12, 12, 0,
                        ChatsConfig.voiceChangerPitch.Int(),
                        "-12 st", "+12 st", true,
                        val -> (val > 0 ? "+" + val : String.valueOf(val)) + " st",
                        val -> {
                            ChatsConfig.voiceChangerPitch.setConfigInt(val);
                            VoiceChangerController.getInstance().resetState();
                            refreshPresetChips();
                        }
                );
            } else if (position == freqCrushSliderRow) {
                cell.bind(
                        getString(R.string.OEVoiceChangerFreqCrush),
                        4, 48, 48,
                        ChatsConfig.voiceChangerFreqCrush.Int() / 1000,
                        "4 kHz", "48 kHz", true,
                        val -> val >= 48 ? "48 kHz (" + getString(R.string.OEVoiceChangerOff) + ")" : val + " kHz",
                        val -> {
                            ChatsConfig.voiceChangerFreqCrush.setConfigInt(val * 1000);
                            VoiceChangerController.getInstance().resetState();
                            refreshPresetChips();
                        }
                );
            } else if (position == bitCrushSliderRow) {
                cell.bind(
                        getString(R.string.OEVoiceChangerBitCrush),
                        2, 16, 16,
                        ChatsConfig.voiceChangerBitCrush.Int(),
                        "2 bit", "16 bit", false,
                        val -> val >= 16 ? "16 bit (" + getString(R.string.OEVoiceChangerOff) + ")" : val + " bit",
                        val -> {
                            ChatsConfig.voiceChangerBitCrush.setConfigInt(val);
                            VoiceChangerController.getInstance().resetState();
                            refreshPresetChips();
                        }
                );
            } else if (position == echoDelaySliderRow) {
                cell.bind(
                        getString(R.string.OEVoiceChangerEchoDelay),
                        0, 800, 0,
                        ChatsConfig.voiceChangerEchoDelay.Int(),
                        "0 ms", "800 ms", true,
                        val -> val <= 0 ? getString(R.string.OEVoiceChangerOff) : val + " ms",
                        val -> {
                            ChatsConfig.voiceChangerEchoDelay.setConfigInt(val);
                            VoiceChangerController.getInstance().resetState();
                            refreshPresetChips();
                        }
                );
            } else if (position == echoFeedbackSliderRow) {
                cell.bind(
                        getString(R.string.OEVoiceChangerEchoFeedback),
                        0, 90, 0,
                        ChatsConfig.voiceChangerEchoFeedback.Int(),
                        "0%", "90%", false,
                        val -> val + "%",
                        val -> {
                            ChatsConfig.voiceChangerEchoFeedback.setConfigInt(val);
                            VoiceChangerController.getInstance().resetState();
                            refreshPresetChips();
                        }
                );
            } else if (position == glitchRateSliderRow) {
                cell.bind(
                        getString(R.string.OEVoiceChangerGlitchRate),
                        0, 100, 0,
                        ChatsConfig.voiceChangerGlitchRate.Int(),
                        "0%", "100%", true,
                        val -> val <= 0 ? getString(R.string.OEVoiceChangerOff) : val + "%",
                        val -> {
                            ChatsConfig.voiceChangerGlitchRate.setConfigInt(val);
                            VoiceChangerController.getInstance().resetState();
                            refreshPresetChips();
                        }
                );
            } else if (position == glitchRepeatsSliderRow) {
                cell.bind(
                        getString(R.string.OEVoiceChangerGlitchRepeats),
                        2, 8, 4,
                        ChatsConfig.voiceChangerGlitchRepeats.Int(),
                        "2x", "8x", false,
                        val -> val + "x",
                        val -> {
                            ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(val);
                            VoiceChangerController.getInstance().resetState();
                            refreshPresetChips();
                        }
                );
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterHeaderRow || position == presetsHeaderRow
                    || position == effectsHeaderRow || position == echoHeaderRow || position == glitchHeaderRow) {
                return TYPE_HEADER;
            } else if (position == masterEnableRow) {
                return TYPE_CHECK;
            } else if (position == testMicRow) {
                return TYPE_TEST_MIC_CARD;
            } else if (position == presetsRow) {
                return TYPE_PRESETS;
            } else if (position == pitchSliderRow || position == freqCrushSliderRow || position == bitCrushSliderRow
                    || position == echoDelaySliderRow || position == echoFeedbackSliderRow
                    || position == glitchRateSliderRow || position == glitchRepeatsSliderRow) {
                return TYPE_SLIDER_ROW;
            } else if (position == masterDividerRow || position == glitchDividerRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
        }
    }

    /**
     * MD3 interactive Mic Test card with recording/playing state badge and clear visual feedback.
     */
    private class MicTestCard extends FrameLayout {

        private final LinearLayout buttonCard;
        private final FrameLayout iconContainer;
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final LinearLayout stateBadge;
        private final View stateDot;
        private final TextView stateLabel;

        MicTestCard(Context context) {
            super(context);
            setPadding(dp(16), dp(6), dp(16), dp(14));

            buttonCard = new LinearLayout(context);
            buttonCard.setOrientation(LinearLayout.HORIZONTAL);
            buttonCard.setGravity(Gravity.CENTER_VERTICAL);
            buttonCard.setPadding(dp(14), dp(12), dp(14), dp(12));
            buttonCard.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                handleTestMicClick();
            });
            addView(buttonCard, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            iconContainer = new FrameLayout(context);
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconContainer.addView(iconView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
            buttonCard.addView(iconContainer, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);

            titleView = new TextView(context);
            titleView.setText(getString(R.string.OEVoiceChangerTestAudio));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            buttonCard.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            stateBadge = new LinearLayout(context);
            stateBadge.setOrientation(LinearLayout.HORIZONTAL);
            stateBadge.setGravity(Gravity.CENTER_VERTICAL);
            stateBadge.setPadding(dp(10), dp(5), dp(10), dp(5));

            stateDot = new View(context);
            stateBadge.addView(stateDot, LayoutHelper.createLinear(6, 6, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

            stateLabel = new TextView(context);
            stateLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            stateLabel.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            stateLabel.setSingleLine(true);
            stateBadge.addView(stateLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            buttonCard.addView(stateBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            updateState(TEST_STATE_IDLE);
        }

        void updateState(int state) {
            int accent = resolveMd3Accent();
            int errorColor = getThemedColor(Theme.key_text_RedBold);
            if (errorColor == 0) {
                errorColor = 0xFFE53935;
            }
            int textPrimary = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
            int textSecondary = getThemedColor(Theme.key_windowBackgroundWhiteGrayText);

            titleView.setTextColor(textPrimary);

            int cardFill;
            int badgeColor;
            int iconRes;
            String badgeText;
            String subtitleText;

            if (state == TEST_STATE_RECORDING) {
                cardFill = ColorUtils.setAlphaComponent(errorColor, 26);
                badgeColor = errorColor;
                iconRes = R.drawable.msg_voicechat;
                badgeText = "REC";
                subtitleText = getString(R.string.OEVoiceChangerStopTest);
            } else if (state == TEST_STATE_PLAYING) {
                cardFill = ColorUtils.setAlphaComponent(accent, 34);
                badgeColor = accent;
                iconRes = R.drawable.msg_round_play_m;
                badgeText = "PLAY";
                subtitleText = getString(R.string.OEVoiceChangerStopTest);
            } else {
                int surfaceFallback = ColorUtils.setAlphaComponent(accent, 22);
                cardFill = AppearanceConfig.resolveThemeToken("surface_container_high", surfaceFallback);
                badgeColor = accent;
                iconRes = R.drawable.msg_voicechat;
                badgeText = "3s";
                subtitleText = getString(R.string.OEVoiceChangerRecordTest);
            }

            buttonCard.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    dp(16), cardFill, ColorUtils.setAlphaComponent(badgeColor, 42)));

            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.OVAL);
            iconBg.setColor(ColorUtils.setAlphaComponent(badgeColor, 36));
            iconContainer.setBackground(iconBg);

            iconView.setImageResource(iconRes);
            iconView.setColorFilter(new PorterDuffColorFilter(badgeColor, PorterDuff.Mode.SRC_IN));

            subtitleView.setText(subtitleText);
            subtitleView.setTextColor(state == TEST_STATE_IDLE ? textSecondary : badgeColor);

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(12));
            badgeBg.setColor(ColorUtils.setAlphaComponent(badgeColor, 34));
            stateBadge.setBackground(badgeBg);

            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(badgeColor);
            stateDot.setBackground(dotBg);

            stateLabel.setText(badgeText);
            stateLabel.setTextColor(badgeColor);
        }
    }

    /**
     * MD3 Expressive horizontal preset chips bar with active filled state, check indicator, and ripple.
     */
    private class PresetChipsView extends HorizontalScrollView {
        private final LinearLayout row;
        private final LinearLayout[] chipContainers;
        private final CheckIndicatorView[] chipChecks;
        private final TextView[] chipLabels;

        PresetChipsView(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setPadding(dp(16), dp(8), dp(16), dp(14));
            setClipToPadding(false);

            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            String[] names = new String[] {
                    getString(R.string.OEVoiceChangerPresetDefault),
                    getString(R.string.OEVoiceChangerPresetGlitch),
                    getString(R.string.OEVoiceChangerPresetBitcrush),
                    getString(R.string.OEVoiceChangerPresetDemon),
                    getString(R.string.OEVoiceChangerPresetChipmunk),
                    getString(R.string.OEVoiceChangerPresetEcho),
                    getString(R.string.OEVoiceChangerPresetWalkie)
            };

            chipContainers = new LinearLayout[names.length];
            chipChecks = new CheckIndicatorView[names.length];
            chipLabels = new TextView[names.length];

            for (int i = 0; i < names.length; i++) {
                final int index = i;
                LinearLayout chip = new LinearLayout(context);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPadding(dp(14), 0, dp(14), 0);

                CheckIndicatorView check = new CheckIndicatorView(context);
                chip.addView(check, LayoutHelper.createLinear(14, 14, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

                TextView label = new TextView(context);
                label.setText(names[i]);
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
                label.setSingleLine(true);
                chip.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

                chip.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    applyPreset(index);
                    VoiceChangerController.getInstance().resetState();
                    updateSelection();
                    scrollToChip(index);
                    if (listAdapter != null) {
                        listAdapter.refreshSliders();
                    }
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36));
                if (i > 0) {
                    lp.leftMargin = dp(8);
                }
                row.addView(chip, lp);
                chipContainers[i] = chip;
                chipChecks[i] = check;
                chipLabels[i] = label;
            }
            updateSelection();
        }

        private void scrollToChip(int index) {
            if (index < 0 || index >= chipContainers.length) {
                return;
            }
            View chip = chipContainers[index];
            post(() -> {
                int targetX = chip.getLeft() - (getWidth() - chip.getWidth()) / 2;
                smoothScrollTo(Math.max(0, targetX), 0);
            });
        }

        void updateSelection() {
            int active = getCurrentPresetIndex();
            int accent = resolveMd3Accent();
            int onAccent = UIUtil.getOnAccentColor(accent);
            int textNormal = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
            int surfaceIdle = AppearanceConfig.resolveThemeToken(
                    "surface_container_high",
                    ColorUtils.setAlphaComponent(textNormal, 18)
            );
            int rippleColor = ColorUtils.setAlphaComponent(accent, 45);

            for (int i = 0; i < chipContainers.length; i++) {
                boolean selected = (i == active);
                chipChecks[i].setVisibility(selected ? VISIBLE : GONE);
                chipChecks[i].setColor(onAccent);
                chipContainers[i].setPadding(dp(selected ? 11 : 14), 0, dp(14), 0);
                chipLabels[i].setTextColor(selected ? onAccent : textNormal);
                chipLabels[i].setTypeface(AndroidUtilities.getTypeface(
                        selected ? AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM : AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
                chipContainers[i].setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                        dp(18), selected ? accent : surfaceIdle, rippleColor));
            }
        }
    }

    private static class CheckIndicatorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        CheckIndicatorView(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(1.8f));
        }

        void setColor(int color) {
            if (paint.getColor() != color) {
                paint.setColor(color);
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            path.reset();
            path.moveTo(w * 0.18f, h * 0.52f);
            path.lineTo(w * 0.42f, h * 0.76f);
            path.lineTo(w * 0.84f, h * 0.26f);
            canvas.drawPath(path, paint);
        }
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private interface ValueFormatter {
        String format(int value);
    }

    /**
     * Modern MD3 slider cell: title + pill value badge + standard SeekBarView + subtle range labels.
     */
    @SuppressLint("ViewConstructor")
    private class VoiceSliderCell extends LinearLayout {

        private final TextView titleView;
        private final TextView valueBadge;
        private final SeekBarView seekBarView;
        private final TextView leftLabel;
        private final TextView rightLabel;

        private int min;
        private int max;
        private int defaultVal;
        private int currentValue;
        private int lastHapticVal = Integer.MIN_VALUE;
        private boolean needDivider;
        private ValueFormatter formatter;
        private IntConsumer onChange;

        VoiceSliderCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            setPadding(dp(18), dp(12), dp(18), dp(10));

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            header.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            valueBadge = new TextView(context);
            valueBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            valueBadge.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            valueBadge.setPadding(dp(9), dp(3), dp(9), dp(3));
            valueBadge.setSingleLine(true);
            header.addView(valueBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            seekBarView = new SeekBarView(context, true, getResourceProvider());
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int val = Math.round(min + (max - min) * progress);
                    if (val != currentValue) {
                        currentValue = val;
                        updateBadgeText();
                        if ((val == min || val == max || val == defaultVal) && val != lastHapticVal) {
                            lastHapticVal = val;
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } else if (val != min && val != max && val != defaultVal) {
                            lastHapticVal = Integer.MIN_VALUE;
                        }
                        if (onChange != null) {
                            onChange.accept(currentValue);
                        }
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }
            });
            addView(seekBarView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 4, 0, 0));

            FrameLayout rangeRow = new FrameLayout(context);
            leftLabel = new TextView(context);
            leftLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            leftLabel.setGravity(Gravity.LEFT);
            rangeRow.addView(leftLabel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            rightLabel = new TextView(context);
            rightLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            rightLabel.setGravity(Gravity.RIGHT);
            rangeRow.addView(rightLabel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

            addView(rangeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));
        }

        void bind(String title, int min, int max, int defaultVal, int value,
                  String minText, String maxText, boolean divider,
                  ValueFormatter formatter, IntConsumer onChange) {
            this.min = min;
            this.max = max;
            this.defaultVal = defaultVal;
            this.currentValue = Math.max(min, Math.min(max, value));
            this.needDivider = divider;
            this.formatter = formatter;
            this.onChange = onChange;
            this.lastHapticVal = Integer.MIN_VALUE;

            int accent = resolveMd3Accent();
            titleView.setText(title);
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(10));
            badgeBg.setColor(ColorUtils.setAlphaComponent(accent, 34));
            valueBadge.setBackground(badgeBg);
            valueBadge.setTextColor(accent);

            int grayText = getThemedColor(Theme.key_windowBackgroundWhiteGrayText);
            leftLabel.setText(minText);
            leftLabel.setTextColor(grayText);
            leftLabel.setAlpha(0.8f);
            rightLabel.setText(maxText);
            rightLabel.setTextColor(grayText);
            rightLabel.setAlpha(0.8f);

            float progress = max == min ? 0f : (currentValue - min) / (float) (max - min);
            seekBarView.setProgress(progress);
            updateBadgeText();
            invalidate();
        }

        private void updateBadgeText() {
            if (formatter != null) {
                valueBadge.setText(formatter.format(currentValue));
            } else {
                valueBadge.setText(String.valueOf(currentValue));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawLine(dp(18), getMeasuredHeight() - 1, getMeasuredWidth() - dp(18), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private int getCurrentPresetIndex() {
        int pitch = ChatsConfig.voiceChangerPitch.Int();
        int freq = ChatsConfig.voiceChangerFreqCrush.Int();
        int bit = ChatsConfig.voiceChangerBitCrush.Int();
        int echoDelay = ChatsConfig.voiceChangerEchoDelay.Int();
        int echoFeedback = ChatsConfig.voiceChangerEchoFeedback.Int();
        int glitch = ChatsConfig.voiceChangerGlitchRate.Int();

        if (pitch == 0 && freq == 48000 && bit == 16 && echoDelay == 0 && echoFeedback == 0 && glitch == 0) return 0;
        if (pitch == 0 && freq == 48000 && bit == 16 && echoDelay == 40 && echoFeedback == 45 && glitch == 75) return 1;
        if (pitch == 0 && freq == 8000 && bit == 4 && echoDelay == 0 && echoFeedback == 0 && glitch == 0) return 2;
        if (pitch == -6 && freq == 48000 && bit == 16 && echoDelay == 120 && echoFeedback == 35 && glitch == 0) return 3;
        if (pitch == 8 && freq == 48000 && bit == 16 && echoDelay == 0 && echoFeedback == 0 && glitch == 0) return 4;
        if (pitch == 0 && freq == 48000 && bit == 16 && echoDelay == 280 && echoFeedback == 65 && glitch == 0) return 5;
        if (pitch == 1 && freq == 6000 && bit == 6 && echoDelay == 0 && echoFeedback == 0 && glitch == 15) return 6;
        return -1;
    }
}
