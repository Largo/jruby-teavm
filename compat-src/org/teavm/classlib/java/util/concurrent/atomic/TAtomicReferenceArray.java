package org.teavm.classlib.java.util.concurrent.atomic;

import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/** compat: java.util.concurrent.atomic.AtomicReferenceArray for TeaVM (single-threaded). */
public class TAtomicReferenceArray<E> implements java.io.Serializable {
    private final Object[] array;

    public TAtomicReferenceArray(int length) {
        array = new Object[length];
    }

    public TAtomicReferenceArray(E[] array) {
        this.array = Arrays.copyOf(array, array.length, Object[].class);
    }

    public final int length() {
        return array.length;
    }

    @SuppressWarnings("unchecked")
    public final E get(int i) {
        return (E) array[i];
    }

    public final void set(int i, E newValue) {
        array[i] = newValue;
    }

    public final void lazySet(int i, E newValue) {
        array[i] = newValue;
    }

    public final E getPlain(int i) { return get(i); }
    public final void setPlain(int i, E v) { set(i, v); }
    public final E getAcquire(int i) { return get(i); }
    public final void setRelease(int i, E v) { set(i, v); }
    public final E getOpaque(int i) { return get(i); }
    public final void setOpaque(int i, E v) { set(i, v); }

    public final E getAndSet(int i, E newValue) {
        E old = get(i);
        array[i] = newValue;
        return old;
    }

    public final boolean compareAndSet(int i, E expectedValue, E newValue) {
        if (array[i] == expectedValue) {
            array[i] = newValue;
            return true;
        }
        return false;
    }

    public final boolean weakCompareAndSet(int i, E expectedValue, E newValue) {
        return compareAndSet(i, expectedValue, newValue);
    }

    public final boolean weakCompareAndSetPlain(int i, E expectedValue, E newValue) {
        return compareAndSet(i, expectedValue, newValue);
    }

    public final E getAndUpdate(int i, UnaryOperator<E> f) {
        E old = get(i);
        array[i] = f.apply(old);
        return old;
    }

    public final E updateAndGet(int i, UnaryOperator<E> f) {
        E v = f.apply(get(i));
        array[i] = v;
        return v;
    }

    public final E getAndAccumulate(int i, E x, BinaryOperator<E> f) {
        E old = get(i);
        array[i] = f.apply(old, x);
        return old;
    }

    public final E accumulateAndGet(int i, E x, BinaryOperator<E> f) {
        E v = f.apply(get(i), x);
        array[i] = v;
        return v;
    }

    @Override
    public String toString() {
        return Arrays.toString(array);
    }
}
