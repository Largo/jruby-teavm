package org.teavm.classlib.java.lang;

import java.util.ArrayDeque;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformRunnable;
import org.teavm.runtime.EventQueue;

/**
 * compat helper: a FIFO of parked TeaVM green threads, used by the compat ReentrantLock/Condition/Semaphore.
 * It is named like a T-class (it becomes "java.lang.CompatParker") so that TeaVM maps its references to
 * TThread onto java.lang.Thread; a plain class would get a separate, unrelated copy of TThread.
 *
 * Written against TeaVM 0.16.0's own primitives because the stock ones are not safe here:
 * Object.wait() does not re-acquire the monitor when the waiter is interrupted, and ArrayBlockingQueue
 * resumes an interrupted waiter synchronously on the interrupting thread's stack. Every wake-up below is
 * postponed to the event loop and runs as the parked thread; an interrupt clears the interrupt flag when it
 * is delivered as InterruptedException, as on the JVM.
 */
public final class TCompatParker {
    private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();

    /** Park until signalled (true), timed out (false; millis &lt;= 0 = no timeout) or interrupted. */
    public boolean await(long millis) throws InterruptedException {
        TThread current = TThread.currentThread();
        if (current.isInterrupted()) {
            TThread.interrupted();
            throw new InterruptedException();
        }
        Waiter waiter = new Waiter(this, current);
        waiters.add(waiter);
        try {
            return park(waiter, millis);
        } catch (InterruptedException e) {
            TThread.interrupted(); // JVM semantics: delivering the exception clears the flag
            throw e;
        } finally {
            waiters.remove(waiter);
        }
    }

    public boolean signalOne() {
        Waiter waiter = waiters.poll();
        if (waiter == null) return false;
        waiter.wake(Boolean.TRUE);
        return true;
    }

    public void signalAll() {
        while (signalOne()) {
            // wake everybody
        }
    }

    public int size() {
        return waiters.size();
    }

    @Async
    private static native Boolean park(Waiter waiter, long millis) throws InterruptedException;

    private static void park(Waiter waiter, long millis, AsyncCallback<Boolean> callback) {
        waiter.callback = callback;
        if (millis > 0) {
            int timeout = millis >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
            waiter.timerId = PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()
                    ? EventQueue.offer(waiter::onTimer, System.currentTimeMillis() + timeout)
                    : Platform.schedule(waiter, timeout);
        }
        waiter.thread.interruptHandler = waiter;
    }

    // PlatformDetector checks must stay inline: TeaVM folds them at compile time and drops the other backend's code
    private static void later(Runnable action) {
        if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
            EventQueue.offer(action::run);
        } else {
            Platform.postpone(action::run);
        }
    }

    static final class Waiter implements PlatformRunnable, TThreadInterruptHandler {
        final TCompatParker parker;
        final TThread thread;
        AsyncCallback<Boolean> callback;
        int timerId = -1;
        boolean done;

        Waiter(TCompatParker parker, TThread thread) {
            this.parker = parker;
            this.thread = thread;
        }

        private boolean finish() {
            if (done) return false;
            done = true;
            if (timerId >= 0) {
                if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
                    EventQueue.kill(timerId);
                } else {
                    Platform.killSchedule(timerId);
                }
                timerId = -1;
            }
            if (thread.interruptHandler == this) thread.interruptHandler = null;
            return true;
        }

        void wake(Boolean value) {
            if (!finish()) return;
            later(() -> {
                TThread.setCurrentThread(thread);
                callback.complete(value);
            });
        }

        // timeout (PlatformRunnable for the JS backend)
        @Override
        public void run() {
            onTimer();
        }

        void onTimer() {
            parker.waiters.remove(this);
            wake(Boolean.FALSE);
        }

        @Override
        public void interrupted() {
            if (!finish()) return;
            parker.waiters.remove(this);
            later(() -> {
                TThread.setCurrentThread(thread);
                callback.error(new TInterruptedException());
            });
        }
    }
}
