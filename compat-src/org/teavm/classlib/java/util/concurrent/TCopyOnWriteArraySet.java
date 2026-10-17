package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** compat: java.util.concurrent.CopyOnWriteArraySet for TeaVM (array copied on every write; iterators see a snapshot). */
public class TCopyOnWriteArraySet<E> extends AbstractSet<E> implements java.io.Serializable {
    private Object[] elements = new Object[0];

    public TCopyOnWriteArraySet() {
    }

    public TCopyOnWriteArraySet(Collection<? extends E> c) {
        addAll(c);
    }

    @Override
    public int size() {
        return elements.length;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    private int indexOf(Object o) {
        Object[] es = elements;
        for (int i = 0; i < es.length; i++) {
            if (o == null ? es[i] == null : o.equals(es[i])) return i;
        }
        return -1;
    }

    @Override
    public boolean add(E e) {
        if (indexOf(e) >= 0) return false;
        Object[] es = Arrays.copyOf(elements, elements.length + 1);
        es[es.length - 1] = e;
        elements = es;
        return true;
    }

    @Override
    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) return false;
        Object[] es = new Object[elements.length - 1];
        System.arraycopy(elements, 0, es, 0, i);
        System.arraycopy(elements, i + 1, es, i, elements.length - i - 1);
        elements = es;
        return true;
    }

    @Override
    public void clear() {
        elements = new Object[0];
    }

    @Override
    public Iterator<E> iterator() {
        final Object[] snapshot = elements;
        return new Iterator<E>() {
            int i;

            @Override
            public boolean hasNext() {
                return i < snapshot.length;
            }

            @Override
            @SuppressWarnings("unchecked")
            public E next() {
                if (i >= snapshot.length) throw new NoSuchElementException();
                return (E) snapshot[i++];
            }
        };
    }
}
