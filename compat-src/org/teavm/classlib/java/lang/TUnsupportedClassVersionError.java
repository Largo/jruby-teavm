package org.teavm.classlib.java.lang;

/** compat: java.lang.UnsupportedClassVersionError (missing in TeaVM 0.16.0). */
public class TUnsupportedClassVersionError extends ClassFormatError {
    public TUnsupportedClassVersionError() {
    }

    public TUnsupportedClassVersionError(String s) {
        super(s);
    }
}
