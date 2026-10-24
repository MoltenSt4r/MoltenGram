package app.exteraless.metrolist;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сопоставитель URL для YouTube Music, youtu.be и Metrolist.
 * Перехватывает ссылки:
 * - music.youtube.com (watch, track, v=...)
 * - youtu.be/<videoId>
 * - metrolist.cc
 */
public final class MetrolistUrlMatcher {

    private static final Pattern YT_MUSIC_PATTERN = Pattern.compile("^https?://(?:www\\.)?music\\.youtube\\.com/(?:watch\\?.*?v=([a-zA-Z0-9_-]{11})|track/([a-zA-Z0-9_-]{11})|([a-zA-Z0-9_-]{11}))");
    private static final Pattern METROLIST_PATTERN = Pattern.compile("^https?://(?:www\\.)?metrolist\\.cc/(?:track/([a-zA-Z0-9_-]{11})|watch\\?.*?v=([a-zA-Z0-9_-]{11}))");
    private static final Pattern HOST_PATTERN = Pattern.compile("^https?://([^/?#]+)");
    private static final Pattern V_PARAM_PATTERN = Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTU_BE_PATTERN = Pattern.compile("^https?://(?:www\\.)?youtu\\.be/([a-zA-Z0-9_-]{11})");
    private static final Pattern YT_PATH_PATTERN = Pattern.compile("^/(?:shorts|live|embed|v)/([a-zA-Z0-9_-]{11})");

    private MetrolistUrlMatcher() {
    }

    /**
     * Проверяет, является ли ссылка поддерживаемой ссылкой YouTube Music, youtu.be или Metrolist.
     */
    public static boolean isSupportedUrl(String url) {
        return extractVideoId(url) != null;
    }

    /**
     * Извлекает 11-символьный videoId из поддерживаемого URL.
     */
    public static String extractVideoId(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        try {
            String host = null;
            try {
                URI uri = new URI(url);
                host = uri.getHost();
            } catch (Exception ignored) {
            }

            if (host == null) {
                Matcher hm = HOST_PATTERN.matcher(url);
                if (hm.find()) {
                    host = hm.group(1);
                }
            }

            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);

            // 1. youtu.be (основной формат шеринга треков с мобильного YouTube Music)
            if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
                Matcher m = YOUTU_BE_PATTERN.matcher(url);
                if (m.find()) {
                    return m.group(1);
                }
                try {
                    URI uri = new URI(url);
                    String path = uri.getPath();
                    if (path != null) {
                        if (path.startsWith("/")) path = path.substring(1);
                        int slash = path.indexOf('/');
                        if (slash > 0) path = path.substring(0, slash);
                        if (path.length() == 11 && path.matches("[a-zA-Z0-9_-]{11}")) {
                            return path;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // 2. YouTube & YouTube Music (music.youtube.com, youtube.com, www.youtube.com, m.youtube.com)
            if (host.equals("music.youtube.com") || host.endsWith(".music.youtube.com") || host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                Matcher vm = V_PARAM_PATTERN.matcher(url);
                if (vm.find()) {
                    return vm.group(1);
                }
                Matcher m = YT_MUSIC_PATTERN.matcher(url);
                if (m.find()) {
                    for (int i = 1; i <= m.groupCount(); i++) {
                        String id = m.group(i);
                        if (id != null && id.length() == 11) {
                            return id;
                        }
                    }
                }
                try {
                    URI uri = new URI(url);
                    String path = uri.getPath();
                    if (path != null) {
                        Matcher pm = YT_PATH_PATTERN.matcher(path);
                        if (pm.find()) {
                            return pm.group(1);
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // 3. Metrolist
            if (host.equals("metrolist.cc") || host.equals("www.metrolist.cc")) {
                Matcher vm = V_PARAM_PATTERN.matcher(url);
                if (vm.find()) {
                    return vm.group(1);
                }
                Matcher m2 = METROLIST_PATTERN.matcher(url);
                if (m2.find()) {
                    for (int i = 1; i <= m2.groupCount(); i++) {
                        String id = m2.group(i);
                        if (id != null && id.length() == 11) {
                            return id;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
