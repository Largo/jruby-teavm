package org.teavm.classlib.java.lang;

/** compat: java.lang.IllegalThreadStateException (missing in TeaVM 0.16.0). */
public class TIllegalThreadStateException extends IllegalArgumentException {
    public TIllegalThreadStateException() {
    }

    public TIllegalThreadStateException(String s) {
        super(s);
    }
}
