package org.teavm.classlib.java.nio.channels;

import java.io.IOException;

/** compat: java.nio.channels.InterruptibleChannel (missing in TeaVM 0.16.0). */
public interface TInterruptibleChannel extends java.nio.channels.Channel {
    @Override
    void close() throws IOException;
}
