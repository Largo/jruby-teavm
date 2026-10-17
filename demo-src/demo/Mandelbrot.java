package demo;

/**
 * Demo class for JRuby's Java integration in the browser: the Mandelbrot set, computed in Java (compiled to
 * JavaScript by TeaVM together with JRuby). Ruby code constructs it (overloaded constructors), calls instance and
 * static methods, gets {@code int[]} rows back, and passes a block (or any Ruby object) as a {@link Listener}
 * that this Java code calls back for every row.
 *
 * Listed in java-classes.txt, which is what makes it visible to Ruby as {@code Java::Demo::Mandelbrot}.
 */
public class Mandelbrot {
    private final int width;
    private final int height;
    private int maxIterations;
    private double centerX = -0.6;
    private double centerY = 0.0;
    private double span = 3.2; // width of the view in the complex plane
    private double cellAspect = 2.0; // height/width of one cell: 2 for text output, 1 for square pixels
    private long pixelsComputed;

    public Mandelbrot(int width, int height) {
        this(width, height, 32);
    }

    public Mandelbrot(int width, int height, int maxIterations) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("size must be positive: " + width + "x" + height);
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public long getPixelsComputed() {
        return pixelsComputed;
    }

    public double getCellAspect() {
        return cellAspect;
    }

    public void setCellAspect(double cellAspect) {
        this.cellAspect = cellAspect;
    }

    /** Moves the view; returns this, so calls can be chained. */
    public Mandelbrot zoom(double centerX, double centerY, double span) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.span = span;
        return this;
    }

    /** Escape-time iteration count for c = re + im*i (maxIterations means "inside the set"). */
    public static int iterations(double re, double im, int maxIterations) {
        double x = 0, y = 0;
        int n = 0;
        while (n < maxIterations && x * x + y * y <= 4.0) {
            double t = x * x - y * y + re;
            y = 2 * x * y + im;
            x = t;
            n++;
        }
        return n;
    }

    public int iterations(double re, double im) {
        return iterations(re, im, maxIterations);
    }

    /** Iteration counts of one row of the view (cells are cellAspect times as high as wide). */
    public int[] row(int y) {
        int[] counts = new int[width];
        double step = span / width;
        double im = centerY + (y - height / 2.0) * step * cellAspect;
        for (int x = 0; x < width; x++) {
            counts[x] = iterations(centerX + (x - width / 2.0) * step, im);
        }
        pixelsComputed += width;
        return counts;
    }

    /** Computes every row and hands it to the listener (Java calling back into Ruby); returns the pixel count. */
    public long render(Listener listener) {
        long before = pixelsComputed;
        for (int y = 0; y < height; y++) {
            listener.row(y, row(y));
        }
        return pixelsComputed - before;
    }

    /** The whole picture as text, one character of the palette per cell (first = outside, last = inside). */
    public String toAscii(String palette) {
        StringBuilder out = new StringBuilder((width + 1) * height);
        int last = palette.length() - 1;
        for (int y = 0; y < height; y++) {
            for (int count : row(y)) {
                out.append(palette.charAt((int) ((long) count * last / maxIterations)));
            }
            out.append('\n');
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return "Mandelbrot[" + width + "x" + height + ", maxIterations=" + maxIterations + "]";
    }
}
