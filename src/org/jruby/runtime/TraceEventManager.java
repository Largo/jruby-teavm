package org.jruby.runtime;

import com.headius.invokebinder.Binder;
import org.jruby.MetaClass;
import org.jruby.Ruby;
import org.jruby.RubyBinding;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyModule;
import org.jruby.RubyProc;
import org.jruby.api.Convert;
import org.jruby.ir.runtime.IRRuntimeHelpers;
import org.jruby.runtime.builtin.IRubyObject;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.jruby.api.Convert.asFixnum;
import static org.jruby.api.Create.newString;
import static org.jruby.api.Warn.warn;

public class TraceEventManager {
    // TEAVM: TRACE_ON/TRACE_OFF MethodHandles and the MutableCallSites removed (only JIT-compiled code links to them)

    private static final EventHook[] EMPTY_HOOKS = new EventHook[0];

    private final Ruby runtime;
    private volatile EventHook[] eventHooks = EMPTY_HOOKS;
    private boolean hasEventHooks;
    private final CallTraceFuncHook callTraceFuncHook = new CallTraceFuncHook(null);


    public TraceEventManager(Ruby runtime) {
        this.runtime = runtime;
    }

    private static final EnumSet<RubyEvent> interest =
            EnumSet.of(
                    RubyEvent.C_CALL,
                    RubyEvent.C_RETURN,
                    RubyEvent.CALL,
                    RubyEvent.CLASS,
                    RubyEvent.END,
                    RubyEvent.LINE,
                    RubyEvent.RAISE,
                    RubyEvent.RETURN
            );

    @Deprecated(since = "10.0.0.0")
    public synchronized void addEventHook(EventHook hook) {
        addEventHook(runtime.getCurrentContext(), hook);
    }

    public synchronized void addEventHook(ThreadContext context, EventHook hook) {
        if (!RubyInstanceConfig.FULL_TRACE_ENABLED && hook.needsDebug()) {
            // without full tracing, many events will not fire
            warn(context, "tracing (e.g. set_trace_func) will not capture all events without --debug flag");
        }

        EventHook[] hooks = eventHooks;
        EventHook[] newHooks = Arrays.copyOf(hooks, hooks.length + 1);
        newHooks[hooks.length] = hook;
        eventHooks = newHooks;

        hasEventHooks = true;

        enableTraceSites(hook);
    }

    public synchronized void removeEventHook(EventHook hook) {
        EventHook[] hooks = eventHooks;

        if (hooks.length == 0) return;

        int pivot = -1;
        for (int i = 0; i < hooks.length; i++) {
            if (hooks[i].equals(hook)) {
                pivot = i;
                break;
            }
        }

        if (pivot == -1) return; // No such hook found.

        EventHook[] newHooks = new EventHook[hooks.length - 1];
        // copy before and after pivot into the new array but don't bother
        // to arraycopy if pivot is first/last element of the old list.
        if (pivot != 0) System.arraycopy(hooks, 0, newHooks, 0, pivot);
        if (pivot != hooks.length - 1) System.arraycopy(hooks, pivot + 1, newHooks, pivot, hooks.length - (pivot + 1));

        eventHooks = newHooks;
        if (newHooks.length == 0) {
            hasEventHooks = false;

            disableTraceSites(hook);
        }
    }

    private void enableTraceSites(EventHook hook) {
        if (hook.isInterestedInEvent(RubyEvent.CALL) || hook.isInterestedInEvent(RubyEvent.RETURN)) {
            // TEAVM: callTrace.setTarget removed
        }

        if (hook.isInterestedInEvent(RubyEvent.B_CALL) || hook.isInterestedInEvent(RubyEvent.B_RETURN)) {
            // TEAVM: bcallTrace.setTarget removed
        }
    }

    private void disableTraceSites(EventHook hook) {
        if (hook.isInterestedInEvent(RubyEvent.CALL) || hook.isInterestedInEvent(RubyEvent.RETURN)) {
            // TEAVM: callTrace.setTarget removed
        }

        if (hook.isInterestedInEvent(RubyEvent.B_CALL) || hook.isInterestedInEvent(RubyEvent.B_RETURN)) {
            // TEAVM: bcallTrace.setTarget removed
        }
    }

