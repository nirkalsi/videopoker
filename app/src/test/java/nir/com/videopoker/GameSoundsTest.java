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

    @Test public void synthBuffersAreNonSilent() {
        int[] all = {GameSounds.CLICK, GameSounds.CHIP, GameSounds.CARD, GameSounds.SPIN,
                GameSounds.WIN, GameSounds.BIG_WIN, GameSounds.LOSE, GameSounds.LEVEL_UP};
        for (int snd : all) {
            short[] pcm = GameSounds.synth(snd);
            assertNotNull("synth " + snd, pcm);
            assertTrue("synth " + snd + " too short", pcm.length > 500);
            int peak = 0;
            for (short v : pcm) peak = Math.max(peak, Math.abs((int) v));
            assertTrue("synth " + snd + " silent", peak > 2000);
        }
    }

    @Test public void musicLoopIsNonSilent() {
        short[] pcm = GameSounds.synthMusic();
        assertNotNull(pcm);
        assertTrue(pcm.length > 100000);
        int peak = 0;
        for (short v : pcm) peak = Math.max(peak, Math.abs((int) v));
        assertTrue("music silent", peak > 1000);
        assertTrue("music may clip", peak < 25000);
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
