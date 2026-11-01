package nir.com.videopoker;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Video Poker (Jacks or Better) - free social casino game. Virtual coins only, no real money, no prizes.
 *  Casino-cabinet redesign: paytable display, big cards, glowing holds, win celebrations. */
public class PokerView extends View {
    public interface Listener {
        void onOutOfCoins();
        void onSpinComplete(int handsDone, long win, long bet, int hands, int winHands, int bestRow);
        void onBigWin(long win, long bet);
        void onLevelUp(int level, long reward);
        void saveState();
    }

    private static final int[] BETS = {5, 10, 25, 50};
    private static final int[] CHIP_COLORS = {0xFFC62828, 0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A};
    private static final String[] SUITS = {"\u2660", "\u2665", "\u2666", "\u2663"};
    private static final String[] RANKS = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
    private static final String[] PAY_NAMES = {"ROYAL FLUSH","Straight Flush","4 of a Kind","Full House","Flush","Straight","3 of a Kind","Two Pair","Jacks or Better"};
    private static final int[] PAY_MULT = {250, 50, 25, 9, 6, 4, 3, 2, 1};

    private final Random rng = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Listener listener;

    private long balance = 500;
    private long shownBalance = 500;
    private long lastWin = 0;
    private int betIdx = 1;
    private long handsDone = 0;
    private long xp = 0;
    private int level = 1;
    private long lastRegenAt = System.currentTimeMillis();

    // phases: 0=betting, 1=hold/draw, 2=result
    private int phase = 0;
    /** Test hooks (read-only, written every onDraw): current phase and the
     *  DEAL/DRAW action-button bounds in view coordinates. No behavior change. */
    public static volatile int debugPhase = -1;
    public static volatile android.graphics.Rect debugActionBtn = null;
    private final List<Integer> hand = new ArrayList<>();   // card = suit*13 + rank
    private final boolean[] held = new boolean[5];
    private final long[] cardT = new long[5];
    private final boolean[] cardNew = new boolean[5];
    private String message = "Place your bet";
    private final boolean[] suggested = new boolean[5];
    private boolean suggestVisible = false;

    // cosmetic card backs: level-gated, no purchase, selection persisted
    private int cosBack = 0;
    private static final int[] BACK_UNLOCK_LEVEL = {1, 2, 4, 6, 9};
    // per variant: gradient top, gradient bottom, border, crosshatch
    private static final int[][] BACK_STYLE = {
            {0xFF8E1620, 0xFF3F0810, 0xFFD4AF37, 0x33FFD4AF},  // classic red
            {0xFF16324F, 0xFF081226, 0xFFB8CCE8, 0x33B8D4FF},  // midnight
            {0xFF0E5A38, 0xFF062A1A, 0xFFD4AF37, 0x33FFE9B0},  // emerald
            {0xFF4A1D6E, 0xFF1E0A38, 0xFFD4AF37, 0x33FFD4AF},  // royal
            {0xFF23232B, 0xFF0A0A10, 0xFFD4AF37, 0x26FFD4AF},  // onyx
    };

    static int backCount() { return BACK_STYLE.length; }
    static int backUnlockLevel(int v) { return BACK_UNLOCK_LEVEL[v]; }

    // lifetime stats (persisted in app prefs)
    private boolean statsOpen = false;
    private long stHands, stWins, stBiggest, stStreak, stBestStreak;
    private int stBestHand = -1;
    private int winRow = -1;
    // multi-hand: 1, 3 or 5 hands per round (feature approved by app owner via WhatsApp 01.11.26;
    // paytable itself unchanged). Extra hands share the held cards and each draws replacements
    // from its own shuffled deck of 52 minus the 5 initial cards.
    private int numHands = 1;
    private final java.util.List<java.util.List<Integer>> extraHands = new ArrayList<>();
    private int[] extraRows = new int[0];
    private final int[] initial5 = new int[5];
    private long resultAt = 0;
    private final float[] px = new float[16], py = new float[16], pvx = new float[16], pvy = new float[16];
    private boolean particlesOn = false;
    // coin burst: coins fly from screen center to the balance pill (daily bonus claim)
    private final float[] cbx = new float[14], cby = new float[14], cbvx = new float[14], cbvy = new float[14];
    private boolean coinBurstOn = false;
    private long coinBurstAt = 0;

    private int levelUpPending = 0;
    private long levelUpReward = 0;
    private boolean levelUpShowing = false;
    private long levelUpAt = 0;
    private int pressedBtn = -1;
    private long betBumpAt = 0;
    private long lastTouchAt = System.currentTimeMillis();
    private int heldFlash = -1;
    private long heldFlashAt = 0;
    private android.graphics.Bitmap bgCache; private int bgW = -1, bgH = -1;
    private float[] dustX, dustY, dustS, dustV;
    private float[] texX, texY; private boolean[] texL;

    public PokerView(Context c) { super(c); loadStats(); }

    private void loadStats() {
        SharedPreferences sp = getContext().getSharedPreferences("videopoker", Context.MODE_PRIVATE);
        stHands = sp.getLong("st_hands", 0);
        stWins = sp.getLong("st_wins", 0);
        stBiggest = sp.getLong("st_biggest", 0);
        stStreak = sp.getLong("st_streak", 0);
        stBestStreak = sp.getLong("st_best_streak", 0);
        stBestHand = sp.getInt("st_best_hand", -1);
        cosBack = sp.getInt("cos_back", 0);
        if (cosBack < 0 || cosBack >= BACK_STYLE.length) cosBack = 0;
    }

    private void saveStats() {
        getContext().getSharedPreferences("videopoker", Context.MODE_PRIVATE).edit()
                .putLong("st_hands", stHands)
                .putLong("st_wins", stWins)
                .putLong("st_biggest", stBiggest)
                .putLong("st_streak", stStreak)
                .putLong("st_best_streak", stBestStreak)
                .putInt("st_best_hand", stBestHand)
                .apply();
    }

    public void setListener(Listener l) { listener = l; }

    public void addCoins(long n) { balance += n; invalidate(); if (listener != null) listener.saveState(); }

    public int getLevel() { return level; }
    public long getSpinsDone() { return handsDone; }
    private long xpNeeded() { return level * 500L; }

    public String serialize() { return balance + ";" + betIdx + ";" + handsDone + ";" + lastRegenAt + ";" + xp + ";" + level + ";" + numHands; }

    public void restore(String s) {
        try {
            String[] a = s.split(";");
            balance = Long.parseLong(a[0]);
            shownBalance = balance;
            betIdx = Integer.parseInt(a[1]);
            handsDone = Long.parseLong(a[2]);
            lastRegenAt = Long.parseLong(a[3]);
            if (a.length > 4) xp = Long.parseLong(a[4]);
            if (a.length > 5) level = Math.max(1, Integer.parseInt(a[5]));
            if (a.length > 6) { int nh = Integer.parseInt(a[6]); numHands = (nh == 3 || nh == 5) ? nh : 1; }
        } catch (Exception ignored) {}
    }

    private void regen() {
        long now = System.currentTimeMillis();
        long ticks = (now - lastRegenAt) / 60000;
        if (ticks > 0) { lastRegenAt += ticks * 60000; if (balance < 100) balance = Math.min(100, balance + ticks * 5); }
    }

