/*
 * TEAVM: Cliff Click's lock-free NonBlockingHashMapLong (sun.misc.Unsafe / VarHandle CAS) replaced by a
 * HashMap-backed map with the same public API. The browser build runs on a single (green) thread.
 * The original lives in upstream JRuby (core/src/main/java/org/jruby/util/collections/NonBlockingHashMapLong.java).
 */
package org.jruby.util.collections;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class NonBlockingHashMapLong<TypeV> extends AbstractMap<Long, TypeV> implements ConcurrentMap<Long, TypeV>, Serializable {
    private static final long serialVersionUID = 1234123412341234124L;

    private final HashMap<Long, TypeV> map;

    public NonBlockingHashMapLong() { this(8, true); }
    public NonBlockingHashMapLong(final int initial_sz) { this(initial_sz, true); }
    public NonBlockingHashMapLong(final boolean opt_for_space) { this(1, opt_for_space); }
    public NonBlockingHashMapLong(final int initial_sz, final boolean opt_for_space) {
        map = new HashMap<>(Math.max(initial_sz, 1));
    }

    public int size() { return map.size(); }
    public boolean containsKey(long key) { return map.containsKey(key); }
    public boolean contains(Object val) { return map.containsValue(val); }
    public TypeV put(long key, TypeV val) { return map.put(key, val); }
    public TypeV putIfAbsent(long key, TypeV val) { return map.putIfAbsent(key, val); }
    public TypeV remove(long key) { return map.remove(key); }
    public boolean remove(long key, Object val) { return map.remove(key, val); }
    public TypeV replace(long key, TypeV val) { return map.replace(key, val); }
    public boolean replace(long key, TypeV oldValue, TypeV newValue) { return map.replace(key, oldValue, newValue); }
    public void clear() { map.clear(); }
    public boolean containsValue(Object val) { return map.containsValue(val); }
    public TypeV get(long key) { return map.get(key); }

    @Override public TypeV get(Object key) { return (key instanceof Long) ? get(((Long) key).longValue()) : null; }
    @Override public TypeV remove(Object key) { return (key instanceof Long) ? remove(((Long) key).longValue()) : null; }
    @Override public boolean remove(Object key, Object val) { return (key instanceof Long) ? remove(((Long) key).longValue(), val) : false; }
    @Override public boolean containsKey(Object key) { return (key instanceof Long) ? containsKey(((Long) key).longValue()) : false; }
    @Override public TypeV putIfAbsent(Long key, TypeV val) { return putIfAbsent(key.longValue(), val); }
    @Override public TypeV replace(Long key, TypeV val) { return replace(key.longValue(), val); }
    @Override public TypeV put(Long key, TypeV val) { return put(key.longValue(), val); }
    @Override public boolean replace(Long key, TypeV oldValue, TypeV newValue) { return replace(key.longValue(), oldValue, newValue); }

    public Enumeration<TypeV> elements() { return Collections.enumeration(map.values()); }
    public Enumeration<Long> keys() { return Collections.enumeration(map.keySet()); }
    @Override public Collection<TypeV> values() { return map.values(); }
    @Override public Set<Long> keySet() { return map.keySet(); }
    @Override public Set<Map.Entry<Long, TypeV>> entrySet() { return map.entrySet(); }
}
