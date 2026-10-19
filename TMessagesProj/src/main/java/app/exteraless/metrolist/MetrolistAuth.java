package app.exteraless.metrolist;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Аутентификация для open-source клиента YouTube Music / Metrolist.
 * Поддерживает 3 режима:
 * 1. Гостевой режим (без входа, публичный Innertube)
 * 2. Импорт cookies (cookies.txt или сырая строка cookies)
 * 3. Вход через Google OAuth (сессионные токены)
 */
public final class MetrolistAuth {

    public static final int MODE_GUEST = 0;
    public static final int MODE_COOKIE = 1;
    public static final int MODE_GOOGLE_OAUTH = 2;

    private static final String PREFS_NAME = "metrolist_auth_prefs";
    private static final String KEY_MODE = "auth_mode";
    private static final String KEY_COOKIES = "cookies";
    private static final String KEY_OAUTH_TOKEN = "oauth_token";
    private static final String KEY_ACCOUNT_EMAIL = "account_email";
    private static final String KEY_ACCOUNT_NAME = "account_name";

    private static volatile MetrolistAuth instance;

    public static MetrolistAuth getInstance() {
        MetrolistAuth local = instance;
        if (local == null) {
            synchronized (MetrolistAuth.class) {
                local = instance;
                if (local == null) {
                    local = instance = new MetrolistAuth();
                }
            }
        }
        return local;
    }

    private final SharedPreferences prefs;

    private MetrolistAuth() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getAuthMode() {
        return prefs.getInt(KEY_MODE, MODE_GUEST);
    }

    public void setAuthMode(int mode) {
        prefs.edit().putInt(KEY_MODE, mode).apply();
    }

    public String getRawCookies() {
        return prefs.getString(KEY_COOKIES, "");
    }

    public void setCookies(String rawCookies) {
        String parsed = normalizeCookies(rawCookies);
        prefs.edit()
                .putString(KEY_COOKIES, parsed)
                .putInt(KEY_MODE, TextUtils.isEmpty(parsed) ? MODE_GUEST : MODE_COOKIE)
                .apply();
    }

    public String getOAuthToken() {
        return prefs.getString(KEY_OAUTH_TOKEN, "");
    }

    public void setOAuthToken(String token, String email, String name) {
        prefs.edit()
                .putString(KEY_OAUTH_TOKEN, token != null ? token : "")
                .putString(KEY_ACCOUNT_EMAIL, email != null ? email : "")
                .putString(KEY_ACCOUNT_NAME, name != null ? name : "")
                .putInt(KEY_MODE, TextUtils.isEmpty(token) ? MODE_GUEST : MODE_GOOGLE_OAUTH)
                .apply();
    }

    public String getAccountEmail() {
        return prefs.getString(KEY_ACCOUNT_EMAIL, "");
    }

    public String getAccountName() {
        return prefs.getString(KEY_ACCOUNT_NAME, "");
    }

    public void logout() {
        prefs.edit()
                .remove(KEY_COOKIES)
                .remove(KEY_OAUTH_TOKEN)
                .remove(KEY_ACCOUNT_EMAIL)
                .remove(KEY_ACCOUNT_NAME)
                .putInt(KEY_MODE, MODE_GUEST)
                .apply();
    }

    /**
     * Возвращает заголовок Authorization (SAPISIDHASH для cookies или Bearer для OAuth).
     */
    public String getAuthorizationHeader() {
        int mode = getAuthMode();
        if (mode == MODE_GOOGLE_OAUTH) {
            String token = getOAuthToken();
            if (!TextUtils.isEmpty(token)) {
                return token.startsWith("Bearer ") ? token : "Bearer " + token;
            }
        } else if (mode == MODE_COOKIE) {
            String cookies = getRawCookies();
            String sapisid = extractCookieValue(cookies, "SAPISID");
            if (TextUtils.isEmpty(sapisid)) {
                sapisid = extractCookieValue(cookies, "__Secure-3PAPISID");
            }
            if (!TextUtils.isEmpty(sapisid)) {
                return generateSapisidHash(sapisid, "https://music.youtube.com");
            }
        }
        return null;
    }

    /**
     * Возвращает заголовок Cookie для запросов Innertube.
     */
    public String getCookieHeader() {
        if (getAuthMode() == MODE_COOKIE) {
            return getRawCookies();
        }
        return "";
    }

    /**
     * Вычисляет SAPISIDHASH: {timestamp}_{sha1(timestamp + " " + sapisid + " " + origin)}
     */
    public static String generateSapisidHash(String sapisid, String origin) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String payload = timestamp + " " + sapisid + " " + origin;
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "SAPISIDHASH " + timestamp + "_" + hexString;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * Нормализует куки из формата cookies.txt (Netscape) или стандартного заголовка "k=v; k2=v2".
     */
    public static String normalizeCookies(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        Map<String, String> cookieMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Netscape format: domain, flag, path, secure, expiration, name, value
                String[] parts = line.split("\t");
                if (parts.length >= 7) {
                    cookieMap.put(parts[5].trim(), parts[6].trim());
                } else if (line.contains("=")) {
                    // Standard "name=value; other=val"
                    String[] pairs = line.split(";");
                    for (String pair : pairs) {
                        int eq = pair.indexOf('=');
                        if (eq > 0) {
                            String k = pair.substring(0, eq).trim();
                            String v = pair.substring(eq + 1).trim();
                            if (!k.isEmpty()) {
                                cookieMap.put(k, v);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (cookieMap.isEmpty()) {
            return input.trim();
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    public static String extractCookieValue(String cookieString, String key) {
        if (TextUtils.isEmpty(cookieString) || TextUtils.isEmpty(key)) {
            return null;
        }
        String[] pairs = cookieString.split(";");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equalsIgnoreCase(key)) {
                return kv[1].trim();
            }
        }
        return null;
    }
}
