package demo;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;

/**
 * A small drawing API over an HTML {@code <canvas>}, so that Ruby code in the page can draw directly
 * (web/visual.html). It is plain Java compiled by TeaVM together with JRuby; the browser calls go through TeaVM's
 * JSO bindings. Listed in java-classes.txt, which makes it {@code Java::Demo::Canvas2D} in Ruby:
 *
 * <pre>
 * canvas = Java::Demo::Canvas2D.new("stage")      # the canvas element with id="stage"
 * canvas.clear("#f4f1e8")
 * canvas.color = "#e4572e"                        # fill and stroke colour (any CSS colour)
 * canvas.fill_circle(100, 80, 20)
 * canvas.fill_box(200, 80, 40, 20, 0.3)           # a rectangle rotated around its centre (radians)
 * canvas.animate { |dt| ... }                     # the block runs once per animation frame
 * canvas.on_click { |x, y| ... }                  # pointer presses, in canvas pixels
 * </pre>
 *
 * {@link #animate} runs its loop on a TeaVM green thread of its own and returns at once: the thread waits for the
 * browser's {@code requestAnimationFrame} and then calls the Ruby block. Presses are queued and handed to the
 * {@link #onClick} block on that same thread between two frames, so Ruby code never runs concurrently with a
 * frame. A new Canvas2D for the same element, {@link #stop}, or a change of the element's {@code data-run}
 * attribute (the page bumps it before each run) ends the animation. An exception in a block stops it too; the
 * message is dispatched as a {@code rubyerror} DOM event on the canvas element (the page shows it).
 *
 * Coordinates are canvas pixels: the size in the element's width/height attributes. The drawing buffer behind them
 * is devicePixelRatio times larger, so lines and text stay sharp on HiDPI screens.
 */
public class Canvas2D {
    /** The block of {@link #animate}: called once per animation frame with the seconds since the previous one. */
    public interface Frame {
        void frame(double dt);
    }

    /** The block of {@link #onClick}: a press on the canvas, in canvas pixel coordinates. */
    public interface Click {
        void click(double x, double y);
    }

    private static final Map<String, Canvas2D> LATEST = new HashMap<>();

    private final String id;
    private final HTMLCanvasElement element;
    private final CanvasRenderingContext2D ctx;
    private final String runToken;
    private final double ratio; // device pixels per canvas pixel: the backing store is that much larger (sharp on HiDPI)
    private int width;
    private int height;
    private String color = "#000000";
    private Click clickHandler;
    private final ArrayDeque<double[]> presses = new ArrayDeque<>();
    private int generation; // bumped by animate() and stop(): an animation loop runs while its number is current
    private boolean animating;
    private long frames;
    private double fps;
    private double frameMs;

    public Canvas2D(String id) {
        this.id = id;
        this.element = find(id);
        if (element == null) {
            throw new IllegalArgumentException("no <canvas id=\"" + id + "\"> in the page");
        }
        this.ctx = context2d(element);
        this.runToken = runToken(element);
        this.ratio = Math.max(1, Math.min(4, devicePixelRatio()));
        Canvas2D previous = LATEST.put(id, this);
        if (previous != null) {
            previous.stop();
        }
        onPress(element, (fx, fy) -> press(fx * width, fy * height));
        setSize(logicalSize(element, true), logicalSize(element, false)); // the size in the HTML (or a previous Canvas2D's)
    }

    public Canvas2D(String id, int width, int height) {
        this(id);
        setSize(width, height);
    }

    // ---- size and state ------------------------------------------------------------------------------------

    /** Width in canvas pixels (the coordinates of all drawing calls), as in the element's width attribute. */
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** Resizes the canvas (in canvas pixels; the page's CSS decides the size on screen). Clears it. */
    public void setSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        element.setWidth((int) Math.round(width * ratio));
        element.setHeight((int) Math.round(height * ratio));
        rememberSize(element, width, height);
        reset();
    }

    private void reset() {
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        ctx.setLineCap("round");
        ctx.setLineJoin("round");
        ctx.setFont("14px system-ui, sans-serif");
        setColor(color);
    }

    // ---- style ---------------------------------------------------------------------------------------------

    /** Fill and stroke colour for the following calls: any CSS colour ("#e4572e", "tomato", "rgba(0,0,0,.5)"). */
    public void setColor(String color) {
        this.color = color;
        ctx.setFillStyle(color);
        ctx.setStrokeStyle(color);
    }

    public String getColor() {
        return color;
    }

    public void setLineWidth(double width) {
        ctx.setLineWidth(width);
    }

    /** CSS font of {@link #text}, e.g. "bold 16px sans-serif". */
    public void setFont(String font) {
        ctx.setFont(font);
    }

    /** "left", "center" or "right": where {@link #text} puts x. */
    public void setTextAlign(String align) {
        ctx.setTextAlign(align);
    }

