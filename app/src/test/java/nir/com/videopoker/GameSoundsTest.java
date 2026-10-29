package nir.com.videopoker;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Mute persistence and lifecycle silence for the placeholder sound engine. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GameSoundsTest {

    @Test public void mutePersistsAcrossReads() {
        android.content.Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("game_sounds", 0).edit().clear().commit();
        assertTrue(GameSounds.isEnabled(c));
        assertFalse(GameSounds.toggle(c));
        assertFalse(GameSounds.isEnabled(c));
        assertTrue(GameSounds.toggle(c));
        assertTrue(GameSounds.isEnabled(c));
    }

    @Test public void backgroundedAppIsSilent() {
        android.content.Context c = RuntimeEnvironment.getApplication();
        GameSounds.onAppResume();
        assertTrue(GameSounds.isForeground());
        GameSounds.onAppPause();
        assertFalse(GameSounds.isForeground());
        // play must no-op without throwing while backgrounded or muted
        GameSounds.play(c, GameSounds.WIN);
        GameSounds.toggle(c); // mute
        GameSounds.onAppResume();
        GameSounds.play(c, GameSounds.WIN);
        GameSounds.toggle(c); // unmute
        GameSounds.play(c, GameSounds.WIN);
        GameSounds.release();
    }
}
