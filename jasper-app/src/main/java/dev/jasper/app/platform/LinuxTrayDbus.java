package dev.jasper.app.platform;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.TypeRef;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.errors.InvalidMethodArgument;
import org.freedesktop.dbus.errors.UnknownInterface;
import org.freedesktop.dbus.errors.UnknownProperty;
import org.freedesktop.dbus.errors.PropertyReadOnly;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** External tray protocols. Public nested carriers are required by dbus-java's marshaller. */
public final class LinuxTrayDbus {
    static final String WATCHER = "org.kde.StatusNotifierWatcher";
    static final String FREEDESKTOP_WATCHER = "org.freedesktop.StatusNotifierWatcher";
    static final String ITEM = "org.kde.StatusNotifierItem";
    static final String FREEDESKTOP_ITEM = "org.freedesktop.StatusNotifierItem";
    static final String MENU = "com.canonical.dbusmenu";
    private LinuxTrayDbus() { }

    /** dbus-java 5.2.0 emits parameterized Tuple outputs twice in introspection only. */
    static void exportMenu(org.freedesktop.dbus.connections.impl.DBusConnection connection, IntConsumer action)
            throws DBusException {
        var menu = new MenuObject(action);
        connection.exportObject(menu);
        var exported = new org.freedesktop.dbus.messages.ExportedObject(menu, false);
        String xml = exported.getIntrospectiondata();
        xml = tupleMetadata(xml, "GetLayout", "u", "(ia{sv}av)");
        xml = tupleMetadata(xml, "AboutToShowGroup", "ai", "ai");
        connection.getObjectTree().add(menu.getObjectPath(), exported, xml);
    }

    private static String tupleMetadata(String xml, String method, String first, String second) {
        String opening = "  <method name=\"" + method + "\" >\n";
        int start = xml.indexOf(opening);
        int end = xml.indexOf("  </method>", start);
        String outputs = "   <arg type=\"" + first + "\" direction=\"out\"/>\n"
            + "   <arg type=\"" + second + "\" direction=\"out\"/>\n";
        if (start < 0 || end < 0 || xml.indexOf(opening, start + 1) >= 0
                || !xml.substring(start, end).endsWith(outputs + outputs)) {
            System.getLogger(LinuxTrayDbus.class.getName()).log(System.Logger.Level.WARNING,
                "D-Bus tuple metadata changed; revalidate the pinned tray binding before publishing");
            throw new IllegalStateException("Unexpected dbus-java 5.2.0 metadata for " + method);
        }
        return xml.substring(0, end - outputs.length()) + xml.substring(end);
    }

    public interface Pixmaps extends TypeRef<List<Pixmap>> { }
    public interface Strings extends TypeRef<List<String>> { }

    @DBusInterfaceName(WATCHER)
    @DBusProperty(name = "IsStatusNotifierHostRegistered", type = Boolean.class, access = DBusProperty.Access.READ)
    public interface Watcher extends DBusInterface {
        void RegisterStatusNotifierItem(String service);
        public static final class StatusNotifierHostRegistered extends DBusSignal {
            public StatusNotifierHostRegistered(String path) throws DBusException {
                super(path);
            }
        }
        public static final class StatusNotifierHostUnregistered extends DBusSignal {
            public StatusNotifierHostUnregistered(String path) throws DBusException {
                super(path);
            }
        }
    }

    @DBusInterfaceName(FREEDESKTOP_WATCHER)
    @DBusProperty(name = "IsStatusNotifierHostRegistered", type = Boolean.class, access = DBusProperty.Access.READ)
    public interface FreedesktopWatcher extends DBusInterface {
        void RegisterStatusNotifierItem(String service);
        public static final class StatusNotifierHostRegistered extends DBusSignal {
            public StatusNotifierHostRegistered(String path) throws DBusException {
                super(path);
            }
        }
        public static final class StatusNotifierHostUnregistered extends DBusSignal {
            public StatusNotifierHostUnregistered(String path) throws DBusException {
                super(path);
            }
        }
    }

