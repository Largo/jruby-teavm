#!/bin/bash
# compile.sh: javac the (patched) JRuby core + launcher with JRuby's own annotation processor,
# then compile the generated $POPULATOR sources and run InvokerGenerator to emit $INVOKER$ classes.
# Output: build/classes (everything TeaVM needs from JRuby itself).
set -eu
here=$(cd "$(dirname "$0")/.." && pwd); cd "$here"
SHARED=${SHARED:-$here/shared}
CP=$(ls "$here"/lib/compile/*.jar "$here"/lib/runtime/*.jar 2>/dev/null | tr '\n' :)
STOCK_BASE=$SHARED/jars/jruby-base-deps/jruby-base-10.1.2.0.jar
JFLAGS="-J-Xmx4g -nowarn -encoding UTF-8 --release 21 -XDignore.symbol.file -Xmaxerrs 2000"
rm -rf build/anno build/classes build/work && mkdir -p build/gen-s build/anno build/classes build/work/target/generated-sources/org/jruby/gen
echo "[0/4] compat (TeaVM classlib gap fillers)"
rm -rf build/compat build/compat-gen && mkdir -p build/compat build/compat-gen/org/teavm/classlib/java/util
# compat-ext/**/T*.java.add: TeaVM 0.16.0's own T-class source with the missing members appended
# (classes earlier on TeaVM's -p win, so these replace the stock ones)
TVSRC=$SHARED/src/teavm-0.16.0/classlib/src/main/java
for add in $(cd compat-ext && find . -name '*.java.add'); do
  rel=${add%.add}; mkdir -p build/compat-gen/$(dirname $rel)
  { sed '$d' $TVSRC/$rel; cat compat-ext/$add; } > build/compat-gen/$rel
done
# compat-ext/**/T*.java.sed: TeaVM's own source with a bug fixed by a sed script (must change the file)
for fix in $(cd compat-ext && find . -name '*.java.sed'); do
  rel=${fix%.sed}; mkdir -p build/compat-gen/$(dirname $rel)
  in=$TVSRC/$rel; [ -f build/compat-gen/$rel ] && in=build/compat-gen/$rel.in && mv build/compat-gen/$rel $in  # .add first
  sed -f compat-ext/$fix $in > build/compat-gen/$rel
  # every s/// line must match exactly one line of the input (guards against a changed TeaVM source)
  python3 - "$in" compat-ext/$fix <<'PY' || exit 1
import re, sys
src = open(sys.argv[1]).read().split('\n')
for line in open(sys.argv[2]):
    if not line.startswith('s/'): continue
    pat = re.match(r's/((?:\\.|[^/\\])*)/', line).group(1)
    rx = re.compile(pat.replace('\\(', '(').replace('\\)', ')').replace('(', '\\(').replace(')', '\\)')
                    .replace('\\[', '[').replace('\\]', ']').replace('[', '\\[').replace(']', '\\]')
                    .replace('\\.', '.').replace('.', '\\.').replace('\\*', '*').replace('*', '\\*')
                    .replace('\\+', '+').replace('+', '\\+').replace('\\?', '?').replace('?', '\\?')
                    .replace('\\{', '{').replace('{', '\\{').replace('\\}', '}').replace('}', '\\}')
                    .replace('\\|', '|').replace('|', '\\|'))
    n = sum(1 for l in src if rx.search(l))
    if n != 1: print(f'{sys.argv[2]}: {n} matches for {pat}'); sys.exit(1)
PY
  rm -f build/compat-gen/$rel.in
done
TCP=$(ls "$SHARED"/jars/teavm/lib/*.jar | tr '\n' :)
javac $JFLAGS -proc:none -cp "$TCP" -d build/compat $(find compat-src build/compat-gen -name '*.java')
echo "[0a] TeaVM compiler plugins (assertion removal; Java integration: reflection policy, Proxy classes)"
rm -rf build/teavm-plugin && mkdir -p build/teavm-plugin/META-INF/services
javac $JFLAGS -proc:none -cp "$TCP" -d build/teavm-plugin $(find teavm-plugin -name '*.java')
cp teavm-plugin/META-INF/services/* build/teavm-plugin/META-INF/services/
cp java-classes.txt build/teavm-plugin/jrubyweb/   # read by JavaClassesPolicy (reflection metadata, proxies)
echo "[0b] deps-patches (patched copies of dependency classes; they precede the jars on TeaVM's -p)"
rm -rf build/deps-patches && mkdir -p build/deps-patches
javac $JFLAGS -proc:none -cp "$CP" -d build/deps-patches $(find deps-patches/src -name '*.java')
echo "[1/4] annotation processor"
javac $JFLAGS -proc:none -cp "$CP" -sourcepath src -implicit:class -d build/anno \
  src/org/jruby/anno/{FrameField,AnnotationBinder,JRubyMethod}.java src/org/jruby/runtime/Visibility.java \
  src/org/jruby/util/{CodegenUtils,SafePropertyAccessor}.java
echo "[2/4] core + launcher (with AnnotationBinder)"
find "$here/src" "$here/gems-src" "$here/demo-src" -name '*.java' > build/work/sources.txt
( cd build/work && javac $JFLAGS -cp "$here/build/anno:$CP" -processorpath "$here/build/anno:$CP" \
    -processor org.jruby.anno.AnnotationBinder -s "$here/build/gen-s" -implicit:none -d "$here/build/classes" @sources.txt ) \
  > build/javac.log 2>&1 || { grep -E 'error:' -A2 build/javac.log | head -150; grep -c 'error:' build/javac.log; exit 1; }
echo "[3/4] invokers"
cp build/work/target/generated-sources/annotated_classes.txt build/annotated_classes.txt
# InvokerGenerator (+ its ASM-based DumpingInvocationMethodFactory) is pruned from the browser tree, so the
# build-time generator runs from the stock jruby-base jar, AFTER our patched classes on the classpath.
java -Xmx2g -Djruby.bytecode.version=21 -cp "build/classes:$CP:$STOCK_BASE" org.jruby.anno.InvokerGenerator build/annotated_classes.txt build/classes
echo "[4/4] populators: $(ls build/work/target/generated-sources/org/jruby/gen | wc -l)"
scripts/gen-registry.sh build/work/target/generated-sources/org/jruby/gen build/registry
javac $JFLAGS -proc:none -cp "build/classes:$CP" -d build/classes build/work/target/generated-sources/org/jruby/gen/*.java build/registry/org/jruby/gen/PopulatorRegistryImpl.java build/registry/org/jruby/gen/JavaClassRegistryImpl.java
mkdir -p build/classes/META-INF/services && cp build/registry/META-INF/services/* build/classes/META-INF/services/
echo "invokers: $(find build/classes -name '*\$INVOKER\$*' | wc -l)  populators: $(ls build/classes/org/jruby/gen | wc -l)"
