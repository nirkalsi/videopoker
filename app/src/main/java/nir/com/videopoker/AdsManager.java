package nir.com.videopoker;

import android.app.Activity;
import android.content.Context;
import android.widget.LinearLayout;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.*;

/** Policy-safe ads: banner always bottom, interstitial at most every 6th completed round, min 2 minutes apart
 *  (min 90s apart, never on first spins, never back-to-back), rewarded only user-initiated. */
public class AdsManager {
    public interface AdEvents { void onInterstitialShown(); }
    private AdEvents events;
    public void setAdEvents(AdEvents e) { events = e; }
    private final Activity act;
    private InterstitialAd inter;
    private RewardedAd rewarded;
    private long lastInterAt = 0;
    private int interRetry = 0;
    private int rewardedRetry = 0;
    private boolean initialized = false;
    private volatile boolean adsRemoved = false;

    public void setAdsRemoved(boolean removed) { adsRemoved = removed; }

    public AdsManager(Activity a) { act = a; }

    public void initWithConsent(Runnable ready) {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        ConsentInformation ci = UserMessagingPlatform.getConsentInformation(act);
        ci.requestConsentInfoUpdate(act, params,
            () -> {
                if (ci.canRequestAds()) startAds(ready);
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(act, e -> {
                    if (ci.canRequestAds()) startAds(ready); else ready.run();
                });
            },
            e -> ready.run());
    }

    private void startAds(Runnable ready) {
        if (initialized || adsRemoved) { ready.run(); return; }
        initialized = true;
        MobileAds.initialize(act, s -> {});
        loadInter(); loadRewarded();
        ready.run();
    }

    public void attachBanner(LinearLayout box) {
        if (!initialized || adsRemoved) return;
        AdView av = new AdView(act);
        av.setAdSize(adaptiveBannerSize());
        av.setAdUnitId(BuildConfig.BANNER_ID);
        box.addView(av);
        av.setAdListener(new AdListener() {
            private int attempts = 0;
            @Override public void onAdLoaded() { attempts = 0; }
            @Override public void onAdFailedToLoad(LoadAdError e) {
                long delayMs = backoffMs(attempts++);
                av.postDelayed(() -> {
                    if (adsRemoved || !av.isAttachedToWindow()) return;
                    av.loadAd(new AdRequest.Builder().build());
                }, delayMs);
            }
        });
        av.loadAd(new AdRequest.Builder().build());
    }

    private AdSize adaptiveBannerSize() {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        act.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int adWidth = (int) (dm.widthPixels / dm.density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(act, adWidth);
    }

    private static long backoffMs(int attempt) {
        return Math.min(30000L * (1L << Math.min(attempt, 4)), 300000L);
    }

    private void scheduleRetry(Runnable r, int attempt) {
        if (attempt >= 6) return; // fresh session re-runs preloads
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (initialized && !adsRemoved) r.run();
        }, backoffMs(attempt));
    }

    private void loadInter() {
        InterstitialAd.load(act, BuildConfig.INTER_ID, new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override public void onAdLoaded(InterstitialAd ad) { inter = ad; }
                @Override public void onAdFailedToLoad(LoadAdError e) { inter = null; scheduleRetry(() -> loadInter(), interRetry++); }
            });
    }

    private void loadRewarded() {
        RewardedAd.load(act, BuildConfig.REWARD_ID, new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(RewardedAd ad) { rewarded = ad; }
                @Override public void onAdFailedToLoad(LoadAdError e) { rewarded = null; scheduleRetry(() -> loadRewarded(), rewardedRetry++); }
            });
    }

    /** Show interstitial if pacing allows. Call with the completed-order count. */
    public void maybeShowInterstitial(int spinsDone) {
        if (adsRemoved) return;
        if (inter == null || spinsDone < 6 || spinsDone % 6 != 0) return;
        long now = System.currentTimeMillis();
        if (now - lastInterAt < 120000) return;
        lastInterAt = now;
        if (events != null) events.onInterstitialShown();
        InterstitialAd ad = inter; inter = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { loadInter(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) { loadInter(); }
        });
        ad.show(act);
    }

    public boolean hasRewarded() { return !adsRemoved && rewarded != null; }

    public void showRewarded(Runnable onReward) {
        if (adsRemoved) { onReward.run(); return; }
        final RewardFlow flow = new RewardFlow();
        RewardedAd ad = rewarded;
        if (ad == null) { // never grant without a completed ad view
            flow.onNoFill();
            loadRewarded();
            toast(RewardFlow.MSG_NOT_READY);
            return;
        }
        rewarded = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { flow.onDismissed(); loadRewarded(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) {
                flow.onShowFailed();
                loadRewarded();
                toast(RewardFlow.MSG_SHOW_FAILED);
            }
        });
        ad.show(act, item -> { // reward granted ONLY via the earned-reward callback, once
            if (flow.onEarned() == RewardFlow.Decision.GRANT_REWARD) onReward.run();
        });
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(act, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
