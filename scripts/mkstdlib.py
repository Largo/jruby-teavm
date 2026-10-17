#!/usr/bin/env python3
"""mkstdlib.py STDLIB_JAR CORE_RUBY_DIR OUT_ZIP JCODINGS_JAR JODA_JAR

Builds the zip that the page mounts at /jruby in TeaVM's in-memory file system:
  classpath/**          JRuby's kernel .rb files (core/src/main/ruby), served as uri:classloader:
  lib/ruby/stdlib/**    the pure-Ruby standard library from jruby-stdlib.jar, minus parts that cannot
                        work in a browser (rubygems, bundler, native/Java-backed libraries, tools)
                        plus the lib/ dirs of a few pure-Ruby bundled gems (ostruct, ...), and java.rb, which
                        loads JRuby's Java integration on require 'java' (it is not loaded at boot)
  jcodings/tables/*.bin the jcodings character tables (case folding, transcoders, ...)
  joda/tzdata/**        joda-time's compiled time zone database
"""
import os, sys, zipfile

jar, core_ruby, out, jcodings_jar, joda_jar = sys.argv[1:6]
STD = 'META-INF/jruby.home/lib/ruby/stdlib/'
GEMS = 'META-INF/jruby.home/lib/ruby/gems/shared/gems/'

EXCLUDE_TOP = {  # top-level entries of stdlib/ to drop (file or directory, without .rb)
    'rubygems', 'bundler', 'bundled_gems', 'rdoc', 'irb', 'rake', 'jars', 'jar-dependencies', 'jar_dependencies',
    'jar_install_post_install_hook', 'ffi', 'fiddle', 'openssl', 'jopenssl', 'psych', 'psych_jars', 'readline',
    'jline', 'did_you_mean', 'error_highlight', 'syntax_suggest', 'socket', 'resolv', 'open-uri', 'net', 'ipaddr',
    'mkmf', 'pty', 'expect', 'subspawn', 'libfixposix', 'ant', 'win32', 'win32ole', 'io/console', 'org',
    'profiler', 'ripper', 'objspace', 'debug', 'digest', 'json', 'java', 'jruby/compiler', 'open3', 'random',
}
BUNDLED = ('ostruct-', 'base64-', 'abbrev-', 'observer-', 'getoptlong-', 'benchmark-', 'logger-', 'prime-',
           'matrix-', 'pstore-', 'mutex_m-')
JAR_PLACEHOLDERS = ('stringio.jar', 'strscan.jar')  # their Java classes are compiled into jruby.js

def excluded(rel):
    parts = rel.split('/')
    for i in range(1, len(parts) + 1):
        key = '/'.join(parts[:i])
        base = key[:-3] if key.endswith('.rb') else key
        if base in EXCLUDE_TOP:
            return True
    return rel.endswith('.jar') and rel not in JAR_PLACEHOLDERS

count = 0
with zipfile.ZipFile(jar) as src, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as dst:
    for root, _, files in os.walk(core_ruby):
        for f in sorted(files):
            p = os.path.join(root, f)
            dst.write(p, 'classpath/' + os.path.relpath(p, core_ruby)); count += 1
    seen = set()
    for info in src.infolist():
        n = info.filename
        if info.is_dir():
            continue
        if n.startswith(STD):
            rel = n[len(STD):]
            if excluded(rel):
                continue
            data = b'' if rel in JAR_PLACEHOLDERS else src.read(n)
        elif n.startswith(GEMS):
            rest = n[len(GEMS):]
            gem, _, inner = rest.partition('/')
            if not gem.startswith(BUNDLED) or not inner.startswith('lib/'):
                continue
            rel = inner[len('lib/'):]
            data = src.read(n)
        else:
            continue
        if rel in seen:
            continue
        seen.add(rel)
        dst.writestr('lib/ruby/stdlib/' + rel, data); count += 1
    # require 'java' loads JRuby's Java integration (not loaded at boot in this build, see Ruby.initJavaSupport)
    dst.writestr('lib/ruby/stdlib/java.rb', "# browser build: JRuby's Java integration (classes of java-classes.txt)\n"
                 "JRuby::Util.load_ext('org.jruby.javasupport.Java')\n"); count += 1
    with zipfile.ZipFile(jcodings_jar) as jc:  # jcodings tables (read by the patched ArrayReader)
        for info in jc.infolist():
            if info.filename.startswith('tables/') and not info.is_dir():
                dst.writestr('jcodings/' + info.filename, jc.read(info.filename)); count += 1
    with zipfile.ZipFile(sys.argv[5]) as jt:  # joda-time compiled tz database (org.joda.time.DateTimeZone.Folder)
        for info in jt.infolist():
            if info.filename.startswith('org/joda/time/tz/data/') and not info.is_dir():
                dst.writestr('joda/tzdata/' + info.filename[len('org/joda/time/tz/data/'):], jt.read(info.filename)); count += 1
print(f'{out}: {count} files, {os.path.getsize(out)} bytes')
