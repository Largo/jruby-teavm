package org.teavm.classlib.java.nio.channels;

/** compat: java.nio.channels.ClosedSelectorException (missing in TeaVM 0.16.0). */
public class TClosedSelectorException extends IllegalStateException {
    public TClosedSelectorException() {
    }
}
