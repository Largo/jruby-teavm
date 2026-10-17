package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/** compat: java.util.concurrent.locks.ReentrantReadWriteLock for TeaVM; see TReentrantLock for the non-blocking rule. */
public class TReentrantReadWriteLock implements TReadWriteLock {
    private final ReadLock readerLock = new ReadLock(this);
    private final WriteLock writerLock = new WriteLock(this);
    final TReentrantLock write = new TReentrantLock();
    int readers;

    public TReentrantReadWriteLock() {
    }

    public TReentrantReadWriteLock(boolean fair) {
    }

    @Override
    public ReadLock readLock() {
        return readerLock;
    }

    @Override
    public WriteLock writeLock() {
        return writerLock;
    }

    public boolean isWriteLocked() {
        return write.isLocked();
    }

    public boolean isWriteLockedByCurrentThread() {
        return write.isHeldByCurrentThread();
    }

    public int getReadLockCount() {
        return readers;
    }

    public int getWriteHoldCount() {
        return write.getHoldCount();
    }

    public static class ReadLock implements TLock {
        private final TReentrantReadWriteLock rw;

        protected ReadLock(TReentrantReadWriteLock rw) {
            this.rw = rw;
        }

        @Override
        public void lock() {
            if (rw.write.isLocked() && !rw.write.isHeldByCurrentThread()) {
                throw new IllegalMonitorStateException("write lock held by another thread (TeaVM compat)");
            }
            rw.readers++;
        }

        @Override
        public void lockInterruptibly() {
            lock();
        }

        @Override
        public boolean tryLock() {
            if (rw.write.isLocked() && !rw.write.isHeldByCurrentThread()) return false;
            rw.readers++;
            return true;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            return tryLock();
        }

        @Override
        public void unlock() {
            if (rw.readers > 0) rw.readers--;
        }

        @Override
        public TCondition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    public static class WriteLock implements TLock {
        private final TReentrantReadWriteLock rw;

        protected WriteLock(TReentrantReadWriteLock rw) {
            this.rw = rw;
        }

        @Override
        public void lock() {
            rw.write.lock();
        }

        @Override
        public void lockInterruptibly() {
            rw.write.lock();
        }

        @Override
        public boolean tryLock() {
            return rw.write.tryLock();
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            return rw.write.tryLock();
        }

        @Override
        public void unlock() {
            rw.write.unlock();
        }

        @Override
        public TCondition newCondition() {
            return rw.write.newCondition();
        }

        public boolean isHeldByCurrentThread() {
            return rw.write.isHeldByCurrentThread();
        }

        public int getHoldCount() {
            return rw.write.getHoldCount();
        }
    }
}
