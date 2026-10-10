package dev.jasper.remote.testsupport;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.CustomContent;
import dev.jasper.sdk.ui.IconAsset;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Headless helpers for Remote's kit views on the testkit fake. The kit is EDT-confined, so plugin tests keep their
 * bodies on the test thread and hop to the EDT for each step ({@link #edt}); waits (loopback servers, MINA threads)
 * happen off the EDT, so completions the plugin marshals with {@link #UI} can run meanwhile and no worker/EDT hand-off
 * can deadlock. Descriptor lookup and a recorder of late view calls complete the set.
 */
public final class Kits {
    private Kits() { }

    /**
     * The plugin's UI executor in tests: inline on the EDT (as {@code Runnable::run} was), otherwise queued to it, so a
     * completion on a MINA or worker thread never touches a kit view off the EDT.
     */
    public static final Executor UI = task -> { if (EventQueue.isDispatchThread()) task.run(); else SwingUtilities.invokeLater(task); };

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

    /** Drains the fake host's queued background work and events on the EDT, where the plugin's views live. */
    public static void settle(FakePluginHost host) throws Exception {
        for (int i = 0; i < 20; i++) edt(() -> { host.runBackground(); host.flush(); });
    }

    /** Settles until the condition (read on the EDT) holds, sleeping off the EDT between rounds. */
    public static void await(FakePluginHost host, BooleanSupplier condition, String what) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (true) {
            settle(host);
            if (value(condition::getAsBoolean)) return;
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(10);
        }
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

    /** Every node in document order. */
    public static List<Nodes.Node> all(UiView view) { var result = new ArrayList<Nodes.Node>(); walk(view.spec().root(), result); return result; }
    private static void walk(Nodes.Node node, List<Nodes.Node> out) { out.add(node); for (var child : children(node)) walk(child, out); }

    /** The visible text of every Text node, in document order. */
    public static List<String> texts(UiView view) {
        return all(view).stream().filter(n -> n instanceof Nodes.Text && n.state().visible()).map(n -> ((Nodes.Text) n).text()).toList();
    }

    public static Nodes.Button button(UiView view, String id) { return node(view, id, Nodes.Button.class); }

    public static Nodes.Form form(UiView view) { return (Nodes.Form) view.spec().root(); }

    /** The form's buttons that Enter would press: none in Remote's dialogs. */
    public static List<Nodes.Button> primaries(UiView view) {
        return form(view).actions().stream().filter(b -> b.kind() == Nodes.ButtonKind.PRIMARY).toList();
    }

    public static ListItem item(Nodes.ItemList list, String id) {
        return list.items().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("No item " + id));
    }

    /** The row's own context menu item with this label. */
    public static MenuItem menuItem(ListItem item, String label) {
        return item.contextActions().orElseThrow(() -> new AssertionError(item.id() + " has no menu")).stream()
            .filter(m -> !m.separator() && m.label().equals(label)).findFirst().orElseThrow(() -> new AssertionError("No menu item " + label));
    }

    public static List<String> menuLabels(ListItem item) {
        return item.contextActions().orElse(List.of()).stream().filter(m -> !m.separator()).map(MenuItem::label).toList();
    }

    public static void emit(UiView view, UiEvent event) { fake(view).emit(event); }
    public static void press(UiView view, String action) { emit(view, new UiEvent.Action(action)); }
    /** A button as the user clicks it: its configured action. */
    public static void click(UiView view, String buttonId) { press(view, button(view, buttonId).action()); }
    public static void type(UiView view, String fieldId, String text) { emit(view, new UiEvent.Edited(fieldId, text)); }

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
