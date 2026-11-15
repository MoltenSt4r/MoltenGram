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
    private double pitchPhase = 0.0;

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
        pitchPhase = 0.0;
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
        process(buffer, lenBytes, 48000);
    }

    /**
     * Обрабатывает буфер 16-bit PCM mono с заданной частотой дискретизации на лету
     */
    public synchronized void process(ByteBuffer buffer, int lenBytes, int sampleRate) {
        if (!isEnabled() || lenBytes <= 0) {
            return;
        }
        int sr = sampleRate > 0 ? sampleRate : 48000;

        int pitch = ChatsConfig.voiceChangerPitch();
        int freqCrush = ChatsConfig.voiceChangerFreqCrush();
        int bitCrush = ChatsConfig.voiceChangerBitCrush();
        int echoDelay = ChatsConfig.voiceChangerEchoDelay();
        int echoFeedback = ChatsConfig.voiceChangerEchoFeedback();
        int glitchRate = ChatsConfig.voiceChangerGlitchRate();
        int glitchRepeats = ChatsConfig.voiceChangerGlitchRepeats();

        boolean hasPitch = (pitch != 0);
        boolean hasFreq = (freqCrush < 48000 && freqCrush >= 1000 && freqCrush < sr);
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
        double phaseStep = (1.0 - pitchRatio) / PITCH_WINDOW;
        float freqStep = hasFreq ? ((float) sr / freqCrush) : 1.0f;
        int bitShift = hasBit ? (16 - bitCrush) : 0;
        int bitStep = hasBit ? (1 << bitShift) : 1;

        int echoDelaySamples = hasEcho ? Math.min(ECHO_BUF_SIZE - 1, Math.max(1, (int) (echoDelay * (long) sr / 1000L))) : 0;
        float echoFb = hasEcho ? Math.min(0.9f, echoFeedback / 100.0f) : 0.0f;

        for (int i = 0; i < numSamples; i++) {
            int byteIndex = startPos + i * 2;
            short sample = buffer.getShort(byteIndex);

            // 1. Pitch Shifter (dual-head Hann-windowed delay line, bounded inside [0..PITCH_WINDOW])
            if (hasPitch) {
                pitchBuffer[pitchWritePos] = sample;

                double p1 = pitchPhase;
                double p2 = p1 + 0.5;
                if (p2 >= 1.0) {
                    p2 -= 1.0;
                }

                double delay1 = p1 * PITCH_WINDOW;
                double delay2 = p2 * PITCH_WINDOW;

                double rp1 = pitchWritePos - delay1;
                if (rp1 < 0) rp1 += PITCH_BUF_SIZE;
                double rp2 = pitchWritePos - delay2;
                if (rp2 < 0) rp2 += PITCH_BUF_SIZE;

                int idx1 = (int) rp1 % PITCH_BUF_SIZE;
                int idx1Next = (idx1 + 1) % PITCH_BUF_SIZE;
                double frac1 = rp1 - (int) rp1;
                double s1 = pitchBuffer[idx1] * (1.0 - frac1) + pitchBuffer[idx1Next] * frac1;

                int idx2 = (int) rp2 % PITCH_BUF_SIZE;
                int idx2Next = (idx2 + 1) % PITCH_BUF_SIZE;
                double frac2 = rp2 - (int) rp2;
                double s2 = pitchBuffer[idx2] * (1.0 - frac2) + pitchBuffer[idx2Next] * frac2;

                double w1 = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * p1));
                double pitchOut = s1 * w1 + s2 * (1.0 - w1);
                sample = (short) Math.max(-32768, Math.min(32767, Math.round(pitchOut)));

                pitchWritePos = (pitchWritePos + 1) % PITCH_BUF_SIZE;
                pitchPhase += phaseStep;
                if (pitchPhase >= 1.0) {
                    pitchPhase -= 1.0;
                } else if (pitchPhase < 0.0) {
                    pitchPhase += 1.0;
                }
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
                        int grainBase = Math.max(240, (int) (sr * 0.02f));
                        int grainRand = Math.max(240, (int) (sr * 0.025f));
                        glitchGrainLength = Math.min(MAX_GRAIN_SIZE, grainBase + random.nextInt(grainRand));
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
                            glitchCooldown = (int) ((long) sr * (100 - glitchRate) / 200L + sr / 20);
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
