package com.spotifyhud.screen;

import com.spotifyhud.Draw;
import com.spotifyhud.Themes;
import com.spotifyhud.Themes.Theme;
import com.spotifyhud.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Shared plumbing for the mod's screens: immediate-mode buttons (draw + register a click region in
 * one call), a tiny text field, theme colors, and scroll/key forwarding.
 */
public abstract class BaseScreen extends Screen {
    protected final Screen parent;
    protected int mx, my;
    protected TextField focus;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    protected BaseScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    protected Theme th() {
        return Themes.menu();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        Ui.menuOpen = true;
    }

    @Override
    public void removed() {
        Ui.menuOpen = false;
    }

    @Override
    public void onClose() {
        Ui.open(parent);
    }

    // ---- immediate-mode helpers -------------------------------------------------------------

    protected void hit(int x, int y, int w, int h, Runnable r) {
        hits.add(new Hit(x, y, w, h, r));
    }

    protected boolean hover(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean highlight, Runnable r) {
        Theme t = th();
        boolean hv = hover(x, y, w, h);
        int fill = highlight ? Themes.mix(t.bg(), t.accent(), hv ? 0.45f : 0.3f)
                : Themes.mix(t.bg(), 0xFFFFFFFF, hv ? 0.2f : 0.1f);
        Draw.roundOutline(g, x, y, w, h, highlight ? t.accent() : Themes.mix(t.bg(), 0xFFFFFFFF, 0.25f), fill);
        String s = Draw.fit(label, w - 6);
        Draw.text(g, s, x + (w - Draw.width(s)) / 2, y + (h - 8) / 2, t.text());
        hit(x, y, w, h, r);
    }

    // ---- sliders (click or drag) -------------------------------------------------------------

    private record Drag(int x, int w, DoubleConsumer onChange, Runnable onRelease) {}

    private Drag drag;
    private double clickX;

    /**
     * Draws nothing itself: registers a draggable region over a track starting at trackX, trackW wide,
     * with a few pixels of slack on each side. The value passed to
     * {@code onChange} is 0..1 across the width. {@code onRelease} runs once when the mouse is let go.
     */
    protected void sliderHit(int trackX, int trackW, int y, int h, DoubleConsumer onChange, Runnable onRelease) {
        hit(trackX - 3, y, trackW + 6, h, () -> {
            drag = new Drag(trackX, trackW, onChange, onRelease);
            onChange.accept(clamp01((clickX - trackX) / trackW));
        });
    }

    /** A flat slider: thin track, filled part, round knob. Click or drag to change. */
    protected void slider(GuiGraphicsExtractor g, int x, int y, int w, double value, int fill, int track,
                          DoubleConsumer onChange, Runnable onRelease) {
        boolean hv = hover(x - 3, y - 6, w + 6, 14) || (drag != null && drag.x == x);
        int cy = y + 2;
        Draw.rrect(g, x, cy - 1, w, 3, 1, track);
        int filled = (int) Math.round(w * clamp01(value));
        if (filled > 0) Draw.rrect(g, x, cy - 1, Math.max(2, filled), 3, 1, fill);
        Draw.circle(g, x + filled, cy, hv ? 3 : 2, 0xFFFFFFFF);
        sliderHit(x, w, y - 6, 14, onChange, onRelease);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        if (drag != null) {
            drag.onChange.accept(clamp01((e.x() - drag.x) / drag.w));
            return true;
        }
        return super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (drag != null) {
            Drag d = drag;
            drag = null;
            if (d.onRelease != null) d.onRelease.run();
            return true;
        }
        return super.mouseReleased(e);
    }

    protected void field(GuiGraphicsExtractor g, TextField f, int x, int y, int w, int h, String placeholder) {
        Theme t = th();
        boolean focused = focus == f;
        Draw.outline(g, x, y, w, h, focused ? t.accent() : Themes.mix(t.bg(), 0xFFFFFFFF, 0.35f));
        Draw.rect(g, x + 1, y + 1, w - 2, h - 2, 0xFF000000);
        String s = f.get();
        int ty = y + (h - 8) / 2;
        if (s.isEmpty() && !focused) {
            Draw.text(g, Draw.fit(placeholder, w - 6), x + 4, ty, 0xFF666670);
        } else {
            String shown = s;
            while (shown.length() > 1 && Draw.width(shown) > w - 10) shown = shown.substring(1);
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            Draw.text(g, shown + (focused && blink ? "_" : ""), x + 4, ty, 0xFFFFFFFF);
        }
        hit(x, y, w, h, () -> focus = f);
    }

    // ---- Screen overrides -------------------------------------------------------------------

    @Override
    public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        hits.clear();
        draw(g, delta);
    }

    protected abstract void draw(GuiGraphicsExtractor g, float delta);

    /** Called for left clicks that landed on nothing registered. */
    protected void onMiss(double x, double y) {
        focus = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != 0) return super.mouseClicked(e, doubleClick);
        double x = e.x(), y = e.y();
        clickX = x;
        TextField before = focus;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(x, y)) {
                if (focus == before) focus = null; // clicking elsewhere unfocuses unless the hit focuses a field
                h.action().run();
                return true;
            }
        }
        onMiss(x, y);
        return true;
    }

    protected void onScroll(double x, double y, double amount) {}

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        onScroll(x, y, scrollY);
        return true;
    }

    protected void onEnter(TextField f) {}

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (focus != null) {
            int k = e.key();
            if (k == 259) { // backspace
                focus.backspace();
                return true;
            }
            if (k == 257 || k == 335) { // enter
                onEnter(focus);
                return true;
            }
            if (k == 86 && e.hasControlDown()) { // ctrl+V
                focus.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            if (k == 256) { // esc just unfocuses
                focus = null;
                return true;
            }
            return true; // swallow other keys while typing
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (focus != null) {
            focus.type(e.codepoint());
            return true;
        }
        return super.charTyped(e);
    }
}
