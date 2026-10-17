package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

/** compat: java.nio.channels.ScatteringByteChannel (missing in TeaVM 0.16.0). */
public interface TScatteringByteChannel extends java.nio.channels.ReadableByteChannel {
    long read(ByteBuffer[] dsts, int offset, int length) throws IOException;
    long read(ByteBuffer[] dsts) throws IOException;
}
