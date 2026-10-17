package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.TimeUnit;

import org.teavm.classlib.java.lang.TCompatParker;

/** compat: java.util.concurrent.Semaphore for TeaVM green threads (parks in a TCompatParker; see TReentrantLock). */
public class TSemaphore {
    private int permits;
    private final TCompatParker queue = new TCompatParker();

    public TSemaphore(int permits) {
        this.permits = permits;
    }

    public TSemaphore(int permits, boolean fair) {
        this.permits = permits;
    }

    public void acquire() throws InterruptedException {
        acquire(1);
    }

    public void acquire(int n) throws InterruptedException {
        while (permits < n) queue.await(0);
        permits -= n;
    }

    public void acquireUninterruptibly() {
        acquireUninterruptibly(1);
    }

    public void acquireUninterruptibly(int n) {
        boolean interrupted = false;
        while (permits < n) {
            try {
                queue.await(0);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        permits -= n;
        if (interrupted) Thread.currentThread().interrupt();
    }

    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(int n) {
        if (permits >= n) {
            permits -= n;
            return true;
        }
        return false;
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return tryAcquire(1, timeout, unit);
    }

    public boolean tryAcquire(int n, long timeout, TimeUnit unit) throws InterruptedException {
        long ms = unit.toMillis(timeout);
        if (ms >= Integer.MAX_VALUE) { // "forever" (e.g. JRuby's sleep uses Long.MAX_VALUE ms)
            acquire(n);
            return true;
        }
        long deadline = System.currentTimeMillis() + ms;
        while (permits < n) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return false;
            queue.await(left);
        }
        permits -= n;
        return true;
    }

    public void release() {
        release(1);
    }

    public void release(int n) {
        permits += n;
        queue.signalAll();
    }

    public int availablePermits() {
        return permits;
    }

    public int drainPermits() {
        int p = permits;
        permits = 0;
        return p;
    }
}
