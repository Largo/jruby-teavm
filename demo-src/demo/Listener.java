package demo;

/**
 * Callback interface of the {@link Mandelbrot} demo. Ruby implements it with a block
 * ({@code m.render { |y, row| ... }}), with {@code Java::Demo::Listener.impl { |name, y, row| ... }} or with a class
 * that does {@code include Java::Demo::Listener}; TeaVM generates the java.lang.reflect.Proxy class for it at build
 * time because it is listed in java-classes.txt.
 */
public interface Listener {
    void row(int y, int[] iterations);
}
