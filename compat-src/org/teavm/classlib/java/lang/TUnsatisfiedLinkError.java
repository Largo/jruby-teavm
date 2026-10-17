package org.teavm.classlib.java.lang;

/** compat: java.lang.UnsatisfiedLinkError (missing in TeaVM 0.16.0). */
public class TUnsatisfiedLinkError extends LinkageError {
    public TUnsatisfiedLinkError() {
    }

    public TUnsatisfiedLinkError(String s) {
        super(s);
    }
}
