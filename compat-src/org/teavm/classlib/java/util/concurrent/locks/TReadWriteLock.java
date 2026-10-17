package org.teavm.classlib.java.util.concurrent.locks;

/** compat: java.util.concurrent.locks.ReadWriteLock (missing in TeaVM 0.16.0). */
public interface TReadWriteLock {
    TLock readLock();
    TLock writeLock();
}
