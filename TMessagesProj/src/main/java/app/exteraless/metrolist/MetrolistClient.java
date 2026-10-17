package app.exteraless.metrolist;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Open-source Innertube / YouTube Music API клиент (ядро Metrolist).
 * Обеспечивает поиск треков, извлечение аудиопотоков для ExoPlayer
 * и видеоклипов для видео-обложек (Canvas).
 */
public final class MetrolistClient {

    private static final String INNERTUBE_API_BASE = "https://music.youtube.com/youtubei/v1";
    private static final String USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String USER_AGENT_ANDROID = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; en_US) gzip";

    public static class Track {
        public final String id;
        public final String title;
        public final String artist;
        public final int duration; // в секундах
        public final String thumbnailUrl;
        public String audioUrl;
        public String videoCoverUrl;

        public Track(String id, String title, String artist, int duration, String thumbnailUrl) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Throwable throwable);
    }

    private static volatile MetrolistClient instance;

    public static MetrolistClient getInstance() {
        MetrolistClient local = instance;
        if (local == null) {
            synchronized (MetrolistClient.class) {
                local = instance;
                if (local == null) {
                    local = instance = new MetrolistClient();
                }
            }
        }
        return local;
    }

    private MetrolistClient() {
    }

    /**
     * Асинхронно получает информацию о треке и прямую ссылку на аудиопоток.
     */
    public void getPlaybackInfo(String videoId, Callback<Track> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Track track = fetchPlayer(videoId);
                if (track != null && !TextUtils.isEmpty(track.audioUrl)) {
                    callback.onSuccess(track);
                } else {
                    callback.onError(new Exception("Unable to extract audio stream for " + videoId));
                }
            } catch (Exception e) {
                FileLog.e(e);
                callback.onError(e);
            }
        });
    }

    /**
     * Поиск треков по поисковому запросу.
     */
    public void searchTracks(String query, Callback<List<Track>> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                List<Track> list = fetchSearch(query);
                callback.onSuccess(list);
            } catch (Exception e) {
                FileLog.e(e);
                callback.onError(e);
            }
        });
    }

    /**
     * Ищет трек и получает прямую ссылку на видеоряд (клип/Canvas) для видео-обложки.
     */
    public void getVideoCoverUrl(String videoId, Callback<String> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Track track = fetchPlayer(videoId);
                if (track != null && !TextUtils.isEmpty(track.videoCoverUrl)) {
                    callback.onSuccess(track.videoCoverUrl);
                } else {
                    callback.onError(new Exception("No video cover stream found"));
                }
            } catch (Exception e) {
                FileLog.e(e);
                callback.onError(e);
            }
        });
    }

    /**
     * Внутренний запрос к Innertube /player.
     */
    public Track fetchPlayer(String videoId) throws Exception {
        // Пробуем ANDROID_MUSIC контекст для прямого получения url
        JSONObject requestObj = new JSONObject();
        JSONObject context = new JSONObject();
        JSONObject client = new JSONObject();
        client.put("clientName", "ANDROID_MUSIC");
        client.put("clientVersion", "6.42.52");
        client.put("androidSdkVersion", 34);
        client.put("hl", "ru");
        client.put("gl", "RU");
        context.put("client", client);
        requestObj.put("context", context);
        requestObj.put("videoId", videoId);

        String jsonResponse = postJson(INNERTUBE_API_BASE + "/player", requestObj.toString(), USER_AGENT_ANDROID);
        JSONObject root = new JSONObject(jsonResponse);

        // Парсинг метаданных
        String title = "";
        String author = "";
        int duration = 0;
        String thumb = null;

        if (root.has("videoDetails")) {
            JSONObject vd = root.getJSONObject("videoDetails");
            title = vd.optString("title", "");
            author = vd.optString("author", "");
            duration = vd.optInt("lengthSeconds", 0);
            if (vd.has("thumbnail")) {
                JSONArray thumbs = vd.getJSONObject("thumbnail").optJSONArray("thumbnails");
                if (thumbs != null && thumbs.length() > 0) {
                    thumb = thumbs.getJSONObject(thumbs.length() - 1).optString("url");
                }
            }
        }

        Track track = new Track(videoId, title, author, duration, thumb);

        // Парсинг потоков (streamingData -> adaptiveFormats)
        if (root.has("streamingData")) {
            JSONObject streamingData = root.getJSONObject("streamingData");
            JSONArray adaptiveFormats = streamingData.optJSONArray("adaptiveFormats");
            if (adaptiveFormats != null) {
                int maxAudioBitrate = -1;
                String bestAudioUrl = null;
                int maxVideoHeight = -1;
                String bestVideoUrl = null;

                for (int i = 0; i < adaptiveFormats.length(); i++) {
                    JSONObject format = adaptiveFormats.getJSONObject(i);
                    String mimeType = format.optString("mimeType", "");
                    String url = format.optString("url", null);

                    if (TextUtils.isEmpty(url)) {
                        continue;
                    }

                    if (mimeType.startsWith("audio/")) {
                        int bitrate = format.optInt("bitrate", 0);
                        if (bitrate > maxAudioBitrate) {
                            maxAudioBitrate = bitrate;
                            bestAudioUrl = url;
                        }
                    } else if (mimeType.startsWith("video/mp4")) {
                        int height = format.optInt("height", 0);
                        // Для Canvas подходят легкие потоки 480p/720p
                        if (height > maxVideoHeight && height <= 720) {
                            maxVideoHeight = height;
                            bestVideoUrl = url;
                        }
                    }
                }

                track.audioUrl = bestAudioUrl;
                track.videoCoverUrl = bestVideoUrl;
            }
        }

        // Если ANDROID_MUSIC не вернул прямую audioUrl, делаем fallback на WEB_REMIX
        if (TextUtils.isEmpty(track.audioUrl)) {
            client.put("clientName", "WEB_REMIX");
            client.put("clientVersion", "1.20240101.01.00");
            client.remove("androidSdkVersion");
            jsonResponse = postJson(INNERTUBE_API_BASE + "/player", requestObj.toString(), USER_AGENT_WEB);
            root = new JSONObject(jsonResponse);
            if (root.has("streamingData")) {
                JSONArray adaptive = root.getJSONObject("streamingData").optJSONArray("adaptiveFormats");
                if (adaptive != null) {
                    int maxBitrate = -1;
                    for (int i = 0; i < adaptive.length(); i++) {
                        JSONObject format = adaptive.getJSONObject(i);
                        String mimeType = format.optString("mimeType", "");
                        String url = format.optString("url", null);
                        if (!TextUtils.isEmpty(url) && mimeType.startsWith("audio/")) {
                            int br = format.optInt("bitrate", 0);
                            if (br > maxBitrate) {
                                maxBitrate = br;
                                track.audioUrl = url;
                            }
                        }
                    }
                }
            }
        }

        return track;
    }

    /**
     * Поиск треков через Innertube /search.
     */
    public List<Track> fetchSearch(String query) throws Exception {
        List<Track> result = new ArrayList<>();
        if (TextUtils.isEmpty(query)) {
            return result;
        }

        JSONObject requestObj = new JSONObject();
        JSONObject context = new JSONObject();
        JSONObject client = new JSONObject();
        client.put("clientName", "WEB_REMIX");
        client.put("clientVersion", "1.20240101.01.00");
        client.put("hl", "ru");
        client.put("gl", "RU");
        context.put("client", client);
        requestObj.put("context", context);
        requestObj.put("query", query);
        // Фильтр для песен
        requestObj.put("params", "Eg-KAQwIABAAGAAgACgB");

        String json = postJson(INNERTUBE_API_BASE + "/search", requestObj.toString(), USER_AGENT_WEB);
        JSONObject root = new JSONObject(json);

        // Парсинг результатов поиска
        if (!root.has("contents")) {
            return result;
        }

        // Рекурсивный поиск элементов videoId в дереве JSON
        findTracksRecursive(root, result);
        return result;
    }

    private void findTracksRecursive(JSONObject obj, List<Track> out) {
        if (obj == null) return;
        try {
            if (obj.has("videoId") && obj.has("title")) {
                String id = obj.getString("videoId");
                String title = "";
                Object tObj = obj.get("title");
                if (tObj instanceof JSONObject) {
                    JSONObject titleJson = (JSONObject) tObj;
                    if (titleJson.has("runs")) {
                        title = titleJson.getJSONArray("runs").getJSONObject(0).optString("text");
                    }
                } else {
                    title = tObj.toString();
                }

                String artist = "";
                if (obj.has("artists")) {
                    JSONArray arr = obj.getJSONArray("artists");
                    if (arr.length() > 0) {
                        artist = arr.getJSONObject(0).optString("name");
                    }
                } else if (obj.has("shortBylineText")) {
                    JSONArray runs = obj.getJSONObject("shortBylineText").optJSONArray("runs");
                    if (runs != null && runs.length() > 0) {
                        artist = runs.getJSONObject(0).optString("text");
                    }
                }

                int duration = 0;
                if (obj.has("lengthText")) {
                    String lenStr = obj.getJSONObject("lengthText").optJSONArray("runs").getJSONObject(0).optString("text");
                    duration = parseDuration(lenStr);
                }

                String thumb = null;
                if (obj.has("thumbnail")) {
                    JSONArray arr = obj.getJSONObject("thumbnail").optJSONArray("thumbnails");
                    if (arr != null && arr.length() > 0) {
                        thumb = arr.getJSONObject(arr.length() - 1).optString("url");
                    }
                }

                if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(title)) {
                    out.add(new Track(id, title, artist, duration, thumb));
                    if (out.size() >= 15) {
                        return;
                    }
                }
            }

            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = names.getString(i);
                    Object child = obj.get(key);
                    if (child instanceof JSONObject) {
                        findTracksRecursive((JSONObject) child, out);
                    } else if (child instanceof JSONArray) {
                        JSONArray arr = (JSONArray) child;
                        for (int j = 0; j < arr.length(); j++) {
                            Object item = arr.get(j);
                            if (item instanceof JSONObject) {
                                findTracksRecursive((JSONObject) item, out);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static int parseDuration(String str) {
        if (TextUtils.isEmpty(str)) return 0;
        try {
            String[] parts = str.trim().split(":");
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            } else if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String postJson(String urlStr, String jsonBody, String userAgent) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Origin", "https://music.youtube.com");
        conn.setRequestProperty("Referer", "https://music.youtube.com/");

        // Аутентификация Metrolist
        String authHeader = MetrolistAuth.getInstance().getAuthorizationHeader();
        if (!TextUtils.isEmpty(authHeader)) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        String cookieHeader = MetrolistAuth.getInstance().getCookieHeader();
        if (!TextUtils.isEmpty(cookieHeader)) {
            conn.setRequestProperty("Cookie", cookieHeader);
        }

        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            throw new Exception("HTTP response code: " + code);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
