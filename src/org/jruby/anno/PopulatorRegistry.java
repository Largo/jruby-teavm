package org.jruby.anno;

import java.util.ServiceLoader;

/**
 * TEAVM: static lookup of pregenerated $POPULATOR classes.
 *
 * Upstream JRuby finds a populator with ClassLoader.getResource + Class.forName(...).newInstance(),
 * which needs runtime class loading. A build step generates an implementation of this interface
 * (a plain switch over populator names) and registers it in META-INF/services; ServiceLoader is
 * resolved at compile time by AOT compilers such as TeaVM, and works unchanged on the JVM.
 */
public interface PopulatorRegistry {
    /** @return a new populator instance for the given generated class name, or null if unknown */
    TypePopulator forName(String populatorClassName);

    static TypePopulator lookup(String populatorClassName) {
        for (PopulatorRegistry registry : Holder.REGISTRIES) {
            TypePopulator populator = registry.forName(populatorClassName);
            if (populator != null) return populator;
        }
        return null;
    }

    final class Holder {
        static final PopulatorRegistry[] REGISTRIES;
        static {
            java.util.ArrayList<PopulatorRegistry> list = new java.util.ArrayList<>();
            for (PopulatorRegistry registry : ServiceLoader.load(PopulatorRegistry.class)) list.add(registry);
            REGISTRIES = list.toArray(new PopulatorRegistry[0]);
        }
    }
}
