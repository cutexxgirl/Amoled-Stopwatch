package app.quiet.stopwatch;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

public final class MainActivity extends Activity {
    private final Stopwatch stopwatch = new Stopwatch();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TimerView timerView;
    private SharedPreferences preferences;
    private TapController taps;
    private boolean visible;
    private long boot;
    private long resumeTapAt;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            long now = SystemClock.elapsedRealtime();
            long elapsed = stopwatch.elapsedMs(now);
            timerView.showTime(elapsed, now);
            if (stopwatch.isRunning()) {
                handler.postDelayed(this, Math.min(1000 - elapsed % 1000,
                        PixelShift.nextShiftDelay(now)));
            } else {
                handler.postDelayed(this, PixelShift.nextShiftDelay(now));
            }
        }
    };

    private final Runnable confirmTap = new Runnable() {
        @Override public void run() {
            apply(taps.confirm(SystemClock.uptimeMillis()));
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        preferences = getSharedPreferences("stopwatch", MODE_PRIVATE);
        boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        stopwatch.restore(preferences.getLong("accumulated", 0),
                preferences.getLong("started", 0), preferences.getBoolean("running", false),
                preferences.getLong("boot", -1), boot,
                preferences.getLong("checkpoint", 0), SystemClock.elapsedRealtime());
        taps = new TapController(ViewConfiguration.getDoubleTapTimeout(),
                ViewConfiguration.get(this).getScaledDoubleTapSlop());
        timerView = new TimerView();
        setContentView(timerView);
        hideSystemBars();
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        hideSystemBars();
        refresh();
    }

    @Override protected void onPause() {
        if (taps.hasPendingResume()) {
            taps.cancel();
            stopwatch.resume(resumeTapAt);
        }
        visible = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(confirmTap);
        save();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void onDigitsTap(float x, float y) {
        if (!stopwatch.isRunning() && !taps.hasPendingResume()) {
            resumeTapAt = SystemClock.elapsedRealtime();
        }
        apply(taps.tap(stopwatch.isRunning(), SystemClock.uptimeMillis(), x, y));
        handler.removeCallbacks(confirmTap);
        if (taps.hasPendingResume()) {
            handler.postAtTime(confirmTap, taps.deadline());
        }
    }

    private void apply(TapController.Action action) {
        long now = SystemClock.elapsedRealtime();
        switch (action) {
            case PAUSE: stopwatch.pause(now); break;
            case RESUME: stopwatch.resume(resumeTapAt); break;
            case RESET: stopwatch.reset(); break;
            default: return;
        }
        save();
        refresh();
    }

    private void refresh() {
        handler.removeCallbacks(tick);
        if (visible) tick.run();
    }

    private void save() {
        preferences.edit().putLong("accumulated", stopwatch.accumulatedMs())
                .putLong("started", stopwatch.startedAtMs())
                .putBoolean("running", stopwatch.isRunning()).putLong("boot", boot)
                .putLong("checkpoint", stopwatch.elapsedMs(SystemClock.elapsedRealtime())).apply();
    }

    private final class TimerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF target = new RectF();
        private final Rect ink = new Rect();
        private String text = "00:00:00";
        private int characterCount = 8;
        private float baseline, downX, downY, digitWidth, colonWidth, lineWidth;
        private float shiftX, shiftY;
        private long downTime;
        private boolean tracking;
        private final float density = getResources().getDisplayMetrics().density;
        private final int touchSlop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();

        TimerView() {
            super(MainActivity.this);
            setBackgroundColor(Color.BLACK);
            setFocusable(true);
            setClickable(true);
            setSoundEffectsEnabled(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            paint.setColor(Color.WHITE);
            paint.setTypeface(getResources().getFont(R.font.montserrat));
            paint.setFontVariationSettings("'wght' 700");
            paint.setTextAlign(Paint.Align.CENTER);
        }

        void showTime(long milliseconds, long now) {
            String next = Stopwatch.format(milliseconds);
            if (next.length() != characterCount) {
                characterCount = next.length();
                fitText();
            }
            if (!next.equals(text)) {
                text = next;
                invalidate();
            }
            if (!tracking) {
                float nextX = PixelShift.xDp(now) * density;
                float nextY = PixelShift.yDp(now) * density;
                if (shiftX != nextX || shiftY != nextY) {
                    shiftX = nextX;
                    shiftY = nextY;
                    updateTarget();
                    invalidate();
                }
            }
            setContentDescription(text);
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { fitText(); }

        private void fitText() {
            if (getWidth() == 0 || getHeight() == 0) return;
            paint.setTextSize(100);
            measureCells();
            float width = Math.max(1, getWidth() - 2 * (24 + PixelShift.MAX_X_DP) * density);
            float height = Math.max(1, getHeight() - 2 * (24 + PixelShift.MAX_Y_DP) * density);
            paint.getTextBounds("0123456789", 0, 10, ink);
            float size = Math.min(100 * width / lineWidth, 100 * height / ink.height());
            paint.setTextSize(size);
            measureCells();
            paint.getTextBounds("0123456789", 0, 10, ink);
            baseline = getHeight() / 2f - (ink.top + ink.bottom) / 2f;
            updateTarget();
            invalidate();
        }

        private void measureCells() {
            digitWidth = 0;
            for (int i = 0; i < 10; i++) {
                digitWidth = Math.max(digitWidth, paint.measureText(Integer.toString(i)));
            }
            digitWidth *= 1.025f;
            colonWidth = Math.max(paint.measureText(":"), digitWidth * 0.38f);
            lineWidth = (characterCount - 2) * digitWidth + 2 * colonWidth;
        }

        private void updateTarget() {
            float centerX = getWidth() / 2f + shiftX;
            target.set(centerX - lineWidth / 2f - 12 * density,
                    baseline + shiftY + ink.top - 24 * density,
                    centerX + lineWidth / 2f + 12 * density,
                    baseline + shiftY + ink.bottom + 24 * density);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = getWidth() / 2f + shiftX - lineWidth / 2f;
            for (int i = 0; i < text.length(); i++) {
                float cell = text.charAt(i) == ':' ? colonWidth : digitWidth;
                canvas.drawText(text, i, i + 1, left + cell / 2f, baseline + shiftY, paint);
                left += cell;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tracking = target.contains(x, y);
                    downX = x; downY = y; downTime = event.getEventTime();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    tracking = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) tracking = false;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (tracking && target.contains(x, y)
                            && event.getEventTime() - downTime < ViewConfiguration.getLongPressTimeout()) {
                        tracking = false;
                        onDigitsTap(x, y);
                        super.performClick();
                    }
                    tracking = false;
                    return true;
                default: return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            onDigitsTap(getWidth() / 2f, getHeight() / 2f);
            return true;
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.Button");
            if (Build.VERSION.SDK_INT >= 30) {
                info.setStateDescription(stopwatch.isRunning() ? "Идёт отсчёт" : "Пауза");
            }
        }
    }
}
