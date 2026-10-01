package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatStylingSupport;
import com.formdev.flatlaf.ui.FlatStylingSupport.StyleableUI;
import java.awt.Color;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Reversible, EDT-confined ownership of one component's generated workspace colors. */
final class WorkspaceStyleLayer implements AutoCloseable {
    private static final String STYLE = "FlatLaf.style";
    private static final String CLASS = "FlatLaf.styleClass";
    private static final String GENERATED = "Jasper.workspaceStyle";
    private static final System.Logger LOG = System.getLogger(WorkspaceStyleLayer.class.getName());
    private final JComponent component;
    private final PropertyChangeListener listener = this::changed;
    private final Map<String, PropertyDescriptor> properties = new HashMap<>();
    private final Map<String, Color> originals = new LinkedHashMap<>();
    private final Map<String, Color> written = new HashMap<>();
    private final Map<String, Color> overrides = new HashMap<>();
    private final Map<String, Color> clearedValues = new HashMap<>();
    private final Map<Object, Boolean> warned = new IdentityHashMap<>();
    private Object pluginStyle;
    private Object pluginClass;
    private Object published;
    private WorkspaceStyles.Recipe recipe;
    private int depth;
    private boolean suspended;
    private boolean closed;
    private final java.util.Set<String> directKeys = new java.util.HashSet<>();

    WorkspaceStyleLayer(JComponent component) {
        requireEdt();
        this.component = Objects.requireNonNull(component);
        pluginStyle = component.getClientProperty(STYLE);
        pluginClass = component.getClientProperty(CLASS);
        try {
            for (var property : Introspector.getBeanInfo(component.getClass()).getPropertyDescriptors())
                properties.put(property.getName(), property);
        } catch (IntrospectionException e) { throw new IllegalArgumentException("Cannot inspect component", e); }
        component.addPropertyChangeListener(listener);
    }

    void apply(WorkspaceStyles.Recipe recipe) {
        requireEdt();
        if (closed) throw new IllegalStateException("Workspace style layer is closed");
        if (this.recipe == null && !suspended) {
            // Swing suppresses equal-RGB change events, but identity still exposes a plugin setter.
            clearedValues.forEach((key, value) -> {
                if (read(key) != value) overrides.put(key, read(key));
            });
        }
        this.recipe = Objects.requireNonNull(recipe);
        if (!suspended) guarded(this::publish);
    }

    void clear() {
        requireEdt();
        if (closed) return;
        guarded(this::release);
        recipe = null;
        captureClearedValues();
    }

    void beforeUiUpdate() {
        requireEdt();
        if (closed || suspended) return;
        suspended = true;
        guarded(this::release);
    }

    void afterUiUpdate() {
        requireEdt();
        if (closed || !suspended) return;
        suspended = false;
        if (recipe != null) guarded(this::publish);
        else captureClearedValues();
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        clear();
        component.removePropertyChangeListener(listener);
        closed = true;
    }

    private void changed(PropertyChangeEvent event) {
        if (depth != 0 || closed) return;
        requireEdt();
        String name = event.getPropertyName();
        if (STYLE.equals(name)) {
            pluginStyle = event.getNewValue();
            if (!suspended && recipe != null) guarded(this::publish);
        } else if (CLASS.equals(name)) {
            pluginClass = event.getNewValue();
            if (!suspended && recipe != null) guarded(() -> {
                // Drop the old inline composite so FlatLaf resolves the newly supplied
                // classes against its own baseline, without rewriting the class property.
                component.putClientProperty(STYLE, null);
                originals.keySet().removeIf(key -> !directKeys.contains(key));
                written.keySet().removeIf(key -> !directKeys.contains(key));
                publish();
            });
        } else if ("UI".equals(name)) {
            if (!suspended && recipe != null) guarded(() -> {
                // The new delegate has already installed the composite. Remove it and reinstall
                // just this component after restoring the pre-host UIResource provenance.
                release();
                component.updateUI();
                publish();
            });
        } else if (!suspended && directKeys.contains(directKey(name))) {
            String key = directKey(name);
            if (installingUi()) {
                if (recipe == null) clearedValues.put(key, read(key));
            } else {
                overrides.put(key, read(key));
                written.remove(key);
            }
        }
    }

    private void captureClearedValues() {
        for (String key : directKeys) clearedValues.put(key, read(key));
    }

