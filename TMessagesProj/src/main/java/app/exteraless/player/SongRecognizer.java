package app.exteraless.player;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

/**
 * Настоящее распознавание аудио по отпечатку — порт генератора подписи SongRec
 * (https://github.com/marin-m/SongRec, src/fingerprinting/*) на Java.
 *
 * Конвейер: MediaExtractor/MediaCodec → PCM 16 кГц моно → FFT 2048 (окно Ханна, шаг 128)
 * → разброс пиков → поиск пиков по 4 частотным полосам → бинарная подпись
 * (data:audio/vnd.shazam.sig) → POST на эндпоинт tag.
 */
public final class SongRecognizer {

    public static final class Result {
        public final String title;
        public final String artist;
        public final String coverUrl;
        public String localCoverPath;

        public Result(String title, String artist) {
            this(title, artist, null);
        }

        public Result(String title, String artist, String coverUrl) {
            this.title = title;
            this.artist = artist;
            this.coverUrl = coverUrl;
        }

        public String getEffectiveCover() {
            if (!TextUtils.isEmpty(localCoverPath) && new File(localCoverPath).exists()) {
                return localCoverPath;
            }
            return coverUrl;
        }
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int SEGMENT_SECONDS = 6;

    private static final LruCache<String, Result> MEM_CACHE = new LruCache<>(128);
    private static final ThreadLocal<Generator> GENERATOR_CACHE = ThreadLocal.withInitial(Generator::new);

    private SongRecognizer() {
    }

