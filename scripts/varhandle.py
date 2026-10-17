#!/usr/bin/env python3
"""varhandle.py FILE... : rewrite simple VarHandle field idioms into plain field accesses (TeaVM has no
java.lang.invoke). Handles:  H.compareAndSet(this, null, EXPR) / H.compareAndSet(OBJ, OLD, NEW) /
H.getAndAdd(this, N) / H.get(this) / H.set(this, V) / H.getVolatile(..) / H.setVolatile(..)
where H was initialised as  H = ...findVarHandle(Owner.class, "field", T.class).
Declarations and the static init blocks must be removed by hand. Prints what it changed."""
import re, sys

def args_at(s, i):
    # s[i] == '(' ; return (list_of_args, end_index_after_close)
    depth = 0; cur = ''; out = []; j = i
    while True:
        c = s[j]
        if c in '([{':
            depth += 1
            if depth > 1: cur += c
        elif c in ')]}':
            depth -= 1
            if depth == 0:
                out.append(cur.strip()); return out, j + 1
            cur += c
        elif c == ',' and depth == 1:
            out.append(cur.strip()); cur = ''
        elif c == '"':
            k = j + 1
            while s[k] != '"':
                if s[k] == '\\': k += 1
                k += 1
            cur += s[j:k+1]; j = k
        else:
            cur += c
        j += 1

for f in sys.argv[1:]:
    s = open(f).read()
    handles = dict(re.findall(r'(\w+)\s*=\s*(?:MethodHandles\.)?lookup\(\)\s*\.findVarHandle\(\s*[\w.]+\.class\s*,\s*"(\w+)"', s))
    n = 0
    for h, field in handles.items():
        while True:
            m = re.search(r'\b' + h + r'\.(compareAndSet|getAndAdd|getAndSet|get|set|getVolatile|setVolatile|getAcquire|setRelease)\s*\(', s)
            if not m: break
            op = m.group(1)
            a, end = args_at(s, m.end() - 1)
            obj = a[0]
            fld = f'{obj}.{field}'
            if op == 'compareAndSet' and a[1] == 'null':
                rep = f'({fld} == null && ({fld} = ({", ".join(a[2:])})) != null)'
            elif op == 'compareAndSet':
                rep = f'org.jruby.util.TeaVMCompat.casField(() -> {fld}, v -> {fld} = v, {a[1]}, {a[2]})'
            elif op == 'getAndAdd':
                rep = f'(({fld} += ({a[1]})) - ({a[1]}))'
            elif op == 'getAndSet':
                rep = f'org.jruby.util.TeaVMCompat.getAndSet(() -> {fld}, v -> {fld} = v, {a[1]})'
            elif op in ('get', 'getVolatile', 'getAcquire'):
                rep = f'{fld}'
            else:
                rep = f'({fld} = {a[1]})'
            s = s[:m.start()] + rep + '/* TEAVM: was VarHandle.' + op + ' */' + s[end:]
            n += 1
    for h in handles:
        s, k = re.subn(r'(?m)^(\s*)(?:private |protected |public )?(?:static )?(?:final )?VarHandle ' + h + r';', r'\1// TEAVM: VarHandle ' + h + ' removed', s)
    # static { try { <only findVarHandle assignments> } catch (...) { throw ...; } }
    s = re.sub(r'(?ms)^(\s*)static \{\s*try \{((?:\s*\w+\s*=\s*(?:MethodHandles\.)?lookup\(\)\s*\.findVarHandle\([^;]*\);)+)\s*\} catch \([^)]*\) \{\s*throw [^;]*;\s*\}\s*\}\n',
               r'\1// TEAVM: VarHandle static initializer removed\n', s)
    open(f, 'w').write(s)
    print(f, 'handles:', handles, 'rewrites:', n)
