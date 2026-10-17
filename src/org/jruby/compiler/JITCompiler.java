/*
 * TEAVM: JITCompiler reduced to an inert facade.
 *
 * The browser build of JRuby (compiled with TeaVM) cannot define JVM classes at runtime, so the
 * JIT is never used: every method stays interpreted. This class keeps the public API that the
 * interpreter calls (buildThresholdReached, getTaskFor, statistics) so the callers compile unchanged.
 * The original implementation lives in upstream JRuby (core/src/main/java/org/jruby/compiler/JITCompiler.java).
 */
package org.jruby.compiler;

import org.jruby.Ruby;
import org.jruby.runtime.ThreadContext;

public class JITCompiler {
    public static final String RUBY_JIT_PREFIX = "rubyjit";

    public JITCompiler(Ruby runtime) {
    }

    public long getSuccessCount() { return 0; }
    public long getCompileCount() { return 0; }
    public long getFailCount() { return 0; }
    public long getCompileTime() { return 0; }
    public long getAbandonCount() { return 0; }
    public long getCodeSize() { return 0; }
    public long getCodeAverageSize() { return 0; }
    public long getCompileTimeAverage() { return 0; }
    public long getCodeLargestSize() { return 0; }
    public long getIRSize() { return 0; }
    public long getIRAverageSize() { return 0; }
    public long getIRLargestSize() { return 0; }
    public String[] getFrameAwareMethods() { return new String[0]; }
    public String[] getScopeAwareMethods() { return new String[0]; }

    public void tearDown() {
    }

    public void shutdown() {
    }

    public boolean isShutdown() {
        return true;
    }

    public Runnable getTaskFor(ThreadContext context, Compilable method) {
        return () -> {};
    }

    public void buildThresholdReached(ThreadContext context, final Compilable method, boolean force) {
        // TEAVM: never JIT; method stays interpreted
    }
}
