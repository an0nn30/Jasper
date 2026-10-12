package dev.jasper.app.appearance;

import dev.jasper.app.plugins.KitViewTestAccess;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Real staged feature owners; no top-level Window, external connection or real user home. */
public final class SurfaceFixtures {
    private SurfaceFixtures() {}
    public static Opened open(String surfaceId, String state) throws Exception { return open(surfaceId, state, "dark"); }
    public static Opened open(String surfaceId, String state, String theme) throws Exception {
        Path home = Files.createTempDirectory("jasper-surface-");
        KitViewTestAccess owner = null;
        try {
            owner = new KitViewTestAccess(home, surfaceId, theme(theme));
            var opened = new Opened(home, owner, surfaceId, state);
            if (surfaceId.equals("fileserver-add")) {
                if (state.equals("vault")) { owner.click("server.login.option.vault"); owner.click("server.writable"); }
                else if (!java.util.Set.of("empty", "disabled", "focus").contains(state)) {
                    owner.type("server.name", "synthetic-preview-server");owner.type("server.folder",home.toString());
                }
                if(state.equals("error"))owner.error();opened.layout();return opened;
            }
            if (surfaceId.equals("host-add")) {
                if (!java.util.Set.of("empty", "disabled", "focus").contains(state)) {
                    owner.type("host.name","synthetic-preview-host");owner.type("host.hostname","synthetic-preview-host.invalid");owner.type("host.username","fixture");owner.click("host.auth.option.agent");
                }
                if(state.equals("error"))owner.error();
                opened.layout();return opened;
            }
            switch (state) {
                case "empty", "disabled", "focus" -> { }
                case "entered", "revealed", "busy" -> {
                    owner.type("password", "synthetic-preview-password");
                    if (surfaceId.equals("vault-create")) owner.type("confirm", "synthetic-preview-password");
                    if (state.equals("revealed")) owner.click("password.reveal");
                }
                case "mismatch" -> { owner.type("password", "synthetic-preview-password"); owner.type("confirm", "different-synthetic"); }
                case "error" -> owner.error();
                default -> throw new IllegalArgumentException("Unknown state: " + state);
            }
            // Reference binding is visual-only. Every actual create, including busy, stays unbound.
            if (surfaceId.equals("vault-create") && !state.equals("busy")) owner.click("bind");
            Thread.sleep(180); // Let the real switch animation settle before measuring its confirmed state.
            opened.layout();
            return opened;
        } catch (Exception | Error failure) { if (owner != null) owner.close(); delete(home); throw failure; }
    }

    /** Slate Steel's {@code "dark"} or {@code "light"} variant with its matching terminal scheme. */
    public static ResolvedTheme theme(String name) {
        return switch (name) {
            case "dark" -> ThemeTestSupport.resolved(true);
            case "light" -> ThemeTestSupport.resolved(false);
            default -> throw new IllegalArgumentException("Unknown surface family: " + name + " (dark or light)");
        };
    }

    public static final class Opened implements AutoCloseable {
        private final Path home;
        private final KitViewTestAccess owner;
        private final String id, state;
        private final int minimumHeight;
        private int width;
        private int height;
        private boolean closed;
        Opened(Path home, KitViewTestAccess owner, String id, String state) {
            this.home=home; this.owner=owner; this.id=id; this.state=state;
            width=com.formdev.flatlaf.util.UIScale.scale((id.equals("host-add") || id.equals("fileserver-add")) ? 540 : id.equals("vault-create") ? 400 : 380); minimumHeight=com.formdev.flatlaf.util.UIScale.scale(id.equals("fileserver-add") ? (state.equals("vault") ? 730 : 690) : id.equals("host-add") ? 732 : id.equals("vault-create") ? 428 : 264);
        }
        public KitView view() { return owner.view(); }
        public ViewSpec snapshot() { return edt(() -> view().spec()); }
        public Rectangle bounds(String id) {
            return edt(() -> { var c=owner.component(id); return SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),view()); });
        }
        public void type(String id, String text) { owner.type(id,text); }
        public Component component(String id) { return owner.component(id); }
        public void click(String id) { owner.click(id); }
        public void error() throws InterruptedException { owner.error(); }
        public void theme(String name) throws Exception { owner.theme(SurfaceFixtures.theme(name)); layout(); }
        public Dimension preferred() { return edt(() -> view().getPreferredSize()); }
        public void width(int value) { width = value; layout(); }
        public void layout() { edt(() -> { height = Math.max(minimumHeight, view().getPreferredSize().height); view().setSize(width,height); arrange(view()); return null; }); }
        /** Device scale2 is an actual Java2D transform. Synthetic focus is explicitly headless, never native proof. */
        public BufferedImage capture(int scale) {
            return edt(() -> {
                if (state.equals("busy")) owner.click(id.equals("fileserver-add") ? "server.save" : id.equals("host-add") ? "host.save" : "submit");
                layout();
                var previous=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                var focused=owner.component(id.equals("fileserver-add") ? "server.name.input" : id.equals("host-add") ? "host.name.input" : "password.input");
                boolean focus=state.equals("focus") || id.equals("vault-unlock") && state.equals("empty");
                try {
                    if (focus) {
                        KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                            @Override public Component getFocusOwner() { return focused; }
                        });
                        var event = new java.awt.event.FocusEvent(focused, java.awt.event.FocusEvent.FOCUS_GAINED, false, null, java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD);
                        for (var listener : focused.getFocusListeners()) listener.focusGained(event);
                    }
                    var image=new BufferedImage(width*scale,height*scale,BufferedImage.TYPE_INT_ARGB);
                    var graphics=image.createGraphics();
                    try { graphics.scale(scale,scale); view().printAll(graphics); } finally { graphics.dispose(); }
                    return image;
                } finally {
                    if (focus) {
                        var event = new java.awt.event.FocusEvent(focused, java.awt.event.FocusEvent.FOCUS_LOST);
                        for (var listener : focused.getFocusListeners()) listener.focusLost(event);
                    }
                    KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
                }
            });
        }
        @Override public void close() throws Exception { if (!closed) { closed=true; try { owner.close(); } finally { delete(home); } } }
    }
    static void arrange(Container parent) { parent.doLayout(); for(var child:parent.getComponents()) if(child instanceof Container c) arrange(c); }
    public static <T> T edt(java.util.function.Supplier<T> action) {
        if (SwingUtilities.isEventDispatchThread()) return action.get();
        var result=new AtomicReference<T>(); var failure=new AtomicReference<Throwable>();
        try { SwingUtilities.invokeAndWait(() -> { try { result.set(action.get()); } catch(Throwable t) { failure.set(t); } }); }
        catch(Exception e) { throw new IllegalStateException(e); }
        if(failure.get() instanceof RuntimeException e) throw e;
        if(failure.get() instanceof Error e) throw e;
        return result.get();
    }
    private static void delete(Path root) throws Exception { try(var paths=Files.walk(root)) { for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
}
