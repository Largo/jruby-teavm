package org.teavm.classlib.java.net;

import java.io.Closeable;
import java.io.IOException;
import org.teavm.classlib.java.lang.TClassLoader;

/**
 * compat: java.net.URLClassLoader (missing in TeaVM 0.16.0). Shape only, so that subclasses (JRuby's
 * JRubyClassLoader family) resolve; nothing can be loaded from URLs in the browser.
 */
public class TURLClassLoader extends TClassLoader implements Closeable {
    public TURLClassLoader(TURL[] urls, TClassLoader parent) {
        super(parent);
    }

    public TURLClassLoader(TURL[] urls) {
        super(TClassLoader.getSystemClassLoader());
    }

    protected void addURL(TURL url) {
        throw new UnsupportedOperationException("URLClassLoader is not supported (TeaVM)");
    }

    public TURL[] getURLs() {
        return new TURL[0];
    }

    @Override
    public void close() throws IOException {
    }
}
