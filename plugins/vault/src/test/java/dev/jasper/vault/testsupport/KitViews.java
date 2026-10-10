package dev.jasper.vault.testsupport;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Headless helpers for Vault views on the testkit fake: EDT runs, descriptor lookup and a late-call recorder. */
public final class KitViews {
    private KitViews() { }

    public interface Body { void run() throws Exception; }

    /** Runs the body on the EDT (the kit requires it) and rethrows its failure. */
    public static void edt(Body body) throws Exception {
        Throwable[] failure = {null};
        SwingUtilities.invokeAndWait(() -> { try { body.run(); } catch (Throwable t) { failure[0] = t; } });
        if (failure[0] instanceof Exception e) throw e;
        if (failure[0] instanceof Error e) throw e;
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
            case Nodes.SettingRow n -> result.add(n.control());
            case Nodes.Card n -> result.addAll(n.children());
            case Nodes.Section n -> result.add(n.body());
            case Nodes.Empty n -> { result.add(n.icon()); result.addAll(n.actions()); }
            case Nodes.Split n -> result.addAll(n.children());
            case Nodes.Panel n -> { result.add(n.header()); n.searchOrNavigation().ifPresent(result::add); result.add(n.body()); n.footer().ifPresent(result::add); }
            case Nodes.Header n -> { n.context().ifPresent(result::add); n.primary().ifPresent(result::add); }
            case Nodes.ItemList n -> {
                n.addAction().ifPresent(result::add);
                for (ListItem item : n.items()) { item.leading().ifPresent(result::add); result.addAll(item.title()); result.addAll(item.subtitle()); result.addAll(item.trailing()); }
            }
            default -> { }
        }
        return result;
    }

    /** All node ids in document order. */
    public static List<Nodes.Node> all(UiView view) {
        var result = new ArrayList<Nodes.Node>(); walk(view.spec().root(), result); return result;
    }
    private static void walk(Nodes.Node node, List<Nodes.Node> out) { out.add(node); for (var child : children(node)) walk(child, out); }

    public static String error(UiView view, String id) { return find(view, id).orElseThrow().state().error(); }

    public static Nodes.Button button(UiView view, String id) { return node(view, id, Nodes.Button.class); }

    /** Types into a secret field as the user would (the fake keeps its own copy and reports SecretChanged). */
    public static void type(UiView view, String id, String value) { char[] chars = value.toCharArray(); fake(view).setSecret(id, chars); java.util.Arrays.fill(chars, (char) 0); }

    /** The secret held in a view, as a String for assertions only (test values are synthetic). */
    public static String secret(UiView view, String id) {
        String[] result = {""}; view.withSecret(id, chars -> result[0] = new String(chars)); return result[0];
    }

    public static void emit(UiView view, UiEvent event) { fake(view).emit(event); }
    public static void press(UiView view, String action) { emit(view, new UiEvent.Action(action)); }

    public static boolean closed(UiView view) {
        try { view.spec(); return false; } catch (IllegalStateException closed) { return true; }
    }

    /** A kit whose views record any use after they were closed, which a disposed owner must never attempt. */
    public static final class RecordingKit implements UiKit {
        public final FakeKit delegate;
        public final List<String> lateCalls = new ArrayList<>();
        public final List<UiEvent> events = new ArrayList<>();
        public RecordingKit(String owner) { delegate = new FakeKit(owner); }
        @Override public UiView create(ViewSpec spec, Consumer<UiEvent> handler) {
            return new RecordingView(this, delegate.create(spec, event -> { events.add(event); handler.accept(event); }));
        }
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
        @Override public void selectAll(String id) { use("selectAll " + id); if (!closed) delegate.selectAll(id); }
        @Override public void reveal(String id) { use("reveal " + id); if (!closed) delegate.reveal(id); }
        @Override public boolean composing(String id) { return delegate.composing(id); }
        @Override public void registerContent(CustomContent content) { delegate.registerContent(content); }
        @Override public void close() { closed = true; delegate.close(); }
    }
}
