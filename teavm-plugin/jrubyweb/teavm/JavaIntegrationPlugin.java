package jrubyweb.teavm;

import org.teavm.backend.javascript.TeaVMJavaScriptHost;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyPlugin;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * TeaVM compiler plugin (runs inside the compiler, not in the page) for JRuby's Java integration:
 *
 * <ul>
 * <li>Dependency analysis for Class.getEnclosingClass() and Class.getDeclaringClass(). TeaVM 0.16.0 models
 *     getSuperclass/getInterfaces/getDeclaredClasses (classlib ClassGenerator) but not these two, so the Class
 *     objects they return carry no metadata: getName() of java.util.TemplateCollections, the enclosing class of the
 *     class behind Collections.EMPTY_LIST, is null. JRuby builds a proxy (and its Ruby name) for the enclosing class
 *     of every proxied nested class. TeaVM's own runtime classes (org.teavm.*) are left out: their nested classes
 *     must never become reachable through reflection (wasm-gc maps some of them to JS references).</li>
 * <li>The types a {@code ReferenceQueue} hands out. TeaVM 0.16.0's own listener for this
 *     (WeakReferenceDependencyListener) makes a new node on every method it sees and connects the queue argument
 *     instead of the reference, so to the analysis {@code poll()} returns nothing: a method that is only called on
 *     polled references (JRuby's ObjectProxyCache calls {@code EntryRef.hash()} so) is left out of the class, and
 *     the first expunge after a JS garbage collection fails with "TypeError: d.bfq is not a function". Here every
 *     reference made with a queue flows into what {@code poll()}/{@code remove()} return.</li>
 * <li>Records the target for {@link JavaClassesPolicy}: native class-library methods with a generated body can be
 *     called reflectively on JavaScript only.</li>
 * </ul>
 */
public class JavaIntegrationPlugin implements TeaVMPlugin {
    static boolean javaScript;

    @Override
    public void install(TeaVMHost host) {
        javaScript = host.getExtension(TeaVMJavaScriptHost.class) != null;
        host.add(new MethodReference(Class.class, "getEnclosingClass", Class.class), new Owner(true));
        host.add(new MethodReference(Class.class, "getDeclaringClass", Class.class), new Owner(false));
        host.add(new QueuedReferences());
    }

    private static final class QueuedReferences extends org.teavm.dependency.AbstractDependencyListener {
        private org.teavm.dependency.DependencyNode references;

        @Override
        public void started(DependencyAgent agent) {
            references = agent.createNode();
        }

        @Override
        public void methodReached(DependencyAgent agent, MethodDependency method) {
            MethodReference ref = method.getReference();
            String owner = ref.getClassName();
            String name = ref.getName();
            boolean reference = owner.equals("java.lang.ref.WeakReference") || owner.equals("java.lang.ref.SoftReference")
                    || owner.equals("java.lang.ref.PhantomReference");
            if (reference && name.equals("<init>") && ref.parameterCount() == 2) {
                method.getVariable(0).connect(references);
            } else if (owner.equals("java.lang.ref.ReferenceQueue") && (name.equals("poll") || name.equals("remove"))
                    && method.getResult() != null) {
                references.connect(method.getResult());
            }
        }
    }

    private static final class Owner implements DependencyPlugin {
        private final boolean enclosing;

        Owner(boolean enclosing) {
            this.enclosing = enclosing;
        }

        @Override
        public void methodReached(DependencyAgent agent, MethodDependency method) {
            method.getResult().propagate(agent.getType(ValueType.object("java.lang.Class")));
            method.getVariable(0).getClassValueNode().addConsumer(type -> {
                if (!(type.getValueType() instanceof ValueType.Object)) {
                    return;
                }
                ClassReader cls = agent.getClassSource().get(((ValueType.Object) type.getValueType()).getClassName());
                if (cls == null) {
                    return;
                }
                String owner = enclosing ? cls.getOwnerName() : cls.getDeclaringClassName();
                if (owner != null && !owner.startsWith("org.teavm.") && agent.getClassSource().get(owner) != null) {
                    agent.linkClass(owner);
                    method.getResult().getClassValueNode().propagate(agent.getType(ValueType.object(owner)));
                }
            });
        }
    }
}
