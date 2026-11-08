package app.exteraless.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import app.exteraless.chats.ChatsConfig;

/**
 * Аудиопроцессор голосовых сообщений (Voice Changer):
 * - Тональность (Pitch Shift)
 * - Freqcrush (децимация/понижение дискретизации)
 * - Bitcrush (понижение разрядности аудио)
 * - Эхо (задержка и обратная связь)
 * - Случайные глитчи/заикания (эффект «перегрева/зависания ПК»)
 */
public class VoiceChangerController {

    private static volatile VoiceChangerController instance;

    public static VoiceChangerController getInstance() {
        if (instance == null) {
            synchronized (VoiceChangerController.class) {
                if (instance == null) {
                    instance = new VoiceChangerController();
                }
            }
        }
        return instance;
    }

    private final Random random = new Random();

    // Pitch shifter state (Dual-head crossfading delay-line)
    private static final int PITCH_BUF_SIZE = 8192;
    private static final int PITCH_WINDOW = 2048;
    private final float[] pitchBuffer = new float[PITCH_BUF_SIZE];
    private int pitchWritePos = 0;
    private double pitchReadPos1 = 0;
    private double pitchReadPos2 = PITCH_WINDOW / 2.0;

    // Freqcrush state
    private float freqAccumulator = 0;
    private short heldFreqSample = 0;

    // Echo state
    private static final int ECHO_BUF_SIZE = 48000; // 1 second at 48kHz
    private final short[] echoBuffer = new short[ECHO_BUF_SIZE];
    private int echoWritePos = 0;

    // Glitch/stutter state
    private static final int MAX_GRAIN_SIZE = 2400; // 50ms at 48kHz
    private final short[] glitchGrain = new short[MAX_GRAIN_SIZE];
    private int glitchWritePos = 0;
    private boolean isGlitching = false;
    private int glitchGrainLength = 1440;
    private int glitchRepeatsLeft = 0;
    private int glitchReadPos = 0;
    private int glitchCooldown = 0;

    private VoiceChangerController() {
    }

    public boolean isEnabled() {
        return ChatsConfig.voiceChangerEnabled();
    }

    public synchronized void resetState() {
        // Pitch
        pitchWritePos = 0;
        pitchReadPos1 = 0;
        pitchReadPos2 = PITCH_WINDOW / 2.0;
        for (int i = 0; i < pitchBuffer.length; i++) {
            pitchBuffer[i] = 0;
        }

        // Freqcrush
        freqAccumulator = 0;
        heldFreqSample = 0;

        // Echo
        echoWritePos = 0;
        for (int i = 0; i < echoBuffer.length; i++) {
            echoBuffer[i] = 0;
        }

        // Glitch
        isGlitching = false;
        glitchWritePos = 0;
        glitchReadPos = 0;
        glitchRepeatsLeft = 0;
        glitchCooldown = 0;
        for (int i = 0; i < glitchGrain.length; i++) {
            glitchGrain[i] = 0;
        }
    }

