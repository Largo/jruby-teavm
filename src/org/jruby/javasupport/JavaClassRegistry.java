package org.jruby.javasupport;

import java.util.ServiceLoader;

/**
 * TEAVM: closed-world lookup of the Java classes that Ruby code may load by name.
 *
 * An ahead-of-time compiled runtime (TeaVM) has no class loading: only classes known at build time exist, and
 * only those with reflection metadata can be proxied. A build step generates an implementation of this interface
 * (a switch over the class names of a fixed list, returning class literals) and registers it in
 * META-INF/services. When a registry is present, {@link JavaSupport#loadJavaClass(String, boolean)} finds
 * classes only through it; without one (the JVM) nothing changes.
 */
public interface JavaClassRegistry {
    /** @return the class of the given binary name, or null if it is not available */
    Class<?> forName(String className);

    /** @return true if a registry is installed (class loading by name is then limited to it) */
    static boolean isInstalled() {
        return Holder.REGISTRIES.length > 0;
    }

    static Class<?> lookup(String className) {
        for (JavaClassRegistry registry : Holder.REGISTRIES) {
            Class<?> klass = registry.forName(className);
            if (klass != null) return klass;
        }
        return null;
    }

    final class Holder {
        static final JavaClassRegistry[] REGISTRIES;
        static {
            java.util.ArrayList<JavaClassRegistry> list = new java.util.ArrayList<>();
            for (JavaClassRegistry registry : ServiceLoader.load(JavaClassRegistry.class)) list.add(registry);
            REGISTRIES = list.toArray(new JavaClassRegistry[0]);
        }
    }
}
