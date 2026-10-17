# compat: like TMethod.java.sed, Constructor.newInstance wraps exceptions of the constructor in
# InvocationTargetException (JDK behaviour; TeaVM 0.16.0 lets them escape)
s/        methodInfo.call(instance, initargs);/        try { methodInfo.call(instance, initargs); } catch (Throwable e) { throw new TInvocationTargetException((org.teavm.classlib.java.lang.TThrowable) (Object) e); }/