    /**
     * Обрабатывает буфер 16-bit PCM mono 48kHz на лету
     */
    public synchronized void process(ByteBuffer buffer, int lenBytes) {
        if (!isEnabled() || lenBytes <= 0) {
            return;
        }

        int pitch = ChatsConfig.voiceChangerPitch();
        int freqCrush = ChatsConfig.voiceChangerFreqCrush();
        int bitCrush = ChatsConfig.voiceChangerBitCrush();
        int echoDelay = ChatsConfig.voiceChangerEchoDelay();
        int echoFeedback = ChatsConfig.voiceChangerEchoFeedback();
        int glitchRate = ChatsConfig.voiceChangerGlitchRate();
        int glitchRepeats = ChatsConfig.voiceChangerGlitchRepeats();

        boolean hasPitch = (pitch != 0);
        boolean hasFreq = (freqCrush < 48000 && freqCrush >= 1000);
        boolean hasBit = (bitCrush < 16 && bitCrush >= 2);
        boolean hasEcho = (echoDelay > 0 && echoFeedback > 0);
        boolean hasGlitch = (glitchRate > 0);

        if (!hasPitch && !hasFreq && !hasBit && !hasEcho && !hasGlitch) {
            return;
        }

        ByteOrder originalOrder = buffer.order();
        buffer.order(ByteOrder.nativeOrder());
        int startPos = buffer.position();
        int numSamples = lenBytes / 2;

        double pitchRatio = hasPitch ? Math.pow(2.0, pitch / 12.0) : 1.0;
        float freqStep = hasFreq ? (48000.0f / freqCrush) : 1.0f;
        int bitShift = hasBit ? (16 - bitCrush) : 0;
        int bitStep = hasBit ? (1 << bitShift) : 1;

        int echoDelaySamples = hasEcho ? Math.min(ECHO_BUF_SIZE - 1, (int) (echoDelay * 48000L / 1000L)) : 0;
        float echoFb = hasEcho ? Math.min(0.9f, echoFeedback / 100.0f) : 0.0f;

        for (int i = 0; i < numSamples; i++) {
            int byteIndex = startPos + i * 2;
            short sample = buffer.getShort(byteIndex);

            // 1. Pitch Shifter
            if (hasPitch) {
                pitchBuffer[pitchWritePos] = sample;

                double delta = (pitchWritePos - pitchReadPos1 + PITCH_BUF_SIZE) % PITCH_BUF_SIZE;
                double phase = (delta % PITCH_WINDOW) / PITCH_WINDOW;
                double w = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * phase));

                int idx1 = (int) pitchReadPos1 % PITCH_BUF_SIZE;
                int idx1Next = (idx1 + 1) % PITCH_BUF_SIZE;
                double frac1 = pitchReadPos1 - (int) pitchReadPos1;
                double s1 = pitchBuffer[idx1] * (1.0 - frac1) + pitchBuffer[idx1Next] * frac1;

                int idx2 = (int) pitchReadPos2 % PITCH_BUF_SIZE;
                int idx2Next = (idx2 + 1) % PITCH_BUF_SIZE;
                double frac2 = pitchReadPos2 - (int) pitchReadPos2;
                double s2 = pitchBuffer[idx2] * (1.0 - frac2) + pitchBuffer[idx2Next] * frac2;

                double pitchOut = s1 * w + s2 * (1.0 - w);
                sample = (short) Math.max(-32768, Math.min(32767, Math.round(pitchOut)));

                pitchWritePos = (pitchWritePos + 1) % PITCH_BUF_SIZE;
                pitchReadPos1 = (pitchReadPos1 + pitchRatio) % PITCH_BUF_SIZE;
                pitchReadPos2 = (pitchReadPos2 + pitchRatio) % PITCH_BUF_SIZE;
            }

            // 2. Random Glitch / Stutter («зависание ПК»)
            if (hasGlitch) {
                if (!isGlitching) {
                    glitchGrain[glitchWritePos] = sample;
                    glitchWritePos = (glitchWritePos + 1) % MAX_GRAIN_SIZE;

                    if (glitchCooldown > 0) {
                        glitchCooldown--;
                    } else if (random.nextFloat() < (glitchRate / 100.0f) * 0.0004f) {
                        isGlitching = true;
                        glitchGrainLength = 960 + random.nextInt(1200); // ~20-45ms зерно
                        glitchRepeatsLeft = Math.max(2, glitchRepeats);
                        glitchReadPos = 0;
                    }
                } else {
                    sample = glitchGrain[glitchReadPos];
                    glitchReadPos++;
                    if (glitchReadPos >= glitchGrainLength) {
                        glitchReadPos = 0;
                        glitchRepeatsLeft--;
                        if (glitchRepeatsLeft <= 0) {
                            isGlitching = false;
                            glitchCooldown = (int) (48000L * (100 - glitchRate) / 200L + 2400);
                        }
                    }
                }
            }

            // 3. Freqcrush (Downsampling)
            if (hasFreq) {
                freqAccumulator += 1.0f;
                if (freqAccumulator >= freqStep) {
                    heldFreqSample = sample;
                    freqAccumulator -= freqStep;
                } else {
                    sample = heldFreqSample;
                }
            }

            // 4. Bitcrush (Quantization)
            if (hasBit) {
                int rounded = ((sample + (bitStep >> 1)) / bitStep) * bitStep;
                sample = (short) Math.max(-32768, Math.min(32767, rounded));
            }

            // 5. Echo (Delay & Feedback)
            if (hasEcho) {
                int readIdx = (echoWritePos - echoDelaySamples + ECHO_BUF_SIZE) % ECHO_BUF_SIZE;
                short delayed = echoBuffer[readIdx];
                int mixed = (int) (sample + delayed * echoFb);
                mixed = Math.max(-32768, Math.min(32767, mixed));

                echoBuffer[echoWritePos] = (short) mixed;
                echoWritePos = (echoWritePos + 1) % ECHO_BUF_SIZE;
                sample = (short) mixed;
            }

            buffer.putShort(byteIndex, sample);
        }

        buffer.position(startPos);
        buffer.order(originalOrder);
    }
}
