package app.exteraless.metrolist;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Строгий сопоставитель URL для YouTube Music и Metrolist.
 * Перехватывает СТРОГО ТОЛЬКО ссылки:
 * - music.youtube.com
 * - metrolist.cc
 * 
 * Строго ИГНОРИРУЕТ обычные ссылки YouTube (youtube.com, youtu.be, m.youtube.com).
 */
public final class MetrolistUrlMatcher {

    private static final Pattern YT_MUSIC_PATTERN = Pattern.compile("^https?://music\\.youtube\\.com/(?:watch\\?.*v=([a-zA-Z0-9_-]{11})|track/([a-zA-Z0-9_-]{11}))");
    private static final Pattern METROLIST_PATTERN = Pattern.compile("^https?://(?:www\\.)?metrolist\\.cc/(?:track/([a-zA-Z0-9_-]{11})|watch\\?.*v=([a-zA-Z0-9_-]{11}))");
    private static final Pattern HOST_PATTERN = Pattern.compile("^https?://([^/?#]+)");
    private static final Pattern V_PARAM_PATTERN = Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})");

    private MetrolistUrlMatcher() {
    }

    /**
     * Проверяет, является ли ссылка поддерживаемой ссылкой YouTube Music или Metrolist.
     * Обычные ссылки YouTube (youtube.com, youtu.be) гарантированно возвращают false.
     */
    public static boolean isSupportedUrl(String url) {
        return extractVideoId(url) != null;
    }

    /**
     * Извлекает 11-символьный videoId из поддерживаемого URL.
     * Возвращает ID только если ссылка принадлежит music.youtube.com или metrolist.cc.
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

            // Строго запрещаем обычный YouTube
            if (host.equals("youtube.com") || host.endsWith(".youtube.com") && !host.equals("music.youtube.com")) {
                return null;
            }
            if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
                return null;
            }

            // 1. YouTube Music
            if (host.equals("music.youtube.com")) {
                Matcher m = YT_MUSIC_PATTERN.matcher(url);
                if (m.find()) {
                    String id = m.group(1);
                    if (id == null) id = m.group(2);
                    if (id != null && id.length() == 11) {
                        return id;
                    }
                }
                Matcher vm = V_PARAM_PATTERN.matcher(url);
                if (vm.find()) {
                    return vm.group(1);
                }
            }

            // 2. Metrolist
            if (host.equals("metrolist.cc") || host.equals("www.metrolist.cc")) {
                Matcher m2 = METROLIST_PATTERN.matcher(url);
                if (m2.find()) {
                    String id = m2.group(1);
                    if (id == null) id = m2.group(2);
                    if (id != null && id.length() == 11) {
                        return id;
                    }
                }
                Matcher vm = V_PARAM_PATTERN.matcher(url);
                if (vm.find()) {
                    return vm.group(1);
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
