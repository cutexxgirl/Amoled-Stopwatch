package app.quiet.stopwatch;

public final class TapController {
    public enum Action { NONE, PAUSE, RESUME, RESET }
    private final long doubleTapTimeout;
    private final float doubleTapSlopSquared;
    private boolean pendingResume;
    private long deadline;
    private long ignoreThrough = Long.MIN_VALUE;
    private float firstX, firstY;

    public TapController(long doubleTapTimeout, float doubleTapSlop) {
        this.doubleTapTimeout = doubleTapTimeout;
        this.doubleTapSlopSquared = doubleTapSlop * doubleTapSlop;
    }

    public boolean hasPendingResume() { return pendingResume; }
    public long deadline() { return deadline; }

    public Action tap(boolean running, long now, float x, float y) {
        if (now <= ignoreThrough) return Action.NONE;
        if (running) {
            pendingResume = false;
            ignoreThrough = now + doubleTapTimeout;
            return Action.PAUSE;
        }
        if (pendingResume) {
            float dx = x - firstX, dy = y - firstY;
            if (now <= deadline && dx * dx + dy * dy <= doubleTapSlopSquared) {
                cancel();
                return Action.RESET;
            }
            cancel();
            ignoreThrough = now + doubleTapTimeout;
            return Action.RESUME;
        }
        firstX = x;
        firstY = y;
        pendingResume = true;
        deadline = now + doubleTapTimeout;
        return Action.NONE;
    }

    public Action confirm(long now) {
        if (pendingResume && now >= deadline) {
            pendingResume = false;
            return Action.RESUME;
        }
        return Action.NONE;
    }

    public void cancel() {
        pendingResume = false;
        ignoreThrough = Long.MIN_VALUE;
    }
}
