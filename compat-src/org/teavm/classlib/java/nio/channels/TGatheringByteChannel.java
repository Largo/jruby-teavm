package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

/** compat: java.nio.channels.GatheringByteChannel (missing in TeaVM 0.16.0). */
public interface TGatheringByteChannel extends java.nio.channels.WritableByteChannel {
    long write(ByteBuffer[] srcs, int offset, int length) throws IOException;
    long write(ByteBuffer[] srcs) throws IOException;
}
