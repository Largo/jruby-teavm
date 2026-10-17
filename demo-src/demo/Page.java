package demo;

import org.teavm.jso.JSBody;

/**
 * The page around the Ruby code (web/visual.html), for Ruby: {@code Java::Demo::Page.value("qr-text")} reads a
 * form field, {@code Java::Demo::Page.set_text("note", "...")} replaces an element's text. Listed in
 * java-classes.txt.
 */
public final class Page {
    private Page() {
    }

    /** The value of the input, textarea or select element with this id ("" if there is none). */
    public static String value(String id) {
        return valueOf(id);
    }

    /** Replaces the text content of the element with this id; false if there is none. */
    public static boolean setText(String id, String text) {
        return textOf(id, text);
    }

    @JSBody(params = "id", script = "var e = document.getElementById(id); return e && e.value != null ? String(e.value) : '';")
    private static native String valueOf(String id);

    @JSBody(params = { "id", "text" }, script = "var e = document.getElementById(id); if (!e) return false;"
            + " e.textContent = text; return true;")
    private static native boolean textOf(String id, String text);
}
