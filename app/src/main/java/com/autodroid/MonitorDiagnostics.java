package com.autodroid;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

/** Independent probes: never query a provider or acquire Store's logging lock here. */
final class MonitorDiagnostics implements AutoCloseable {
    private final HandlerThread thread = new HandlerThread("inbox-diagnostics");
    private final Handler handler;
    private final Handler main, worker;
    private volatile boolean closed;
    private volatile long mainAck = SystemClock.elapsedRealtime(), workerAck = mainAck;
    private volatile String phase = "starting";
    private long elapsed = mainAck, uptime = SystemClock.uptimeMillis();
    private volatile boolean mainPending, workerPending;

    MonitorDiagnostics(Handler main, Handler worker) {
        this.main = main;
        this.worker = worker;
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.postDelayed(this::tick, 15000);
    }

    void phase(String value) {
        phase = value;
    }

    private void tick() {
        if (closed) return;
        long now = SystemClock.elapsedRealtime(), up = SystemClock.uptimeMillis();
        Log.i(
                "AutoDroid",
                "INBOX_WATCHDOG elapsedGapMs="
                        + (now - elapsed)
                        + " uptimeGapMs="
                        + (up - uptime)
                        + " mainAckAgeMs="
                        + (now - mainAck)
                        + " workerAckAgeMs="
                        + (now - workerAck)
                        + " phase="
                        + phase
                        + " workerState="
                        + worker.getLooper().getThread().getState());
        if (now - workerAck > 45000) stack("worker", worker.getLooper().getThread());
        if (now - mainAck > 45000) stack("main", main.getLooper().getThread());
        elapsed = now;
        uptime = up;
        // At most one queued probe per thread even during a long stall.
        if (!mainPending) {
            mainPending = true;
            main.post(
                    () -> {
                        mainAck = SystemClock.elapsedRealtime();
                        mainPending = false;
                    });
        }
        if (!workerPending) {
            workerPending = true;
            worker.post(
                    () -> {
                        workerAck = SystemClock.elapsedRealtime();
                        workerPending = false;
                    });
        }
        handler.postDelayed(this::tick, 15000);
    }

    private void stack(String name, Thread target) {
        StackTraceElement[] frames = target.getStackTrace();
        for (int i = 0; i < Math.min(frames.length, 12); i++)
            Log.i("AutoDroid", "INBOX_THREAD_STACK thread=" + name + " at " + frames[i]);
    }

    @Override
    public void close() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        thread.quitSafely();
    }
}
