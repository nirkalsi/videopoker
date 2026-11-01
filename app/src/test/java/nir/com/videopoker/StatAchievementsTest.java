package nir.com.videopoker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatAchievementsTest {

    @Before public void clearPrefs() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("videopoker", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void statDrivenAchievements() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("videopoker", Context.MODE_PRIVATE).edit()
                .putLong("st_best_streak", 5).putInt("st_best_hand", 1).commit();
        MissionsManager mm = new MissionsManager(RuntimeEnvironment.getApplication(), null);
        MissionsManager.Item streak = null, sf = null;
        for (MissionsManager.Item i : mm.achievements(1, 0)) {
            if (i.id.equals("ach7")) streak = i;
            if (i.id.equals("ach8")) sf = i;
        }
        assertTrue(streak != null && streak.done());
        assertTrue(sf != null && sf.done());
        assertEquals(800, mm.claim(streak));
        assertEquals(1500, mm.claim(sf));
        // a freshly loaded list reflects the claim (claim() updates prefs, not the Item)
        for (MissionsManager.Item i : mm.achievements(1, 0))
            if (i.id.equals("ach8")) assertEquals(0, mm.claim(i));
    }
}
