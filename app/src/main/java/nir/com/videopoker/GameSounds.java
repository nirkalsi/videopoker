package nir.com.videopoker;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Procedurally synthesized sound effects: no audio assets needed, fully
 *  defensive - any device/audio failure disables sound silently instead of
 *  crashing. Sounds are generated PCM, chosen per game action. */
public class GameSounds {
    public static final int CLICK = 0, CHIP = 1, CARD = 2, WIN = 3, BIG_WIN = 4, LOSE = 5, LEVEL_UP = 6, SPIN = 7;
    private static final int SR = 22050;
    private static final Map<Integer, short[]> cache = new HashMap<>();
    private static boolean failed = false;
    private static Boolean enabled = null;
    private static boolean foreground = true;

    public static boolean isEnabled(android.content.Context c) {
        if (enabled == null)
            enabled = c.getSharedPreferences("game_sounds", 0).getBoolean("enabled", true);
        return enabled;
    }

    /** Toggle sound on/off, persisted. Returns the new state. */
    public static boolean toggle(android.content.Context c) {
        boolean now = !isEnabled(c);
        c.getSharedPreferences("game_sounds", 0).edit().putBoolean("enabled", now).apply();
        enabled = now;
        if (now) startMusic(c); else stopMusic();
        return now;
    }

    /** App moved to background: silence everything until onAppResume. */
    public static void onAppPause() { foreground = false; }

    /** App back in foreground. */
    public static void onAppResume() { foreground = true; }

    /** Visible for tests. */
    public static boolean isForeground() { return foreground; }

    /** Drop cached buffers and stop music (call from Activity onDestroy). */
    public static void release() {
        synchronized (cache) { cache.clear(); }
        synchronized (GameSounds.class) {
            try { if (music != null) { music.stop(); music.release(); } } catch (Throwable ignored) {}
            music = null;
        }
    }

    public static void play(int sound) { play(null, sound); }

    public static void play(android.content.Context c, int sound) {
        if (!foreground) return;
        if (c != null && !isEnabled(c)) return;
        if (failed) return;
        try {
            final short[] pcm = buffer(sound);
            if (pcm == null) return;
            final AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(pcm.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
            t.write(pcm, 0, pcm.length);
            t.play();
            new Thread(() -> {
                try { Thread.sleep(pcm.length * 1000L / SR + 200); } catch (InterruptedException ignored) {}
                try { t.stop(); t.release(); } catch (Throwable ignored) {}
            }).start();
        } catch (Throwable t) { failed = true; }
    }

    private static short[] buffer(int sound) {
        synchronized (cache) {
            short[] b = cache.get(sound);
            if (b == null) {
                b = synth(sound);
                if (b != null) cache.put(sound, b);
            }
            return b;
        }
    }

    // ---------------- tiny PCM synth ----------------

    static short[] synth(int sound) {
        switch (sound) {
            case CLICK: return tone(1250, 35, 0.45);
            case CHIP: return chip();
            case CARD: return noiseBurst(95, 0.55);
            case SPIN: return sweep(280, 920, 400);
            case WIN: return arpeggio(new int[]{523, 659, 784}, 120);
            case BIG_WIN: return arpeggio(new int[]{523, 659, 784, 1047, 1319}, 130);
            case LOSE: return arpeggio(new int[]{392, 311}, 230);
            case LEVEL_UP: return arpeggio(new int[]{392, 523, 659, 784}, 115);
            default: return null;
        }
    }

    private static short[] tone(double hz, int ms, double vol) {
        int n = SR * ms / 1000;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double env = Math.exp(-4.0 * t);
            out[i] = (short) (Math.sin(2 * Math.PI * hz * i / SR) * vol * env * 32767 * 0.6);
        }
        fade(out);
        return out;
    }

