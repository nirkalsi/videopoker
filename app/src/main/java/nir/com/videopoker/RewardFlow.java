package nir.com.videopoker;

/**
 * Rewarded-ad flow decisions, extracted as pure logic for JVM unit tests.
 * Rule: coins are granted ONLY on the ad network's earned-reward callback,
 * never on click, load failure, show failure, or dismissal.
 * Terminal semantics: once an attempt ends (show failure or dismissal),
 * no later callback for that attempt can grant. One instance per attempt.
 */
public final class RewardFlow {
    public static final String MSG_NOT_READY = "Ad not ready yet - try again in a moment";
    public static final String MSG_SHOW_FAILED = "Ad couldn't play - try again in a moment";

    public enum Decision { GRANT_REWARD, NOTIFY_NOT_READY, NOTIFY_SHOW_FAILED, IGNORE }

    private boolean granted;
    private boolean terminated;

    /** No ad was loaded when the user tapped. Never grants. Attempt not started. */
    public Decision onNoFill() { return Decision.NOTIFY_NOT_READY; }

    /** The ad failed to show. Never grants. Terminates this attempt. */
    public Decision onShowFailed() { terminated = true; return Decision.NOTIFY_SHOW_FAILED; }

    /** The ad was dismissed (with or without a prior earn). Terminates this attempt;
     *  any later callback for this attempt is ignored. */
    public Decision onDismissed() { terminated = true; return Decision.IGNORE; }

    /** Earned-reward callback. Grants once, only if the attempt is still open;
     *  duplicate or late (post-terminal) callbacks are ignored. */
    public Decision onEarned() {
        if (granted || terminated) return Decision.IGNORE;
        granted = true;
        return Decision.GRANT_REWARD;
    }
}
