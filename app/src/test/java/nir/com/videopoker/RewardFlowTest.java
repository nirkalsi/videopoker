package nir.com.videopoker;

import org.junit.Test;
import static org.junit.Assert.*;

public class RewardFlowTest {

    @Test public void noFill_notifies_and_neverGrants() {
        RewardFlow f = new RewardFlow();
        assertEquals(RewardFlow.Decision.NOTIFY_NOT_READY, f.onNoFill());
        assertEquals(RewardFlow.Decision.NOTIFY_NOT_READY, f.onNoFill());
    }

    @Test public void showFailure_notifies_andIsTerminal() {
        RewardFlow f = new RewardFlow();
        assertEquals(RewardFlow.Decision.NOTIFY_SHOW_FAILED, f.onShowFailed());
        assertEquals("late earned callback after show-failure must not grant",
                RewardFlow.Decision.IGNORE, f.onEarned());
    }

    @Test public void dismissal_isTerminal_lateEarnedNeverGrants() {
        RewardFlow f = new RewardFlow();
        assertEquals(RewardFlow.Decision.IGNORE, f.onDismissed());
        assertEquals("a dismissal terminally ends the attempt",
                RewardFlow.Decision.IGNORE, f.onEarned());
        assertEquals(RewardFlow.Decision.IGNORE, f.onEarned());
    }

    @Test public void earnedCallback_grantsExactlyOnce() {
        RewardFlow f = new RewardFlow();
        assertEquals(RewardFlow.Decision.GRANT_REWARD, f.onEarned());
    }

    @Test public void normalOrder_earnThenDismiss_keepsSingleGrant() {
        RewardFlow f = new RewardFlow();
        assertEquals(RewardFlow.Decision.GRANT_REWARD, f.onEarned());
        assertEquals(RewardFlow.Decision.IGNORE, f.onDismissed());
        assertEquals(RewardFlow.Decision.IGNORE, f.onEarned());
    }

    @Test public void duplicateEarnedCallbacks_grantOnlyOnce() {
        RewardFlow f = new RewardFlow();
        assertEquals(RewardFlow.Decision.GRANT_REWARD, f.onEarned());
        assertEquals(RewardFlow.Decision.IGNORE, f.onEarned());
    }

    @Test public void attemptsAreIndependent() {
        RewardFlow first = new RewardFlow();
        RewardFlow second = new RewardFlow();
        assertEquals(RewardFlow.Decision.GRANT_REWARD, first.onEarned());
        assertEquals("a new attempt must not inherit the grant",
                RewardFlow.Decision.NOTIFY_NOT_READY, second.onNoFill());
        assertEquals(RewardFlow.Decision.GRANT_REWARD, second.onEarned());
    }

    @Test public void failureStrings_areUserSafe() {
        for (String s : new String[]{RewardFlow.MSG_NOT_READY, RewardFlow.MSG_SHOW_FAILED}) {
            assertFalse(s.isEmpty());
            assertTrue("must tell the user what to do next", s.contains("try again"));
            assertFalse("no technical jargon", s.contains("AdMob"));
            assertFalse("no error codes", s.matches(".*\\b\\d{3}\\b.*"));
        }
    }
}
