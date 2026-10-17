package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import org.teavm.classlib.java.nio.channels.spi.TAbstractInterruptibleChannel;
import org.teavm.classlib.java.nio.channels.spi.TSelectorProvider;

/** compat: java.nio.channels.SelectableChannel (missing in TeaVM 0.16.0). Abstract shape only; nothing is selectable in the browser. */
public abstract class TSelectableChannel extends TAbstractInterruptibleChannel {
    protected TSelectableChannel() {
    }

    public abstract TSelectorProvider provider();

    public abstract int validOps();

    public abstract boolean isRegistered();

    public abstract TSelectionKey keyFor(TSelector sel);

    public abstract TSelectionKey register(TSelector sel, int ops, Object att) throws java.nio.channels.ClosedChannelException;

    public final TSelectionKey register(TSelector sel, int ops) throws java.nio.channels.ClosedChannelException {
        return register(sel, ops, null);
    }

    public abstract TSelectableChannel configureBlocking(boolean block) throws IOException;

    public abstract boolean isBlocking();

    public abstract Object blockingLock();
}
