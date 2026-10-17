# compat: TeaVM 0.16.0's Method.invoke lets an exception thrown by the invoked method escape unwrapped; the JDK
# wraps it in InvocationTargetException (JRuby's Java integration relies on that to tell a Java exception from
# an argument mismatch: an unwrapped NumberFormatException would look like a bad-argument IllegalArgumentException)
s/        return methodInfo.call(obj, args);/        try { return methodInfo.call(obj, args); } catch (Throwable e) { throw new TInvocationTargetException((org.teavm.classlib.java.lang.TThrowable) (Object) e); }/
