package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;

/** compat: java.util.concurrent.ConcurrentLinkedQueue for TeaVM (single-threaded, ArrayDeque-backed). */
public class TConcurrentLinkedQueue<E> extends AbstractQueue<E> implements java.io.Serializable {
    private final ArrayDeque<E> deque = new ArrayDeque<>();

    public TConcurrentLinkedQueue() {
    }

    public TConcurrentLinkedQueue(Collection<? extends E> c) {
        deque.addAll(c);
    }

    @Override
    public boolean offer(E e) {
        return deque.offer(e);
    }

    @Override
    public E poll() {
        return deque.poll();
    }

    @Override
    public E peek() {
        return deque.peek();
    }

    @Override
    public Iterator<E> iterator() {
        return deque.iterator();
    }

    @Override
    public int size() {
        return deque.size();
    }

    @Override
    public boolean remove(Object o) {
        return deque.remove(o);
    }

    @Override
    public boolean contains(Object o) {
        return deque.contains(o);
    }

    @Override
    public boolean isEmpty() {
        return deque.isEmpty();
    }
}