    private static boolean installingUi() {
        // A shared delegate can reinstall without changing UI identity or firing a UI event.
        // Inspect this synchronous setter's origin rather than misclassifying UIResource
        // values: a plugin is allowed to set those same values outside delegate installation.
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames.anyMatch(frame ->
            frame.getDeclaringClass() == JComponent.class && frame.getMethodName().equals("setUI")));
    }

    private void publish() {
        // Validate generated keys separately: a host recipe defect must never be blamed on a plugin.
        validate(recipe.style());
        Object composite;
        try {
            validate(pluginStyle);
            validate(FlatStylingSupport.getStyleForClasses(pluginClass, recipe.type()));
            composite = composite(component, recipe, pluginStyle);
        } catch (IllegalArgumentException | ClassCastException failure) {
            release();
            Object offending = pluginStyle != null ? pluginStyle : pluginClass;
            if (warned.put(offending, Boolean.TRUE) == null)
                LOG.log(System.Logger.Level.WARNING, "Ignoring workspace colors for invalid plugin style", failure);
            return;
        }
        detectDirectOverrides();
        // Retain component-level originals too: an isolated updateUI may capture generated
        // plain Colors as its baseline before the UI property notification reaches us.
        if (originals.isEmpty()) {
            // FlatLaf owns the pre-inline baseline. Remove inline colors before capturing;
            // otherwise an initial plugin color survives removal of the plugin's style.
            // Class state remains exclusively plugin-owned.
            component.putClientProperty(STYLE, null);
            for (String key : recipe.style().keySet()) capture(key);
            for (String key : recipe.direct().keySet()) capture(key);
        }
        directKeys.addAll(recipe.direct().keySet());
        published = composite;
        component.putClientProperty(STYLE, composite);
        component.putClientProperty(GENERATED, recipe.style());
        for (var entry : recipe.direct().entrySet()) {
            if (!overrides.containsKey(entry.getKey())) write(entry.getKey(), entry.getValue());
        }
        applyDirectPluginStyle();
        // Plugin setters override direct fallbacks for the entire ownership lifetime.
        overrides.forEach(this::write);
        for (String key : originals.keySet()) {
            if (!overrides.containsKey(key)) written.put(key, read(key));
        }
    }

    private static Object composite(JComponent component, WorkspaceStyles.Recipe recipe, Object pluginStyle) {
        Object classes = FlatStylingSupport.getStyleForClasses(component.getClientProperty(CLASS), recipe.type());
        return FlatStylingSupport.joinStyles(FlatStylingSupport.joinStyles(recipe.style(), classes), pluginStyle);
    }

    private void validate(Object style) {
        if (style == null) return;
        if (!(style instanceof String) && !(style instanceof Map<?, ?>))
            throw new IllegalArgumentException("Style must be String, Map or null");
        Map<String, Class<?>> infos = component.getUI() instanceof StyleableUI ui
            ? ui.getStyleableInfos(component) : Map.of();
        FlatStylingSupport.parseAndApply(null, style, (key, value) -> {
            Class<?> type = infos.get(key);
            if (type == null) {
                var property = properties.get(key);
                if (property != null && property.getWriteMethod() != null) type = property.getPropertyType();
            }
            if (type == null) throw new IllegalArgumentException("Unsupported style key: " + key);
            if (type.isPrimitive()) type = switch (type.getName()) {
                case "boolean" -> Boolean.class;
                case "int" -> Integer.class;
                case "float" -> Float.class;
                case "double" -> Double.class;
                default -> type;
            };
            if (value != null && !type.isInstance(value))
                throw new IllegalArgumentException("Invalid style value for " + key);
            return null;
        });
    }

    private void capture(String key) {
        String propertyKey = propertyKey(key);
        var property = properties.get(propertyKey);
        if (property != null && property.getPropertyType() == Color.class
            && property.getReadMethod() != null && property.getWriteMethod() != null)
            if (!originals.containsKey(key)) originals.put(key, read(key));
    }

    private void detectDirectOverrides() {
        if (recipe == null) return;
        for (String key : recipe.direct().keySet()) {
            if (written.containsKey(key) && read(key) != written.get(key)) {
                overrides.put(key, read(key));
                written.remove(key);
            }
        }
    }

    private void release() {
        detectDirectOverrides();
        // Remember ownership before FlatLaf restores its own style originals.
        var owned = new HashMap<String, Boolean>();
        written.forEach((key, value) -> owned.put(key, read(key) == value));
        if (published != null || component.getClientProperty(STYLE) != pluginStyle) {
            component.putClientProperty(STYLE, null);
            owned.forEach((key, value) -> { if (value) write(key, originals.get(key)); });
            component.putClientProperty(STYLE, pluginStyle);
        } else owned.forEach((key, value) -> { if (value) write(key, originals.get(key)); });
        applyDirectPluginStyle();
        overrides.forEach(this::write);
        component.putClientProperty(GENERATED, null);
        published = null;
        originals.clear();
        written.clear();
    }

    private void applyDirectPluginStyle() {
        if (directKeys.isEmpty() || recipe == null) return;
        Object plugin;
        try {
            plugin = FlatStylingSupport.joinStyles(
                FlatStylingSupport.getStyleForClasses(pluginClass, recipe.type()), pluginStyle);
            validate(plugin);
        } catch (IllegalArgumentException | ClassCastException invalid) { return; }
        FlatStylingSupport.parseAndApply(null, plugin, (key, value) -> {
            String direct = directKey(key);
            if (directKeys.contains(direct)) write(direct, (Color) value);
            return null;
        });
    }

    private String propertyKey(String key) {
        if (component instanceof javax.swing.text.JTextComponent) return switch (key) {
            case "selectionBackground" -> "selectionColor";
            case "selectionForeground" -> "selectedTextColor";
            default -> key;
        };
        return key;
    }

    private static String directKey(String key) {
        return switch (key) {
            case "selectionColor" -> "selectionBackground";
            case "selectedTextColor" -> "selectionForeground";
            default -> key;
        };
    }

    private Color read(String key) {
        try { return (Color) properties.get(propertyKey(key)).getReadMethod().invoke(component); }
        catch (IllegalAccessException | InvocationTargetException e) { throw new IllegalStateException(e); }
    }

    private void write(String key, Color value) {
        try { properties.get(propertyKey(key)).getWriteMethod().invoke(component, value); }
        catch (IllegalAccessException | InvocationTargetException e) { throw new IllegalStateException(e); }
    }

    private void guarded(Runnable operation) {
        depth++;
        try { operation.run(); }
        finally { depth--; }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Workspace styles require EDT");
    }
}