    /** Opacity of what follows, 0.0 to 1.0. */
    public void setAlpha(double alpha) {
        ctx.setGlobalAlpha(alpha);
    }

    // ---- drawing (all methods return this, so calls can be chained) ----------------------------------------

    /** Makes the whole canvas transparent. */
    public Canvas2D clear() {
        ctx.save();
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.clearRect(0, 0, element.getWidth(), element.getHeight());
        ctx.restore();
        return this;
    }

    /** Paints the whole canvas with a colour (the current colour stays). */
    public Canvas2D clear(String background) {
        ctx.save();
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.setFillStyle(background);
        ctx.fillRect(0, 0, element.getWidth(), element.getHeight());
        ctx.restore();
        return this;
    }

    public Canvas2D fillRect(double x, double y, double w, double h) {
        ctx.fillRect(x, y, w, h);
        return this;
    }

    public Canvas2D strokeRect(double x, double y, double w, double h) {
        ctx.strokeRect(x, y, w, h);
        return this;
    }

    /** A w x h rectangle centred on (cx, cy), rotated by angle radians (clockwise on screen). */
    public Canvas2D fillBox(double cx, double cy, double w, double h, double angle) {
        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(angle);
        ctx.fillRect(-w / 2, -h / 2, w, h);
        ctx.restore();
        return this;
    }

    public Canvas2D strokeBox(double cx, double cy, double w, double h, double angle) {
        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(angle);
        ctx.strokeRect(-w / 2, -h / 2, w, h);
        ctx.restore();
        return this;
    }

    public Canvas2D fillCircle(double x, double y, double r) {
        ctx.beginPath();
        ctx.arc(x, y, r, 0, 2 * Math.PI);
        ctx.fill();
        return this;
    }

    public Canvas2D strokeCircle(double x, double y, double r) {
        ctx.beginPath();
        ctx.arc(x, y, r, 0, 2 * Math.PI);
        ctx.stroke();
        return this;
    }

