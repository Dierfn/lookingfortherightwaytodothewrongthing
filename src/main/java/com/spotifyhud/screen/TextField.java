package com.spotifyhud.screen;

/** Minimal single-line text input (avoids depending on vanilla EditBox internals). */
public final class TextField {
    public final StringBuilder text = new StringBuilder();
    public final int maxLen;

    public TextField(String initial, int maxLen) {
        this.maxLen = maxLen;
        if (initial != null) text.append(initial);
    }

    public void type(int codePoint) {
        if (codePoint < 32 || codePoint == 127) return;
        if (text.length() < maxLen) text.appendCodePoint(codePoint);
    }

    public void paste(String s) {
        if (s == null) return;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            type(cp);
        }
    }

    public void backspace() {
        if (!text.isEmpty()) text.deleteCharAt(text.length() - 1);
    }

    public String get() {
        return text.toString();
    }
}
