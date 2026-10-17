package jrubyweb.teavm;

import org.teavm.model.transformation.AssertionRemoval;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * TeaVM compiler plugin (runs inside the compiler, not in the page): strips Java `assert` statements.
 * TeaVM's Class.desiredAssertionStatus() returns true, so JRuby's internal asserts would otherwise be live
 * in the browser (slower, and some assume JVM-only invariants such as one Java thread per Ruby thread).
 * Registered through META-INF/services/org.teavm.vm.spi.TeaVMPlugin; TeaVM finds it on its -p classpath.
 */
public class NoAssertionsPlugin implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        host.add(new AssertionRemoval());
    }
}
