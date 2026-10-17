package org.teavm.classlib.java.nio.channels.spi;

import java.io.IOException;
import org.teavm.classlib.java.nio.channels.TInterruptibleChannel;

/** compat: java.nio.channels.spi.AbstractInterruptibleChannel (missing in TeaVM 0.16.0); no interruption support. */
public abstract class TAbstractInterruptibleChannel implements TInterruptibleChannel {
    private boolean closed;

    protected TAbstractInterruptibleChannel() {
    }

    @Override
    public final void close() throws IOException {
        if (closed) return;
        closed = true;
        implCloseChannel();
    }

    protected abstract void implCloseChannel() throws IOException;

    @Override
    public final boolean isOpen() {
        return !closed;
    }

    protected final void begin() {
    }

    protected final void end(boolean completed) throws java.nio.channels.ClosedChannelException {
    }
}
