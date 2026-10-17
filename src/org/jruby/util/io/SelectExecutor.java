/*
 * TEAVM: SelectExecutor reduced to a facade without java.nio.channels selectors.
 *
 * In the browser every IO is an in-memory stream or console, which is always ready, so IO.select
 * reports all given read/write IOs as ready immediately. The original implementation lives in
 * upstream JRuby (core/src/main/java/org/jruby/util/io/SelectExecutor.java).
 */
package org.jruby.util.io;

import org.jruby.RubyArray;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import static org.jruby.api.Create.newArray;
import static org.jruby.api.Create.newEmptyArray;

public class SelectExecutor {
    // values of java.nio.channels.SelectionKey.OP_*
    public static final int READ_ACCEPT_OPS = 1 | 16;
    public static final int WRITE_CONNECT_OPS = 4 | 8;

    final IRubyObject read, write, except;
    final Long timeout;

    public SelectExecutor(IRubyObject read, IRubyObject write, IRubyObject except, Long timeout) {
        this.read = read;
        this.write = write;
        this.except = except;
        this.timeout = timeout;
    }

    public IRubyObject go(ThreadContext context) {
        IRubyObject r = read.isNil() ? newEmptyArray(context) : ((RubyArray) read.convertToArray()).aryDup();
        IRubyObject w = write.isNil() ? newEmptyArray(context) : ((RubyArray) write.convertToArray()).aryDup();
        return newArray(context, r, w, newEmptyArray(context));
    }
}
