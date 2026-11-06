package app.quiet.stopwatch;

public final class Stopwatch {
    private long accumulatedMs;
    private long startedAtMs;
    private boolean running;

    public boolean isRunning() { return running; }
    public long accumulatedMs() { return accumulatedMs; }
    public long startedAtMs() { return startedAtMs; }

    public long elapsedMs(long now) {
        return accumulatedMs + (running ? Math.max(0, now - startedAtMs) : 0);
    }

    public void resume(long now) {
        if (!running) {
            startedAtMs = now;
            running = true;
        }
    }

    public void pause(long now) {
        accumulatedMs = elapsedMs(now);
        running = false;
    }

    public void reset() {
        accumulatedMs = 0;
        startedAtMs = 0;
        running = false;
    }

    public void restore(long accumulated, long start, boolean wasRunning,
                        long savedBoot, long currentBoot, long checkpoint, long now) {
        boolean sameBoot = savedBoot >= 0 && currentBoot == savedBoot && now >= start;
        accumulatedMs = Math.max(0, sameBoot ? accumulated : checkpoint);
        startedAtMs = Math.max(0, start);
        running = sameBoot && wasRunning;
    }

    public static String format(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return twoDigits(seconds / 3600) + ":" + twoDigits(seconds / 60 % 60)
                + ":" + twoDigits(seconds % 60);
    }

    private static String twoDigits(long number) {
        return number < 10 ? "0" + number : Long.toString(number);
    }
}
