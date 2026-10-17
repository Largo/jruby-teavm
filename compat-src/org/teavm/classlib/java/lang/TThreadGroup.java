package org.teavm.classlib.java.lang;

/** compat: java.lang.ThreadGroup (missing in TeaVM 0.16.0). A single group; just enough for naming and lookups. */
public class TThreadGroup {
    static final TThreadGroup MAIN = new TThreadGroup(null, "main");

    private final TThreadGroup parent;
    private final String name;
    private int maxPriority = Thread.MAX_PRIORITY;
    private boolean daemon;

    public TThreadGroup(String name) {
        this(MAIN, name);
    }

    public TThreadGroup(TThreadGroup parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public final TThreadGroup getParent() {
        return parent;
    }

    public final int getMaxPriority() {
        return maxPriority;
    }

    public final void setMaxPriority(int pri) {
        maxPriority = pri;
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public int activeCount() {
        return 1;
    }

    public int enumerate(TThread[] list) {
        return 0;
    }

    public void interrupt() {
    }
}
