package app.quiet.stopwatch;

public final class StopwatchTest {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        Stopwatch s = new Stopwatch();
        eq("00:00:00", Stopwatch.format(s.elapsedMs(10000)));
        s.resume(10000);
        eq(999L, s.elapsedMs(10999));
        eq("00:00:00", Stopwatch.format(s.elapsedMs(10999)));
        eq("00:00:01", Stopwatch.format(s.elapsedMs(11000)));
        eq("00:01:00", Stopwatch.format(s.elapsedMs(70000)));
        eq("01:00:00", Stopwatch.format(s.elapsedMs(3610000)));
        eq("03:00:00", Stopwatch.format(s.elapsedMs(10810000)));
        s.pause(10810321);
        eq(10800321L, s.elapsedMs(99999999));
        s.resume(20000000);
        s.resume(20001000);
        eq(10801321L, s.elapsedMs(20001000));
        s.pause(20001000);
        eq(10801321L, s.elapsedMs(20050000));
        s.reset();
        eq(0L, s.elapsedMs(999999999));
        eq(false, s.isRunning());
        s.restore(2500, 10000, true, 7, 7, 3500, 20000);
        eq(12500L, s.elapsedMs(20000));
        eq(true, s.isRunning());
        s.restore(2500, 10000, false, 7, 7, 2500, 20000);
        eq(2500L, s.elapsedMs(20000));
        eq(false, s.isRunning());
        s.restore(2500, 10000, true, 7, 8, 3500, 20000);
        eq(3500L, s.elapsedMs(20000));
        eq(false, s.isRunning());
        s.restore(2500, 10000, true, -1, -1, 3500, 20000);
        eq(false, s.isRunning());
        eq("99:59:59", Stopwatch.format(359999000));
        eq("100:00:00", Stopwatch.format(360000000));
        eq("00:00:00", Stopwatch.format(-1));

        TapController t = new TapController(300, 40);
        eq(TapController.Action.NONE, t.tap(false, 1000, 0, 0));
        eq(true, t.hasPendingResume());
        eq(TapController.Action.NONE, t.confirm(1299));
        eq(TapController.Action.RESUME, t.confirm(1300));
        eq(false, t.hasPendingResume());
        eq(TapController.Action.PAUSE, t.tap(true, 2000, 0, 0));
        eq(TapController.Action.NONE, t.tap(false, 2100, 0, 0));
        eq(false, t.hasPendingResume());
        eq(TapController.Action.NONE, t.tap(false, 3000, 0, 0));
        eq(TapController.Action.RESET, t.tap(false, 3200, 5, 5));
        eq(TapController.Action.NONE, t.confirm(4000));
        eq(TapController.Action.NONE, t.tap(false, 5000, 0, 0));
        eq(TapController.Action.RESUME, t.tap(false, 5200, 100, 100));
        eq(TapController.Action.NONE, t.tap(false, 6000, 0, 0));
        eq(TapController.Action.RESUME, t.tap(false, 6400, 0, 0));
        t.cancel();
        eq(TapController.Action.NONE, t.confirm(9999999));
        for (long elapsed : new long[] {0, 1, 999, 1000, 1001, 10800321}) {
            long delay = 1000 - elapsed % 1000;
            eq(0L, (elapsed + delay) % 1000);
            eq(true, delay >= 1 && delay <= 1000);
        }
        eq(60000L, PixelShift.nextShiftDelay(0));
        eq(1L, PixelShift.nextShiftDelay(59999));
        eq(60000L, PixelShift.nextShiftDelay(60000));
        boolean bounded = true, smallSteps = true, repeats = true;
        java.util.Set<String> positions = new java.util.HashSet<>();
        for (int minute = 0; minute < 96; minute++) {
            long now = minute * 60000L;
            int x = PixelShift.xDp(now), y = PixelShift.yDp(now);
            positions.add(x + ":" + y);
            bounded &= Math.abs(x) <= 3 && Math.abs(y) <= 3;
            smallSteps &= Math.abs(PixelShift.xDp(now + 60000) - x) <= 1
                    && Math.abs(PixelShift.yDp(now + 60000) - y) <= 1;
            repeats &= PixelShift.xDp(now + 96 * 60000L) == x
                    && PixelShift.yDp(now + 96 * 60000L) == y;
        }
        eq(true, bounded);
        eq(true, smallSteps);
        eq(true, repeats);
        eq(49, positions.size());
        System.out.println("PASS: " + checks + " timing, restoration, gestures and pixel-shift checks");
    }
}
