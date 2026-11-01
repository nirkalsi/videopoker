package nir.com.videopoker;

import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for the simplified Jacks-or-Better hold suggestion.
 *  Card encoding: card = suit*13 + rank, rank 0=A, 1=2 ... 9=T, 10=J, 11=Q, 12=K. */
@RunWith(RobolectricTestRunner.class)
public class PokerStrategyTest {

    private static List<Integer> hand(int... cards) {
        List<Integer> h = new ArrayList<>();
        for (int c : cards) h.add(c);
        return h;
    }

    private static boolean[] holds(boolean... b) {
        boolean[] r = new boolean[5];
        System.arraycopy(b, 0, r, 0, 5);
        return r;
    }

    @Test public void patFlushHoldsAll() {
        // A,3,5,8,T all diamonds: flush, no straight
        assertArrayEquals(holds(true, true, true, true, true),
                PokerView.basicStrategyHolds(hand(13, 15, 17, 20, 22)));
    }

    @Test public void tripsHoldThree() {
        // 7c 7d 7h 2s 9c
        assertArrayEquals(holds(true, true, true, false, false),
                PokerView.basicStrategyHolds(hand(6, 19, 32, 40, 8)));
    }

    @Test public void twoPairHoldsFour() {
        // Jd Jh 4s 4d 9c
        assertArrayEquals(holds(true, true, true, true, false),
                PokerView.basicStrategyHolds(hand(23, 36, 42, 16, 8)));
    }

    @Test public void highPairBeatsFlushDraw() {
        // Jc Jd 5c 6c 7c: high pair AND 4 to a flush; pair wins in JoB
        assertArrayEquals(holds(true, true, false, false, false),
                PokerView.basicStrategyHolds(hand(10, 23, 4, 5, 6)));
    }

    @Test public void royalDraw4BeatsHighPair() {
        // Ah Kh Qh Jh Jd: 4 to a royal beats the high pair
        assertArrayEquals(holds(true, true, true, true, false),
                PokerView.basicStrategyHolds(hand(26, 38, 37, 36, 23)));
    }

    @Test public void outsideStraightDraw() {
        // 5c 6d 7h 8s Kd
        assertArrayEquals(holds(true, true, true, true, false),
                PokerView.basicStrategyHolds(hand(4, 18, 32, 46, 25)));
    }

    @Test public void lowPairBeatsUnsuitedHighs() {
        // 4c 4d Ah Kh 8c (low pair beats two suited high cards)
        assertArrayEquals(holds(true, true, false, false, false),
                PokerView.basicStrategyHolds(hand(3, 16, 26, 38, 7)));
    }

    @Test public void suitedHighTwo() {
        // Ah Kh 6c 5d 9s
        assertArrayEquals(holds(true, true, false, false, false),
                PokerView.basicStrategyHolds(hand(26, 38, 5, 17, 47)));
    }

    @Test public void singleHighCard() {
        // Kh 6c 5d 2h 9s
        assertArrayEquals(holds(true, false, false, false, false),
                PokerView.basicStrategyHolds(hand(38, 5, 17, 27, 47)));
    }

    @Test public void garbageDrawsFive() {
        // 3c 5d 4h 8s 9c: no pair, no high, no 4-draw
        assertArrayEquals(holds(false, false, false, false, false),
                PokerView.basicStrategyHolds(hand(2, 17, 29, 46, 8)));
    }

    @Test public void straightFlushDraw4() {
        // 8h 9h Th Jh 2c: 4 to a straight flush
        assertArrayEquals(holds(true, true, true, true, false),
                PokerView.basicStrategyHolds(hand(33, 34, 35, 36, 2)));
    }

    @Test public void insideStraightDraw() {
        // 3c 5d 6h 7s Kc: inside straight draw (3,5,6,7) + unsuited K
        assertArrayEquals(holds(true, true, true, true, false),
                PokerView.basicStrategyHolds(hand(2, 17, 31, 45, 12)));
    }
}
