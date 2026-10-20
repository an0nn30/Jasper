package dev.jasper.app.platform;

import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/** A root-bound identity lease for an owner's measured body. Geometry is not plugin authorization. EDT only. */
public final class WindowBody implements AutoCloseable {
    private static final Object KEY = new Object();
    private final JRootPane root;
    private final JComponent coordinates;
    private final Supplier<Rectangle> region;
    private final List<Runnable> listeners = new ArrayList<>();
    private final ComponentAdapter geometry = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { changed(); }
        @Override public void componentMoved(ComponentEvent event) { changed(); }
    };
    private final HierarchyListener hierarchy = event -> changed();
    private final PropertyChangeListener properties = event -> changed();
    private Rectangle last;
    private boolean closed;

    private WindowBody(JRootPane root, JComponent coordinates, Supplier<Rectangle> region) {
        this.root = Objects.requireNonNull(root); this.coordinates = Objects.requireNonNull(coordinates);
        this.region = Objects.requireNonNull(region);
        root.putClientProperty(KEY, this);
        coordinates.addComponentListener(geometry); coordinates.addHierarchyListener(hierarchy);
        root.addComponentListener(geometry); root.addPropertyChangeListener(properties);
    }
    /** Installs a new lease. Closing a superseded lease cannot clear the replacement. */
    public static WindowBody install(JRootPane root, JComponent coordinates, Supplier<Rectangle> region) {
        var previous = root.getClientProperty(KEY);
        if (previous instanceof WindowBody body) body.close();
        return new WindowBody(root, coordinates, region);
    }
    /** Resolves only a live registration in this exact root. Unlaid-out bounds remain unavailable. */
    public static Optional<WindowBody> find(JRootPane root) {
        return root.getClientProperty(KEY) instanceof WindowBody body && body.live() ? Optional.of(body) : Optional.empty();
    }
    private boolean live() {
        return !closed && root.getClientProperty(KEY) == this && SwingUtilities.getRootPane(coordinates) == root
            && SwingUtilities.isDescendingFrom(coordinates, root.getContentPane());
    }
    /** Current defensive body rectangle in root user coordinates, never guessed from decorations. */
    public Optional<Rectangle> boundsInRoot() {
        if (!live()) return Optional.empty();
        Rectangle local = region.get();
        if (local == null || local.isEmpty()) return Optional.empty();
        return Optional.of(SwingUtilities.convertRectangle(coordinates, new Rectangle(local), root));
    }
    /** Current coordinate space, for scoped native geometry listeners. */
    public JComponent coordinates() { return coordinates; }
    /** Exact registered root. */
    public JRootPane root() { return root; }
    /** The owner calls this after layout; unchanged geometry produces no notification loop. */
    public void changed() {
        Rectangle now = boundsInRoot().orElse(null);
        if (Objects.equals(last, now)) return;
        last = now;
        for (var listener : List.copyOf(listeners)) listener.run();
    }
    /** Registers a geometry observer and returns its idempotent removal action. */
    public Runnable onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        coordinates.removeComponentListener(geometry); coordinates.removeHierarchyListener(hierarchy);
        root.removeComponentListener(geometry); root.removePropertyChangeListener(properties);
        if (root.getClientProperty(KEY) == this) root.putClientProperty(KEY, null);
        for (var listener : List.copyOf(listeners)) listener.run();
        listeners.clear();
    }
}
