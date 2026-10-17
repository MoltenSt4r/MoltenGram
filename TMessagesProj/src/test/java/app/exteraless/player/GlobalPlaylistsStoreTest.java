package app.exteraless.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import app.exteraless.player.LocalPlaylistsController.LocalPlaylist;
import app.exteraless.player.LocalPlaylistsController.PlaylistItem;

/**
 * Unit test suite for GlobalPlaylistsStore verifying:
 * 1. Disk persistence & cold reload integrity.
 * 2. Account-switch persistence (app-level storage independent of account).
 * 3. Account-reset and logout survival (survives account cache/db purge).
 * 4. Active playing playlist tracking and lifecycle.
 * 5. External streaming track persistence (Metrolist / YouTube Music).
 * 6. Deduplication logic.
 */
public class GlobalPlaylistsStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File storageFile;
    private GlobalPlaylistsStore store;

    @Before
    public void setUp() throws Exception {
        storageFile = tempFolder.newFile("test_playlists.json");
        store = new GlobalPlaylistsStore(storageFile);
    }

    @After
    public void tearDown() {
        GlobalPlaylistsStore.resetInstanceForTest();
    }

    /**
     * Requirement: testSaveAndReloadPlaylists
     * Verifies saving tracks to playlists, writing to disk, and cold reloading from disk.
     */
    @Test
    public void testSaveAndReloadPlaylists() {
        // 1. Initial state is empty
        assertTrue("Store must start empty", store.getPlaylists().isEmpty());

        // 2. Create playlist
        LocalPlaylist pl1 = store.createPlaylist("Favorites");
        assertNotNull("Created playlist must not be null", pl1);
        assertNotNull("Playlist ID must be generated", pl1.id);
        assertEquals("Favorites", pl1.name);

        // 3. Add first track with full Telegram metadata
        PlaylistItem track1 = new PlaylistItem();
        track1.title = "Afterglow";
        track1.performer = "Ed Sheeran";
        track1.duration = 185;
        track1.dialogId = 12345678L;
        track1.messageId = 101;
        track1.documentId = 987654321L;
        track1.accessHash = 555666777L;
        track1.dcId = 2;
        track1.size = 5242880L;
        track1.mimeType = "audio/mpeg";
        track1.fileName = "afterglow.mp3";
        track1.attachPath = "/mock/cache/afterglow.mp3";

        boolean added1 = store.addTrack(pl1.id, track1);
        assertTrue("First track should be added successfully", added1);

        // 4. Add second track
        PlaylistItem track2 = new PlaylistItem();
        track2.title = "Shape of You";
        track2.performer = "Ed Sheeran";
        track2.duration = 233;
        track2.attachPath = "/mock/cache/shape_of_you.mp3";

        boolean added2 = store.addTrack(pl1.id, track2);
        assertTrue("Second track should be added successfully", added2);

        // 5. Create a second playlist
        LocalPlaylist pl2 = store.createPlaylist("Workout");
        PlaylistItem track3 = new PlaylistItem();
        track3.title = "Eye of the Tiger";
        track3.performer = "Survivor";
        track3.duration = 245;
        store.addTrack(pl2.id, track3);

        // 6. Verify file exists on disk and is non-empty
        assertTrue("Storage file must exist", storageFile.exists());
        assertTrue("Storage file must have data", storageFile.length() > 0);

        // 7. Cold reload: instantiate a brand new store from the same file
        GlobalPlaylistsStore reloadedStore = new GlobalPlaylistsStore(storageFile);
        List<LocalPlaylist> reloadedList = reloadedStore.getPlaylists();

        assertEquals("Reloaded store must have 2 playlists", 2, reloadedList.size());

        // 8. Verify contents of playlist 1
        LocalPlaylist reloadedPl1 = reloadedStore.getPlaylist(pl1.id);
        assertNotNull("Playlist 1 must be present in reloaded store", reloadedPl1);
        assertEquals("Favorites", reloadedPl1.name);
        assertEquals(2, reloadedPl1.tracks.size());

        PlaylistItem reloadedTrack1 = reloadedPl1.tracks.get(0);
        assertEquals("Afterglow", reloadedTrack1.title);
        assertEquals("Ed Sheeran", reloadedTrack1.performer);
        assertEquals(185, reloadedTrack1.duration);
        assertEquals(12345678L, reloadedTrack1.dialogId);
        assertEquals(101, reloadedTrack1.messageId);
        assertEquals(987654321L, reloadedTrack1.documentId);
        assertEquals(555666777L, reloadedTrack1.accessHash);
        assertEquals(2, reloadedTrack1.dcId);
        assertEquals(5242880L, reloadedTrack1.size);
        assertEquals("audio/mpeg", reloadedTrack1.mimeType);
        assertEquals("afterglow.mp3", reloadedTrack1.fileName);
        assertEquals("/mock/cache/afterglow.mp3", reloadedTrack1.attachPath);

        PlaylistItem reloadedTrack2 = reloadedPl1.tracks.get(1);
        assertEquals("Shape of You", reloadedTrack2.title);

        // 9. Verify contents of playlist 2
        LocalPlaylist reloadedPl2 = reloadedStore.getPlaylist(pl2.id);
        assertNotNull("Playlist 2 must be present in reloaded store", reloadedPl2);
        assertEquals("Workout", reloadedPl2.name);
        assertEquals(1, reloadedPl2.tracks.size());
        assertEquals("Eye of the Tiger", reloadedPl2.tracks.get(0).title);

        // 10. Verify track removal persistence across cold reload
        reloadedStore.removeTrack(pl1.id, 0); // remove "Afterglow"
        GlobalPlaylistsStore secondReload = new GlobalPlaylistsStore(storageFile);
        LocalPlaylist postRemovalPl1 = secondReload.getPlaylist(pl1.id);
        assertEquals(1, postRemovalPl1.tracks.size());
        assertEquals("Shape of You", postRemovalPl1.tracks.get(0).title);

        // 11. Verify playlist deletion persistence across cold reload
        secondReload.deletePlaylist(pl2.id);
        GlobalPlaylistsStore thirdReload = new GlobalPlaylistsStore(storageFile);
        assertEquals(1, thirdReload.getPlaylists().size());
        assertNull(thirdReload.getPlaylist(pl2.id));
    }

    /**
     * Requirement: testAccountSwitchPersistence
     * Verifies that the store retains all playlists and tracks regardless of simulated account switching.
     */
    @Test
    public void testAccountSwitchPersistence() {
        // 1. Simulate Account 0 active
        int activeAccount = 0;
        LocalPlaylist pl0 = store.createPlaylist("Account 0 Playlist");
        PlaylistItem track0 = new PlaylistItem();
        track0.title = "Account 0 Song";
        track0.performer = "Artist A";
        track0.duration = 200;
        store.addTrack(pl0.id, track0);

        List<LocalPlaylist> listAcc0 = store.getPlaylists();
        assertEquals(1, listAcc0.size());
        assertEquals("Account 0 Playlist", listAcc0.get(0).name);

        // 2. Simulate account switch to Account 1
        activeAccount = 1;
        List<LocalPlaylist> listAcc1 = store.getPlaylists();
        assertEquals("Playlists must remain accessible under Account 1", 1, listAcc1.size());
        assertEquals("Account 0 Playlist", listAcc1.get(0).name);

        // Add track and create second playlist under Account 1
        PlaylistItem track1 = new PlaylistItem();
        track1.title = "Added From Account 1";
        track1.performer = "Artist B";
        track1.duration = 210;
        store.addTrack(pl0.id, track1);

        LocalPlaylist pl1 = store.createPlaylist("Account 1 Chill");
        PlaylistItem track2 = new PlaylistItem();
        track2.title = "Account 1 Ambient";
        track2.performer = "Artist C";
        track2.duration = 180;
        store.addTrack(pl1.id, track2);

        // 3. Simulate account switch to Account 2
        activeAccount = 2;
        List<LocalPlaylist> listAcc2 = store.getPlaylists();
        assertEquals("All playlists must be accessible under Account 2", 2, listAcc2.size());

        // 4. Cold reload to verify disk persistence is account-agnostic
        GlobalPlaylistsStore reloadedStore = new GlobalPlaylistsStore(storageFile);
        List<LocalPlaylist> reloadedList = reloadedStore.getPlaylists();
        assertEquals(2, reloadedList.size());

        LocalPlaylist reloadedPl0 = reloadedStore.getPlaylist(pl0.id);
        assertNotNull(reloadedPl0);
        assertEquals(2, reloadedPl0.tracks.size());
        assertEquals("Account 0 Song", reloadedPl0.tracks.get(0).title);
        assertEquals("Added From Account 1", reloadedPl0.tracks.get(1).title);

        LocalPlaylist reloadedPl1 = reloadedStore.getPlaylist(pl1.id);
        assertNotNull(reloadedPl1);
        assertEquals(1, reloadedPl1.tracks.size());
        assertEquals("Account 1 Ambient", reloadedPl1.tracks.get(0).title);
    }

    /**
     * Requirement: testAccountResetAndLogoutPersistence
     * Verifies that the store file survives a complete purge of simulated account cache directories.
     */
    @Test
    public void testAccountResetAndLogoutPersistence() throws Exception {
        // 1. Simulate application sandboxed environment
        File appDir = tempFolder.newFolder("app_sandbox");
        File filesDir = new File(appDir, "files");
        assertTrue(filesDir.mkdirs());
        File storeFile = new File(filesDir, "extera_local_playlists.json");

        // Simulate account-scoped cache directory where cache4.db lives
        File accountCacheDir = new File(appDir, "account_0_cache");
        assertTrue(accountCacheDir.mkdirs());
        File accountDb = new File(accountCacheDir, "cache4.db");
        assertTrue(accountDb.createNewFile());
        File accountPrefs = new File(accountCacheDir, "userconf0.xml");
        assertTrue(accountPrefs.createNewFile());

        // 2. Initialize GlobalPlaylistsStore in app files directory
        GlobalPlaylistsStore localStore = new GlobalPlaylistsStore(storeFile);
        LocalPlaylist pl = localStore.createPlaylist("Indestructible List");
        PlaylistItem item = new PlaylistItem();
        item.title = "Eternal Track";
        item.performer = "Doom";
        item.duration = 300;
        localStore.addTrack(pl.id, item);

        assertTrue("Store file must exist in app filesDir", storeFile.exists());
        assertTrue("Account DB must exist prior to logout", accountDb.exists());

        // 3. Simulate account logout / account reset (purge account cache and SQLite DBs)
        if (accountCacheDir.exists()) {
            File[] files = accountCacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    assertTrue(f.delete());
                }
            }
            assertTrue(accountCacheDir.delete());
        }

        // Verify account files are purged
        assertFalse("Account database must be deleted on logout", accountDb.exists());
        assertFalse("Account cache directory must be deleted on logout", accountCacheDir.exists());

        // 4. Verify GlobalPlaylistsStore file and contents remain completely intact
        assertTrue("Global playlist store file must survive account logout", storeFile.exists());
        assertTrue("Global playlist store file must not be empty", storeFile.length() > 0);

        // Verify in-memory store retains data
        assertEquals(1, localStore.getPlaylists().size());
        assertEquals("Indestructible List", localStore.getPlaylists().get(0).name);

        // Verify newly instantiated store recovers data from disk with full fidelity
        GlobalPlaylistsStore postLogoutStore = new GlobalPlaylistsStore(storeFile);
        List<LocalPlaylist> restoredPlaylists = postLogoutStore.getPlaylists();
        assertEquals(1, restoredPlaylists.size());
        assertEquals("Indestructible List", restoredPlaylists.get(0).name);
        assertEquals(1, restoredPlaylists.get(0).tracks.size());
        assertEquals("Eternal Track", restoredPlaylists.get(0).tracks.get(0).title);
    }

    /**
     * Requirement: testActivePlaylistTracking
     * Verifies setting and getting the active playing playlist, name updates, and deletion cleanup.
     */
    @Test
    public void testActivePlaylistTracking() {
        LocalPlaylist pl1 = store.createPlaylist("Rock Classics");
        LocalPlaylist pl2 = store.createPlaylist("Jazz Vibes");

        // 1. Initial state is null
        assertNull("Active playlist must initially be null", store.getPlayingPlaylist());

        // 2. Set active playlist
        store.setPlayingPlaylist(pl1);
        assertNotNull("Active playlist must not be null after set", store.getPlayingPlaylist());
        assertSame("Active playlist must match set instance", pl1, store.getPlayingPlaylist());
        assertEquals("Rock Classics", store.getPlayingPlaylist().name);

        // 3. Switch active playlist
        store.setPlayingPlaylist(pl2);
        assertSame("Active playlist must switch to pl2", pl2, store.getPlayingPlaylist());
        assertEquals("Jazz Vibes", store.getPlayingPlaylist().name);

        // 4. Rename active playlist
        store.renamePlaylist(pl2.id, "Smooth Jazz");
        assertEquals("Active playlist name must reflect rename", "Smooth Jazz", store.getPlayingPlaylist().name);

        // 5. Delete active playlist: should clear active playlist
        store.deletePlaylist(pl2.id);
        assertNull("Active playlist must be cleared when deleted", store.getPlayingPlaylist());

        // 6. Explicit clear
        store.setPlayingPlaylist(pl1);
        store.clearPlayingPlaylist();
        assertNull("Active playlist must be null after clearPlayingPlaylist", store.getPlayingPlaylist());
    }

    /**
     * Requirement: testExternalTracksStorage (R2 Readiness)
     * Verifies YouTube Music & Metrolist tracks with remote stream URLs and cover URLs persist correctly.
     */
    @Test
    public void testExternalTracksStorage() {
        LocalPlaylist pl = store.createPlaylist("Streaming Mix");
        PlaylistItem ytTrack = new PlaylistItem(
                "Starboy",
                "The Weeknd",
                230,
                "youtube",
                "https://googlevideo.com/videoplayback?id=123",
                "https://i.ytimg.com/vi/abc/hqdefault.jpg",
                "abc123xyz"
        );
        boolean added = store.addTrack(pl.id, ytTrack);
        assertTrue("External track must be added", added);

        // Reload
        GlobalPlaylistsStore reloadedStore = new GlobalPlaylistsStore(storageFile);
        LocalPlaylist reloadedPl = reloadedStore.getPlaylist(pl.id);
        assertNotNull(reloadedPl);
        assertEquals(1, reloadedPl.tracks.size());

        PlaylistItem reloadedTrack = reloadedPl.tracks.get(0);
        assertEquals("Starboy", reloadedTrack.title);
        assertEquals("The Weeknd", reloadedTrack.performer);
        assertEquals("youtube", reloadedTrack.source);
        assertEquals("https://googlevideo.com/videoplayback?id=123", reloadedTrack.streamUrl);
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", reloadedTrack.coverUrl);
        assertEquals("abc123xyz", reloadedTrack.externalId);
        assertTrue(reloadedTrack.isExternal());
        assertFalse(reloadedTrack.isTelegram());
    }

    /**
     * Requirement: Deduplication
     * Verifies that adding duplicate Telegram tracks or external tracks to the same playlist is rejected.
     */
    @Test
    public void testDeduplication() {
        LocalPlaylist pl = store.createPlaylist("Dedup Test");

        // 1. Telegram track deduplication by documentId
        PlaylistItem t1 = new PlaylistItem();
        t1.documentId = 999999L;
        t1.title = "Song 1";
        assertTrue("Initial track addition must succeed", store.addTrack(pl.id, t1));

        PlaylistItem t1Dup = new PlaylistItem();
        t1Dup.documentId = 999999L;
        t1Dup.title = "Song 1 (Duplicate)";
        assertFalse("Duplicate documentId must be rejected", store.addTrack(pl.id, t1Dup));

        // 2. External track deduplication by externalId
        PlaylistItem ext1 = new PlaylistItem(
                "YT 1", "Artist", 120, "youtube", "https://stream1", "", "yt-id-1"
        );
        assertTrue("Initial external track addition must succeed", store.addTrack(pl.id, ext1));

        PlaylistItem ext1Dup = new PlaylistItem(
                "YT 1", "Artist", 120, "youtube", "https://stream2", "", "yt-id-1"
        );
        assertFalse("Duplicate externalId must be rejected", store.addTrack(pl.id, ext1Dup));

        // 3. Different track must succeed
        PlaylistItem ext2 = new PlaylistItem(
                "YT 2", "Artist", 150, "youtube", "https://stream3", "", "yt-id-2"
        );
        assertTrue("Distinct externalId must be accepted", store.addTrack(pl.id, ext2));
        assertEquals(3, store.getPlaylist(pl.id).tracks.size());
    }
}