    private static File getCacheDir() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "songrec");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File getCoversDir() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "songrec_covers");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static Result readDiskCache(String cacheKey) {
        try {
            File dir = getCacheDir();
            if (dir == null) return null;
            File f = new File(dir, cacheKey + ".json");
            if (!f.exists()) return null;
            byte[] bytes = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int read = 0;
                while (read < bytes.length) {
                    int r = in.read(bytes, read, bytes.length - read);
                    if (r < 0) break;
                    read += r;
                }
            }
            JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            String title = obj.optString("title", null);
            String artist = obj.optString("artist", null);
            String coverUrl = obj.optString("coverUrl", null);
            String localCoverPath = obj.optString("localCoverPath", null);
            if (title != null) {
                Result r = new Result(title, artist, coverUrl);
                r.localCoverPath = localCoverPath;
                return r;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private static void writeDiskCache(String cacheKey, Result result) {
        try {
            File dir = getCacheDir();
            if (dir == null || result == null) return;
            File f = new File(dir, cacheKey + ".json");
            JSONObject obj = new JSONObject();
            obj.put("title", result.title);
            obj.put("artist", result.artist);
            if (result.coverUrl != null) obj.put("coverUrl", result.coverUrl);
            if (result.localCoverPath != null) obj.put("localCoverPath", result.localCoverPath);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(obj.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String httpGet(String u) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (java.io.InputStream in = c.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            } finally {
                c.disconnect();
            }
        } catch (Throwable e) {
            return null;
        }
    }

    public static File fetchCoverByMetadata(String artist, String title) {
        if (TextUtils.isEmpty(title)) {
            return null;
        }
        String term = ((TextUtils.isEmpty(artist) ? "" : artist + " ") + title).trim();
        try {
            String q = java.net.URLEncoder.encode(term, "UTF-8");
            String body = httpGet("https://itunes.apple.com/search?term=" + q + "&media=music&entity=song&limit=1");
            if (body != null) {
                JSONArray arr = new JSONObject(body).optJSONArray("results");
                if (arr != null && arr.length() > 0) {
                    String art = arr.getJSONObject(0).optString("artworkUrl100", null);
                    if (!TextUtils.isEmpty(art)) {
                        File f = downloadAndCacheCover(art.replace("100x100bb", "600x600bb"));
                        if (f != null) {
                            return f;
                        }
                    }
                }
            }
            body = httpGet("https://api.deezer.com/search?q=" + q + "&limit=1");
            if (body != null) {
                JSONArray arr = new JSONObject(body).optJSONArray("data");
                if (arr != null && arr.length() > 0) {
                    JSONObject album = arr.getJSONObject(0).optJSONObject("album");
                    String art = album != null ? album.optString("cover_xl", null) : null;
                    if (!TextUtils.isEmpty(art)) {
                        return downloadAndCacheCover(art);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    public static File downloadAndCacheCover(String coverUrl) {
        if (TextUtils.isEmpty(coverUrl)) {
            return null;
        }
        try {
            File dir = getCoversDir();
            if (dir == null) return null;
            String name = Utilities.MD5(coverUrl) + ".jpg";
            File target = new File(dir, name);
            if (target.exists() && target.length() > 0) {
                return target;
            }
            File temp = new File(dir, name + ".tmp");
            HttpURLConnection conn = (HttpURLConnection) new URL(coverUrl).openConnection();
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(6_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
            conn.setRequestProperty("Connection", "Keep-Alive");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                if (temp.length() > 0 && temp.renameTo(target)) {
                    return target;
                }
            }
            conn.disconnect();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    /** Блокирующий вызов: вызывать только из фонового потока. */
    public static Result recognize(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        String cacheKey = Utilities.MD5(path + ":" + file.length() + ":" + file.lastModified());
        Result cached = MEM_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Result disk = readDiskCache(cacheKey);
        if (disk != null) {
            MEM_CACHE.put(cacheKey, disk);
            return disk;
        }

        try {
            short[] pcm = decodeSegment(path, SEGMENT_SECONDS);
            if (pcm == null || pcm.length < SAMPLE_RATE * 3) {
                return null;
            }
            Generator gen = GENERATOR_CACHE.get();
            gen.reset();
            gen.process(pcm);
            byte[] sig = gen.encode(pcm.length);
            Result res = query(sig, pcm.length * 1000L / SAMPLE_RATE);
            if (res != null) {
                if (!TextUtils.isEmpty(res.coverUrl)) {
                    File coverFile = downloadAndCacheCover(res.coverUrl);
                    if (coverFile != null && coverFile.exists()) {
                        res.localCoverPath = coverFile.getAbsolutePath();
                    }
                }
                MEM_CACHE.put(cacheKey, res);
                writeDiskCache(cacheKey, res);
            }
            return res;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // ------------------------------------------------------------------ decoding

    /** Декодирует только первые seconds секунд файла в 16 кГц моно (без полного декодирования). */
    private static short[] decodeSegment(String path, int seconds) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(path);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0) {
                return null;
            }
            extractor.selectTrack(track);
            // Декодируем первые seconds секунд с самого начала файла без лишнего перемоточного поиска
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            int srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            boolean floatPcm = false;

            long needSrc = (long) srcRate * seconds;
            ShortList mono = new ShortList((int) needSrc + srcRate);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int idle = 0;
            while (!outputDone && mono.size < needSrc && idle < 200) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(5_000);
                    if (in >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(in);
                        int n = buf == null ? -1 : extractor.readSampleData(buf, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, n, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, 5_000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    needSrc = (long) srcRate * seconds;
                    if (of.containsKey("pcm-encoding")) {
                        floatPcm = of.getInteger("pcm-encoding") == 4; // ENCODING_PCM_FLOAT
                    }
                } else if (out >= 0) {
                    idle = 0;
                    ByteBuffer ob = codec.getOutputBuffer(out);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ob.order(ByteOrder.nativeOrder());
                        if (floatPcm) {
                            int frames = info.size / 4 / channels;
                            for (int f = 0; f < frames; f++) {
                                float s = 0;
                                for (int c = 0; c < channels; c++) s += ob.getFloat();
                                mono.add((short) Math.max(-32768, Math.min(32767, s / channels * 32767f)));
                                if (mono.size >= needSrc) break;
                            }
                        } else {
                            int frames = info.size / 2 / channels;
                            for (int f = 0; f < frames; f++) {
                                int s = 0;
                                for (int c = 0; c < channels; c++) s += ob.getShort();
                                mono.add((short) (s / channels));
                                if (mono.size >= needSrc) break;
                            }
                        }
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || mono.size >= needSrc) {
                        outputDone = true;
                    }
                } else {
                    idle++;
                }
            }
            return resample(mono.data, mono.size, srcRate, SAMPLE_RATE);
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Throwable ignore) { }
                try { codec.release(); } catch (Throwable ignore) { }
            }
            extractor.release();
        }
    }

    private static short[] resample(short[] src, int len, int from, int to) {
        if (from == to) {
            short[] r = new short[len];
            System.arraycopy(src, 0, r, 0, len);
            return r;
        }
        int outLen = (int) ((long) len * to / from);
        short[] out = new short[outLen];
        double ratio = (double) from / to;
        if (from > to) {
            // усреднение по окну — простой антиалиасинг при понижении частоты
            for (int i = 0; i < outLen; i++) {
                int a = (int) (i * ratio);
                int b = Math.min(len, (int) ((i + 1) * ratio));
                long sum = 0;
                for (int k = a; k < b; k++) sum += src[k];
                out[i] = (short) (b > a ? sum / (b - a) : src[Math.min(a, len - 1)]);
            }
        } else {
            for (int i = 0; i < outLen; i++) {
                double p = i * ratio;
                int a = (int) p;
                int b = Math.min(len - 1, a + 1);
                double t = p - a;
                out[i] = (short) (src[a] * (1 - t) + src[b] * t);
            }
        }
        return out;
    }

    private static final class ShortList {
        short[] data;
        int size;

        ShortList(int cap) {
            data = new short[Math.max(16, cap)];
        }

        void add(short v) {
            if (size == data.length) {
                short[] n = new short[data.length * 2];
                System.arraycopy(data, 0, n, 0, size);
                data = n;
            }
            data[size++] = v;
        }
    }

    // ------------------------------------------------------------------ signature generator

    static final class Peak {
        final int fftPass;
        final int magnitude;
        final int bin;

        Peak(int fftPass, int magnitude, int bin) {
            this.fftPass = fftPass;
            this.magnitude = magnitude;
            this.bin = bin;
        }
    }

    static final class Generator {
        private static final double[] HANNING = new double[2048];

        static {
            for (int i = 0; i < 2048; i++) {
                HANNING[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * (i + 1) / 2049.0));
            }
        }

        private final short[] ring = new short[2048];
        private int ringIndex;

        private final float[][] fftOutputs = new float[256][1025];
        private int fftIndex;

        private final float[][] spreadOutputs = new float[256][1025];
        private int spreadIndex;
        private int spreadWritten;

        @SuppressWarnings("unchecked")
        final List<Peak>[] bands = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};

        private final double[] re = new double[2048];
        private final double[] im = new double[2048];

        void reset() {
            java.util.Arrays.fill(ring, (short) 0);
            ringIndex = 0;
            for (int i = 0; i < 256; i++) {
                java.util.Arrays.fill(fftOutputs[i], 0f);
                java.util.Arrays.fill(spreadOutputs[i], 0f);
            }
            fftIndex = 0;
            spreadIndex = 0;
            spreadWritten = 0;
            for (int i = 0; i < 4; i++) {
                bands[i].clear();
            }
        }

        void process(short[] pcm) {
            for (int off = 0; off + 128 <= pcm.length; off += 128) {
                doFft(pcm, off);
                doPeakSpreading();
                if (spreadWritten >= 46) {
                    doPeakRecognition();
                }
            }
        }

        private void doFft(short[] pcm, int off) {
            System.arraycopy(pcm, off, ring, ringIndex, 128);
            ringIndex = (ringIndex + 128) % 2048;
            for (int i = 0; i < 2048; i++) {
                re[i] = ring[(i + ringIndex) % 2048] * HANNING[i];
                im[i] = 0;
            }
            fft(re, im);
            float[] out = fftOutputs[fftIndex];
            for (int i = 0; i <= 1024; i++) {
                double v = (re[i] * re[i] + im[i] * im[i]) / (1 << 17);
                out[i] = (float) Math.max(v, 1e-10);
            }
            fftIndex = (fftIndex + 1) % 256;
        }

        private void doPeakSpreading() {
            float[] origin = fftOutputs[Math.floorMod(fftIndex - 1, 256)];
            float[] spread = spreadOutputs[spreadIndex];
            System.arraycopy(origin, 0, spread, 0, 1025);
            for (int p = 0; p <= 1022; p++) {
                spread[p] = Math.max(spread[p], Math.max(spread[p + 1], spread[p + 2]));
            }
            int[] formers = {1, 3, 6};
            for (int p = 0; p <= 1024; p++) {
                for (int former : formers) {
                    float[] f = spreadOutputs[Math.floorMod(spreadIndex - former, 256)];
                    f[p] = Math.max(f[p], spread[p]);
                }
            }
            spreadIndex = (spreadIndex + 1) % 256;
            spreadWritten++;
        }

        private static final int[] NEIGHBORS = {-10, -7, -4, -3, 1, 2, 5, 8};
        private static final int[] OTHERS = {-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249};

        private void doPeakRecognition() {
            float[] m46 = fftOutputs[Math.floorMod(fftIndex - 46, 256)];
            float[] m49 = spreadOutputs[Math.floorMod(spreadIndex - 49, 256)];
            for (int bin = 10; bin <= 1014; bin++) {
                float v = m46[bin];
                if (v < 1f / 64f || v < m49[bin - 1]) continue;
                float maxN = 0;
                for (int o : NEIGHBORS) maxN = Math.max(maxN, m49[bin + o]);
                if (v <= maxN) continue;
                float maxO = maxN;
                for (int o : OTHERS) {
                    maxO = Math.max(maxO, spreadOutputs[Math.floorMod(spreadIndex + o, 256)][bin - 1]);
                }
                if (v <= maxO) continue;

                int fftNumber = spreadWritten - 46;
                double peak = Math.log(Math.max(1.0 / 64, v)) * 1477.3 + 6144;
                double before = Math.log(Math.max(1.0 / 64, m46[bin - 1])) * 1477.3 + 6144;
                double after = Math.log(Math.max(1.0 / 64, m46[bin + 1])) * 1477.3 + 6144;
                double var1 = peak * 2 - before - after;
                double var2 = (after - before) * 32 / var1;
                double corrected = bin * 64 + var2;
                double hz = corrected * (16000.0 / 2 / 1024 / 64);
                int band;
                if (hz >= 250 && hz < 520) band = 0;
                else if (hz >= 520 && hz < 1450) band = 1;
                else if (hz >= 1450 && hz < 3500) band = 2;
                else if (hz >= 3500 && hz <= 5500) band = 3;
                else continue;
                bands[band].add(new Peak(fftNumber, (int) peak & 0xFFFF, (int) corrected & 0xFFFF));
            }
        }

        /** Итеративное radix-2 FFT на месте. */
        private static void fft(double[] re, double[] im) {
            int n = re.length;
            for (int i = 1, j = 0; i < n; i++) {
                int bit = n >> 1;
                for (; (j & bit) != 0; bit >>= 1) j ^= bit;
                j ^= bit;
                if (i < j) {
                    double t = re[i]; re[i] = re[j]; re[j] = t;
                    t = im[i]; im[i] = im[j]; im[j] = t;
                }
            }
            for (int len = 2; len <= n; len <<= 1) {
                double ang = -2 * Math.PI / len;
                double wr = Math.cos(ang), wi = Math.sin(ang);
                for (int i = 0; i < n; i += len) {
                    double cr = 1, ci = 0;
                    for (int k = 0; k < len / 2; k++) {
                        int a = i + k, b = a + len / 2;
                        double xr = re[b] * cr - im[b] * ci;
                        double xi = re[b] * ci + im[b] * cr;
                        re[b] = re[a] - xr; im[b] = im[a] - xi;
                        re[a] += xr; im[a] += xi;
                        double ncr = cr * wr - ci * wi;
                        ci = cr * wi + ci * wr;
                        cr = ncr;
                    }
                }
            }
        }

        /** Бинарный формат подписи — signature_format.rs encode_to_binary. */
        byte[] encode(int numberSamples) {
            ByteArrayOutputStream contents = new ByteArrayOutputStream();
            for (int band = 0; band < 4; band++) {
                List<Peak> peaks = bands[band];
                if (peaks.isEmpty()) continue;
                ByteArrayOutputStream pb = new ByteArrayOutputStream();
                int pass = 0;
                for (Peak p : peaks) {
                    if (p.fftPass - pass >= 255) {
                        pb.write(0xFF);
                        writeU32(pb, p.fftPass);
                        pass = p.fftPass;
                    }
                    pb.write(p.fftPass - pass);
                    writeU16(pb, p.magnitude);
                    writeU16(pb, p.bin);
                    pass = p.fftPass;
                }
                byte[] pbytes = pb.toByteArray();
                writeU32(contents, 0x60030040 + band);
                writeU32(contents, pbytes.length);
                contents.write(pbytes, 0, pbytes.length);
                for (int i = 0; i < (4 - pbytes.length % 4) % 4; i++) contents.write(0);
            }
            byte[] cbytes = contents.toByteArray();
            int sizeMinusHeader = cbytes.length + 8;

            ByteBuffer bb = ByteBuffer.allocate(48 + 8 + cbytes.length).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(0xcafe2580);
            bb.putInt(0); // crc32, заполняется ниже
            bb.putInt(sizeMinusHeader);
            bb.putInt(0x94119c00);
            bb.putInt(0); bb.putInt(0); bb.putInt(0);
            bb.putInt(3 << 27); // sample rate id: 16000 Гц
            bb.putInt(0); bb.putInt(0);
            bb.putInt((int) (numberSamples + SAMPLE_RATE * 0.24));
            bb.putInt((15 << 19) + 0x40000);
            bb.putInt(0x40000000);
            bb.putInt(sizeMinusHeader);
            bb.put(cbytes);
            byte[] all = bb.array();
            CRC32 crc = new CRC32();
            crc.update(all, 8, all.length - 8);
            ByteBuffer.wrap(all, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) crc.getValue());
            return all;
        }

        private static void writeU32(ByteArrayOutputStream o, int v) {
            o.write(v); o.write(v >> 8); o.write(v >> 16); o.write(v >> 24);
        }

        private static void writeU16(ByteArrayOutputStream o, int v) {
            o.write(v); o.write(v >> 8);
        }
    }

    // ------------------------------------------------------------------ network

    private static Result query(byte[] sig, long sampleMs) throws Exception {
        String uri = "data:audio/vnd.shazam.sig;base64," + Base64.encodeToString(sig, Base64.NO_WRAP);
        long now = System.currentTimeMillis();
        JSONObject body = new JSONObject();
        body.put("geolocation", new JSONObject().put("altitude", 300).put("latitude", 45).put("longitude", 2));
        body.put("signature", new JSONObject().put("samplems", sampleMs).put("timestamp", now).put("uri", uri));
        body.put("timestamp", now);
        body.put("timezone", TimeZone.getDefault().getID());

        String url = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/"
                + UUID.randomUUID().toString().toUpperCase() + "/" + UUID.randomUUID().toString().toUpperCase()
                + "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(5_000);
            c.setReadTimeout(6_000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Content-Language", "en_US");
            c.setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 13; Pixel 7 Build/TQ3A.230805.001)");
            c.setRequestProperty("Accept-Encoding", "gzip, deflate");
            c.setRequestProperty("Connection", "Keep-Alive");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() != 200) {
                return null;
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            InputStream rawIs = c.getInputStream();
            InputStream is = "gzip".equalsIgnoreCase(c.getContentEncoding()) ? new GZIPInputStream(rawIs) : rawIs;
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = is.read(b)) > 0) buf.write(b, 0, n);
            } finally {
                is.close();
            }
            JSONObject root = new JSONObject(buf.toString("UTF-8"));
            JSONObject track = root.optJSONObject("track");
            if (track == null) {
                return null;
            }
            String title = track.optString("title", null);
            if (title == null || title.isEmpty()) {
                return null;
            }
            String artist = track.optString("subtitle", null);
            String coverUrl = null;
            JSONObject images = track.optJSONObject("images");
            if (images != null) {
                if (images.has("coverarthq")) {
                    coverUrl = images.optString("coverarthq", null);
                } else if (images.has("coverart")) {
                    coverUrl = images.optString("coverart", null);
                } else if (images.has("background")) {
                    coverUrl = images.optString("background", null);
                }
            }
            return new Result(title, artist, coverUrl);
        } finally {
            c.disconnect();
        }
    }
}