    private void deal() {
        GameSounds.play(getContext(), GameSounds.CARD);
        long bet = BETS[betIdx] * numHands;
        if (balance < bet) { if (listener != null) listener.onOutOfCoins(); return; }
        balance -= bet;
        xp += bet;
        lastWin = 0; winRow = -1; particlesOn = false; levelUpShowing = false;
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 52; i++) deck.add(i);
        Collections.shuffle(deck, rng);
        hand.clear();
        extraHands.clear();
        extraRows = new int[0];
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            hand.add(deck.get(i));
            initial5[i] = deck.get(i);
            held[i] = false;
            cardT[i] = now + i * 110;
            cardNew[i] = true;
        }
        boolean[] sug = basicStrategyHolds(hand);
        System.arraycopy(sug, 0, suggested, 0, 5);
        suggestVisible = true;
        message = "Tap cards to HOLD, then DRAW";
        phase = 1;
        invalidate(); if (listener != null) listener.saveState();
    }

    private void draw() {
        GameSounds.play(getContext(), GameSounds.CARD);
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 52; i++) if (!hand.contains(i)) deck.add(i);
        Collections.shuffle(deck, rng);
        int di = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) if (!held[i]) {
            hand.set(i, deck.get(di++));
            cardT[i] = now + i * 70;
            cardNew[i] = true;
        }
        for (int k = 1; k < numHands; k++) {
            List<Integer> d = new ArrayList<>();
            for (int c = 0; c < 52; c++) {
                boolean skip = false;
                for (int ii = 0; ii < 5; ii++) if (c == initial5[ii]) { skip = true; break; }
                if (!skip) d.add(c);
            }
            Collections.shuffle(d, rng);
            List<Integer> ex = new ArrayList<>(5);
            int di2 = 0;
            for (int i = 0; i < 5; i++) ex.add(held[i] ? initial5[i] : d.get(di2++));
            extraHands.add(ex);
        }
        settle();
    }

    static int evaluate(List<Integer> cards) {
        int[] rankCount = new int[13];
        int[] suitCount = new int[4];
        boolean[] present = new boolean[13];
        for (int c : cards) { rankCount[c % 13]++; suitCount[c / 13]++; present[c % 13] = true; }
        boolean flush = false;
        for (int s : suitCount) if (s == 5) flush = true;
        int run = 0, maxRun = 0;
        for (int i = 0; i < 13; i++) { run = present[i] ? run + 1 : 0; maxRun = Math.max(maxRun, run); }
        boolean wheel = present[0] && present[1] && present[2] && present[3] && present[4];
        boolean broadway = present[0] && present[9] && present[10] && present[11] && present[12];
        boolean straight = maxRun >= 5 || wheel || broadway;
        int pairs = 0, trips = 0, quads = 0; boolean pairJacksUp = false;
        for (int i = 0; i < 13; i++) {
            if (rankCount[i] == 2) { pairs++; if (i == 0 || i >= 10) pairJacksUp = true; }
            if (rankCount[i] == 3) trips++;
            if (rankCount[i] == 4) quads++;
        }
        if (straight && flush && broadway) return 0;
        if (straight && flush) return 1;
        if (quads > 0) return 2;
        if (trips > 0 && pairs > 0) return 3;
        if (flush) return 4;
        if (straight) return 5;
        if (trips > 0) return 6;
        if (pairs == 2) return 7;
        if (pairJacksUp) return 8;
        return -1;
    }

    // ---------- basic strategy hint (simplified Jacks-or-Better, not full optimal EV) ----------
    static boolean[] basicStrategyHolds(List<Integer> cards) {
        boolean[] hold = new boolean[5];
        int[] r = new int[5], s = new int[5];
        for (int i = 0; i < 5; i++) { r[i] = cards.get(i) % 13; s[i] = cards.get(i) / 13; }
        int ev = evaluate(cards);
        if (ev >= 0 && ev <= 5) { java.util.Arrays.fill(hold, true); return hold; } // pat straight or better
        boolean[] m;
        if ((m = royalDraw4(r, s)) != null) return m;
        if ((m = straightFlushDraw4(r, s)) != null) return m;
        if (ev == 6) { holdCount(hold, r, 3); return hold; }   // trips
        if (ev == 7) { holdCount(hold, r, 2); return hold; }   // two pair
        if (ev == 8) { holdCount(hold, r, 2); return hold; }   // high pair
        if ((m = flushDraw4(r, s)) != null) return m;
        if ((m = straightDraw4(r, s, true)) != null) return m; // outside straight
        int lp = lowPairRank(r);
        if (lp >= 0) { for (int i = 0; i < 5; i++) hold[i] = r[i] == lp; return hold; }
        if ((m = straightFlushDraw3(r, s)) != null) return m;
        if ((m = suitedHigh2(r, s)) != null) return m;
        if ((m = straightDraw4(r, s, false)) != null) return m; // inside straight
        List<Integer> his = new ArrayList<>();
        for (int i = 0; i < 5; i++) if (isHigh(r[i])) his.add(i);
        his.sort((a, b) -> Integer.compare(highOrder(r[a]), highOrder(r[b])));
        for (int i = 0; i < Math.min(2, his.size()); i++) hold[his.get(i)] = true;
        return hold;
    }

    private static boolean isHigh(int rank) { return rank == 0 || rank >= 10; }
    private static boolean isRoyal(int rank) { return rank == 0 || rank >= 9; }
    private static int highOrder(int rank) { return rank == 0 ? 13 : rank; } // J<Q<K<A

    private static void holdCount(boolean[] hold, int[] r, int cnt) {
        for (int i = 0; i < 5; i++) {
            int c = 0; for (int j = 0; j < 5; j++) if (r[j] == r[i]) c++;
            if (c == cnt) hold[i] = true;
        }
    }

    private static int lowPairRank(int[] r) {
        for (int i = 0; i < 5; i++) for (int j = i + 1; j < 5; j++)
            if (r[i] == r[j] && r[i] != 0 && r[i] < 10) return r[i];
        return -1;
    }

    private static boolean[] maskExcept(int drop) {
        boolean[] m = new boolean[5];
        for (int i = 0; i < 5; i++) m[i] = i != drop;
        return m;
    }

    private static boolean[] royalDraw4(int[] r, int[] s) {
        for (int drop = 0; drop < 5; drop++) {
            boolean ok = true; int suit = -1;
            for (int i = 0; i < 5 && ok; i++) {
                if (i == drop) continue;
                if (!isRoyal(r[i])) ok = false;
                else if (suit < 0) suit = s[i];
                else if (s[i] != suit) ok = false;
            }
            if (ok) return maskExcept(drop);
        }
        return null;
    }

    private static boolean[] straightFlushDraw4(int[] r, int[] s) {
        for (int drop = 0; drop < 5; drop++) {
            int[] rr = new int[4]; int suit = -1; boolean ok = true; int k = 0;
            for (int i = 0; i < 5; i++) {
                if (i == drop) continue;
                if (suit < 0) suit = s[i]; else if (s[i] != suit) { ok = false; break; }
                rr[k++] = r[i];
            }
            if (ok && fitsWindow(rr, 5)) return maskExcept(drop);
        }
        return null;
    }

    private static boolean[] flushDraw4(int[] r, int[] s) {
        for (int drop = 0; drop < 5; drop++) {
            int suit = -1; boolean ok = true;
            for (int i = 0; i < 5; i++) {
                if (i == drop) continue;
                if (suit < 0) suit = s[i]; else if (s[i] != suit) { ok = false; break; }
            }
            if (ok) return maskExcept(drop);
        }
        return null;
    }

    private static boolean[] straightDraw4(int[] r, int[] s, boolean outside) {
        for (int drop = 0; drop < 5; drop++) {
            int[] rr = new int[4]; int k = 0;
            for (int i = 0; i < 5; i++) { if (i == drop) continue; rr[k++] = r[i]; }
            boolean consec = fitsWindow(rr, 4);
            boolean win5 = fitsWindow(rr, 5);
            if (outside ? consec : (win5 && !consec)) return maskExcept(drop);
        }
        return null;
    }

    private static boolean[] straightFlushDraw3(int[] r, int[] s) {
        for (int a = 0; a < 5; a++) for (int b = a + 1; b < 5; b++) {
            int[] rr = new int[3]; int suit = -1; boolean ok = true; int k = 0;
            for (int i = 0; i < 5; i++) {
                if (i == a || i == b) continue;
                if (suit < 0) suit = s[i]; else if (s[i] != suit) { ok = false; break; }
                rr[k++] = r[i];
            }
            if (ok && fitsWindow(rr, 5)) {
                boolean[] m = new boolean[5];
                for (int i = 0; i < 5; i++) m[i] = i != a && i != b;
                return m;
            }
        }
        return null;
    }

    private static boolean[] suitedHigh2(int[] r, int[] s) {
        for (int i = 0; i < 5; i++) for (int j = i + 1; j < 5; j++)
            if (s[i] == s[j] && isHigh(r[i]) && isHigh(r[j])) {
                boolean[] m = new boolean[5];
                m[i] = m[j] = true;
                return m;
            }
        return null;
    }

    private static boolean fitsWindow(int[] ranks, int w) {
        return fitsAssign(ranks, w, false) || fitsAssign(ranks, w, true);
    }

    private static boolean fitsAssign(int[] ranks, int w, boolean aceHigh) {
        int mn = 99, mx = -1; long seen = 0;
        for (int rank : ranks) {
            int v = rank == 0 ? (aceHigh ? 14 : 1) : rank + 1;
            if ((seen & (1L << v)) != 0) return false;
            seen |= 1L << v;
            mn = Math.min(mn, v); mx = Math.max(mx, v);
        }
        return mx - mn <= w - 1;
    }

    private void settle() {
        long perBet = BETS[betIdx];
        long totalBet = perBet * numHands;
        int[] rows = new int[1 + extraHands.size()];
        rows[0] = evaluate(hand);
        for (int k = 0; k < extraHands.size(); k++) rows[k + 1] = evaluate(extraHands.get(k));
        extraRows = rows;
        resultAt = System.currentTimeMillis();
        int winHands = 0, bestIdx = -1;
        long totalWin = 0;
        for (int r : rows) {
            if (r >= 0) {
                winHands++;
                totalWin += perBet * PAY_MULT[r];
                if (bestIdx < 0 || r < bestIdx) bestIdx = r;
            }
        }
        stHands += numHands;
        stWins += winHands;
        if (totalWin > 0) {
            stStreak++;
            if (stStreak > stBestStreak) stBestStreak = stStreak;
        } else {
            stStreak = 0;
        }
        if (totalWin > stBiggest) stBiggest = totalWin;
        if (bestIdx >= 0 && (stBestHand < 0 || bestIdx < stBestHand)) stBestHand = bestIdx;
        saveStats();
        if (totalWin > 0) {
            balance += totalWin;
            GameSounds.play(bestIdx >= 6 ? GameSounds.BIG_WIN : GameSounds.WIN);
            lastWin = totalWin;
            message = PAY_NAMES[bestIdx] + "!" + (numHands > 1 ? "  " + winHands + "/" + numHands + " hands win" : "");
            winRow = bestIdx;
            spawnParticles();
        } else {
            GameSounds.play(getContext(), GameSounds.LOSE);
            lastWin = 0;
            message = "No win - try again";
            winRow = -1;
        }
        phase = 2;
        handsDone += numHands;
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();
            level++;
            long reward = 150L * level;
            balance += reward;
            levelUpPending = level; levelUpReward += reward;
            if (listener != null) listener.onLevelUp(level, reward);
        }
        if (listener != null) {
            if (bestIdx >= 0 && bestIdx <= 2) listener.onBigWin(totalWin, totalBet);
            listener.onSpinComplete((int) handsDone, totalWin, totalBet, numHands, winHands, bestIdx);
            listener.saveState();
        }
        if (balance < BETS[0] && listener != null) listener.onOutOfCoins();
        invalidate();
    }


    /** Coins burst from center and fly to the balance pill (e.g. daily claim). */
    public void coinBurst() {
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 14; i++) {
            cbx[i] = 0.5f + (r.nextFloat() - 0.5f) * 0.12f;
            cby[i] = 0.45f + (r.nextFloat() - 0.5f) * 0.08f;
            cbvx[i] = (r.nextFloat() - 0.5f) * 0.08f;
            cbvy[i] = (r.nextFloat() - 0.5f) * 0.08f;
        }
        coinBurstOn = true;
        coinBurstAt = System.currentTimeMillis();
        invalidate();
    }

    private void drawCoinBurst(Canvas cv, float w, float h, long now) {
        if (!coinBurstOn) return;
        float t = (now - coinBurstAt) / 750f;
        if (t >= 1f) { coinBurstOn = false; return; }
        for (int i = 0; i < 14; i++) {
            float local = Math.min(1f, Math.max(0f, t * 1.25f - i * 0.015f));
            float e = local * local;
            float x = (cbx[i] + cbvx[i] * local + (0.5f - cbx[i]) * e) * w;
            float y = (cby[i] + cbvy[i] * local + (0.045f - cby[i]) * e) * h;
            float rad = w * 0.014f * (1f - 0.4f * local);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD54F);
            cv.drawCircle(x, y, rad, p);
            p.setColor(0xFFB8860B);
            cv.drawCircle(x, y, rad * 0.55f, p);
        }
        postInvalidateDelayed(28);
    }

    private void spawnParticles() {
        particlesOn = true;
        for (int i = 0; i < 16; i++) {
            px[i] = 0.5f + (rng.nextFloat() - 0.5f) * 0.2f;
            py[i] = 0.5f;
            pvx[i] = (rng.nextFloat() - 0.5f) * 0.0016f;
            pvy[i] = -(0.0006f + rng.nextFloat() * 0.0016f);
        }
    }

    private float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }
    private float easeOutBack(float t) { float c = 1.70158f; t -= 1f; return 1f + (c + 1f) * t * t * t + c * t * t; }


    private int lighten(int color, float f) {
        int a = color & 0xFF000000;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * f));
        g = Math.min(255, (int) (g + (255 - g) * f));
        b = Math.min(255, (int) (b + (255 - b) * f));
        return a | (r << 16) | (g << 8) | b;
    }
    private int darken(int color, float f) {
        int a = color & 0xFF000000;
        int r = (int) (((color >> 16) & 0xFF) * (1f - f));
        int g = (int) (((color >> 8) & 0xFF) * (1f - f));
        int b = (int) ((color & 0xFF) * (1f - f));
        return a | (r << 16) | (g << 8) | b;
    }

    /** Static machine-hall backdrop: deep blue radial lighting, brushed side columns,
     *  grain, spotlight cone, vignette. Cached as a bitmap, rebuilt only on size change. */
    private void buildBackground(int w, int h) {
        if (bgCache != null) bgCache.recycle();
        bgCache = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bgCache);
        p.setStyle(Paint.Style.FILL);
        // blue key light high center
        p.setShader(new RadialGradient(w / 2f, h * 0.26f, h * 0.85f,
                new int[]{0xFF3355D8, 0xFF1A3396, 0xFF0A1748, 0xFF03061C},
                new float[]{0f, 0.42f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // warm pool over the card zone
        p.setShader(new RadialGradient(w / 2f, h * 0.44f, w * 0.62f, 0x22B0C4FF, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // brushed-metal side columns framing the machine
        p.setShader(new LinearGradient(0, 0, w * 0.045f, 0, 0xFF2A3242, 0xFF10141F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w * 0.030f, h, p);
        p.setShader(new LinearGradient(w, 0, w - w * 0.045f, 0, 0xFF2A3242, 0xFF10141F, Shader.TileMode.CLAMP));
        c.drawRect(w - w * 0.030f, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x66D8B45A);
        c.drawRect(w * 0.030f, 0, w * 0.030f + Math.max(2f, w * 0.004f), h, p);
        c.drawRect(w - w * 0.030f - Math.max(2f, w * 0.004f), 0, w - w * 0.030f, h, p);
        // spotlight cone
        p.setShader(new LinearGradient(0, 0, 0, h * 0.60f, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        android.graphics.Path cone = new android.graphics.Path();
        cone.moveTo(w * 0.28f, 0); cone.lineTo(w * 0.72f, 0);
        cone.lineTo(w * 0.96f, h * 0.60f); cone.lineTo(w * 0.04f, h * 0.60f);
        cone.close();
        c.drawPath(cone, p);
        p.setShader(null);
        // grain
        if (texX == null) {
            java.util.Random tr = new java.util.Random(20241030L);
            texX = new float[300]; texY = new float[300]; texL = new boolean[300];
            for (int i = 0; i < 300; i++) { texX[i] = tr.nextFloat(); texY[i] = tr.nextFloat(); texL[i] = tr.nextBoolean(); }
        }
        float dotR = Math.max(1f, w * 0.0012f);
        for (int i = 0; i < 300; i++) {
            p.setColor(texL[i] ? 0x0CFFFFFF : 0x16000000);
            c.drawCircle(texX[i] * w, texY[i] * h, (i % 5 == 0) ? dotR * 1.9f : dotR, p);
        }
        // vignette
        p.setShader(new RadialGradient(w / 2f, h * 0.46f, h * 0.74f,
                new int[]{0x00000000, 0x00000000, 0x59000000},
                new float[]{0f, 0.60f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private void drawCard(Canvas cv, int card, float x, float y, float w, float h, boolean isHeld, float scaleX, float glowT) {
        float cx = x + w / 2f;
        float dw = w * Math.abs(scaleX);
        if (dw < 2) return;
        float dx = cx - dw / 2f;
        boolean showBack = scaleX < 0;
        float rad = dw * 0.10f;
        // soft layered shadow
        rf.set(dx + dw * 0.03f, y + h * 0.035f, dx + dw + dw * 0.03f, y + h + h * 0.035f);
        p.setStyle(Paint.Style.FILL); p.setColor(0x73000000);
        cv.drawRoundRect(rf, rad, rad, p);
        rf.set(dx, y, dx + dw, y + h);
        if (showBack) {
            drawBackFace(cv, dx, y, dw, h, rad, cosBack);
            return;
        }
        // face: warm ivory stock with beveled edge
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, y, 0, y + h, 0xFFFFFEFC, 0xFFF6F1E4, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, rad, rad, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, dw * 0.012f));
        p.setColor(0xBFFFFFFF);
        cv.drawLine(dx + rad, y + 1, dx + dw - rad, y + 1, p);
        cv.drawLine(dx + 1, y + rad, dx + 1, y + h - rad, p);
        p.setColor(0x33000000);
        cv.drawLine(dx + rad, y + h - 1, dx + dw - rad, y + h - 1, p);
        cv.drawLine(dx + dw - 1, y + rad, dx + dw - 1, y + h - rad, p);
        p.setStrokeWidth(Math.max(1.5f, dw * 0.014f)); p.setColor(isHeld ? 0xFFFFD700 : 0xFF9C9690);
        cv.drawRoundRect(rf, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
        int rank = card % 13, suit = card / 13;
        boolean red = suit == 1 || suit == 2;
        int ink = red ? 0xFFC62828 : 0xFF1B1B1B;
        boolean face = rank >= 10;
        p.setColor(ink); p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(true);
        if (scaleX >= 0) {
            p.setTextSize(w * 0.235f);
            cv.drawText(RANKS[rank], dx + dw * 0.055f, y + h * 0.20f, p);
            p.setTextSize(w * 0.19f);
            cv.drawText(SUITS[suit], dx + dw * 0.055f, y + h * 0.365f, p);
            cv.save();
            cv.rotate(180f, cx, y + h / 2f);
            p.setTextSize(w * 0.235f);
            cv.drawText(RANKS[rank], dx + dw * 0.055f, y + h * 0.20f, p);
            p.setTextSize(w * 0.19f);
            cv.drawText(SUITS[suit], dx + dw * 0.055f, y + h * 0.365f, p);
            cv.restore();
            p.setFakeBoldText(false);
            p.setTextAlign(Paint.Align.CENTER);
            if (face) {
                float mr = w * 0.24f, my = y + h * 0.575f;
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(0xFF); p.setShader(new RadialGradient(cx - mr * 0.3f, my - mr * 0.35f, mr * 1.9f, 0xFFFFFDF6, 0xFFEFE3C4, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, my, mr, p);
                p.setAlpha(0xFF); p.setShader(null);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(mr * 0.10f); p.setColor(0xFFD4AF37);
                cv.drawCircle(cx, my, mr * 0.90f, p);
                p.setStrokeWidth(mr * 0.045f); p.setColor(0xFFEFE0B8);
                cv.drawCircle(cx, my, mr * 0.76f, p);
                p.setStyle(Paint.Style.FILL);
                p.setFakeBoldText(true);
                p.setColor(ink); p.setTextSize(mr * 1.05f);
                cv.drawText(RANKS[rank], cx, my + mr * 0.36f, p);
                p.setTextSize(mr * 0.52f); p.setColor(0xFFB8860B);
                cv.drawText("\u265B", cx, my - mr * 0.60f, p);
                p.setFakeBoldText(false);
            } else if (rank == 0) {
                p.setTextSize(w * 0.58f);
                cv.drawText(SUITS[suit], cx, y + h * 0.75f, p);
            } else {
                int n = rank + 1;
                float pip = n >= 7 ? w * 0.125f : w * 0.155f;
                float xl = dx + dw * 0.325f, xr = dx + dw * 0.675f, xc = cx;
                float T0 = y + h * 0.390f, T1 = y + h * 0.475f, T2 = y + h * 0.560f, T3 = y + h * 0.645f, T4 = y + h * 0.730f;
                float TH0 = y + h * 0.4325f, TH1 = y + h * 0.6875f;
                p.setTextSize(pip);
                java.util.List<float[]> pts = new java.util.ArrayList<>();
                switch (n) {
                    case 2: pts.add(new float[]{xc, T0, 0}); pts.add(new float[]{xc, T4, 1}); break;
                    case 3: pts.add(new float[]{xc, T0, 0}); pts.add(new float[]{xc, T2, 0}); pts.add(new float[]{xc, T4, 1}); break;
                    case 4: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 5: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xc, T2, 0}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 6: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xl, T2, 0}); pts.add(new float[]{xr, T2, 0}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 7: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xc, T1, 0}); pts.add(new float[]{xl, T2, 0}); pts.add(new float[]{xr, T2, 0}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 8: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xc, T1, 0}); pts.add(new float[]{xl, T2, 0}); pts.add(new float[]{xr, T2, 0}); pts.add(new float[]{xc, T3, 1}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 9: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xl, T1, 0}); pts.add(new float[]{xr, T1, 0}); pts.add(new float[]{xc, T2, 0}); pts.add(new float[]{xl, T3, 1}); pts.add(new float[]{xr, T3, 1}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    default: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xc, TH0, 0}); pts.add(new float[]{xl, T1, 0}); pts.add(new float[]{xr, T1, 0}); pts.add(new float[]{xl, T3, 1}); pts.add(new float[]{xr, T3, 1}); pts.add(new float[]{xc, TH1, 1}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                }
                for (float[] pt : pts) {
                    float pxp = pt[0], pyp = pt[1];
                    boolean flip = pt[2] > 0.5f;
                    cv.save();
                    if (flip) cv.rotate(180f, pxp, pyp - pip * 0.30f);
                    cv.drawText(SUITS[suit], pxp, pyp, p);
                    cv.restore();
                }
            }
        }
        p.setFakeBoldText(false);
        if (isHeld) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(glowT / 250.0);
            // gold glow plate behind the HELD tag
            float tagW = dw * 0.72f, tagH = h * 0.085f;
            rf.set(cx - tagW / 2, y - tagH * 0.62f, cx + tagW / 2, y + tagH * 0.38f);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(0xFF); p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFFFFE082, 0xFFB8860B, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rf, tagH * 0.5f, tagH * 0.5f, p);
            p.setAlpha(0xFF); p.setShader(null);
            p.setColor(Color.argb((int) (200 * pulse), 77, 53, 0));
            p.setTextSize(tagH * 0.62f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("HELD", cx, y + tagH * 0.10f, p);
            p.setFakeBoldText(false);
        }
    }

    private void drawChip(Canvas cv, float cx, float cy, float r, int color, String label, boolean selected) {
        float edge = r * 0.22f;
        p.setStyle(Paint.Style.FILL); p.setColor(0x73000000);
        cv.drawCircle(cx + r * 0.06f, cy + edge + r * 0.12f, r, p);
        p.setColor(darken(color, 0.55f));
        rf.set(cx - r, cy - r + edge, cx + r, cy + r + edge);
        cv.drawOval(rf, p);
        p.setColor(0xE6F5F5F5);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * (15 + i * 30) / 180.0;
            float exx = cx + (float) Math.cos(a) * r * 0.985f;
            float eyy = cy + edge + (float) Math.sin(a) * r * 0.985f;
            if (Math.sin(a) > 0.15) {
                p.setStrokeWidth(r * 0.14f); p.setStyle(Paint.Style.STROKE);
                cv.drawLine(exx, eyy - edge * 0.55f, exx, eyy + edge * 0.55f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        p.setAlpha(0xFF); p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.9f,
                lighten(color, 0.22f), darken(color, 0.38f), Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.20f); p.setColor(0xFFF5F5F5);
        rf.set(cx - r * 0.88f, cy - r * 0.88f, cx + r * 0.88f, cy + r * 0.88f);
        for (int i = 0; i < 8; i++)
            cv.drawArc(rf, i * 45f + 8f, 29f, false, p);
        p.setStrokeWidth(r * 0.05f); p.setColor(0xB3FFFFFF);
        rf.set(cx - r * 0.60f, cy - r * 0.60f, cx + r * 0.60f, cy + r * 0.60f);
        cv.drawArc(rf, 0f, 360f, false, p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(0xFF); p.setShader(new RadialGradient(cx, cy - r * 0.2f, r * 0.62f, 0xFFFFFFFF, 0xFFD8D8D8, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r * 0.52f, p);
        p.setAlpha(0xFF); p.setShader(null);
        if (label != null && !label.isEmpty()) {
            p.setColor(darken(color, 0.45f)); p.setTextSize(r * 0.56f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText(label, cx, cy + r * 0.20f, p);
            p.setFakeBoldText(false);
        }
        if (selected) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.13f); p.setColor(0x40FFD700);
            cv.drawCircle(cx, cy, r * 1.20f, p);
            p.setStrokeWidth(r * 0.07f); p.setColor(0xFFFFD700);
            cv.drawCircle(cx, cy, r * 1.14f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawPillButton(Canvas cv, float x, float y, float w, float h, String label,
                                int bgTop, int bgBottom, boolean enabled, boolean pressed, float pulse) {
        float sc = pressed ? 0.94f : 1f;
        float cx = x + w / 2f, cy = y + h / 2f;
        float dw = w * sc, dh = h * sc;
        // drop shadow
        p.setStyle(Paint.Style.FILL); p.setColor(0x59000000);
        rf.set(cx - dw / 2 + 2, cy - dh / 2 + dh * 0.10f, cx + dw / 2 + 2, cy + dh / 2 + dh * 0.10f);
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
        rf.set(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2);
        if (pulse > 0f && enabled) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h * 0.06f);
            p.setColor(0x44FFD700);
            float grow = pulse * h * 0.22f;
            RectF glow = new RectF(rf.left - grow, rf.top - grow, rf.right + grow, rf.bottom + grow);
            cv.drawRoundRect(glow, dh * 0.5f + grow, dh * 0.5f + grow, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (enabled) {
            p.setAlpha(0xFF); p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom,
                    new int[]{lighten(bgTop, 0.22f), bgTop, bgBottom},
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        } else {
            p.setAlpha(0xFF); p.setShader(null); p.setColor(0xFF37474F);
        }
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
        p.setAlpha(0xFF); p.setShader(null);
        if (enabled) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, dh * 0.030f));
            p.setColor(0x59FFFFFF);
            cv.drawLine(rf.left + dh * 0.45f, rf.top + dh * 0.09f, rf.right - dh * 0.45f, rf.top + dh * 0.09f, p);
            p.setColor(0x33000000);
            cv.drawLine(rf.left + dh * 0.45f, rf.bottom - dh * 0.09f, rf.right - dh * 0.45f, rf.bottom - dh * 0.09f, p);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(0xFF); p.setShader(new LinearGradient(0, cy - dh / 2, 0, cy - dh * 0.05f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rf.set(cx - dw / 2 + dh * 0.12f, cy - dh / 2 + dh * 0.08f, cx + dw / 2 - dh * 0.12f, cy - dh * 0.06f);
            cv.drawRoundRect(rf, dh * 0.4f, dh * 0.4f, p);
            p.setAlpha(0xFF); p.setShader(null);
            rf.set(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2);
        }
        // gold rim
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, dh * 0.045f));
        p.setColor(enabled ? 0xE6FFD700 : 0x55FFFFFF);
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
        p.setStrokeWidth(Math.max(1f, dh * 0.018f)); p.setColor(enabled ? 0x66FFF3C4 : 0x33FFFFFF);
        rf.inset(dh * 0.05f, dh * 0.05f);
        cv.drawRoundRect(rf, dh * 0.45f, dh * 0.45f, p);
        rf.inset(-dh * 0.05f, -dh * 0.05f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(enabled ? 0xFFFFFFFF : 0xFF90A4AE);
        p.setTextSize(dh * 0.42f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        p.setLetterSpacing(0.08f);
        p.setShadowLayer(4, 0, 2, 0x80000000);
        cv.drawText(label, cx, cy + dh * 0.15f, p);
        p.clearShadowLayer();
        p.setLetterSpacing(0f);
        p.setFakeBoldText(false);
    }

    private void drawFancyText(Canvas cv, String text, float cx, float cy, float size, int topColor, int bottomColor) {
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(size); p.setFakeBoldText(true);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.10f); p.setColor(0xAA000000);
        cv.drawText(text, cx, cy, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, cy - size, 0, cy + size * 0.2f, topColor, bottomColor, Shader.TileMode.CLAMP));
        cv.drawText(text, cx, cy, p);
        p.setShader(null); p.setFakeBoldText(false);
    }

    @Override protected void onDraw(Canvas cv) {
        regen();
        long now = System.currentTimeMillis();
        int w = getWidth(), h = getHeight();
        boolean animating = false;

        // cached machine-hall backdrop + animated dust + marquee chase lamps
        if (bgCache == null || bgW != w || bgH != h) { buildBackground(w, h); bgW = w; bgH = h; }
        cv.drawBitmap(bgCache, 0, 0, null);

        if (dustX == null) {
            java.util.Random dr2 = new java.util.Random(4242L);
            dustX = new float[16]; dustY = new float[16]; dustS = new float[16]; dustV = new float[16];
            for (int i = 0; i < 16; i++) { dustX[i] = dr2.nextFloat(); dustY[i] = dr2.nextFloat(); dustS[i] = 0.5f + dr2.nextFloat(); dustV[i] = 0.00012f + dr2.nextFloat() * 0.00025f; }
        }
        for (int i = 0; i < 16; i++) {
            float dy = (dustY[i] - now * dustV[i]) % 1f; if (dy < 0) dy += 1f;
            float dxp = dustX[i] + 0.02f * (float) Math.sin(now / 1700.0 + i * 1.7);
            float fade = (float) Math.sin(dy * Math.PI);
            p.setColor(((int) (40 * fade * dustS[i]) << 24) | 0xD9E4FF);
            cv.drawCircle(dxp * w, dy * h * 0.72f, Math.max(1f, w * 0.0022f * dustS[i]), p);
        }
        animating = true;

        // ---- HUD: level badge + coin pill ----
        float lvR = w * 0.055f, lvX = w * 0.10f, lvY = h * 0.042f;
        p.setStyle(Paint.Style.FILL); p.setColor(0x55000000);
        cv.drawCircle(lvX + 2, lvY + 3, lvR, p);
        p.setAlpha(0xFF); p.setShader(new RadialGradient(lvX - lvR * 0.3f, lvY - lvR * 0.35f, lvR * 1.9f, 0xFF2E4E86, 0xFF081C3E, Shader.TileMode.CLAMP));
        cv.drawCircle(lvX, lvY, lvR, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, lvY - lvR, 0, lvY, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(lvX, lvY, lvR * 0.96f, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(lvR * 0.09f); p.setColor(0xE6D8B45A);
        cv.drawCircle(lvX, lvY, lvR * 0.94f, p);
        p.setStrokeWidth(lvR * 0.035f); p.setColor(0x80FFF3C4);
        cv.drawCircle(lvX, lvY, lvR * 0.85f, p);
        float xpFrac = Math.min(1f, xp / (float) xpNeeded());
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(lvR * 0.18f); p.setColor(0x33FFFFFF);
        cv.drawCircle(lvX, lvY, lvR * 0.82f, p);
        p.setColor(0xFFFFD700);
        cv.drawArc(lvX - lvR * 0.82f, lvY - lvR * 0.82f, lvX + lvR * 0.82f, lvY + lvR * 0.82f, -90, xpFrac * 360f, false, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        p.setTextSize(lvR * 0.62f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText("" + level, lvX, lvY + lvR * 0.24f, p);
        p.setFakeBoldText(false);
        p.setTextSize(h * 0.010f); p.setColor(0x99FFFFFF);
        cv.drawText("LEVEL", lvX, lvY + lvR * 1.5f, p);
        // sound toggle
        float spX = w * 0.93f, spY = h * 0.042f, spR = w * 0.045f;
        p.setColor(0x66000000);
        cv.drawCircle(spX, spY, spR, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x55FFFFFF);
        cv.drawCircle(spX, spY, spR, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(spR * 0.9f); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(GameSounds.isEnabled(getContext()) ? "\uD83D\uDD0A" : "\uD83D\uDD07", spX, spY + spR * 0.32f, p);
        // stats toggle (mini bar chart)
        float stX = w * 0.80f;
        p.setColor(0x66000000);
        cv.drawCircle(stX, spY, spR, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x55FFFFFF);
        cv.drawCircle(stX, spY, spR, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xCCFFD700);
        for (int bi = 0; bi < 3; bi++) {
            float bw = spR * 0.26f, bhv = spR * (0.45f + bi * 0.33f);
            rf.set(stX + (bi - 1) * spR * 0.42f - bw / 2, spY + spR * 0.55f - bhv, stX + (bi - 1) * spR * 0.42f + bw / 2, spY + spR * 0.55f);
            cv.drawRoundRect(rf, bw * 0.25f, bw * 0.25f, p);
        }


        float pillW = w * 0.34f, pillH = h * 0.032f, pillX = w / 2f - pillW / 2f + w * 0.06f, pillY = h * 0.026f;
        rf.set(pillX, pillY, pillX + pillW, pillY + pillH);
        p.setColor(0x59000000);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, pillY, 0, pillY + pillH, 0xF21E2A4E, 0xF2070F28, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, pillY, 0, pillY + pillH * 0.5f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rf.set(pillX + pillH * 0.10f, pillY + pillH * 0.08f, pillX + pillW - pillH * 0.10f, pillY + pillH * 0.48f);
        cv.drawRoundRect(rf, pillH * 0.4f, pillH * 0.4f, p);
        p.setAlpha(0xFF); p.setShader(null);
        rf.set(pillX, pillY, pillX + pillW, pillY + pillH);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(0xCCFFD700);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setStrokeWidth(1f); p.setColor(0x55FFF3C4);
        rf.inset(2.5f, 2.5f);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        rf.inset(-2.5f, -2.5f);
        p.setStyle(Paint.Style.FILL);
        float coinR = pillH * 0.36f, coinX = pillX + pillH * 0.55f, coinY = pillY + pillH / 2f;
        p.setAlpha(0xFF); p.setShader(new RadialGradient(coinX - coinR * 0.3f, coinY - coinR * 0.3f, coinR * 1.8f, 0xFFFFE082, 0xFFB8860B, Shader.TileMode.CLAMP));
        cv.drawCircle(coinX, coinY, coinR, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0xFF8D6E00);
        cv.drawCircle(coinX, coinY, coinR * 0.72f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF6D4C00); p.setTextSize(coinR * 1.1f); p.setFakeBoldText(true);
        cv.drawText("$", coinX, coinY + coinR * 0.38f, p);
        p.setFakeBoldText(false);
        if (shownBalance != balance) {
            long diff = balance - shownBalance;
            long step = Math.max(1, Math.abs(diff) / 6);
            shownBalance += diff > 0 ? step : -step;
            if ((diff > 0 && shownBalance > balance) || (diff < 0 && shownBalance < balance)) shownBalance = balance;
            animating = true;
        }
        p.setColor(0xFFFFFFFF); p.setTextSize(pillH * 0.52f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText(String.format("%,d", shownBalance), pillX + pillW * 0.58f, pillY + pillH * 0.68f, p);
        p.setFakeBoldText(false);
        if (balance < 100) {
            long nextIn = 60000 - (now - lastRegenAt) % 60000;
            p.setTextSize(h * 0.010f); p.setColor(0x99FFFFFF);
            cv.drawText("+5 free in " + (nextIn / 1000 + 1) + "s", pillX + pillW / 2f, pillY + pillH + h * 0.015f, p);
            animating = true;
        }

        // ---- paytable panel (machine display) ----
        float ptX = w * 0.035f, ptY = h * 0.100f, ptW = w * 0.93f, ptH = h * 0.228f;
        // metallic gold bezel with inner groove
        rf.set(ptX, ptY, ptX + ptW, ptY + ptH);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(ptX, ptY, ptX + ptW, ptY + ptH, 0xFFFFF59D, 0xFF9A6A08, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, 16, 16, p);
        p.setShader(null);
        rf.set(ptX + 3, ptY + 3, ptX + ptW - 3, ptY + ptH - 3);
        p.setColor(0xFF050E38); cv.drawRoundRect(rf, 13, 13, p);
        rf.set(ptX + 6, ptY + 6, ptX + ptW - 6, ptY + ptH - 6);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, ptY, 0, ptY + ptH, 0xFF1B35A6, 0xFF0B1C64, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, 11, 11, p);
        p.setShader(null);
        // marquee chase lamps along the bezel top
        {
            int lampN = 9;
            float lampY = ptY - h * 0.004f;
            for (int i = 0; i < lampN; i++) {
                float lx = ptX + ptW * (0.08f + i * (0.84f / (lampN - 1)));
                boolean hotLamp = ((now / 160) % lampN) == i;
                float lr = h * 0.0062f;
                if (hotLamp) {
                    p.setColor(0x55FFD700);
                    cv.drawCircle(lx, lampY, lr * 2.6f, p);
                }
                p.setAlpha(0xFF); p.setShader(new RadialGradient(lx - lr * 0.3f, lampY - lr * 0.3f, lr * 2f,
                        hotLamp ? 0xFFFFF6C8 : 0xFFC9A227, hotLamp ? 0xFFFFB300 : 0xFF6E5208, Shader.TileMode.CLAMP));
                cv.drawCircle(lx, lampY, lr, p);
                p.setAlpha(0xFF); p.setShader(null);
            }
            animating = true;
        }
        // corner rivets
        for (int rc = 0; rc < 4; rc++) {
            float rvx = (rc % 2 == 0) ? ptX + 11 : ptX + ptW - 11;
            float rvy = (rc < 2) ? ptY + 11 : ptY + ptH - 11;
            p.setColor(0xFF7A5C10); cv.drawCircle(rvx, rvy, h * 0.0038f, p);
            p.setColor(0xFFFFF59D); cv.drawCircle(rvx - h * 0.0013f, rvy - h * 0.0013f, h * 0.0024f, p);
        }
        // engraved gold header plaque
        float plW = ptW * 0.66f, plH = h * 0.030f, plX = w / 2f - plW / 2f, plY = ptY + h * 0.009f;
        rf.set(plX, plY, plX + plW, plY + plH);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, plY, 0, plY + plH, 0xFFFFE082, 0xFFB8860B, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, plH * 0.5f, plH * 0.5f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xFFFFF9C4);
        cv.drawRoundRect(rf, plH * 0.5f, plH * 0.5f, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h * 0.0145f); p.setFakeBoldText(true);
        p.setColor(0x99FFFFFF); cv.drawText("JACKS OR BETTER  \u2726  PAYTABLE", w / 2f, plY + plH * 0.66f - 1.5f, p);
        p.setColor(0xFF5D4300); cv.drawText("JACKS OR BETTER  \u2726  PAYTABLE", w / 2f, plY + plH * 0.66f, p);
        p.setFakeBoldText(false);
        // bet medallions (active bet is lit red)
        float medY = ptY + h * 0.052f, medR = h * 0.0118f;
        p.setTextAlign(Paint.Align.LEFT); p.setColor(0xCCFFD54F); p.setTextSize(h * 0.010f); p.setFakeBoldText(true);
        cv.drawText("BET", ptX + ptW * 0.04f, medY + h * 0.004f, p);
        p.setFakeBoldText(false);
        for (int b = 0; b < BETS.length; b++) {
            float hx = ptX + ptW * (0.52f + b * 0.12f);
            if (b == betIdx) {
                p.setColor(0x66FFD700);
                cv.drawCircle(hx, medY, medR * 1.45f, p);
            }
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(0, medY - medR, 0, medY + medR,
                    b == betIdx ? 0xFFFF5252 : 0xFFFFD54F, b == betIdx ? 0xFFB71C1C : 0xFF9A6A08, Shader.TileMode.CLAMP));
            cv.drawCircle(hx, medY, medR, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            p.setColor(b == betIdx ? 0xFFFFFFFF : 0x88FFF9C4);
            cv.drawCircle(hx, medY, medR, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setColor(b == betIdx ? 0xFFFFFFFF : 0xFF4A3200);
            p.setTextSize(h * 0.011f);
            cv.drawText("" + BETS[b], hx, medY + h * 0.0045f, p);
        }
        p.setFakeBoldText(false);
        // lit lane under the active bet column
        float laneX = ptX + ptW * (0.52f + betIdx * 0.12f);
        rf.set(laneX - ptW * 0.058f, ptY + h * 0.066f, laneX + ptW * 0.058f, ptY + ptH - h * 0.006f);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, ptY + h * 0.066f, 0, ptY + ptH - h * 0.006f, 0x66FF5252, 0x4DB71C1C, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, 8, 8, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0x99FF8A80);
        cv.drawRoundRect(rf, 8, 8, p);
        p.setStyle(Paint.Style.FILL);
        // rows
        float rowH = h * 0.0172f, rowY0 = ptY + h * 0.082f;
        for (int i = 0; i < 9; i++) {
            boolean hot = i == winRow && now - resultAt < 2400;
            float ry = rowY0 + i * rowH;
            if (i == 0) {
                // Royal Flush celebration banner
                rf.set(ptX + 8, ry - rowH * 0.80f, ptX + ptW - 8, ry + rowH * 0.24f);
                p.setColor(0xFFFFFFFF);
                p.setShader(new LinearGradient(0, ry - rowH, 0, ry + rowH * 0.24f, 0xFFFFE082, 0xFFFFB300, Shader.TileMode.CLAMP));
                cv.drawRoundRect(rf, 8, 8, p);
                p.setShader(null);
            } else if (i % 2 == 1) {
                rf.set(ptX + 8, ry - rowH * 0.80f, ptX + ptW - 8, ry + rowH * 0.24f);
                p.setColor(0x10FFFFFF);
                cv.drawRoundRect(rf, 8, 8, p);
            }
            if (hot) {
                rf.set(ptX + 8, ry - rowH * 0.80f, ptX + ptW - 8, ry + rowH * 0.24f);
                p.setColor(0x55FFD700);
                cv.drawRoundRect(rf, 8, 8, p);
                animating = true;
            }
            if (i > 0) {
                p.setColor(0x2EFFD54F);
                cv.drawRect(ptX + ptW * 0.03f, ry - rowH * 0.92f, ptX + ptW * 0.97f, ry - rowH * 0.92f + 1.2f, p);
            }
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(hot ? 0xFFFFD54F : (i == 0 ? 0xFF4A3200 : 0xFFD6E4FF));
            p.setTextSize(h * 0.0122f);
            p.setFakeBoldText(i == 0 || hot);
            cv.drawText(PAY_NAMES[i], ptX + ptW * 0.04f, ry, p);
            for (int b = 0; b < BETS.length; b++) {
                float cxR = ptX + ptW * (0.52f + b * 0.12f);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(hot ? 0xFFFFD54F : (i == 0 ? 0xFF4A3200 : (b == betIdx ? 0xFFFFFFFF : 0xFF9FB6E8)));
                p.setFakeBoldText(b == betIdx || i == 0 || hot);
                cv.drawText("" + (PAY_MULT[i] * BETS[b]), cxR, ry, p);
            }
            p.setFakeBoldText(false);
            p.setTextAlign(Paint.Align.LEFT);
        }

        // ---- hand row ----
        float pScale = primaryScale(w, h);
        float cw = w * 0.165f * pScale, ch = cw * 1.42f, gap = w * 0.028f * pScale;
        float total = 5 * cw + 4 * gap;
        float x0 = (w - total) / 2f, y0 = primaryY0(w, h);
        for (int i = 0; i < hand.size(); i++) {
            long t0 = cardT[i];
            float t = Math.min(1f, (now - t0) / 300f);
            if (t < 1f) animating = true;
            float scaleX = 1f;
            if (t < 1f) scaleX = (float) Math.cos(t * Math.PI / 2);
            float y = y0 + (held[i] ? -h * 0.008f : 0);
            drawCard(cv, hand.get(i), x0 + i * (cw + gap), y, cw, ch, held[i], scaleX, now);
            if (held[i]) animating = true;
            if (phase == 1 && suggestVisible && suggested[i] && !held[i] && t >= 1f) {
                float pl = 0.55f + 0.45f * (float) Math.sin(now / 300.0);
                float sx0 = x0 + i * (cw + gap);
                rf.set(sx0 - cw * 0.035f, y - ch * 0.018f, sx0 + cw * 1.035f, y + ch * 1.018f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(cw * 0.028f);
                p.setColor(((int) (0xA6 * pl) << 24) | 0x9FD8FF);
                cv.drawRoundRect(rf, cw * 0.14f, cw * 0.14f, p);
                p.setStyle(Paint.Style.FILL);
                if (numHands == 1) {
                    p.setColor(((int) (0xD8 * pl) << 24) | 0xCFE8FF);
                    p.setTextSize(cw * 0.125f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
                    cv.drawText("SUGGESTED", sx0 + cw / 2f, y + ch + h * 0.028f, p);
                    p.setFakeBoldText(false);
                }
                animating = true;
            }
        }
        // ---- extra hands (multi-hand results) ----
        if (phase == 2 && !extraHands.isEmpty()) {
            float ms = miniScale(w, h);
            float mcw = w * 0.165f * ms, mch = mcw * 1.42f, mgap = w * 0.028f * ms;
            float mtotal = 5 * mcw + 4 * mgap;
            boolean grid = numHands == 5;
            for (int k = 0; k < extraHands.size(); k++) {
                float mx0 = miniX(k, w, h);
                float my = miniY(k, w, h);
                List<Integer> ex = extraHands.get(k);
                int r = k + 1 < extraRows.length ? extraRows[k + 1] : -1;
                boolean won = r >= 0;
                if (won) {
                    rf.set(mx0 - mcw * 0.06f, my - mch * 0.05f, mx0 + mtotal + mcw * 0.06f, my + mch * 1.05f);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, mcw * 0.03f)); p.setColor(0x88FFD54F);
                    cv.drawRoundRect(rf, mcw * 0.12f, mcw * 0.12f, p);
                    p.setStyle(Paint.Style.FILL);
                }
                for (int i = 0; i < 5; i++) drawCard(cv, ex.get(i), mx0 + i * (mcw + mgap), my, mcw, mch, false, 1f, now);
                String lbl = won ? PAY_NAMES[r] + "  +" + (PAY_MULT[r] * BETS[betIdx]) : "no win";
                p.setFakeBoldText(won);
                p.setColor(won ? 0xFFFFD54F : 0xFF7F8FB8);
                if (grid) {
                    p.setTextAlign(Paint.Align.CENTER); p.setTextSize(mch * 0.34f);
                    cv.drawText(lbl, mx0 + mtotal / 2f, my + mch + h * 0.014f, p);
                } else {
                    p.setTextAlign(Paint.Align.LEFT); p.setTextSize(mch * 0.30f);
                    cv.drawText(lbl, mx0 + mtotal + w * 0.02f, my + mch * 0.60f, p);
                }
                p.setFakeBoldText(false);
            }
        }

        if (hand.isEmpty()) {
            // empty card slots
            for (int i = 0; i < 5; i++) {
                rf.set(x0 + i * (cw + gap), y0, x0 + i * (cw + gap) + cw, y0 + ch);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0x336FA8FF);
                cv.drawRoundRect(rf, cw * 0.09f, cw * 0.09f, p);
                p.setStyle(Paint.Style.FILL);
            }
            float bw = w * 0.52f, bhh = h * 0.038f;
            rf.set(w / 2f - bw / 2f, y0 + ch / 2f - bhh / 2f, w / 2f + bw / 2f, y0 + ch / 2f + bhh / 2f);
            p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFF1B34A8, 0xFF0A1750, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rf, bhh / 2, bhh / 2, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0xFFFFD54F);
            cv.drawRoundRect(rf, bhh / 2, bhh / 2, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD700); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h * 0.017f); p.setFakeBoldText(true);
            cv.drawText("\u25B6  TAP DEAL TO PLAY", w / 2, y0 + ch / 2 + h * 0.006f, p);
            p.setFakeBoldText(false);
        }

        // ---- message / win banner ----
        if (numHands == 1 && phase == 2 && lastWin > 0 && now - resultAt < 2300 && !levelUpShowing) {
            float t = Math.min(1f, (now - resultAt) / 380f);
            float sc = easeOutBack(t);
            animating = true;
            cv.save();
            cv.scale(sc, sc, w / 2f, h * 0.612f);
            // win rays behind the plaque
            {
                float rayT = Math.min(1f, (now - resultAt) / 900f);
                int rayN = winRow >= 0 && winRow <= 2 ? 18 : 12;
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < rayN; i++) {
                    double a = Math.PI * 2 * i / rayN + now / 2600.0;
                    float inn = w * 0.10f, out = inn + w * (0.10f + 0.16f * rayT) * (0.7f + 0.3f * (float) Math.sin(i * 2.3));
                    int al = winRow >= 0 && winRow <= 2 ? 90 : (int) (55 * (1f - rayT * 0.5f));
                    p.setStrokeWidth(w * 0.010f);
                    p.setColor((al << 24) | 0xFFD700);
                    cv.drawLine(w / 2f + (float) Math.cos(a) * inn, h * 0.612f + (float) Math.sin(a) * inn * 0.5f,
                            w / 2f + (float) Math.cos(a) * out, h * 0.612f + (float) Math.sin(a) * out * 0.5f, p);
                }
                p.setStyle(Paint.Style.FILL);
            }
            // dark glass plaque behind the two result lines
            {
                float tsz = h * 0.036f;
                p.setTextSize(tsz); p.setFakeBoldText(true);
                float tw = Math.max(p.measureText(message), p.measureText("+" + lastWin));
                p.setFakeBoldText(false);
                float plW2 = tw + w * 0.16f, plH2 = tsz * 3.1f;
                rf.set(w / 2f - plW2 / 2f, h * 0.612f - plH2 * 0.62f, w / 2f + plW2 / 2f, h * 0.612f + plH2 * 0.38f);
                p.setAlpha(0xFF); p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xE6121A2E, 0xE6040818, Shader.TileMode.CLAMP));
                cv.drawRoundRect(rf, plH2 * 0.30f, plH2 * 0.30f, p);
                p.setAlpha(0xFF); p.setShader(null);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, tsz * 0.06f)); p.setColor(0xFFD8B45A);
                cv.drawRoundRect(rf, plH2 * 0.30f, plH2 * 0.30f, p);
                p.setStrokeWidth(Math.max(1.5f, tsz * 0.026f)); p.setColor(0x77FFE9B0);
                rf.inset(tsz * 0.08f, tsz * 0.08f);
                cv.drawRoundRect(rf, plH2 * 0.26f, plH2 * 0.26f, p);
                p.setStyle(Paint.Style.FILL);
            }
            drawFancyText(cv, message, w / 2f, h * 0.590f, h * 0.036f, 0xFFFFE082, 0xFFFF8F00);
            drawFancyText(cv, "+" + lastWin, w / 2f, h * 0.635f, h * 0.030f, 0xFFD7FFB0, 0xFF43A047);
            cv.restore();
            if (particlesOn) {
                for (int i = 0; i < 16; i++) {
                    float xx = (px[i] + pvx[i] * (now - resultAt)) * w;
                    float yy = (py[i] + pvy[i] * (now - resultAt) + 0.0000009f * (now - resultAt) * (now - resultAt)) * h;
                    if ((now - resultAt) < 1200) drawChip(cv, xx, yy, w * 0.015f, CHIP_COLORS[i % 4], "", false);
                }
            }
        } else {
            p.setColor(0xFFE8F0FF); p.setTextSize(h * 0.022f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            float msgBase = msgY(w, h);
            cv.drawText(message, w / 2, msgBase, p);
            p.setFakeBoldText(false);
            if (numHands > 1 && phase == 2 && lastWin > 0) {
                p.setColor(0xFF7CFF9B); p.setTextSize(h * 0.026f); p.setFakeBoldText(true);
                cv.drawText("+" + lastWin, w / 2, msgBase + h * 0.032f, p);
                p.setFakeBoldText(false);
            }
        }

        drawCoinBurst(cv, w, h, now);

        // ---- controls ----
        float bh = h * 0.056f;
        long idleMs = now - Math.max(resultAt, lastTouchAt);
        float pulse = ((phase == 0 || phase == 2) && idleMs > 3000) ? (float) (0.5 + 0.5 * Math.sin(now / 450.0)) : 0f;
        if (pulse > 0) animating = true;
        if (phase == 0 || phase == 2) {
            float chipR = w * 0.060f, chipGap = w * 0.040f;
            float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
            float cx0 = w / 2f - rowW / 2f + chipR;
            p.setColor(0xCCFFD54F); p.setTextSize(h * 0.0125f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setShadowLayer(4, 0, 1, 0x66000000);
            p.clearShadowLayer();
            p.setFakeBoldText(false);
            // HANDS selector (x1 / x3 / x5) + total wager
            {
                float pw2 = w * 0.10f, ph2 = h * 0.024f, pgap = w * 0.02f;
                int[] opts = {1, 3, 5};
                float px0 = w * 0.30f;
                p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(h * 0.014f); p.setColor(0xCCFFD54F); p.setFakeBoldText(true);
                cv.drawText("HANDS", px0 - w * 0.02f, h * 0.646f + ph2 * 0.35f, p);
                p.setTextAlign(Paint.Align.CENTER);
                for (int o = 0; o < opts.length; o++) {
                    boolean sel = numHands == opts[o];
                    float lx = px0 + o * (pw2 + pgap);
                    rf.set(lx, h * 0.646f - ph2 / 2f, lx + pw2, h * 0.646f + ph2 / 2f);
                    p.setAlpha(0xFF); p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, sel ? 0xFF2A4A9E : 0xFF16244E, sel ? 0xFF12285E : 0xFF0A1434, Shader.TileMode.CLAMP));
                    cv.drawRoundRect(rf, ph2 / 2, ph2 / 2, p);
                    p.setAlpha(0xFF); p.setShader(null);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sel ? 3f : 1.5f); p.setColor(sel ? 0xFFFFD54F : 0x446FA8FF);
                    cv.drawRoundRect(rf, ph2 / 2, ph2 / 2, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(sel ? 0xFFFFD54F : 0xFF9FB6E8); p.setTextSize(h * 0.014f); p.setFakeBoldText(sel);
                    cv.drawText("x" + opts[o], lx + pw2 / 2f, h * 0.646f + ph2 * 0.35f, p);
                }
                p.setFakeBoldText(false);
                if (numHands > 1) {
                    p.setTextAlign(Paint.Align.LEFT); p.setColor(0xFFFFD54F); p.setTextSize(h * 0.014f); p.setFakeBoldText(true);
                    cv.drawText("TOTAL " + (BETS[betIdx] * numHands), px0 + 3 * pw2 + 2 * pgap + w * 0.03f, h * 0.646f + ph2 * 0.35f, p);
                    p.setFakeBoldText(false);
                    p.setTextAlign(Paint.Align.CENTER);
                }
            }
            for (int i = 0; i < BETS.length; i++) {
                boolean sel = i == betIdx;
                drawChip(cv, cx0 + i * (chipR * 2 + chipGap), h * 0.710f + (sel ? -h * 0.008f : 0), chipR, CHIP_COLORS[i], "" + BETS[i], sel);
            }

            // MAX BET shortcut
            float mx = w / 2f + rowW / 2f + chipGap * 0.6f + chipR * 0.8f;
            float bump = now - betBumpAt < 250 ? 1f + 0.25f * (1f - (now - betBumpAt) / 250f) : 1f;
            if (now - betBumpAt < 250) postInvalidateDelayed(28);
            float mr2 = chipR * 0.8f * bump;
            p.setStyle(Paint.Style.FILL); p.setColor(0x73000000);
            cv.drawCircle(mx + mr2 * 0.06f, h * 0.710f + mr2 * 0.12f, mr2, p);
            p.setAlpha(0xFF); p.setShader(new RadialGradient(mx - mr2 * 0.35f, h * 0.710f - mr2 * 0.4f, mr2 * 1.9f, 0xFFFFE082, 0xFFB8860B, Shader.TileMode.CLAMP));
            cv.drawCircle(mx, h * 0.710f, mr2, p);
            p.setAlpha(0xFF); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(mr2 * 0.08f); p.setColor(0xFF8D6E00);
            cv.drawCircle(mx, h * 0.710f, mr2 * 0.92f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF4A3200); p.setTextSize(chipR * 0.4f * bump); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("MAX", mx, h * 0.710f + chipR * 0.14f * bump, p);
            p.setFakeBoldText(false);
            debugPhase = phase; // this branch draws for BOTH 0 (betting) and 2 (result)
            debugActionBtn = new android.graphics.Rect((int)(w * 0.14f), (int)(h * 0.785f), (int)(w * 0.86f), (int)(h * 0.785f + bh));
            drawPillButton(cv, w * 0.14f, h * 0.785f, w * 0.72f, bh,
                    numHands > 1 ? "DEAL x" + numHands + "   \u2022   " + (BETS[betIdx] * numHands) + "  \u25B6" : "DEAL   \u25B6",
                    0xFF43A047, 0xFF1B5E20,
                    balance >= BETS[betIdx] * numHands, pressedBtn == 0, pulse);
        } else {
            debugPhase = phase;
            debugActionBtn = new android.graphics.Rect((int)(w * 0.14f), (int)(h * 0.765f), (int)(w * 0.86f), (int)(h * 0.765f + bh));
            drawPillButton(cv, w * 0.14f, h * 0.765f, w * 0.72f, bh, "DRAW", 0xFFFF5252, 0xFFB71C1C, true, pressedBtn == 1, pulse > 0 ? Math.max(pulse, 0.55f) : (float) (0.55 + 0.35 * Math.sin(now / 450.0)));
            animating = true;
        }

        // ---- level-up overlay ----
        if (levelUpPending > 0 && !levelUpShowing && now - resultAt > 1400) {
            levelUpShowing = true; levelUpAt = now;
        }
        if (levelUpShowing) {
            animating = true;
            float t = Math.min(1f, (now - levelUpAt) / 420f);
            cv.drawColor(0xB3000000);
            float sc = easeOutBack(t);
            cv.save();
            cv.scale(sc, sc, w / 2f, h * 0.42f);
            float badgeR = w * 0.15f;
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFD700);
            cv.drawCircle(w / 2f, h * 0.36f, badgeR, p);
            p.setColor(0xFF6D4C00); p.setTextSize(badgeR * 0.9f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("" + levelUpPending, w / 2f, h * 0.36f + badgeR * 0.32f, p);
            p.setFakeBoldText(false);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w * 0.012f); p.setColor(0x55FFD700);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI / 6 * i + now / 900.0;
                cv.drawLine(w / 2f + (float) Math.cos(a) * badgeR * 1.25f, h * 0.36f + (float) Math.sin(a) * badgeR * 1.25f,
                        w / 2f + (float) Math.cos(a) * badgeR * 1.6f, h * 0.36f + (float) Math.sin(a) * badgeR * 1.6f, p);
            }
            p.setStyle(Paint.Style.FILL);
            drawFancyText(cv, "LEVEL UP!", w / 2f, h * 0.53f, h * 0.045f, 0xFFFFE082, 0xFFFF8F00);
            p.setColor(0xFFFFFFFF); p.setTextSize(h * 0.022f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("Reward: +" + levelUpReward + " coins", w / 2f, h * 0.575f, p);
            cv.restore();
            if (t >= 1f)
                drawPillButton(cv, w * 0.3f, h * 0.62f, w * 0.4f, h * 0.052f, "COLLECT", 0xFF8E24AA, 0xFF4A148C, true, pressedBtn == 9, 0);
        }

        if (statsOpen) drawStatsPanel(cv, w, h);

        if (animating) postInvalidateDelayed(32);
    }

    /** Draws the card-back face for a cosmetic variant into the given rect. */
    // On screens shorter than ~2.08:1 the w-sized cards grow relative to h, so shrink the
    // multi-hand stack proportionally and chain the rows: minis -> primary -> message.
    private float fit(int w, int h) { return Math.min(1f, (h / (float) w) / 2.077f); }
    private float primaryCh(int w, int h) { return w * 0.165f * primaryScale(w, h) * 1.42f; }
    private float primaryY0(int w, int h) {
        if (numHands == 1) return h * 0.358f;
        float mch = miniCardH(w, h);
        if (numHands == 3) {
            float y2 = h * 0.330f + mch + h * 0.006f;
            return y2 + mch + h * 0.018f;
        }
        float y2 = h * 0.336f + mch + h * 0.018f;
        return y2 + mch + h * 0.032f;
    }
    private float msgY(int w, int h) {
        return numHands == 1 ? h * 0.598f : primaryY0(w, h) + primaryCh(w, h) + h * 0.024f;
    }
    private float primaryScale(int w, int h) {
        if (numHands == 1) return 1f;
        return (numHands == 3 ? 0.92f : 0.86f) * fit(w, h);
    }
    private float miniScale(int w, int h) { return (numHands == 3 ? 0.45f : 0.30f) * fit(w, h); }
    private float miniCardH(int w, int h) { return w * 0.165f * miniScale(w, h) * 1.42f; }
    // extra-hand slot: x3 stacks two rows above the primary hand with right-side labels;
    // x5 uses a 2x2 grid with labels under each mini hand.
    private float miniX(int k, int w, int h) {
        float ms = miniScale(w, h);
        float handW = 5 * w * 0.165f * ms + 4 * w * 0.028f * ms;
        if (numHands == 3) return (w - handW) / 2f - w * 0.055f;
        return (k % 2 == 0) ? w * 0.204f : w * 0.204f + handW + w * 0.03f;
    }
    private float miniY(int k, int w, int h) {
        float mch = miniCardH(w, h);
        if (numHands == 3) return h * 0.330f + k * (mch + h * 0.006f);
        return h * 0.336f + (k / 2) * (mch + h * 0.018f);
    }

    private void drawBackFace(Canvas cv, float dx, float y, float dw, float h, float rad, int variant) {
        int[] st = BACK_STYLE[Math.max(0, Math.min(variant, BACK_STYLE.length - 1))];
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, y, 0, y + h, st[0], st[1], Shader.TileMode.CLAMP));
        rf.set(dx, y, dx + dw, y + h);
        cv.drawRoundRect(rf, rad, rad, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, dw * 0.022f)); p.setColor(st[2]);
        rf.set(dx + dw * 0.07f, y + h * 0.045f, dx + dw * 0.93f, y + h * 0.955f);
        cv.drawRoundRect(rf, rad * 0.7f, rad * 0.7f, p);
        p.setStrokeWidth(Math.max(1f, dw * 0.012f)); p.setColor(st[3]);
        float step = dw * 0.18f;
        cv.save();
        cv.clipRect(dx + dw * 0.08f, y + h * 0.05f, dx + dw * 0.92f, y + h * 0.95f);
        for (float i = -h; i < h + dw; i += step) {
            cv.drawLine(dx + dw * 0.07f + i, y + h * 0.045f, dx + dw * 0.07f + i + h * 0.91f * 0.5f, y + h * 0.955f, p);
            cv.drawLine(dx + dw * 0.07f + i, y + h * 0.955f, dx + dw * 0.07f + i + h * 0.91f * 0.5f, y + h * 0.045f, p);
        }
        cv.restore();
        p.setStyle(Paint.Style.FILL);
    }

    /** Swatch rect (in panel coordinates) for a cosmetic variant; shared by draw and touch. */
    private void swatchRect(int w, int h, int v, RectF out) {
        float pw = w * 0.80f, ph = h * 0.56f, px = (w - pw) / 2f, py = h * 0.20f;
        float swH = ph * 0.115f, swW = swH / 1.42f, gap = pw * 0.03f;
        float sx0 = px + pw / 2f - (BACK_STYLE.length * swW + (BACK_STYLE.length - 1) * gap) / 2f;
        out.set(sx0 + v * (swW + gap), py + ph * 0.830f, sx0 + v * (swW + gap) + swW, py + ph * 0.830f + swH);
    }

    private void drawStatsPanel(Canvas cv, int w, int h) {
        p.setStyle(Paint.Style.FILL); p.setColor(0x90000818);
        cv.drawRect(0, 0, w, h, p);
        float pw = w * 0.80f, ph = h * 0.56f, px = (w - pw) / 2f, py = h * 0.20f;
        rf.set(px + 4, py + 6, px + pw + 4, py + ph + 6);
        p.setColor(0x73000000);
        cv.drawRoundRect(rf, pw * 0.06f, pw * 0.06f, p);
        rf.set(px, py, px + pw, py + ph);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, py, 0, py + ph, 0xF21E2A4E, 0xF2070F28, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, pw * 0.06f, pw * 0.06f, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, py, 0, py + ph * 0.4f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rf.set(px + pw * 0.03f, py + ph * 0.03f, px + pw * 0.97f, py + ph * 0.38f);
        cv.drawRoundRect(rf, pw * 0.05f, pw * 0.05f, p);
        p.setAlpha(0xFF); p.setShader(null);
        rf.set(px, py, px + pw, py + ph);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(0xCCFFD700);
        cv.drawRoundRect(rf, pw * 0.06f, pw * 0.06f, p);
        p.setStrokeWidth(1.5f); p.setColor(0x55FFF3C4);
        rf.inset(4f, 4f);
        cv.drawRoundRect(rf, pw * 0.05f, pw * 0.05f, p);
        p.setStyle(Paint.Style.FILL);
        drawFancyText(cv, "LIFETIME STATS", w / 2f, py + ph * 0.14f, h * 0.024f, 0xFFFFE082, 0xFFFF8F00);
        String[][] rows = {
                {"Hands played", String.valueOf(stHands)},
                {"Winning hands", String.valueOf(stWins)},
                {"Win rate", stHands > 0 ? String.format(java.util.Locale.US, "%.1f%%", 100f * stWins / stHands) : "-"},
                {"Biggest win", stBiggest > 0 ? "+" + stBiggest : "-"},
                {"Best hand", stBestHand >= 0 ? PAY_NAMES[stBestHand] : "-"},
                {"Current streak", String.valueOf(stStreak)},
                {"Best streak", String.valueOf(stBestStreak)},
        };
        float ry = py + ph * 0.205f, rstep = ph * 0.086f;
        for (int i = 0; i < rows.length; i++) {
            if (i % 2 == 0) {
                rf.set(px + pw * 0.05f, ry - rstep * 0.62f, px + pw * 0.95f, ry + rstep * 0.38f);
                p.setColor(0x14FFFFFF);
                cv.drawRoundRect(rf, rstep * 0.2f, rstep * 0.2f, p);
            }
            p.setColor(0xFFB9C8E8); p.setTextSize(h * 0.017f); p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(false);
            cv.drawText(rows[i][0], px + pw * 0.09f, ry, p);
            p.setColor(0xFFFFE082); p.setTextAlign(Paint.Align.RIGHT); p.setFakeBoldText(true);
            cv.drawText(rows[i][1], px + pw * 0.91f, ry, p);
            p.setFakeBoldText(false);
            ry += rstep;
        }
        // cosmetic card-back picker
        p.setColor(0xFFB9C8E8); p.setTextSize(h * 0.015f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText("CARD BACK", w / 2f, py + ph * 0.795f, p);
        p.setFakeBoldText(false);
        RectF sw = new RectF();
        for (int v = 0; v < BACK_STYLE.length; v++) {
            swatchRect(w, h, v, sw);
            boolean unlocked = level >= BACK_UNLOCK_LEVEL[v];
            drawBackFace(cv, sw.left, sw.top, sw.width(), sw.height(), sw.width() * 0.10f, v);
            if (!unlocked) {
                p.setColor(0xB0000812);
                cv.drawRoundRect(sw, sw.width() * 0.10f, sw.width() * 0.10f, p);
                p.setColor(0xFFC9D4EA); p.setTextSize(sw.height() * 0.24f); p.setFakeBoldText(true);
                cv.drawText("Lv " + BACK_UNLOCK_LEVEL[v], sw.centerX(), sw.centerY() + sw.height() * 0.08f, p);
                p.setFakeBoldText(false);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(v == cosBack ? 4f : 1.5f);
            p.setColor(v == cosBack ? 0xFFFFD700 : 0x44FFFFFF);
            cv.drawRoundRect(sw, sw.width() * 0.10f, sw.width() * 0.10f, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0x66FFFFFF); p.setTextSize(h * 0.013f); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText("tap a back to apply - tap elsewhere to close", w / 2f, py + ph * 0.985f, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth(), h = getHeight();
        float x = e.getX(), y = e.getY();
        if (Math.hypot(x - w * 0.93f, y - getHeight() * 0.042f) <= w * 0.06f) {
            GameSounds.toggle(getContext());
            invalidate();
            return true;
        }
        if (Math.hypot(x - w * 0.80f, y - getHeight() * 0.042f) <= w * 0.06f) {
            if (e.getAction() == MotionEvent.ACTION_UP) { statsOpen = !statsOpen; invalidate(); }
            return true;
        }
        if (statsOpen) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                RectF sw = new RectF();
                boolean consumed = false;
                for (int v = 0; v < BACK_STYLE.length; v++) {
                    swatchRect(w, h, v, sw);
                    if (sw.contains(x, y)) {
                        consumed = true;
                        if (level >= BACK_UNLOCK_LEVEL[v]) {
                            cosBack = v;
                            getContext().getSharedPreferences("videopoker", Context.MODE_PRIVATE)
                                    .edit().putInt("cos_back", v).apply();
                        }
                        break;
                    }
                }
                if (!consumed) statsOpen = false;
                invalidate();
            }
            return true;
        }

        float bh = h * 0.056f;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            lastTouchAt = System.currentTimeMillis();
            pressedBtn = hitButton(w, h, bh, x, y);
            if (pressedBtn >= 0) invalidate();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int btn = hitButton(w, h, bh, x, y);
            int was = pressedBtn; pressedBtn = -1; invalidate();
            if (levelUpShowing) {
                if (btn == 9 && was == 9) { levelUpShowing = false; levelUpPending = 0; levelUpReward = 0; invalidate(); }
                return true;
            }
            if (phase == 1) {
                // card taps toggle hold
                float pScale = primaryScale(w, h);
                float cw = w * 0.165f * pScale, ch = cw * 1.42f, gap = w * 0.028f * pScale;
                float total = 5 * cw + 4 * gap;
                float x0 = (w - total) / 2f, y0 = primaryY0(w, h);
                if (y >= y0 - h * 0.045f && y <= y0 + ch) {
                    for (int i = 0; i < hand.size(); i++) {
                        if (x >= x0 + i * (cw + gap) && x <= x0 + i * (cw + gap) + cw) {
                            held[i] = !held[i]; cardNew[i] = false; suggestVisible = false;
                            heldFlash = i; heldFlashAt = System.currentTimeMillis();
                            invalidate();
                            return true;
                        }
                    }
                }
                if (btn == 1 && was == 1) draw();
                return true;
            }
            if (btn == 0 && was == 0) {
                if (balance >= BETS[betIdx] * numHands) { deal(); return true; }
                message = "Not enough coins - lower bet or hands";
                invalidate();
                return true;
            }
            if ((phase == 0 || phase == 2) && btn < 0) {
                if (selectHandsAt(w, h, x, y)) return true;
                selectChipAt(w, h, x, y);
            }
            return true;
        }
        return true;
    }

    private int hitButton(int w, int h, float bh, float x, float y) {
        if (levelUpShowing) {
            if (x >= w * 0.3f && x <= w * 0.7f && y >= h * 0.62f && y <= h * 0.62f + h * 0.052f) return 9;
            return -1;
        }
        if (phase == 0 || phase == 2) {
            if (x >= w * 0.14f && x <= w * 0.86f && y >= h * 0.785f && y <= h * 0.785f + bh) return 0;
        } else if (phase == 1) {
            if (x >= w * 0.14f && x <= w * 0.86f && y >= h * 0.765f && y <= h * 0.765f + bh) return 1;
        }
        return -1;
    }


    private void maxBet() {
        for (int i = BETS.length - 1; i >= 0; i--) {
            if (balance >= BETS[i]) {
                if (i != betIdx) {
                    betIdx = i;
                    GameSounds.play(getContext(), GameSounds.CHIP);
                    betBumpAt = System.currentTimeMillis();
                }
                invalidate();
                if (listener != null) listener.saveState();
                return;
            }
        }
    }

    private boolean selectHandsAt(int w, int h, float x, float y) {
        float pw2 = w * 0.10f, ph2 = h * 0.024f, pgap = w * 0.02f;
        int[] opts = {1, 3, 5};
        float px0 = w * 0.30f;
        if (y < h * 0.646f - ph2 || y > h * 0.646f + ph2) return false;
        for (int o = 0; o < opts.length; o++) {
            float lx = px0 + o * (pw2 + pgap);
            if (x >= lx && x <= lx + pw2 && opts[o] != numHands) {
                numHands = opts[o];
                GameSounds.play(getContext(), GameSounds.CHIP);
                invalidate();
                if (listener != null) listener.saveState();
                return true;
            }
        }
        return false;
    }

    private void selectChipAt(int w, int h, float x, float y) {
        float chipR = w * 0.060f, chipGap = w * 0.040f;
        float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
        float cx0 = w / 2f - rowW / 2f + chipR;
        float mx = w / 2f + rowW / 2f + chipGap * 0.6f + chipR * 0.8f;
        if (Math.hypot(x - mx, y - h * 0.710f) <= chipR * 1.2f) { maxBet(); return; }
        for (int i = 0; i < BETS.length; i++) {
            float cx = cx0 + i * (chipR * 2 + chipGap);
            float ddx = x - cx, ddy = y - h * 0.710f;
            if (ddx * ddx + ddy * ddy <= chipR * chipR * 1.44f && i != betIdx) {
                betIdx = i;
                invalidate();
                if (listener != null) listener.saveState();
                return;
            }
        }
    }
}
