package jrubyweb.teavm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.extension.introspect.IntrospectClass;
import org.teavm.extension.introspect.IntrospectMember;
import org.teavm.extension.introspect.IntrospectMethod;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/**
 * TeaVM compiler plugin (runs inside the compiler, not in the page): reflection metadata for JRuby's Java
 * integration, driven by the build-time class list {@code java-classes.txt} (copied next to this class).
 *
 * For every listed class it makes the public constructors, methods and fields reflectable (Method.invoke,
 * Constructor.newInstance, Field.get/set), together with those of all its superclasses and superinterfaces
 * (JRuby's MethodGatherer walks the hierarchy). Listed classes can be found by name, and every listed interface
 * gets a precompiled java.lang.reflect.Proxy class for the interface list that JRuby uses for Ruby
 * implementations ({@code [interface, RubyObjectHolderProxy]}): blocks, {@code Iface.impl { }} and Ruby
 * classes that include the interface. A listed class may name its own members to restrict them, e.g.
 * {@code java.lang.System currentTimeMillis getProperty} ({@code new} names the constructors).
 *
 * Registered through META-INF/services/org.teavm.extension.spi.reflection.ReflectionPolicy (TeaVM loads policies
 * from its -p classpath).
 */
public class JavaClassesPolicy extends SimpleReflectionPolicy {
    static final String LIST = "jrubyweb/java-classes.txt";
    static final String HOLDER = "org.jruby.java.proxies.RubyObjectHolderProxy";

    @Override
    protected void setup() {
        Map<String, Set<String>> listed = read();
        Set<String> closure = new LinkedHashSet<>();
        for (String name : listed.keySet()) {
            IntrospectClass<?> cls = environment().findClass(name);
            if (cls == null) {
                environment().diagnostics().error(null, LIST + ": class not found: " + name);
                continue;
            }
            addSupertypes(cls, closure);
        }
        selectClasses(c -> closure.contains(c.name()))
                .reflectableMembers(m -> PUBLIC.test(m) && callable(m) && allowed(listed, m));
        selectClasses(c -> listed.containsKey(c.name())).foundByName();
        for (String name : listed.keySet()) {
            IntrospectClass<?> cls = environment().findClass(name);
            if (cls != null && cls.isInterface()) proxyGroup(name, HOLDER);
        }
    }

    /**
     * Native methods of TeaVM's class library can be called through reflection only where they have a real function:
     * on JavaScript those with a generated body (Math.sqrt: @GeneratedBy), not the inlined ones (Double.isNaN:
     * @JSBody); on wasm-gc none (intrinsics, or imports the runtime does not provide reflectively).
     */
    private static boolean callable(IntrospectMember member) {
        return !java.lang.reflect.Modifier.isNative(member.modifiers())
                || (JavaIntegrationPlugin.javaScript
                        && member.hasAnnotation(org.teavm.backend.javascript.spi.GeneratedBy.class));
    }

    /** a listed class with a member list exposes only those members (its supertypes are unrestricted) */
    private static boolean allowed(Map<String, Set<String>> listed, IntrospectMember member) {
        Set<String> members = listed.get(member.declaringClass().name());
        if (members == null || members.isEmpty()) return true;
        return members.contains(member.name()) || (member instanceof IntrospectMethod && member.name().equals("<init>")
                && members.contains("new"));
    }

    private static void addSupertypes(IntrospectClass<?> cls, Set<String> out) {
        if (cls == null || !out.add(cls.name())) return;
        addSupertypes(cls.superclass(), out);
        for (IntrospectClass<?> itf : cls.interfaces()) addSupertypes(itf, out);
    }

    private Map<String, Set<String>> read() {
        Map<String, Set<String>> result = new java.util.LinkedHashMap<>();
        try (InputStream in = JavaClassesPolicy.class.getClassLoader().getResourceAsStream(LIST)) {
            if (in == null) {
                environment().diagnostics().error(null, "missing " + LIST + " on the TeaVM classpath");
                return result;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                String[] words = line.trim().split("\\s+");
                if (words.length == 0 || words[0].isEmpty()) continue;
                Set<String> members = new HashSet<>();
                for (int i = 1; i < words.length; i++) members.add(words[i]);
                result.put(words[0], members);
            }
        } catch (IOException e) {
            environment().diagnostics().error(null, "cannot read " + LIST + ": " + e);
        }
        return result;
    }
}
