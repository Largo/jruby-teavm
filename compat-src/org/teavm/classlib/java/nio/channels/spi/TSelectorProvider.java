package org.teavm.classlib.java.nio.channels.spi;

import java.io.IOException;
import org.teavm.classlib.java.nio.channels.TSelector;

/** compat: java.nio.channels.spi.SelectorProvider (missing in TeaVM 0.16.0); there is no default provider. */
public abstract class TSelectorProvider {
    protected TSelectorProvider() {
    }

    public static TSelectorProvider provider() {
        throw new UnsupportedOperationException("no SelectorProvider (TeaVM)");
    }

    public abstract TSelector openSelector() throws IOException;
}
