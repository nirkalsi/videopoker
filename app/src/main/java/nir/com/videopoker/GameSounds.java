package nir.com.videopoker;

import android.media.AudioManager;
import android.media.ToneGenerator;

/** Tiny synthesized sound effects: no audio assets needed, fully defensive -
 *  any device/audio failure disables sound silently instead of crashing. */
public class GameSounds {
    public static final int CLICK = 0, CHIP = 1, CARD = 2, WIN = 3, BIG_WIN = 4, LOSE = 5, LEVEL_UP = 6, SPIN = 7;
    private static ToneGenerator tg;
    private static boolean failed = false;

    private static ToneGenerator tg() {
        if (failed) return null;
        if (tg == null) {
            try { tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 70); }
            catch (Throwable t) { failed = true; }
        }
        return tg;
    }

    public static void play(int sound) {
        ToneGenerator g = tg();
        if (g == null) return;
        try {
            switch (sound) {
                case CLICK: g.startTone(ToneGenerator.TONE_PROP_BEEP, 40); break;
                case CHIP: g.startTone(ToneGenerator.TONE_PROP_ACK, 60); break;
                case CARD: g.startTone(ToneGenerator.TONE_PROP_BEEP2, 50); break;
                case SPIN: g.startTone(ToneGenerator.TONE_SUP_DIAL, 120); break;
                case WIN: g.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 220); break;
                case BIG_WIN: g.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 450); break;
                case LOSE: g.startTone(ToneGenerator.TONE_PROP_NACK, 180); break;
                case LEVEL_UP: g.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350); break;
                default: break;
            }
        } catch (Throwable ignored) {}
    }
}
