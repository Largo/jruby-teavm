package org.teavm.classlib.java.nio.channels.spi;

import java.io.IOException;
import org.teavm.classlib.java.nio.channels.TSelectableChannel;
import org.teavm.classlib.java.nio.channels.TSelectionKey;
import org.teavm.classlib.java.nio.channels.TSelector;

/** compat: java.nio.channels.spi.AbstractSelectableChannel (missing in TeaVM 0.16.0). */
public abstract class TAbstractSelectableChannel extends TSelectableChannel {
    private final TSelectorProvider provider;
    private boolean blocking = true;
    private final Object lock = new Object();

    protected TAbstractSelectableChannel(TSelectorProvider provider) {
        this.provider = provider;
    }

    @Override
    public final TSelectorProvider provider() {
        return provider;
    }

    @Override
    public final boolean isRegistered() {
        return false;
    }

    @Override
    public final TSelectionKey keyFor(TSelector sel) {
        return null;
    }

    @Override
    public final TSelectionKey register(TSelector sel, int ops, Object att) throws java.nio.channels.ClosedChannelException {
        throw new UnsupportedOperationException("selectors are not supported (TeaVM)");
    }

    @Override
    protected final void implCloseChannel() throws IOException {
        implCloseSelectableChannel();
    }

    protected abstract void implCloseSelectableChannel() throws IOException;

    @Override
    public final boolean isBlocking() {
        return blocking;
    }

    @Override
    public final Object blockingLock() {
        return lock;
    }

    @Override
    public final TSelectableChannel configureBlocking(boolean block) throws IOException {
        implConfigureBlocking(block);
        blocking = block;
        return this;
    }

    protected abstract void implConfigureBlocking(boolean block) throws IOException;
}
