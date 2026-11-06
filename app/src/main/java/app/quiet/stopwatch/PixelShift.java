package app.quiet.stopwatch;

public final class PixelShift {
    public static final long INTERVAL_MS = 60000;
    public static final int MAX_X_DP = 9;
    public static final int MAX_Y_DP = 24;

    private static int position(long now) {
        int phase = (int) (Math.max(0, now) / INTERVAL_MS % 96);
        return phase <= 48 ? phase : 96 - phase;
    }

    public static int xDp(long now) {
        int position = position(now);
        int row = position / 7;
        int column = position % 7;
        if (row % 2 != 0) column = 6 - column;
        return (column - 3) * 1;
    }

    public static int yDp(long now) {
        return (position(now) / 7 - 3) * 1;
    }

    public static long nextShiftDelay(long now) {
        return INTERVAL_MS - now % INTERVAL_MS;
    }
}
