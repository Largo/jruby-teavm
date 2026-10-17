/*
 * TEAVM: RubyObjectSpecializer reduced to a facade.
 *
 * Upstream generates RubyObject subclasses with one Java field per instance variable (jitescript +
 * class definition at runtime, or pregenerated at build time). The browser build keeps all
 * instance variables in the generic variable table, so specialization returns the plain allocator.
 * The original lives in upstream JRuby (core/src/main/java/org/jruby/specialized/RubyObjectSpecializer.java).
 */
package org.jruby.specialized;

import java.lang.invoke.MethodHandles;
import java.util.Set;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyObject;
import org.jruby.runtime.ObjectAllocator;

public class RubyObjectSpecializer {
    /** TEAVM: no MethodHandles.Lookup in the browser; only referenced by unreachable field-accessor code. */
    public static final MethodHandles.Lookup LOOKUP = null;

    public RubyObjectSpecializer(Ruby runtime) {
    }

    public ObjectAllocator specializeForVariables(RubyClass klass, Set<String> foundVariables) {
        return RubyObject.OBJECT_ALLOCATOR;
    }
}
