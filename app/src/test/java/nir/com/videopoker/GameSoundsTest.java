package nir.com.videopoker;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Sound calls must never crash, including with a shadowed/absent audio stack. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GameSoundsTest {
    @Test public void allSoundsPlayWithoutCrash() {
        for (int s = 0; s <= 7; s++) GameSounds.play(s);
    }
}
