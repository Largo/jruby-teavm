package org.teavm.classlib.java.lang;

import java.io.InputStream;
import java.io.OutputStream;

/** compat: java.lang.Process (missing in TeaVM 0.16.0). Abstract shape only; nothing can start a process. */
public abstract class TProcess {
    public abstract OutputStream getOutputStream();
    public abstract InputStream getInputStream();
    public abstract InputStream getErrorStream();
    public abstract int waitFor() throws InterruptedException;
    public abstract int exitValue();
    public abstract void destroy();

    public TProcess destroyForcibly() {
        destroy();
        return this;
    }

    public boolean isAlive() {
        try {
            exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    public long pid() {
        throw new UnsupportedOperationException();
    }
}
