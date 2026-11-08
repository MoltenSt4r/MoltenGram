package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import app.exteraless.appearance.AvatarCornersSeekBar;
import app.exteraless.chats.ChatsConfig;
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

    private int masterHeaderRow;
    private int masterEnableRow;
    private int masterDividerRow;

    private int presetsHeaderRow;
    private int presetsRow;
    private int presetsDividerRow;

    private int effectsHeaderRow;
    private int pitchSliderRow;
    private int freqCrushSliderRow;
    private int bitCrushSliderRow;
    private int echoDelaySliderRow;
    private int echoFeedbackSliderRow;
    private int glitchRateSliderRow;
    private int glitchRepeatsSliderRow;
    private int effectsDividerRow;

    private int testHeaderRow;
    private int testMicRow;
    private int testDividerRow;

    private boolean isTesting = false;
    private AudioTrack activeTestTrack = null;

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEVoiceChanger);
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        masterHeaderRow = addRow("masterHeader");
        masterEnableRow = addRow("masterEnable");
        masterDividerRow = addRow();

        boolean enabled = ChatsConfig.voiceChangerEnabled.Bool();

        if (enabled) {
            presetsHeaderRow = addRow("presetsHeader");
            presetsRow = addRow("presets");
            presetsDividerRow = addRow();

            effectsHeaderRow = addRow("effectsHeader");
            pitchSliderRow = addRow("pitchSlider");
            freqCrushSliderRow = addRow("freqCrushSlider");
            bitCrushSliderRow = addRow("bitCrushSlider");
            echoDelaySliderRow = addRow("echoDelaySlider");
            echoFeedbackSliderRow = addRow("echoFeedbackSlider");
            glitchRateSliderRow = addRow("glitchRateSlider");
            glitchRepeatsSliderRow = addRow("glitchRepeatsSlider");
            effectsDividerRow = addRow();

            testHeaderRow = addRow("testHeader");
            testMicRow = addRow("testMic");
            testDividerRow = addRow();
        } else {
            presetsHeaderRow = -1;
            presetsRow = -1;
            presetsDividerRow = -1;
            effectsHeaderRow = -1;
            pitchSliderRow = -1;
            freqCrushSliderRow = -1;
            bitCrushSliderRow = -1;
            echoDelaySliderRow = -1;
            echoFeedbackSliderRow = -1;
            glitchRateSliderRow = -1;
            glitchRepeatsSliderRow = -1;
            effectsDividerRow = -1;
            testHeaderRow = -1;
            testMicRow = -1;
            testDividerRow = -1;
        }
    }

    @Override
    protected void onItemClick(View view, int position) {
        if (position == masterEnableRow) {
            boolean next = !ChatsConfig.voiceChangerEnabled.Bool();
            ChatsConfig.voiceChangerEnabled.setConfigBool(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
            VoiceChangerController.getInstance().resetState();
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        } else if (position == presetsRow) {
            showPresetDialog();
        } else if (position == testMicRow) {
            if (isTesting) {
                stopAudioTest();
            } else {
                startAudioTest();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        stopAudioTest();
    }

    private void showPresetDialog() {
        if (getParentActivity() == null) {
            return;
        }
        String[] presetNames = new String[] {
                getString(R.string.OEVoiceChangerPresetDefault),
                getString(R.string.OEVoiceChangerPresetGlitch),
                getString(R.string.OEVoiceChangerPresetBitcrush),
                getString(R.string.OEVoiceChangerPresetDemon),
                getString(R.string.OEVoiceChangerPresetChipmunk),
                getString(R.string.OEVoiceChangerPresetEcho),
                getString(R.string.OEVoiceChangerPresetRadio)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEVoiceChangerPresetSelect));
        builder.setItems(presetNames, (dialog, which) -> {
            applyPreset(which);
            VoiceChangerController.getInstance().resetState();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, presetNames[which]).show();
        });
        showDialog(builder.create());
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

    private String getCurrentPresetName() {
        int pitch = ChatsConfig.voiceChangerPitch.Int();
        int freq = ChatsConfig.voiceChangerFreqCrush.Int();
        int bit = ChatsConfig.voiceChangerBitCrush.Int();
        int echoDelay = ChatsConfig.voiceChangerEchoDelay.Int();
        int echoFeedback = ChatsConfig.voiceChangerEchoFeedback.Int();
        int glitch = ChatsConfig.voiceChangerGlitchRate.Int();

        if (pitch == 0 && freq == 48000 && bit == 16 && echoDelay == 0 && echoFeedback == 0 && glitch == 0) {
            return getString(R.string.OEVoiceChangerPresetDefault);
        }
        if (pitch == 0 && freq == 48000 && bit == 16 && echoDelay == 40 && echoFeedback == 45 && glitch == 75) {
            return getString(R.string.OEVoiceChangerPresetGlitch);
        }
        if (pitch == 0 && freq == 8000 && bit == 4 && echoDelay == 0 && echoFeedback == 0 && glitch == 0) {
            return getString(R.string.OEVoiceChangerPresetBitcrush);
        }
        if (pitch == -6 && freq == 48000 && bit == 16 && echoDelay == 120 && echoFeedback == 35 && glitch == 0) {
            return getString(R.string.OEVoiceChangerPresetDemon);
        }
        if (pitch == 8 && freq == 48000 && bit == 16 && echoDelay == 0 && echoFeedback == 0 && glitch == 0) {
            return getString(R.string.OEVoiceChangerPresetChipmunk);
        }
        if (pitch == 0 && freq == 48000 && bit == 16 && echoDelay == 280 && echoFeedback == 65 && glitch == 0) {
            return getString(R.string.OEVoiceChangerPresetEcho);
        }
        if (pitch == 1 && freq == 6000 && bit == 6 && echoDelay == 0 && echoFeedback == 0 && glitch == 15) {
            return getString(R.string.OEVoiceChangerPresetRadio);
        }
        return getString(R.string.OEVoiceChangerPresetCustom);
    }

    private void startAudioTest() {
        if (isTesting) {
            return;
        }
        isTesting = true;
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
                    while (isTesting && System.currentTimeMillis() - start < 3000 && totalBytes + bufferSize <= maxBytes) {
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
                if (!isTesting) {
                    return;
                }
                if (finalData == null || finalTotal <= 0) {
                    stopAudioTest();
                    return;
                }

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

                        long playDurationMs = (long) (finalTotal / 2.0 / sampleRate * 1000L) + 500L;
                        Thread.sleep(Math.max(500, playDurationMs));
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        stopAudioTest();
                    }
                });
            });
        });

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void stopAudioTest() {
        isTesting = false;
        if (activeTestTrack != null) {
            try {
                activeTestTrack.stop();
                activeTestTrack.release();
            } catch (Exception ignored) {}
            activeTestTrack = null;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_CHECK = 1;
        private static final int TYPE_SETTINGS = 2;
        private static final int TYPE_SHADOW = 3;
        private static final int TYPE_PITCH_SLIDER = 4;
        private static final int TYPE_FREQ_SLIDER = 5;
        private static final int TYPE_BITCRUSH_SLIDER = 6;
        private static final int TYPE_ECHO_DELAY_SLIDER = 7;
        private static final int TYPE_ECHO_FEEDBACK_SLIDER = 8;
        private static final int TYPE_GLITCH_RATE_SLIDER = 9;
        private static final int TYPE_GLITCH_REPEATS_SLIDER = 10;

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_SETTINGS:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_PITCH_SLIDER: {
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            value -> {
                                ChatsConfig.voiceChangerPitch.setConfigInt(value);
                                VoiceChangerController.getInstance().resetState();
                            },
                            -12, 12,
                            getString(R.string.OEVoiceChangerPitch),
                            "-12 st", "+12 st");
                    slider.setValueSuffix("st");
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                }
                case TYPE_FREQ_SLIDER: {
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            value -> {
                                ChatsConfig.voiceChangerFreqCrush.setConfigInt(value * 1000);
                                VoiceChangerController.getInstance().resetState();
                            },
                            4, 48,
                            getString(R.string.OEVoiceChangerFreqCrush),
                            "4 kHz", "48 kHz (Off)");
                    slider.setValueSuffix("kHz");
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                }
                case TYPE_BITCRUSH_SLIDER: {
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            value -> {
                                ChatsConfig.voiceChangerBitCrush.setConfigInt(value);
                                VoiceChangerController.getInstance().resetState();
                            },
                            2, 16,
                            getString(R.string.OEVoiceChangerBitCrush),
                            "2 bit", "16 bit (Off)");
                    slider.setValueSuffix("bit");
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                }
                case TYPE_ECHO_DELAY_SLIDER: {
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            value -> {
                                ChatsConfig.voiceChangerEchoDelay.setConfigInt(value * 10);
                                VoiceChangerController.getInstance().resetState();
                            },
                            0, 80,
                            getString(R.string.OEVoiceChangerEchoDelay),
                            "0 ms", "800 ms");
                    slider.setValueSuffix("0 ms");
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                }
                case TYPE_ECHO_FEEDBACK_SLIDER: {
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            value -> {
                                ChatsConfig.voiceChangerEchoFeedback.setConfigInt(value);
                                VoiceChangerController.getInstance().resetState();
                            },
                            0, 90,
                            getString(R.string.OEVoiceChangerEchoFeedback),
                            "0%", "90%");
                    slider.setValueSuffix("%");
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                }
                case TYPE_GLITCH_RATE_SLIDER: {
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            value -> {
                                ChatsConfig.voiceChangerGlitchRate.setConfigInt(value);
                                VoiceChangerController.getInstance().resetState();
                            },
                            0, 100,
                            getString(R.string.OEVoiceChangerGlitchRate),
                            "0%", "100%");
                    slider.setValueSuffix("%");
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                }
                case TYPE_GLITCH_REPEATS_SLIDER: {
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            value -> {
                                ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(value);
                                VoiceChangerController.getInstance().resetState();
                            },
                            2, 8,
                            getString(R.string.OEVoiceChangerGlitchRepeats),
                            "2x", "8x");
                    slider.setValueSuffix("x");
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                }
                case TYPE_SHADOW:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == masterHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChanger));
                    } else if (position == presetsHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChangerPresets));
                    } else if (position == effectsHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChangerEffects));
                    } else if (position == testHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChangerTest));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == masterEnableRow) {
                        cell.setTextAndCheck(getString(R.string.OEVoiceChangerEnable),
                                ChatsConfig.voiceChangerEnabled.Bool(), false);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == presetsRow) {
                        cell.setTextAndValue(getString(R.string.OEVoiceChangerPresetSelect), getCurrentPresetName(), false);
                    } else if (position == testMicRow) {
                        cell.setTextAndValue(getString(R.string.OEVoiceChangerTest),
                                isTesting ? getString(R.string.OEVoiceChangerPlayingTest) : getString(R.string.OEVoiceChangerRecordTest), false);
                    }
                    break;
                }
                case TYPE_PITCH_SLIDER: {
                    AvatarCornersSeekBar slider = (AvatarCornersSeekBar) holder.itemView;
                    slider.setValue(ChatsConfig.voiceChangerPitch.Int());
                    break;
                }
                case TYPE_FREQ_SLIDER: {
                    AvatarCornersSeekBar slider = (AvatarCornersSeekBar) holder.itemView;
                    slider.setValue(ChatsConfig.voiceChangerFreqCrush.Int() / 1000);
                    break;
                }
                case TYPE_BITCRUSH_SLIDER: {
                    AvatarCornersSeekBar slider = (AvatarCornersSeekBar) holder.itemView;
                    slider.setValue(ChatsConfig.voiceChangerBitCrush.Int());
                    break;
                }
                case TYPE_ECHO_DELAY_SLIDER: {
                    AvatarCornersSeekBar slider = (AvatarCornersSeekBar) holder.itemView;
                    slider.setValue(ChatsConfig.voiceChangerEchoDelay.Int() / 10);
                    break;
                }
                case TYPE_ECHO_FEEDBACK_SLIDER: {
                    AvatarCornersSeekBar slider = (AvatarCornersSeekBar) holder.itemView;
                    slider.setValue(ChatsConfig.voiceChangerEchoFeedback.Int());
                    break;
                }
                case TYPE_GLITCH_RATE_SLIDER: {
                    AvatarCornersSeekBar slider = (AvatarCornersSeekBar) holder.itemView;
                    slider.setValue(ChatsConfig.voiceChangerGlitchRate.Int());
                    break;
                }
                case TYPE_GLITCH_REPEATS_SLIDER: {
                    AvatarCornersSeekBar slider = (AvatarCornersSeekBar) holder.itemView;
                    slider.setValue(ChatsConfig.voiceChangerGlitchRepeats.Int());
                    break;
                }
                case TYPE_SHADOW: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == effectsDividerRow) {
                        cell.setText(getString(R.string.OEVoiceChangerGlitchRateInfo));
                    } else {
                        cell.setText(null);
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterHeaderRow || position == presetsHeaderRow
                    || position == effectsHeaderRow || position == testHeaderRow) {
                return TYPE_HEADER;
            } else if (position == masterEnableRow) {
                return TYPE_CHECK;
            } else if (position == presetsRow || position == testMicRow) {
                return TYPE_SETTINGS;
            } else if (position == pitchSliderRow) {
                return TYPE_PITCH_SLIDER;
            } else if (position == freqCrushSliderRow) {
                return TYPE_FREQ_SLIDER;
            } else if (position == bitCrushSliderRow) {
                return TYPE_BITCRUSH_SLIDER;
            } else if (position == echoDelaySliderRow) {
                return TYPE_ECHO_DELAY_SLIDER;
            } else if (position == echoFeedbackSliderRow) {
                return TYPE_ECHO_FEEDBACK_SLIDER;
            } else if (position == glitchRateSliderRow) {
                return TYPE_GLITCH_RATE_SLIDER;
            } else if (position == glitchRepeatsSliderRow) {
                return TYPE_GLITCH_REPEATS_SLIDER;
            }
            return TYPE_SHADOW;
        }
    }
}
