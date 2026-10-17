# compat: TeaVM 0.16.0's Class.getPackage() names the package with a trailing dot ("java.lang."); the JDK's
# Package.getName() is "java.lang" (JRuby builds its Java::JavaLang package modules from it)
s/        name = name.substring(0, name.lastIndexOf('.') + 1);/        name = name.substring(0, Math.max(0, name.lastIndexOf('.')));/
