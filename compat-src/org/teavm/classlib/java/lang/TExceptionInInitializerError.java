package org.teavm.classlib.java.lang;

/** compat: java.lang.ExceptionInInitializerError (missing in TeaVM 0.16.0). */
public class TExceptionInInitializerError extends LinkageError {
    private final Throwable exception;

    public TExceptionInInitializerError() {
        this.exception = null;
    }

    public TExceptionInInitializerError(Throwable thrown) {
        super(null, thrown);
        this.exception = thrown;
    }

    public TExceptionInInitializerError(String s) {
        super(s);
        this.exception = null;
    }

    public Throwable getException() {
        return exception;
    }
}
