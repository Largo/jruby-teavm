package org.teavm.classlib.java.util.concurrent.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.teavm.classlib.java.lang.TCompatParker;

/**
 * compat: java.util.concurrent.locks.ReentrantLock for TeaVM (missing in 0.16.0).
 *
 * TeaVM threads are green threads on the single JS thread and only switch at suspension points, so the
 * owner check needs no atomics. A contended lock() parks in a {@link TCompatParker} until unlock().
 */
public class TReentrantLock implements TLock {
    private Thread owner;
    private int holds;
    private final TCompatParker queue = new TCompatParker();

    public TReentrantLock() {
    }

    public TReentrantLock(boolean fair) {
    }

    @Override
    public void lock() {
        Thread current = Thread.currentThread();
        if (owner == current) {
            holds++;
            return;
        }
        boolean interrupted = false;
        while (owner != null) {
            try {
                queue.await(0);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        owner = current;
        holds = 1;
        if (interrupted) current.interrupt();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        Thread current = Thread.currentThread();
        if (owner == current) {
            holds++;
            return;
        }
        if (Thread.interrupted()) throw new InterruptedException();
        while (owner != null) queue.await(0);
        owner = current;
        holds = 1;
    }

    @Override
    public boolean tryLock() {
        Thread current = Thread.currentThread();
        if (owner == null || owner == current) {
            owner = current;
            holds++;
            return true;
        }
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        if (tryLock()) return true;
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        while (owner != null) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return false;
            queue.await(left);
        }
        return tryLock();
    }

    @Override
    public void unlock() {
        if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
        if (--holds == 0) {
            owner = null;
            queue.signalOne();
        }
    }

    /** Fully release (for Condition.await) and return the hold count to restore. */
    int releaseAll() {
        if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
        int saved = holds;
        holds = 0;
        owner = null;
        queue.signalOne();
        return saved;
    }

    void reacquire(int saved) {
        lock();
        holds = saved;
    }

    @Override
    public TCondition newCondition() {
        return new SimpleCondition(this);
    }

    public int getHoldCount() {
        return owner == Thread.currentThread() ? holds : 0;
    }

    public boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    public boolean isLocked() {
        return owner != null;
    }

    public final boolean isFair() {
        return false;
    }

    public final boolean hasQueuedThreads() {
        return queue.size() > 0;
    }

    public final boolean hasQueuedThread(Thread thread) {
        return queue.size() > 0;
    }

    public final int getQueueLength() {
        return queue.size();
    }

    public int getWaitQueueLength(TCondition condition) {
        return condition instanceof SimpleCondition c ? c.waiters.size() : 0;
    }

    public boolean hasWaiters(TCondition condition) {
        return getWaitQueueLength(condition) > 0;
    }

    protected Thread getOwner() {
        return owner;
    }

    @Override
    public String toString() {
        return super.toString() + (owner == null ? "[Unlocked]" : "[Locked by thread " + owner.getName() + "]");
    }

    static final class SimpleCondition implements TCondition {
        private final TReentrantLock lock;
        final TCompatParker waiters = new TCompatParker();

        SimpleCondition(TReentrantLock lock) {
            this.lock = lock;
        }

        private boolean doWait(long millis) throws InterruptedException {
            if (Thread.interrupted()) throw new InterruptedException();
            int saved = lock.releaseAll();
            try {
                return waiters.await(millis);
            } finally {
                lock.reacquire(saved);
            }
        }

        @Override
        public void await() throws InterruptedException {
            doWait(0);
        }

        @Override
        public void awaitUninterruptibly() {
            int saved = lock.releaseAll();
            try {
                try {
                    waiters.await(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // keep the status, return like a spurious wake-up
                }
            } finally {
                lock.reacquire(saved);
            }
        }

        @Override
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            if (nanosTimeout <= 0) return 0;
            long start = System.nanoTime();
            doWait(Math.max(1, nanosTimeout / 1000000));
            return nanosTimeout - (System.nanoTime() - start);
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            if (time <= 0) return false;
            return doWait(Math.max(1, unit.toMillis(time)));
        }

        @Override
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            long left = deadline.getTime() - System.currentTimeMillis();
            if (left <= 0) return false;
            return doWait(left);
        }

        @Override
        public void signal() {
            waiters.signalOne();
        }

        @Override
        public void signalAll() {
            waiters.signalAll();
        }
    }
}
