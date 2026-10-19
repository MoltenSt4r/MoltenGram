package app.exteraless.metrolist;

import static org.junit.Assert.*;

import org.junit.Test;

public class MetrolistUrlMatcherTest {

    @Test
    public void testStrictMatching_musicYoutubeCom() {
        String url = "https://music.youtube.com/watch?v=dQw4w9WgXcQ";
        assertTrue("music.youtube.com watch must be supported", MetrolistUrlMatcher.isSupportedUrl(url));
        assertEquals("dQw4w9WgXcQ", MetrolistUrlMatcher.extractVideoId(url));
    }

    @Test
    public void testStrictMatching_metrolistCc() {
        String trackUrl = "https://metrolist.cc/track/dQw4w9WgXcQ";
        assertTrue("metrolist.cc/track must be supported", MetrolistUrlMatcher.isSupportedUrl(trackUrl));
        assertEquals("dQw4w9WgXcQ", MetrolistUrlMatcher.extractVideoId(trackUrl));

        String watchUrl = "https://metrolist.cc/watch?v=dQw4w9WgXcQ";
        assertTrue("metrolist.cc/watch must be supported", MetrolistUrlMatcher.isSupportedUrl(watchUrl));
        assertEquals("dQw4w9WgXcQ", MetrolistUrlMatcher.extractVideoId(watchUrl));
    }

    @Test
    public void testStrictRejection_regularYoutube() {
        // User explicitly demanded: regular youtube.com and youtu.be must NOT be intercepted!
        String[] rejectedUrls = {
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ",
                "https://youtube.com/shorts/dQw4w9WgXcQ",
                "https://www.google.com",
                "https://t.me/telegram"
        };

        for (String u : rejectedUrls) {
            assertFalse("URL must NOT be intercepted by Metrolist: " + u, MetrolistUrlMatcher.isSupportedUrl(u));
            assertNull("Video ID must be null for non-music link: " + u, MetrolistUrlMatcher.extractVideoId(u));
        }
    }
}
