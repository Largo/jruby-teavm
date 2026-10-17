package org.teavm.classlib.java.nio.channels;

/** compat: java.nio.channels.OverlappingFileLockException (missing in TeaVM 0.16.0). */
public class TOverlappingFileLockException extends IllegalStateException {
    public TOverlappingFileLockException() {
    }
}
