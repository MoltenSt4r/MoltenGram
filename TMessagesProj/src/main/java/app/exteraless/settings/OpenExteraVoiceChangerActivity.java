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
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

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

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_SETTINGS = 2;
    private static final int TYPE_DETAIL_SETTINGS = 3;
    private static final int TYPE_SHADOW = 4;
    private static final int TYPE_SLIDER = 5;

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
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        actionBar.setTitle(getString(R.string.OEVoiceChanger));
        return view;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterEnableRow) {
            boolean enabled = ChatsConfig.voiceChangerEnabled.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            VoiceChangerController.getInstance().resetState();
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        } else if (position == presetsRow) {
            showPresetsDialog();
        } else if (position == testMicRow) {
            startAudioTest();
        }
    }

    private void showPresetsDialog() {
        final String[] presetNames = {
                getString(R.string.OEVoiceChangerPresetDefault),
                getString(R.string.OEVoiceChangerPresetGlitch),
                getString(R.string.OEVoiceChangerPresetBitcrush),
                getString(R.string.OEVoiceChangerPresetDemon),
                getString(R.string.OEVoiceChangerPresetChipmunk),
                getString(R.string.OEVoiceChangerPresetEcho),
                getString(R.string.OEVoiceChangerPresetWalkie)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEVoiceChangerPresetSelect));
        builder.setItems(presetNames, (dialog, which) -> {
            applyPreset(which);
            VoiceChangerController.getInstance().resetState();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.voip_unmute, presetNames[which]).show();
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
        int delay = ChatsConfig.voiceChangerEchoDelay.Int();
        int rate = ChatsConfig.voiceChangerGlitchRate.Int();

        if (pitch == 0 && freq == 48000 && bit == 16 && delay == 0 && rate == 0) {
            return getString(R.string.OEVoiceChangerPresetDefault);
        } else if (rate >= 60 && delay > 0) {
            return getString(R.string.OEVoiceChangerPresetGlitch);
        } else if (bit <= 6 && freq <= 10000) {
            return getString(R.string.OEVoiceChangerPresetBitcrush);
        } else if (pitch < -3) {
            return getString(R.string.OEVoiceChangerPresetDemon);
        } else if (pitch > 4) {
            return getString(R.string.OEVoiceChangerPresetChipmunk);
        } else if (delay > 200) {
            return getString(R.string.OEVoiceChangerPresetEcho);
        }
        return getString(R.string.OEVoiceChangerPresetCustom);
    }

    private void startAudioTest() {
        if (isTesting) {
            return;
        }
        isTesting = true;
        BulletinFactory.of(this).createSimpleBulletin(R.raw.voip_unmute, getString(R.string.OEVoiceChangerRecordTest)).show();

        Utilities.globalQueue.postRunnable(() -> {
            AudioRecord record = null;
            byte[] recordedData = null;
            int totalBytes = 0;
            int sampleRate = 48000;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize <= 0) bufferSize = 4096;

            try {
                record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
                record.startRecording();
                VoiceChangerController.getInstance().resetState();

                // Записываем 2.5 секунды аудио
                int maxBytes = sampleRate * 2 * 5 / 2; // ~2.5 sec = 240,000 bytes
                ByteBuffer accumulator = ByteBuffer.allocate(maxBytes);
                accumulator.order(ByteOrder.nativeOrder());
                ByteBuffer chunk = ByteBuffer.allocateDirect(bufferSize);
                chunk.order(ByteOrder.nativeOrder());

                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 2500 && accumulator.remaining() >= bufferSize) {
                    chunk.rewind();
                    int read = record.read(chunk, bufferSize);
                    if (read > 0) {
                        chunk.limit(read);
                        VoiceChangerController.getInstance().process(chunk, read);
                        chunk.position(0);
                        accumulator.put(chunk);
                        totalBytes += read;
                    }
                }
                recordedData = new byte[totalBytes];
                accumulator.position(0);
                accumulator.get(recordedData, 0, totalBytes);
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (record != null) {
                    try {
                        record.stop();
                        record.release();
                    } catch (Exception ignored) {
                    }
                }
            }

            if (recordedData != null && totalBytes > 0) {
                final byte[] audioToPlay = recordedData;
                final int bytesToPlay = totalBytes;
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.sound_on, getString(R.string.OEVoiceChangerStopTest)).show();
                    playAudioTest(audioToPlay, bytesToPlay, sampleRate);
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> isTesting = false);
            }
        });
    }

    private void playAudioTest(byte[] data, int length, int sampleRate) {
        Utilities.globalQueue.postRunnable(() -> {
            AudioTrack track = null;
            try {
                int minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, length), AudioTrack.MODE_STREAM);
                activeTestTrack = track;
                track.play();
                track.write(data, 0, length);
                // Ждём завершения воспроизведения
                Thread.sleep(length / (sampleRate * 2 / 1000) + 200);
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (track != null) {
                    try {
                        track.stop();
                        track.release();
                    } catch (Exception ignored) {
                    }
                }
                activeTestTrack = null;
                AndroidUtilities.runOnUIThread(() -> isTesting = false);
            }
        });
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (activeTestTrack != null) {
            try {
                activeTestTrack.stop();
                activeTestTrack.release();
            } catch (Exception ignored) {
            }
            activeTestTrack = null;
        }
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
                case TYPE_DETAIL_SETTINGS:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_SLIDER: {
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext, value -> {}, 0, 100, "", "", "");
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                }
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
                        cell.setText(getString(R.string.OEVoiceChangerSubtitle));
                    } else if (position == testHeaderRow) {
                        cell.setText(getString(R.string.OEVoiceChangerTestAudio));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == masterEnableRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.OEVoiceChangerEnable),
                                getString(R.string.OEVoiceChangerEnableInfo),
                                ChatsConfig.voiceChangerEnabled.Bool(), false, true);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == presetsRow) {
                        cell.setTextAndValue(getString(R.string.OEVoiceChangerPresets), getCurrentPresetName(), false);
                    } else if (position == testMicRow) {
                        cell.setText(getString(R.string.OEVoiceChangerRecordTest), false);
                    }
                    break;
                }
                case TYPE_SLIDER: {
                    AvatarCornersSeekBar slider = (AvatarCornersSeekBar) holder.itemView;
                    if (position == pitchSliderRow) {
                        slider.setTitle(getString(R.string.OEVoiceChangerPitch));
                        slider.setRange(-12, 12);
                        slider.setValueSuffix(" st");
                        slider.setLeftRightText("-12", "+12");
                        slider.setValue(ChatsConfig.voiceChangerPitch.Int());
                        slider.setOnDrag(value -> {
                            ChatsConfig.voiceChangerPitch.setConfigInt(value);
                            VoiceChangerController.getInstance().resetState();
                        });
                    } else if (position == freqCrushSliderRow) {
                        slider.setTitle(getString(R.string.OEVoiceChangerFreqCrush));
                        slider.setRange(1, 48);
                        slider.setValueSuffix(" kHz");
                        slider.setLeftRightText("1 kHz", "48 kHz");
                        int khz = Math.max(1, ChatsConfig.voiceChangerFreqCrush.Int() / 1000);
                        slider.setValue(khz);
                        slider.setOnDrag(value -> {
                            ChatsConfig.voiceChangerFreqCrush.setConfigInt(value * 1000);
                            VoiceChangerController.getInstance().resetState();
                        });
                    } else if (position == bitCrushSliderRow) {
                        slider.setTitle(getString(R.string.OEVoiceChangerBitCrush));
                        slider.setRange(2, 16);
                        slider.setValueSuffix(" bit");
                        slider.setLeftRightText("2 bit", "16 bit");
                        slider.setValue(ChatsConfig.voiceChangerBitCrush.Int());
                        slider.setOnDrag(value -> {
                            ChatsConfig.voiceChangerBitCrush.setConfigInt(value);
                            VoiceChangerController.getInstance().resetState();
                        });
                    } else if (position == echoDelaySliderRow) {
                        slider.setTitle(getString(R.string.OEVoiceChangerEchoDelay));
                        slider.setRange(0, 80);
                        slider.setValueSuffix("0 ms");
                        slider.setLeftRightText("0 ms", "800 ms");
                        slider.setValue(ChatsConfig.voiceChangerEchoDelay.Int() / 10);
                        slider.setOnDrag(value -> {
                            ChatsConfig.voiceChangerEchoDelay.setConfigInt(value * 10);
                            VoiceChangerController.getInstance().resetState();
                        });
                    } else if (position == echoFeedbackSliderRow) {
                        slider.setTitle(getString(R.string.OEVoiceChangerEchoFeedback));
                        slider.setRange(0, 90);
                        slider.setValueSuffix("%");
                        slider.setLeftRightText("0%", "90%");
                        slider.setValue(ChatsConfig.voiceChangerEchoFeedback.Int());
                        slider.setOnDrag(value -> {
                            ChatsConfig.voiceChangerEchoFeedback.setConfigInt(value);
                            VoiceChangerController.getInstance().resetState();
                        });
                    } else if (position == glitchRateSliderRow) {
                        slider.setTitle(getString(R.string.OEVoiceChangerGlitchRate));
                        slider.setRange(0, 100);
                        slider.setValueSuffix("%");
                        slider.setLeftRightText("0%", "100%");
                        slider.setValue(ChatsConfig.voiceChangerGlitchRate.Int());
                        slider.setOnDrag(value -> {
                            ChatsConfig.voiceChangerGlitchRate.setConfigInt(value);
                            VoiceChangerController.getInstance().resetState();
                        });
                    } else if (position == glitchRepeatsSliderRow) {
                        slider.setTitle(getString(R.string.OEVoiceChangerGlitchRepeats));
                        slider.setRange(2, 8);
                        slider.setValueSuffix("x");
                        slider.setLeftRightText("2x", "8x");
                        slider.setValue(ChatsConfig.voiceChangerGlitchRepeats.Int());
                        slider.setOnDrag(value -> {
                            ChatsConfig.voiceChangerGlitchRepeats.setConfigInt(value);
                            VoiceChangerController.getInstance().resetState();
                        });
                    }
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
            } else if (position == pitchSliderRow || position == freqCrushSliderRow
                    || position == bitCrushSliderRow || position == echoDelaySliderRow
                    || position == echoFeedbackSliderRow || position == glitchRateSliderRow
                    || position == glitchRepeatsSliderRow) {
                return TYPE_SLIDER;
            }
            return TYPE_SHADOW;
        }
    }
}
