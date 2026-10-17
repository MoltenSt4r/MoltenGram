package app.exteraless.metrolist;

import static org.junit.Assert.*;

import org.junit.Test;

public class MetrolistAuthTest {

    @Test
    public void testCookieNormalization_standardString() {
        String raw = "SAPISID=abc123456; __Secure-3PAPISID=def789; HSID=xyz";
        String normalized = MetrolistAuth.normalizeCookies(raw);
        assertNotNull(normalized);
        assertTrue(normalized.contains("SAPISID=abc123456"));
        assertTrue(normalized.contains("__Secure-3PAPISID=def789"));
        assertTrue(normalized.contains("HSID=xyz"));

        assertEquals("abc123456", MetrolistAuth.extractCookieValue(normalized, "SAPISID"));
        assertEquals("def789", MetrolistAuth.extractCookieValue(normalized, "__Secure-3PAPISID"));
    }

    @Test
    public void testCookieNormalization_netscapeCookiesTxt() {
        String netscape = "# Netscape HTTP Cookie File\n" +
                ".youtube.com\tTRUE\t/\tTRUE\t1750000000\tSAPISID\tmy_sapisid_token_123\n" +
                ".youtube.com\tTRUE\t/\tTRUE\t1750000000\tLOGIN_INFO\tlogin_info_val\n";
        String normalized = MetrolistAuth.normalizeCookies(netscape);
        assertNotNull(normalized);
        assertTrue(normalized.contains("SAPISID=my_sapisid_token_123"));
        assertTrue(normalized.contains("LOGIN_INFO=login_info_val"));
        assertEquals("my_sapisid_token_123", MetrolistAuth.extractCookieValue(normalized, "SAPISID"));
    }

    @Test
    public void testSapisidHashGeneration() {
        String hash = MetrolistAuth.generateSapisidHash("test_sapisid_secret", "https://music.youtube.com");
        assertNotNull(hash);
        assertTrue(hash.startsWith("SAPISIDHASH "));
        assertTrue(hash.contains("_"));
        String[] parts = hash.substring("SAPISIDHASH ".length()).split("_");
        assertEquals(2, parts.length);
        assertTrue(Long.parseLong(parts[0]) > 0);
        assertEquals(40, parts[1].length()); // SHA-1 is 40 hex characters
    }
}
