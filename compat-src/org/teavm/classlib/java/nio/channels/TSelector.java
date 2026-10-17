package org.teavm.classlib.java.nio.channels;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;
import org.teavm.classlib.java.nio.channels.spi.TSelectorProvider;

/** compat: java.nio.channels.Selector (missing in TeaVM 0.16.0). open() fails: there is nothing to select on in the browser. */
public abstract class TSelector implements Closeable {
    protected TSelector() {
    }

    public static TSelector open() throws IOException {
        throw new IOException("selectors are not supported (TeaVM)");
    }

    public abstract boolean isOpen();

    public abstract TSelectorProvider provider();

    public abstract Set<TSelectionKey> keys();

    public abstract Set<TSelectionKey> selectedKeys();

    public abstract int selectNow() throws IOException;

    public abstract int select(long timeout) throws IOException;

    public abstract int select() throws IOException;

    public abstract TSelector wakeup();

    @Override
    public abstract void close() throws IOException;
}
