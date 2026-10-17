package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import org.teavm.classlib.java.nio.channels.spi.TAbstractSelectableChannel;
import org.teavm.classlib.java.nio.channels.spi.TSelectorProvider;

/** compat: java.nio.channels.SocketChannel (missing in TeaVM 0.16.0). Abstract shape only; there are no sockets in the browser. */
public abstract class TSocketChannel extends TAbstractSelectableChannel
        implements java.nio.channels.ByteChannel, TScatteringByteChannel, TGatheringByteChannel {
    protected TSocketChannel(TSelectorProvider provider) {
        super(provider);
    }

    @Override
    public final int validOps() {
        return TSelectionKey.OP_READ | TSelectionKey.OP_WRITE | TSelectionKey.OP_CONNECT;
    }

    public abstract TSocketChannel shutdownInput() throws IOException;

    public abstract TSocketChannel shutdownOutput() throws IOException;

    public abstract boolean isConnected();
}