    @DBusInterfaceName(ITEM)
    @DBusProperty(name = "Category", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Id", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Title", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Status", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "IconName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "OverlayIconName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "AttentionIconName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "AttentionMovieName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "WindowId", type = UInt32.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "IconPixmap", type = Pixmaps.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "OverlayIconPixmap", type = Pixmaps.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "AttentionIconPixmap", type = Pixmaps.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "ToolTip", type = ToolTip.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "ItemIsMenu", type = Boolean.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Menu", type = DBusPath.class, access = DBusProperty.Access.READ)
    public interface Item extends DBusInterface {
        void Activate(int x, int y);
        void SecondaryActivate(int x, int y);
        void ContextMenu(int x, int y);
        void Scroll(int delta, String orientation);
        public static final class NewTitle extends DBusSignal {
            public NewTitle(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewIcon extends DBusSignal {
            public NewIcon(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewAttentionIcon extends DBusSignal {
            public NewAttentionIcon(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewOverlayIcon extends DBusSignal {
            public NewOverlayIcon(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewToolTip extends DBusSignal {
            public NewToolTip(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewStatus extends DBusSignal {
            public NewStatus(String path, String status) throws DBusException {
                super(path, status);
            }
        }
    }

    @DBusInterfaceName(FREEDESKTOP_ITEM)
    @DBusProperty(name = "Category", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Id", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Title", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Status", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "IconName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "OverlayIconName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "AttentionIconName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "AttentionMovieName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "WindowId", type = UInt32.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "IconPixmap", type = Pixmaps.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "OverlayIconPixmap", type = Pixmaps.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "AttentionIconPixmap", type = Pixmaps.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "ToolTip", type = ToolTip.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "ItemIsMenu", type = Boolean.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Menu", type = DBusPath.class, access = DBusProperty.Access.READ)
    public interface FreedesktopItem extends DBusInterface {
        void Activate(int x, int y);
        void SecondaryActivate(int x, int y);
        void ContextMenu(int x, int y);
        void Scroll(int delta, String orientation);
        public static final class NewTitle extends DBusSignal {
            public NewTitle(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewIcon extends DBusSignal {
            public NewIcon(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewAttentionIcon extends DBusSignal {
            public NewAttentionIcon(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewOverlayIcon extends DBusSignal {
            public NewOverlayIcon(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewToolTip extends DBusSignal {
            public NewToolTip(String path) throws DBusException {
                super(path);
            }
        }
        public static final class NewStatus extends DBusSignal {
            public NewStatus(String path, String status) throws DBusException {
                super(path, status);
            }
        }
    }

    @DBusInterfaceName(MENU)
    @DBusProperty(name = "Version", type = UInt32.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "TextDirection", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Status", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "IconThemePath", type = Strings.class, access = DBusProperty.Access.READ)
    public interface Menu extends DBusInterface {
        LayoutReply<UInt32, Layout> GetLayout(int parentId, int recursionDepth, String[] propertyNames);
        List<RowProperties> GetGroupProperties(int[] ids, String[] propertyNames);
        Variant<?> GetProperty(int id, String name);
        void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);
        int[] EventGroup(List<MenuEvent> events);
        boolean AboutToShow(int id);
        AboutReply<int[], int[]> AboutToShowGroup(int[] ids);
        public static final class LayoutUpdated extends DBusSignal {
            public LayoutUpdated(String path, UInt32 revision, int parent) throws DBusException {
                super(path, revision, parent);
            }
        }
        public static final class ItemsPropertiesUpdated extends DBusSignal {
            public ItemsPropertiesUpdated(String path, List<RowProperties> updated, List<RemovedProperties> removed) throws DBusException {
                super(path, updated, removed);
            }
        }
        public static final class ItemActivationRequested extends DBusSignal {
            public ItemActivationRequested(String path, int id, UInt32 timestamp) throws DBusException {
                super(path, id, timestamp);
            }
        }
    }

    public static final class Pixmap extends Struct {
        @Position(0) public final int width;
        @Position(1) public final int height;
        @Position(2) public final byte[] bytes;
        public Pixmap(int width, int height, byte[] bytes) {
            this.width = width;
            this.height = height;
            this.bytes = bytes;
        }
    }

    public static final class ToolTip extends Struct {
        @Position(0) public final String iconName;
        @Position(1) public final List<Pixmap> pixmaps;
        @Position(2) public final String title;
        @Position(3) public final String description;
        public ToolTip(String iconName, List<Pixmap> pixmaps, String title, String description) {
            this.iconName = iconName;
            this.pixmaps = pixmaps;
            this.title = title;
            this.description = description;
        }
    }

    public static final class Layout extends Struct {
        @Position(0) public final int id;
        @Position(1) public final Map<String, Variant<?>> properties;
        @Position(2) public final List<Variant<?>> children;
        public Layout(int id, Map<String, Variant<?>> properties, List<Variant<?>> children) {
            this.id = id;
            this.properties = properties;
            this.children = children;
        }
    }

    public static final class LayoutReply<R, L> extends Tuple {
        @Position(0) public final R revision;
        @Position(1) public final L layout;
        public LayoutReply(R revision, L layout) {
            this.revision = revision;
            this.layout = layout;
        }
    }

    public static final class RowProperties extends Struct {
        @Position(0) public final int id;
        @Position(1) public final Map<String, Variant<?>> properties;
        public RowProperties(int id, Map<String, Variant<?>> properties) {
            this.id = id;
            this.properties = properties;
        }
    }

    public static final class MenuEvent extends Struct {
        @Position(0) public final int id;
        @Position(1) public final String eventId;
        @Position(2) public final Variant<?> data;
        @Position(3) public final UInt32 timestamp;
        public MenuEvent(int id, String eventId, Variant<?> data, UInt32 timestamp) {
            this.id = id;
            this.eventId = eventId;
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    public static final class AboutReply<U, E> extends Tuple {
        @Position(0) public final U updates;
        @Position(1) public final E errors;
        public AboutReply(U updates, E errors) {
            this.updates = updates;
            this.errors = errors;
        }
    }

    public static final class RemovedProperties extends Struct {
        @Position(0) public final int id;
        @Position(1) public final List<String> properties;
        public RemovedProperties(int id, List<String> properties) {
            this.id = id;
            this.properties = properties;
        }
    }

    public abstract static class ReadOnly implements Properties {
        final String interfaceName;
        final Map<String, Variant<?>> values;
        ReadOnly(String interfaceName, Map<String, Variant<?>> values) {
            this.interfaceName = interfaceName;
            this.values = Map.copyOf(values);
        }
        @Override public Map<String, Variant<?>> GetAll(String requested) {
            if (!interfaceName.equals(requested)) throw new UnknownInterface("Unknown interface: " + requested);
            return values;
        }
        @Override @SuppressWarnings("unchecked") public <A> A Get(String requested, String property) {
            Variant<?> value = GetAll(requested).get(property);
            if (value == null) throw new UnknownProperty("Unknown property: " + property);
            // Preserve the explicit compound/empty signature when Properties.Get is marshalled.
            return (A) value;
        }
        @Override public <A> void Set(String requested, String property, A value) {
            throw new PropertyReadOnly("Read-only property: " + property);
        }
    }

    public static class ItemObject extends ReadOnly implements Item {
        final IntConsumer action;
        ItemObject(IntConsumer action) { this(ITEM, action); }
        ItemObject(String name, IntConsumer action) { super(name, itemValues()); this.action = action; }
        @Override public String getObjectPath() { return "/StatusNotifierItem"; }
        @Override public void Activate(int x, int y) { action.accept(1); }
        @Override public void SecondaryActivate(int x, int y) { }
        @Override public void ContextMenu(int x, int y) { }
        @Override public void Scroll(int delta, String orientation) { }
    }

    public static final class FreedesktopItemObject extends ItemObject implements FreedesktopItem {
        FreedesktopItemObject(IntConsumer action) { super(FREEDESKTOP_ITEM, action); }
        @Override public Map<String, Variant<?>> GetAll(String requested) {
            return ITEM.equals(requested) ? values : super.GetAll(requested);
        }
    }

    public static final class MenuObject extends ReadOnly implements Menu {
        private final Map<Integer, Map<String, Variant<?>>> rows;
        private final IntConsumer action;
        MenuObject(IntConsumer action) {
            super(MENU, Map.of("Version", new Variant<>(new UInt32(3)), "TextDirection", new Variant<>("ltr"),
                "Status", new Variant<>("normal"), "IconThemePath", new Variant<>(List.of(), "as")));
            this.action = action;
            var data = new LinkedHashMap<Integer, Map<String, Variant<?>>>();
            data.put(0, Map.of("children-display", new Variant<>("submenu")));
            for (var row : LinuxTrayMenu.rows()) data.put(row.id(), row.separator()
                ? Map.of("type", new Variant<>("separator"), "visible", new Variant<>(true))
                : Map.of("label", new Variant<>(row.label()), "visible", new Variant<>(true), "enabled", new Variant<>(true)));
            rows = java.util.Collections.unmodifiableMap(data);
        }
        @Override public String getObjectPath() { return "/MenuBar"; }
        private Map<String, Variant<?>> properties(int id, String[] names) {
            var values = rows.get(id);
            if (values == null) throw new InvalidMethodArgument("Unknown menu id: " + id);
            if (names.length == 0) return values;
            var filtered = new LinkedHashMap<String, Variant<?>>();
            for (String name : names) if (values.containsKey(name)) filtered.put(name, values.get(name));
            return Map.copyOf(filtered);
        }
        @Override public LayoutReply<UInt32, Layout> GetLayout(int parentId, int depth, String[] names) {
            if (depth < -1) throw new InvalidMethodArgument("Invalid recursion depth: " + depth);
            var properties = properties(parentId, names);
            List<Variant<?>> children = new ArrayList<>();
            if (parentId == 0 && depth != 0) for (var row : LinuxTrayMenu.rows())
                children.add(new Variant<>(new Layout(row.id(), properties(row.id(), names), List.of()), "(ia{sv}av)"));
            return new LayoutReply<>(new UInt32(1), new Layout(parentId, properties, List.copyOf(children)));
        }
        @Override public List<RowProperties> GetGroupProperties(int[] ids, String[] names) {
            var result = new ArrayList<RowProperties>();
            int[] selected = ids.length == 0 ? rows.keySet().stream().mapToInt(Integer::intValue).toArray() : ids;
            for (int id : selected) if (rows.containsKey(id)) result.add(new RowProperties(id, properties(id, names)));
            return List.copyOf(result);
        }
        @Override public Variant<?> GetProperty(int id, String name) {
            Variant<?> value = properties(id, new String[0]).get(name);
            if (value == null) throw new UnknownProperty("Unknown menu property: " + name);
            return value;
        }
        @Override public void Event(int id, String eventId, Variant<?> data, UInt32 timestamp) {
            if ("clicked".equals(eventId) && (id == 1 || id == 2 || id == 3 || id == 5)) action.accept(id);
        }
        @Override public int[] EventGroup(List<MenuEvent> events) {
            var errors = new ArrayList<Integer>();
            for (var event : events) {
                if (!rows.containsKey(event.id)) errors.add(event.id);
                else Event(event.id, event.eventId, event.data, event.timestamp);
            }
            return errors.stream().mapToInt(Integer::intValue).toArray();
        }
        @Override public boolean AboutToShow(int id) { properties(id, new String[0]); return false; }
        @Override public AboutReply<int[], int[]> AboutToShowGroup(int[] ids) {
            return new AboutReply<>(new int[0], java.util.Arrays.stream(ids).filter(id -> !rows.containsKey(id)).toArray());
        }
    }

    private static Map<String, Variant<?>> itemValues() {
        var result = new LinkedHashMap<String, Variant<?>>();
        result.put("Category", new Variant<>("ApplicationStatus"));
        result.put("Id", new Variant<>("Jasper"));
        result.put("Title", new Variant<>("Jasper"));
        result.put("Status", new Variant<>("Active"));
        result.put("WindowId", new Variant<>(new UInt32(0)));
        for (String name : List.of("IconName", "OverlayIconName", "AttentionIconName", "AttentionMovieName"))
            result.put(name, new Variant<>(""));
        var pixmaps = new ArrayList<Pixmap>();
        var source = ApplicationIcon.images("linux").getLast();
        for (int size : new int[]{16, 22, 24, 32, 48, 64}) {
            var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { graphics.drawImage(source, 0, 0, size, size, null); }
            finally { graphics.dispose(); }
            pixmaps.add(new Pixmap(size, size, LinuxTrayMenu.argb(image)));
        }
        result.put("IconPixmap", new Variant<>(List.copyOf(pixmaps), "a(iiay)"));
        result.put("OverlayIconPixmap", new Variant<>(List.of(), "a(iiay)"));
        result.put("AttentionIconPixmap", new Variant<>(List.of(), "a(iiay)"));
        result.put("ToolTip", new Variant<>(new ToolTip("", List.copyOf(pixmaps), "Jasper", "Jasper is running in the background"), "(sa(iiay)ss)"));
        result.put("ItemIsMenu", new Variant<>(false));
        result.put("Menu", new Variant<>(new DBusPath("/MenuBar")));
        return Map.copyOf(result);
    }
}