    private static short[] sweep(double f0, double f1, int ms) {
        int n = SR * ms / 1000;
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double f = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * f / SR;
            double env = Math.min(1, t * 8) * Math.min(1, (1 - t) * 4);
            out[i] = (short) (Math.sin(phase) * env * 32767 * 0.35);
        }
        return out;
    }

    private static short[] arpeggio(int[] notes, int noteMs) {
        int n = SR * noteMs / 1000;
        short[] out = new short[n * notes.length];
        for (int k = 0; k < notes.length; k++) {
            for (int i = 0; i < n; i++) {
                double t = (double) i / n;
                double env = Math.exp(-2.5 * t);
                // fundamental + soft octave shimmer
                double v = Math.sin(2 * Math.PI * notes[k] * i / SR)
                         + 0.25 * Math.sin(4 * Math.PI * notes[k] * i / SR);
                out[k * n + i] = (short) (v * env * 32767 * 0.45);
            }
        }
        fade(out);
        return out;
    }

    private static short[] noiseBurst(int ms, double vol) {
        int n = SR * ms / 1000;
        short[] out = new short[n];
        Random r = new Random(42);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double env = Math.exp(-6.0 * t);
            lp += 0.25 * ((r.nextDouble() * 2 - 1) - lp); // one-pole lowpass: paper/card swish
            out[i] = (short) (lp * env * vol * 32767 * 1.4);
        }
        fade(out);
        return out;
    }

    private static short[] chip() {
        // casino chip clack: sharp noise transient + high ping
        int n = SR * 70 / 1000;
        short[] out = new short[n];
        Random r = new Random(7);
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double env = Math.exp(-14.0 * t);
            double noise = r.nextDouble() * 2 - 1;
            double ping = Math.sin(2 * Math.PI * 2600 * i / SR) * 0.6;
            out[i] = (short) ((noise * 0.5 + ping) * env * 32767 * 0.55);
        }
        fade(out);
        return out;
    }

    private static void fade(short[] b) {
        int f = Math.min(64, b.length / 4);
        for (int i = 0; i < f; i++) {
            b[i] = (short) (b[i] * i / f);
            b[b.length - 1 - i] = (short) (b[b.length - 1 - i] * i / f);
        }
    }

    // ---------------- background music ----------------

    private static AudioTrack music;

    /** Per-theme transpose factor for the shared chord loop. */
    private static final double MUSIC_PITCH = 0.891;

    /** Start (or resume) the ambient music loop, if sound is enabled. */
    public static synchronized void startMusic(android.content.Context c) {
        if (!isEnabled(c) || failed) return;
        if (music != null) {
            try { music.play(); } catch (Throwable ignored) {}
            return;
        }
        try {
            short[] loop = synthMusic();
            music = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(loop.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
            music.write(loop, 0, loop.length);
            music.setLoopPoints(0, loop.length, -1);
            music.setVolume(0.35f);
            music.play();
        } catch (Throwable t) {
            music = null;
        }
    }

    /** Pause the music loop (background, mute). */
    public static synchronized void stopMusic() {
        try { if (music != null) music.pause(); } catch (Throwable ignored) {}
    }

    /** Ambient chord-pad loop: Am - F - C - G, soft attack, ~14s. */
    static short[] synthMusic() {
        double[][] chords = {
            {110.00, 130.81, 164.81, 220.00},   // Am7-ish
            { 87.31, 110.00, 130.81, 174.61},   // F
            {130.81, 164.81, 196.00, 261.63},   // C
            { 98.00, 123.47, 146.83, 196.00},   // G
        };
        int chordMs = 3500;
        int n = SR * chordMs / 1000;
        short[] out = new short[n * chords.length];
        for (int k = 0; k < chords.length; k++) {
            for (int i = 0; i < n; i++) {
                double t = (double) i / n;
                double env = Math.min(1, t * 6) * Math.min(1, (1 - t) * 6); // slow in/out
                double v = 0;
                for (double f : chords[k]) {
                    v += Math.sin(2 * Math.PI * f * MUSIC_PITCH * i / SR);
                }
                v += 0.3 * Math.sin(4 * Math.PI * chords[k][0] * MUSIC_PITCH * i / SR); // soft octave
                out[k * n + i] = (short) (v * env * 32767 * 0.07);
            }
        }
        return out;
    }
}
