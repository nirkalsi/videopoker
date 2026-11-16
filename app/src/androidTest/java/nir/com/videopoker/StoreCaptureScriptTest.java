package nir.com.videopoker;

import android.graphics.Rect;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Store-screenshot capture driver: plays one scripted 5-hand round on the live v6 design,
 *  pausing at photogenic states and saving native PNGs via the BJ-proven plain instrumentation shell transport.
 *  Taps reuse the proven GameplayScriptTest layout math. Sample ads only (debug build);
 *  no ad content is clicked. */
@RunWith(AndroidJUnit4.class)
public class StoreCaptureScriptTest {
    private static final String TAG = "StoreCaptureVP";
    private UiDevice d;
    private Rect v;
    private int shot = 0;

    private void tap(float fx, float fy) {
        v = CaptureStateProbe.bounds("PokerView");
        d.click((int) (v.left + v.width() * fx), (int) (v.top + v.height() * fy));
    }

    // Plain executable calls only: UiAutomation uses Runtime.exec(String), whose
    // whitespace tokenizer does not understand shell quotes, pipes or &&.
    private byte[] shellStage(String stage, String... args) throws Exception {
        String command = CaptureShellTransport.command(args);
        Log.i(TAG, "STAGE_START " + stage + " command=" + command);
        android.os.ParcelFileDescriptor[] fds = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommandRwe(command);
        if (fds == null || fds.length != 3) throw new AssertionError("missing descriptors: " + stage);
        for (android.os.ParcelFileDescriptor fd : fds)
            if (fd == null) throw new AssertionError("null descriptor: " + stage);
        try (java.io.OutputStream stdin = new android.os.ParcelFileDescriptor.AutoCloseOutputStream(fds[1]);
             java.io.InputStream stdout = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fds[0]);
             java.io.InputStream stderr = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fds[2])) {
            stdin.close(); // No script or input is sent. Each command has plain argv only.
            CaptureShellTransport.Result result = CaptureShellTransport.collect(stdout, stderr);
            String err = new String(result.stderr, java.nio.charset.StandardCharsets.UTF_8);
            Log.i(TAG, "STAGE_DONE " + stage + " stdoutBytes=" + result.stdout.length + " stderr=" + err);
            if (!err.trim().isEmpty()) throw new AssertionError("shell stderr at " + stage + ": " + err);
            return result.stdout;
        } catch (Exception e) {
            throw new AssertionError("shell stage failed: " + stage, e);
        }
    }

    // VP preparation native PNG pilot. No new app permissions or services. Storage writes
    // and Firebase collector behavior still need device proof after transport repair.
    private void shot(String name) throws Exception {
        if (!name.matches("[a-z0-9_]+")) throw new AssertionError("unsafe screenshot name");
        shot++;
        String path = "/sdcard/screenshots/vp_store_" + shot + "_" + name + ".png";
        shellStage("mkdir_" + shot, "/system/bin/mkdir", "-p", "/sdcard/screenshots");
        // Remove any stale pose before capture. rm errors are surfaced separately.
        shellStage("remove_stale_" + shot, "/system/bin/rm", "-f", path);
        shellStage("screencap_" + shot, "/system/bin/screencap", "-p", path);
        String count = new String(shellStage("stat_" + shot, "/system/bin/stat", "-c", "%s", path),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        long size;
        try { size = Long.parseLong(count); }
        catch (NumberFormatException e) { throw new AssertionError("invalid stat size: " + count, e); }
        if (size <= 8 || size > 24 * 1024 * 1024) throw new AssertionError("invalid PNG size " + size);
        byte[] png = shellStage("readback_" + shot, "/system/bin/cat", path);
        if (png.length != size) throw new AssertionError("readback size mismatch for " + path);
        byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        for (int k = 0; k < signature.length; k++)
            if (png[k] != signature[k]) throw new AssertionError("not PNG: " + path);
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length, bounds);
        if (!"image/png".equals(bounds.outMimeType)
                || bounds.outWidth != d.getDisplayWidth() || bounds.outHeight != d.getDisplayHeight())
            throw new AssertionError("bad PNG bounds " + bounds.outWidth + "x" + bounds.outHeight
                    + " mime=" + bounds.outMimeType);
        Log.i(TAG, "PNG_VERIFIED " + path + " bytes=" + size + " " + bounds.outWidth + "x" + bounds.outHeight);
    }

    @Test public void captureStoreScreens() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // deterministic state: healthy balance, 5-hand mode (serialize field 7), no prior rounds
        c.getSharedPreferences("videopoker", 0).edit().clear()
                .putString("state", "10000;2;0;" + System.currentTimeMillis() + ";0;1;5")
                .putString("daily_last", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(new java.util.Date()))
                .commit();

        android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage("nir.com.videopoker");
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);

        // --- UMP consent (real production form; handle it, never bypass) ---
        long consentDeadline = System.currentTimeMillis() + 75000;
        int cleanPolls = 0;
        boolean sawForm = false;
        while (System.currentTimeMillis() < consentDeadline) {
            UiObject2 doNot = d.findObject(By.text("Do not consent"));
            UiObject2 confirm = d.findObject(By.text("Confirm choices"));
            UiObject2 welcome = d.findObject(By.textContains("asks for your consent"));
            UiObject2 manageData = d.findObject(By.text("Manage your data"));
            UiObject2 acceptAll = d.findObject(By.text("Accept all"));
            if (doNot != null) { sawForm = true; doNot.click(); cleanPolls = 0; }
            else if (confirm != null) { sawForm = true; confirm.click(); cleanPolls = 0; }
            else if (welcome != null || manageData != null || acceptAll != null) {
                sawForm = true;
                UiObject2 opts = d.findObject(By.text("Manage options"));
                if (opts != null) opts.click();
                cleanPolls = 0;
            } else {
                cleanPolls++;
                if (cleanPolls >= (sawForm ? 4 : 12)) break;
            }
            Thread.sleep(750);
        }
        if (d.findObject(By.text("Do not consent")) != null
                || d.findObject(By.text("Confirm choices")) != null
                || d.findObject(By.text("Accept all")) != null
                || d.findObject(By.textContains("asks for your consent")) != null
                || d.findObject(By.text("Manage your data")) != null)
            throw new AssertionError("consent overlay still present before capture");

        Thread.sleep(1000);
        assertState("phase", 0);
        assertState("betIdx", 2);
        assertState("balance", 10000);
        assertState("numHands", 5);
        tap(0.5f, 0.829f);
        awaitPhase(1, 5000);
        assertState("balance", 9875);
        assertState("betIdx", 2);
        Log.i(TAG, "WAGER_VERIFIED perHand=25 hands=5 total=125 balance=10000->9875");
        Thread.sleep(900);
        // Use current primary-card layout instead of obsolete fixed y fractions.
        android.graphics.Rect r = CaptureStateProbe.bounds("PokerView");
        float scale = CaptureStateProbe.layout("PokerView", "primaryScale");
        float cw = r.width() * 0.165f * scale, gap = r.width() * 0.028f * scale;
        float x0 = (r.width() - (5*cw + 4*gap))/2f;
        float y0 = CaptureStateProbe.layout("PokerView", "primaryY0");
        float ch = CaptureStateProbe.layout("PokerView", "primaryCh");
        boolean[] suggestions = (boolean[])CaptureStateProbe.read("PokerView", "suggested");
        for (int k=0; k<5; k++) if (suggestions[k])
            tap((x0+k*(cw+gap)+cw/2)/r.width(), (y0+ch/2)/r.height());
        if (!java.util.Arrays.equals(suggestions, (boolean[])CaptureStateProbe.read("PokerView", "held")))
            throw new AssertionError("hold taps did not match suggestions");
        assertUnobscured();
        shot("five_hand_hold_decision");
        tap(0.5f, 0.790f);
        awaitPhase(2, 10000);
        assertState("handsDone", 5);
        assertState("numHands", 5);
        if (((java.util.List<?>)CaptureStateProbe.read("PokerView", "extraHands")).size() != 4)
            throw new AssertionError("five-hand result incomplete");
        Thread.sleep(650);
        assertUnobscured();
        Log.i(TAG, "RESULT_VERIFIED " + CaptureStateProbe.read("PokerView", "message"));
        shot("five_hand_settled_result");
        String foreground = d.getCurrentPackageName();
        if (!"nir.com.videopoker".equals(foreground)) throw new AssertionError("foreground=" + foreground);
        Log.i(TAG, "capture complete: 2 asserted poses");
    }

    private void assertState(String field, long expected) {
        Object actual = CaptureStateProbe.read("PokerView", field);
        if (!(actual instanceof Number) || ((Number)actual).longValue() != expected)
            throw new AssertionError(field + " expected=" + expected + " actual=" + actual);
    }
    private void awaitPhase(int expected, long timeout) throws Exception {
        long until = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < until) {
            if (((Number)CaptureStateProbe.read("PokerView", "phase")).intValue() == expected) return;
            Thread.sleep(30);
        }
        throw new AssertionError("phase " + expected + " not reached");
    }
    private void assertUnobscured() {
        if (!"nir.com.videopoker".equals(d.getCurrentPackageName())
                || d.findObject(By.textContains("Daily Bonus")) != null
                || d.findObject(By.textContains("Claim +")) != null
                || Boolean.TRUE.equals(CaptureStateProbe.read("PokerView", "levelUpShowing")))
            throw new AssertionError("game obscured by modal/overlay");
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!today.equals(c.getSharedPreferences("videopoker", 0).getString("daily_last", "")))
            throw new AssertionError("daily state changed or local date crossed");
    }
}