    public Canvas2D line(double x1, double y1, double x2, double y2) {
        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x2, y2);
        ctx.stroke();
        return this;
    }

    /** A filled polygon through the points x1, y1, x2, y2, ... (a Ruby Array or separate arguments). */
    public Canvas2D fillPolygon(double... xy) {
        if (path(xy)) {
            ctx.fill();
        }
        return this;
    }

    public Canvas2D strokePolygon(double... xy) {
        if (path(xy)) {
            ctx.stroke();
        }
        return this;
    }

    private boolean path(double[] xy) {
        if (xy.length < 4) {
            return false;
        }
        ctx.beginPath();
        ctx.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) {
            ctx.lineTo(xy[i], xy[i + 1]);
        }
        ctx.closePath();
        return true;
    }

    public Canvas2D text(String text, double x, double y) {
        ctx.fillText(text, x, y);
        return this;
    }

    /** The colour of the canvas pixel at (x, y) as 0xRRGGBB (what was drawn there; transparent reads as black). */
    public int pixel(double x, double y) {
        return pixelAt(ctx, (int) Math.floor(x * ratio), (int) Math.floor(y * ratio));
    }

    // transforms (as in the canvas API): translate / rotate / scale apply to what is drawn until restore

    public Canvas2D save() {
        ctx.save();
        return this;
    }

    public Canvas2D restore() {
        ctx.restore();
        return this;
    }

    public Canvas2D translate(double x, double y) {
        ctx.translate(x, y);
        return this;
    }

    public Canvas2D rotate(double angle) {
        ctx.rotate(angle);
        return this;
    }

    public Canvas2D scale(double sx, double sy) {
        ctx.scale(sx, sy);
        return this;
    }

    // ---- animation and input -------------------------------------------------------------------------------

    /**
     * Calls the block once per browser animation frame (requestAnimationFrame), on a green thread of its own, until
     * {@link #stop} or a newer Canvas2D for this element. Returns at once; a running animation of this canvas is
     * replaced.
     */
    public void animate(Frame frame) {
        int number = ++generation;
        animating = true;
        presses.clear();
        Thread thread = new Thread(() -> loop(frame, number), "canvas-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        generation++;
        animating = false;
    }

    public boolean isRunning() {
        return animating && current(generation);
    }

    /** Calls the block for every press (mouse or touch) on the canvas, with canvas pixel coordinates. */
    public void onClick(Click handler) {
        clickHandler = handler;
    }

    /** Animation frames per second over the last second (0 before the first second). */
    public double getFps() {
        return fps;
    }

    /** Average time per frame spent in the {@link #animate} block over the last second, in milliseconds. */
    public double getFrameMs() {
        return frameMs;
    }

    /** Frames drawn by the current animation so far. */
    public long getFrames() {
        return frames;
    }

    private boolean current(int number) {
        return number == generation && LATEST.get(id) == this && runToken.equals(runToken(element));
    }

    private void loop(Frame frame, int number) {
        Semaphore signal = new Semaphore(0);
        double[] now = new double[1];
        double last = -1;
        double windowStart = -1;
        double busy = 0;
        int count = 0;
        frames = 0;
        try {
            while (true) {
                Window.requestAnimationFrame(timestamp -> {
                    now[0] = timestamp;
                    signal.release();
                });
                signal.acquire(); // parks this green thread until the browser's next frame
                if (!current(number)) {
                    break;
                }
                double dt = last < 0 ? 1.0 / 60 : Math.min(0.1, (now[0] - last) / 1000);
                last = now[0];
                double start = performanceNow();
                while (!presses.isEmpty() && clickHandler != null) {
                    double[] p = presses.poll();
                    clickHandler.click(p[0], p[1]);
                }
                frame.frame(dt);
                busy += performanceNow() - start;
                count++;
                frames++;
                if (windowStart < 0) {
                    windowStart = now[0];
                } else if (now[0] - windowStart >= 1000) {
                    fps = count * 1000 / (now[0] - windowStart);
                    frameMs = busy / count;
                    publishStats(element, fps, frameMs, frames);
                    windowStart = now[0];
                    busy = 0;
                    count = 0;
                }
            }
        } catch (Throwable t) {
            if (current(number)) {
                report(t);
            }
        } finally {
            if (number == generation) {
                animating = false;
            }
        }
    }

    private void press(double x, double y) {
        Click handler = clickHandler;
        if (handler == null) {
            return;
        }
        if (isRunning()) {
            presses.add(new double[] { x, y }); // handled by the animation thread before its next frame
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                handler.click(x, y);
            } catch (Throwable t) {
                report(t);
            }
        }, "canvas-click");
        thread.setDaemon(true);
        thread.start();
    }

    private void report(Throwable t) {
        String message = t.getMessage();
        String text = message == null || message.isEmpty() ? t.toString() : message;
        System.err.println("Canvas2D(" + id + "): " + text);
        dispatchError(element, text);
    }

    @Override
    public String toString() {
        return "Canvas2D[#" + id + " " + getWidth() + "x" + getHeight() + "]";
    }

    // ---- JavaScript ----------------------------------------------------------------------------------------

    @JSFunctor
    interface PressListener extends JSObject {
        void press(double fractionX, double fractionY);
    }

    @JSBody(params = "id", script = "var e = document.getElementById(id);"
            + " return e && e.getContext ? e : null;")
    private static native HTMLCanvasElement find(String id);

    @JSBody(params = "c", script = "return c.getContext('2d');")
    private static native CanvasRenderingContext2D context2d(HTMLCanvasElement c);

    @JSBody(params = "c", script = "return c.dataset.run || '';")
    private static native String runToken(HTMLCanvasElement c);

    @JSBody(script = "return performance.now();")
    private static native double performanceNow();

    @JSBody(script = "return window.devicePixelRatio || 1;")
    private static native double devicePixelRatio();

    // the canvas size Ruby sees; the width/height attributes hold the (larger) backing store size once a Canvas2D ran
    @JSBody(params = { "c", "w" }, script = "var v = w ? c.dataset.canvasWidth : c.dataset.canvasHeight;"
            + " return v ? +v : (w ? c.width : c.height);")
    private static native int logicalSize(HTMLCanvasElement c, boolean w);

    @JSBody(params = { "c", "w", "h" }, script = "c.dataset.canvasWidth = w; c.dataset.canvasHeight = h;")
    private static native void rememberSize(HTMLCanvasElement c, int w, int h);

    @JSBody(params = { "ctx", "x", "y" }, script = "var d = ctx.getImageData(x, y, 1, 1).data;"
            + " return (d[0] << 16) | (d[1] << 8) | d[2];")
    private static native int pixelAt(CanvasRenderingContext2D ctx, int x, int y);

    // presses as fractions of the element's size on screen, whatever size CSS gives it
    @JSBody(params = { "c", "f" }, script = "c.onpointerdown = function(e) {"
            + " var r = c.getBoundingClientRect();"
            + " f((e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height);"
            + " e.preventDefault(); };")
    private static native void onPress(HTMLCanvasElement c, PressListener f);

    @JSBody(params = { "c", "message" }, script = "c.dispatchEvent(new CustomEvent('rubyerror',"
            + " { detail: message, bubbles: true }));")
    private static native void dispatchError(HTMLCanvasElement c, String message);

    // for the page and the screenshots: window.__canvasStats = { id, fps, frameMs, frames }
    @JSBody(params = { "c", "fps", "ms", "frames" }, script = "window.__canvasStats = { id: c.id,"
            + " fps: Math.round(fps * 10) / 10, frameMs: Math.round(ms * 10) / 10, frames: frames };"
            + " c.dispatchEvent(new CustomEvent('canvasstats', { detail: window.__canvasStats }));")
    private static native void publishStats(HTMLCanvasElement c, double fps, double ms, double frames);
}
