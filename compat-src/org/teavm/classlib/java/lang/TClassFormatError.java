package org.teavm.classlib.java.lang;

/** compat: java.lang.ClassFormatError (missing in TeaVM 0.16.0). */
public class TClassFormatError extends LinkageError {
    public TClassFormatError() {
    }

    public TClassFormatError(String s) {
        super(s);
    }
}