    private void disableTraceSites() {
        // TEAVM: callTrace.setTarget removed

        // TEAVM: bcallTrace.setTarget removed
    }

    public void setTraceFunction(RubyProc traceFunction) {
        setTraceFunction(callTraceFuncHook, traceFunction);
    }

    public void setTraceFunction(CallTraceFuncHook hook, RubyProc traceFunction) {
        removeEventHook(hook);

        if (traceFunction == null) return;

        hook.setTraceFunc(traceFunction);
        addEventHook(runtime.getCurrentContext(), hook);
    }

    /**
     * Remove all event hooks which are associated with a particular thread.
     *
     * @param context the context of the ruby thread we are interested in.
     */
    public void removeAllCallEventHooksFor(ThreadContext context) {
        if (eventHooks.length == 0) return;

        int size = eventHooks.length;
        List<EventHook> hooks = new ArrayList<>(Arrays.asList(eventHooks));

        hooks = hooks.stream().filter(hook ->
                !(hook instanceof CallTraceFuncHook ctfHook && ctfHook.getThread().equals(context))
        ).collect(Collectors.toList());

        EventHook[] newHooks = new EventHook[hooks.size()];
        eventHooks = hooks.toArray(newHooks);
        if (hooks.isEmpty()) {
            hasEventHooks = false;

            disableTraceSites();
        }
    }

    public void callEventHooks(ThreadContext context, RubyEvent event, String file, int line, String name, IRubyObject type) {
        if (context.isEventHooksEnabled()) {
            EventHook hooks[] = eventHooks;

            for (EventHook eventHook : hooks) {
                if (eventHook.isInterestedInEvent(event)) {
                    IRubyObject klass = context.nil;
                    if (type instanceof RubyModule) {
                        if (((RubyModule) type).isIncluded()) {
                            klass = ((RubyModule) type).getOrigin();
                        } else if (((RubyModule) type).isSingleton()) {
                            klass = ((MetaClass) type).getAttached();
                        }
                    }
                    eventHook.event(context, event, file, line, name, klass);
                }
            }
        }
    }

    // TEAVM: getCallReturnSite/getBCallBReturnSite removed (JIT only)

    public boolean hasEventHooks() {
        return hasEventHooks;
    }

    public int[] eventHookStats() {
        return new int[] {eventHooks.length, 0};
    }

    public static class CallTraceFuncHook extends EventHook {
        private RubyProc traceFunc;
        private final ThreadContext thread; // if non-null only call traceFunc if it is from this thread.

        public CallTraceFuncHook(ThreadContext context) {
            this.thread = context;
        }

        public void setTraceFunc(RubyProc traceFunc) {
            this.traceFunc = traceFunc;
        }

        public void eventHandler(ThreadContext context, String eventName, String file, int line, String name, IRubyObject type) {
            if (context.isWithinTrace()) return;
            if (thread != null && thread != context) return;

            if (file == null) file = "(ruby)";
            if (type == null) type = context.nil;

            RubyBinding binding = RubyBinding.newBinding(context.runtime, context.currentBinding());

            // FIXME: Ultimately we should be getting proper string for this event type
            switch (eventName) {
                case "c_return":
                    eventName = "c-return";
                    break;
                case "c_call":
                    eventName = "c-call";
                    break;
            }

            context.preTrace();
            try {
                traceFunc.call(context, new IRubyObject[]{
                        newString(context, eventName), // event name
                        newString(context, file), // filename
                        asFixnum(context, line), // line numbers should be 1-based
                        name != null ? Convert.asSymbol(context, name) : context.nil,
                        binding,
                        type
                });
            } finally {
                context.postTrace();
            }
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CallTraceFuncHook)) return false;

            return traceFunc == ((CallTraceFuncHook) other).traceFunc && thread == ((CallTraceFuncHook) other).thread;
        }

        @Override
        public int hashCode() {
            return 13 * traceFunc.hashCode() + 5 * (thread == null ? 0 : thread.hashCode());
        }

        @Override
        public boolean isInterestedInEvent(RubyEvent event) {
            return interest.contains(event);
        }

        public ThreadContext getThread() {
            return thread;
        }

        @Override
        public EnumSet<RubyEvent> eventSet() {
            return interest;
        }
    }
}
