package org.jruby.runtime.backtrace;

import java.util.function.Function;
import java.util.stream.Stream;

import com.headius.backport9.stack.StackWalker;

/**
 * TEAVM: a StackWalker for platforms without an inspectable Java stack (TeaVM has no
 * java.lang.StackWalker and no reliable Throwable stack traces). It always yields an empty stream;
 * {@link BacktraceData} then builds Ruby backtraces from JRuby's own frame stack only.
 */
public final class NoStackWalker implements StackWalker {
    public static final NoStackWalker INSTANCE = new NoStackWalker();

    private NoStackWalker() {
    }

    @Override
    public <T> T walk(Function<? super Stream<StackFrame>, ? extends T> function) {
        return function.apply(Stream.empty());
    }

    @Override
    public <T> T walk(StackTraceElement[] stackTrace, Function<? super Stream<StackFrame>, ? extends T> function) {
        return function.apply(Stream.empty());
    }
}
