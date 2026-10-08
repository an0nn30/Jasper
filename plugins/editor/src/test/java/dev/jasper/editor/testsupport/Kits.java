package dev.jasper.editor.testsupport;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.CustomContent;
import dev.jasper.sdk.ui.IconAsset;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Headless helpers for the Editor's kit views on the testkit fake (Plan 2 Task 9a, the Remote {@code Kits} shape): the
 * kit is EDT-confined, so test bodies hop to the EDT for each step ({@link #edt}); descriptor lookup and a recorder of
 * late view calls complete the set.
 */
public final class Kits {
    private Kits() { }

    public interface Body { void run() throws Exception; }

    /** Runs the body on the EDT and rethrows its failure; inline when already there. */
    public static void edt(Body body) throws Exception {
        if (EventQueue.isDispatchThread()) { body.run(); return; }
        Throwable[] failure = {null};
        SwingUtilities.invokeAndWait(() -> { try { body.run(); } catch (Throwable t) { failure[0] = t; } });
        if (failure[0] instanceof Exception e) throw e;
        if (failure[0] instanceof Error e) throw e;
    }

    /** Computes a value on the EDT. */
    public static <T> T value(Callable<T> body) throws Exception {
        Object[] value = {null};
        edt(() -> { value[0] = body.call(); });
        @SuppressWarnings("unchecked") T result = (T) value[0];
        return result;
    }

    public static FakeKitView fake(UiView view) {
        if (view instanceof RecordingView recording) return (FakeKitView) recording.delegate;
        return (FakeKitView) view;
    }

    public static <T extends Nodes.Node> T node(UiView view, String id, Class<T> type) {
        return find(view.spec().root(), id).map(type::cast).orElseThrow(() -> new AssertionError("No node " + id));
    }

    public static Optional<Nodes.Node> find(UiView view, String id) { return find(view.spec().root(), id); }

    public static Optional<Nodes.Node> find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return Optional.of(node);
        for (var child : children(node)) { var found = find(child, id); if (found.isPresent()) return found; }
        return Optional.empty();
    }

    public static List<Nodes.Node> children(Nodes.Node node) {
        var result = new ArrayList<Nodes.Node>();
        switch (node) {
            case Nodes.Form n -> { n.hero().ifPresent(result::add); result.addAll(n.sections()); n.preview().ifPresent(result::add); result.addAll(n.actions()); }
            case Nodes.Stack n -> result.addAll(n.children());
            case Nodes.FormRow n -> { result.add(n.control()); n.trailing().ifPresent(result::add); }
            case Nodes.Section n -> result.add(n.body());
            case Nodes.Card n -> result.addAll(n.children());
            case Nodes.ItemList n -> {
                n.addAction().ifPresent(result::add);
                for (ListItem item : n.items()) { item.leading().ifPresent(result::add); result.addAll(item.title()); result.addAll(item.subtitle()); result.addAll(item.trailing()); }
            }
            default -> { }
        }
        return result;
    }

    public static Nodes.Form form(UiView view) { return (Nodes.Form) view.spec().root(); }

    /** The form's buttons that Enter presses (at most one). */
    public static List<Nodes.Button> primaries(UiView view) {
        return form(view).actions().stream().filter(b -> b.kind() == Nodes.ButtonKind.PRIMARY).toList();
    }

    public static Nodes.Button button(UiView view, String id) { return node(view, id, Nodes.Button.class); }

    public static void emit(UiView view, UiEvent event) { fake(view).emit(event); }
    public static void press(UiView view, String action) { emit(view, new UiEvent.Action(action)); }
    /** A button as the user clicks it: its configured action. */
    public static void click(UiView view, String buttonId) { press(view, button(view, buttonId).action()); }
    public static void type(UiView view, String fieldId, String text) { emit(view, new UiEvent.Edited(fieldId, text)); }
    /** Escape as the kit reports it: the form's cancel action. */
    public static void escape(UiView view) { press(view, form(view).cancelAction()); }

    public static boolean closed(UiView view) {
        try { fake(view).spec(); return false; } catch (IllegalStateException closed) { return true; }
    }

    /** A kit whose views record any use after they were closed, which a disposed owner must never attempt. */
    public static final class RecordingKit implements UiKit {
        public final FakeKit delegate;
        public final List<String> lateCalls = new ArrayList<>();
        public RecordingKit(String owner) { delegate = new FakeKit(owner); }
        @Override public UiView create(ViewSpec spec, Consumer<UiEvent> handler) { return new RecordingView(this, delegate.create(spec, handler)); }
        @Override public Subscription registerIcon(IconAsset asset) { return delegate.registerIcon(asset); }
    }

    static final class RecordingView implements UiView {
        final RecordingKit kit; final UiView delegate; boolean closed;
        RecordingView(RecordingKit kit, UiView delegate) { this.kit = kit; this.delegate = delegate; }
        private void use(String call) { if (closed) kit.lateCalls.add(call); }
        @Override public void update(ViewSpec spec) { use("update"); if (!closed) delegate.update(spec); }
        @Override public ViewSpec spec() { return delegate.spec(); }
        @Override public void withSecret(String id, Consumer<char[]> use) { use("withSecret " + id); delegate.withSecret(id, use); }
        @Override public void clearSecret(String id) { use("clearSecret " + id); if (!closed) delegate.clearSecret(id); }
        @Override public void replaceSecret(String id, char[] value) { use("replaceSecret " + id); if (!closed) delegate.replaceSecret(id, value); }
        @Override public void focus(String id) { use("focus " + id); if (!closed) delegate.focus(id); }
        @Override public void reveal(String id) { use("reveal " + id); if (!closed) delegate.reveal(id); }
        @Override public boolean composing(String id) { return delegate.composing(id); }
        @Override public void registerContent(CustomContent content) { delegate.registerContent(content); }
        @Override public void close() { closed = true; delegate.close(); }
    }
}
