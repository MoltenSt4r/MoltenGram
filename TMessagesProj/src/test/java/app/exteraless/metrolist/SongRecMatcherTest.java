package app.exteraless.metrolist;

import static org.junit.Assert.*;

import org.junit.Test;

public class SongRecMatcherTest {

    @Test
    public void testStringSimilarity() {
        // Exact match
        assertEquals(1.0f, SongRecMatcher.stringSimilarity("Afterglow", "Afterglow"), 0.001f);

        // Substring / contained
        assertEquals(1.0f, SongRecMatcher.stringSimilarity("Afterglow", "Ed Sheeran - Afterglow"), 0.001f);

        // Word overlap
        float sim = SongRecMatcher.stringSimilarity("Ed Sheeran Afterglow Official Audio", "Afterglow Ed Sheeran");
        assertTrue(sim >= 0.5f);
    }

    @Test
    public void testCandidateEvaluation() {
        SongRecMatcher matcher = SongRecMatcher.getInstance();
        MetrolistClient.Track cand = new MetrolistClient.Track(
                "dQw4w9WgXcQ",
                "Afterglow",
                "Ed Sheeran",
                185,
                "https://example.com/thumb.jpg"
        );

        float score = matcher.evaluateMatch("Afterglow", "Ed Sheeran", 185, cand, null);
        assertTrue("High confidence expected for matching track metadata", score >= 0.85f);

        // Different track
        MetrolistClient.Track wrongCand = new MetrolistClient.Track(
                "abc12345678",
                "Shape of You",
                "Ed Sheeran",
                233,
                "https://example.com/thumb2.jpg"
        );
        float wrongScore = matcher.evaluateMatch("Afterglow", "Wilkinson", 185, wrongCand, null);
        assertTrue("Low score expected for completely different song", wrongScore < 0.50f);
    }

    @Test
    public void testUpgradeThumbnail() {
        String ytUrl = "https://lh3.googleusercontent.com/sample=w120-h120-l90-rj";
        String hdUrl = SongRecMatcher.upgradeThumbnail(ytUrl);
        assertEquals("https://lh3.googleusercontent.com/sample=w800-h800-l90-rj", hdUrl);
    }
}
