package dev.jasper.ui.gallery;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Test-runtime catalog and deterministic, window-free gallery entry point. */
public final class KitGallery {
    private KitGallery() {}
    public record Fixture(String name, String reference, String state, int width, int height, ViewSpec spec) {}
    public record Reference(int x, int y, int width, int height, int sourceScale) {}
    public static final Map<String, Reference> REFERENCES = Map.ofEntries(
        Map.entry("add-host.png", new Reference(48, 100, 1080, 1464, 2)),
        Map.entry("unlock-vault.png", new Reference(62, 112, 760, 528, 2)),
        Map.entry("new-file-server.png", new Reference(30, 74, 1080, 1460, 2)),
        Map.entry("create-vault.png", new Reference(44, 94, 800, 856, 2)),
        Map.entry("credential-vault.png", new Reference(38, 114, 1800, 1110, 2)),
        Map.entry("vault-add-menu.png", new Reference(160, 140, 444, 274, 2)),
        Map.entry("add-login-sheet.png", new Reference(434, 106, 964, 730, 2)),
        Map.entry("add-ssh-key-sheet.png", new Reference(446, 120, 964, 614, 2)),
        Map.entry("plugin-panels.png", new Reference(168, 144, 520, 1686, 2)));

    public static List<Fixture> fixtures() {
        var result = new ArrayList<Fixture>();
        for (String name : List.of("add-host", "unlock-vault", "new-file-server", "create-vault", "credential-vault", "vault-add-menu", "add-login-sheet", "add-ssh-key-sheet")) {
            var reference = REFERENCES.get(name + ".png");
            result.add(new Fixture(name, name + ".png", name.equals("unlock-vault") ? "focus" : "normal", reference.width() / 2, reference.height() / 2, FormFixtures.create(name)));
        }
        for (String panel : List.of("sessions", "tunnels", "sftp", "fileservers"))
            result.add(new Fixture(panel, "plugin-panels.png", panel.equals("sftp") || panel.equals("tunnels") ? "empty" : "normal", 264, panel.equals("sessions") || panel.equals("sftp") ? 426 : 424, PanelFixtures.create(panel)));
        result.add(panelState("sessions-filtered-empty", "sessions", "filtered-empty", demo -> demo.dispatch(new UiEvent.Edited("sessions-query", "no-matches"))));
        result.add(panelState("tunnels-added", "tunnels", "selected", demo -> demo.dispatch(new UiEvent.Action("tunnels-new"))));
        result.add(panelState("sftp-connected", "sftp", "selected", demo -> demo.dispatch(new UiEvent.Action("choose-amp"))));
        result.add(panelState("sftp-connected-short-font24", "sftp", "selected", demo -> demo.dispatch(new UiEvent.Action("choose-amp"))));
        result.add(panelState("fileservers-pending", "fileservers", "busy", demo -> demo.dispatch(new UiEvent.Toggled("server-toggle", true))));
        result.add(panelState("fileservers-failed", "fileservers", "error", demo -> { demo.dispatch(new UiEvent.Toggled("server-toggle", true)); demo.completePending(false); }));
        result.add(panelState("fileservers-confirmed", "fileservers", "selected", demo -> { demo.dispatch(new UiEvent.Toggled("server-toggle", true)); demo.completePending(true); }));
        result.add(panelState("fileservers-added-pending", "fileservers", "busy", demo -> { demo.dispatch(new UiEvent.Action("fileservers-add")); demo.dispatch(new UiEvent.Toggled("server-1-toggle", true)); }));
        result.addAll(StateFixtures.create());
        result.add(new Fixture("stored-secret-masked","","stored-secret-masked",320,88,new ViewSpec(new Secret("stored-secret",ControlState.READY,"","Passphrase","",SecretKind.DETAIL,"copy-secret"))));
        result.add(new Fixture("stored-secret-revealed","","stored-secret-revealed",320,88,new ViewSpec(new Secret("stored-secret",ControlState.READY,"","Passphrase","",SecretKind.DETAIL,"copy-secret"))));
        for (String state : List.of("empty", "populated", "focus"))
            result.add(new Fixture("discreet-secret-" + state, "", state, 320, 40,
                new ViewSpec(new Secret("discreet-secret", ControlState.READY, "Password", "Password", "", SecretKind.DISCREET_INPUT, ""))));
        result.add(new Fixture("window-form", "", "normal", 640, 320,
            new ViewSpec(new Form("window", ControlState.READY, FormKind.WINDOW, "Library", Optional.empty(),
                List.of(new Text("window-content", ControlState.READY, "Window content", TextRole.BODY)), Optional.empty(),
                List.of(new Nodes.Button("done", ControlState.READY, "Done", "", ButtonKind.PRIMARY, "done", "")), "done", FormSubmission.PRIMARY))));
        result.add(new Fixture("secure-note","","normal",360,220,new ViewSpec(new Secret("secure-note",ControlState.READY,"Write a note","Secure note","",SecretKind.MULTILINE,""))));
        result.add(new Fixture("dropdown-open","","dropdown-open",240,130,new ViewSpec(new Nodes.Choice("dropdown",ControlState.READY,ChoiceKind.DROPDOWN,List.of(new Option("ssh","SSH",Optional.of(Hue.GREEN)),new Option("local","Local",Optional.of(Hue.BLUE)),new Option("sftp","SFTP",Optional.of(Hue.PURPLE))),"ssh","Protocol","protocol"))));
        for(var density:PickerDensity.values())result.add(new Fixture("rich-picker-"+density,"","selected",220,50,new ViewSpec(new Nodes.Choice("picker",ControlState.READY,ChoiceKind.PICKER,List.of(),"","Credential","pick","lock",Optional.empty(),Optional.of(new PickerFace("root","Stored key · fixture","key",Hue.GREEN,density))))));
        result.add(new Fixture("picker-menu","","normal",222,118,new ViewSpec(new PickerMenu("menu",ControlState.READY,"","Search credentials",List.of(new PickerItem("none","None","","none",Hue.NONE,PickerItemKind.UTILITY),new PickerItem("root","root","Stored key","key",Hue.GREEN)),"root","root","No matches","New Credential…","create","cancel"))));
        // Plan 3b (SDK 0.18): two read-only fields, a fact and a data value.
        result.add(new Fixture("read-only-fields", "", "normal", 400, 110, new ViewSpec(new Nodes.Stack("read-only", ControlState.READY, LayoutRole.VERTICAL, List.of(
            new Nodes.Field("read-only-login", ControlState.READY, FieldKind.TEXT, "No login (TFTP has none)", "", "Login", "", Optional.empty(), FieldPresentation.STANDARD, "", "", "", true),
            new Nodes.Field("read-only-folder", ControlState.READY, FieldKind.TEXT, "/Users/me/Downloads", "No folder selected", "Folder", "", Optional.empty(), FieldPresentation.TECHNICAL, "", "", "", true))))));
        // Plan 3b (SDK 0.18): rich faces whose captions are data values, at both densities.
        result.add(new Fixture("technical-captions", "", "normal", 260, 110, new ViewSpec(new Nodes.Stack("technical-captions", ControlState.READY, LayoutRole.VERTICAL, List.of(
            new Nodes.Choice("technical-via", ControlState.READY, ChoiceKind.DROPDOWN, List.of(new Option("mac", "Mac", Optional.empty()), new Option("box", "build-box", Optional.empty())),
                "mac", "Via host", "via", "", Optional.empty(), Optional.of(new PickerFace("Mac", "dustin@192.168.1.119:22", "server", Hue.BLUE, PickerDensity.DEFAULT, true))),
            new Nodes.Choice("technical-key", ControlState.READY, ChoiceKind.PICKER, List.of(), "", "Credential", "pick", "lock", Optional.empty(),
                Optional.of(new PickerFace("root", "SHA256:4f2a9c", "key", Hue.GREEN, PickerDensity.COMPACT, true))))))));
        // Plan 3b (SDK 0.18): a TABS choice (Overview and Details).
        result.add(new Fixture("tabs", "", "normal", 360, 60, new ViewSpec(new Nodes.Choice("tabs", ControlState.READY, ChoiceKind.TABS,
            List.of(new Option("overview", "Overview", Optional.empty()), new Option("details", "Details", Optional.empty())), "overview", "Plugin information", "tabs"))));
        // Plan 3b (SDK 0.18): a swatch of every hue that has one, each beside its name.
        var swatches = new ArrayList<Nodes.Node>();
        for (Hue hue : List.of(Hue.BLUE, Hue.GREEN, Hue.AMBER, Hue.PURPLE, Hue.NEUTRAL)) {
            swatches.add(new Nodes.Icon("swatch-" + hue, ControlState.READY, "file", hue, IconSize.SWATCH, hue.name()));
            swatches.add(new Nodes.Text("swatch-label-" + hue, ControlState.READY, hue.name(), TextRole.CAPTION));
        }
        result.add(new Fixture("swatches", "", "normal", 420, 40, new ViewSpec(new Nodes.Stack("swatches", ControlState.READY, LayoutRole.INLINE, swatches))));
        // Plan 3b (SDK 0.18): SFTP's navigation row, its groups divided by separators.
        result.add(new Fixture("separators", "", "normal", 420, 44, new ViewSpec(new Nodes.Stack("sftp-toolbar", ControlState.READY, LayoutRole.INLINE, List.of(
            new Nodes.Button("toolbar-back", ControlState.READY, "", "back", ButtonKind.GHOST, "back", "Back"),
            new Nodes.Button("toolbar-forward", ControlState.READY, "", "forward", ButtonKind.GHOST, "forward", "Forward"),
            new Nodes.Button("toolbar-refresh", ControlState.READY, "", "refresh", ButtonKind.GHOST, "refresh", "Refresh"),
            new Nodes.Separator("toolbar-first", ControlState.READY),
            new Nodes.Button("toolbar-hidden", ControlState.READY, "Hidden files", "", ButtonKind.GHOST, "hidden", ""),
            new Nodes.Separator("toolbar-second", ControlState.READY),
            new MenuButton("toolbar-upload", ControlState.READY, "Upload", "upload", ButtonKind.GHOST, "",
                List.of(new dev.jasper.ui.model.MenuItem("upload-files", "Files…", "", "files", true, false)), ""))))));
        // Plan 3b (SDK 0.18): a notice of each severity, the last with its action.
        var notices = new ArrayList<Nodes.Node>();
        for (var severity : Nodes.Severity.values()) notices.add(new Nodes.Notice("notice-" + severity, ControlState.READY, severity,
            switch (severity) { case INFO -> "Changes apply when the server restarts"; case WARNING -> "Plain FTP sends the password unencrypted"; case DANGER -> "The folder no longer exists"; },
            severity == Nodes.Severity.DANGER ? List.of(new Nodes.Button("notice-choose", ControlState.READY, "Choose…", "", ButtonKind.SECONDARY, "choose", "")) : List.of()));
        result.add(new Fixture("notice-severities", "", "normal", 420, 170, new ViewSpec(new Nodes.Stack("notice-severities", ControlState.READY, LayoutRole.VERTICAL, notices))));
        // Plan 3b (SDK 0.18): a selected host's properties, three data values and one with a severity.
        result.add(new Fixture("properties", "", "normal", 320, 170, new ViewSpec(new Nodes.Properties("properties", ControlState.READY, "Properties — minecraft-local", List.of(
            new Nodes.PropertyRow("Host", "192.168.1.124", true), new Nodes.PropertyRow("User", "dustin", true), new Nodes.PropertyRow("Port", "22", true),
            new Nodes.PropertyRow("Credentials", "Vault locked", false, Optional.of(Nodes.Severity.WARNING)))))));
        // Plan 3b (SDK 0.18): a prompt with a footnote, which takes the line above its full-width actions.
        result.add(new Fixture("form-footnote", "", "normal", 380, 200, new ViewSpec(new Form("unlock-footnote", ControlState.READY, FormKind.PROMPT_COMPACT, "Unlock Vault",
            Optional.empty(), List.of(new Secret("footnote-password", ControlState.READY, "Master password", "Master password", "")), Optional.empty(),
            List.of(new Nodes.Button("footnote-cancel", ControlState.READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("footnote-unlock", ControlState.READY, "Unlock", "", ButtonKind.PRIMARY, "unlock", "")),
            "cancel", FormSubmission.PRIMARY, "Auto-locks after 15 min idle"))));
        // Plan 3c (SDK 0.18): tree columns, cell nodes with the bare DROPDOWN, heading rows and the empty states.
        result.add(new Fixture("tables-0-18", "", "normal", 640, 860, TableFixtures.create()));
        return List.copyOf(result);
    }
    private static Fixture panelState(String name, String panel, String state, java.util.function.Consumer<PanelFixtures.Demo> transition) {
        ViewSpec[] spec = {null};
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var demo = PanelFixtures.mount(panel, context)) { transition.accept(demo); spec[0] = demo.view().spec(); }
            finally { context.binding().close(); context.icons().close(); }
        });
        return new Fixture(name, "", state, name.contains("short") ? 240 : 264, name.contains("short") ? 180 : 424, spec[0]);
    }
    public static GeneratedTheme theme(String name) {
        return switch (name) {
            case "dark" -> KitFixtures.dark(); case "light" -> KitFixtures.light();
            default -> throw new IllegalArgumentException("Unknown gallery theme: " + name);
        };
    }
    public static IconRegistry icons() {
        var registry = new IconRegistry();
        registry.register("gallery:user", "M 11 5 A 3 3 0 1 1 5 5 A 3 3 0 1 1 11 5 M 2 14 Q 2 9 8 9 Q 14 9 14 14");
        registry.register("gallery:vault", "M 8 1 L 14 4 V 8 Q 14 12 8 15 Q 2 12 2 8 V 4 Z M 6 8 V 6 A 2 2 0 0 1 10 6 V 8 M 5 8 H 11 V 12 H 5 Z");
        registry.register("gallery:menu", "M 3 4 H 13 M 3 8 H 13 M 3 12 H 13");
        return registry;
    }
    private static ThemeBinding contextBinding(ThemeBinding binding) { return binding; }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "jasper-ui/build/gallery" : args[0]);
        String name = System.getProperty("jasper.galleryTheme", "dark");
        GeneratedTheme theme = theme(name);
        Edt.run(() -> installed(theme, dev.jasper.ui.kit.Kit.DEFAULT, (context, focus) -> {
            try {
                float scale = UIScale.getUserScaleFactor();
                Path directory = output.resolve(name + "-" + (int)scale + "x");
                Files.createDirectories(directory);
                var contact = new ArrayList<Rendered>();
                for (Fixture fixture : fixtures()) {
                    var capture = capture(fixture, context, focus);
                    ImageIO.write(capture.image(), "png", directory.resolve(fixture.name() + ".png").toFile());
                    Files.writeString(directory.resolve(fixture.name() + ".json"), capture.metadata());
                    contact.add(new Rendered(fixture.name(), capture.image()));
                }
                writeSheets(directory, contact, scale);
                Files.writeString(directory.resolve("manifest.json"), "{\"theme\":" + quote(name) + ",\"scale\":" + scale + ",\"deviceScale\":1,\"lookAndFeel\":" + quote(UIManager.getLookAndFeel().getClass().getName()) + ",\"fixtureCount\":" + contact.size() + ",\"nodeKinds\":" + Nodes.Node.class.getPermittedSubclasses().length + "}");
                System.out.println("Rendered " + contact.size() + " fixtures with " + UIManager.getLookAndFeel().getName() + " at " + scale + "x to " + directory);
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }));
    }

    /** One fixture as the gallery renders it: the image it writes and the metadata beside it. */
    public record Capture(Fixture fixture, BufferedImage image, String metadata) {}

    /**
     * Runs {@code action} with {@code theme} and {@code kit} installed as the gallery renders them: real FlatLaf, the
     * platform family at 13 points, no caps-lock query and the gallery's focus manager, all restored afterwards. EDT.
     */
    public static void installed(GeneratedTheme theme, dev.jasper.ui.kit.Kit kit, java.util.function.BiConsumer<KitContext, GalleryFocus> action) {
        var priorLaf = UIManager.getLookAndFeel();
        Object priorFont = UIManager.get("defaultFont");
        Object priorCaps = UIManager.get("PasswordField.showCapsLock");
        String family = KitFixtures.platformFamily();
        var priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var focus = new GalleryFocus();
        try {
            // Install before creating any gallery widget, including transition controllers.
            FlatLafInstaller.install(theme, kit);
            UIManager.put("PasswordField.showCapsLock", false); // native keyboard-lock queries are unavailable headlessly
            UIManager.put("defaultFont", new Font(family, Font.PLAIN, 13));
            if (!(UIManager.getLookAndFeel() instanceof FlatLaf)) throw new AssertionError("Gallery must use real FlatLaf");
            KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
            var binding = new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); });
            try (var icons = icons(); var ownedBinding = contextBinding(binding)) {
                action.accept(KitContext.of(kit, ownedBinding, ThemeRoute.WORKSPACE, icons, () -> false, ignored -> ""), focus);
            }
        } finally {
            KeyboardFocusManager.setCurrentKeyboardFocusManager(priorFocus);
            UIManager.put("defaultFont", priorFont);
            UIManager.put("PasswordField.showCapsLock", priorCaps);
            try { UIManager.setLookAndFeel(priorLaf); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
        }
    }

    /** {@code fixture} rendered as {@link #main} writes it, in {@code context} with {@code focus} from {@link #installed}. EDT. */
    public static Capture capture(Fixture fixture, KitContext context, GalleryFocus focus) {
        String family = KitFixtures.platformFamily();
        float scale = UIScale.getUserScaleFactor();
        UIManager.put("defaultFont", new Font(family, Font.PLAIN, fixture.name().contains("font24") ? 24 : 13));
        try (var view = new KitView(context, fixture.spec(), e -> {})) {
            registerCustom(view, fixture.spec().root(), context);
            int width = UIScale.scale(fixture.width()), height = UIScale.scale(fixture.height());
            view.setAvailableHeight(fixture.height() + 1000); // reference content has no artificial screen cap
            KitFixtures.render(view, width, height, 1);
            if (fixture.name().contains("short-font24")) {
                descendants(view).stream().filter(JScrollPane.class::isInstance).map(JScrollPane.class::cast).findFirst().ifPresent(scroll -> scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()));
            }
            applyState(view, fixture, focus);
            JComponent paintedRoot = view;
            BufferedImage rendered = onSurface(KitFixtures.render(view, width, height, 1), context);
            if (fixture.name().equals("dropdown-open")) {
                var combo=(JComboBox<?>)find(view,"dropdown.input");
                var popup=(javax.swing.plaf.basic.BasicComboPopup)combo.getUI().getAccessibleChild(combo,0);
                popup.getList().setSelectedIndex(0);paintedRoot=popup;
                rendered=paint(popup,UIScale.scale(240),UIScale.scale(100));
            }
            if (fixture.name().equals("vault-add-menu")) {
                var anchor = find(view, "add-menu");
                var popup = anchor.getComponentPopupMenu();
                popup.setInvoker(anchor);
                paintedRoot = popup;
                rendered = paint(popup, popup.getPreferredSize().width, popup.getPreferredSize().height);
            }
            return new Capture(fixture, rendered, metadata(fixture, view, context, scale, rendered, paintedRoot));
        } finally {
            if (focus.target != null) {
                var lost = new FocusEvent(focus.target, FocusEvent.FOCUS_LOST);
                for (var listener : focus.target.getFocusListeners()) listener.focusLost(lost);
                focus.target = null;
            }
        }
    }

    /** The gallery's focus manager: the focus owner is whatever {@link #applyState} focused. */
    public static final class GalleryFocus extends DefaultKeyboardFocusManager {
        Component target;
        @Override public Component getFocusOwner() { return target; }
        @Override public Component getPermanentFocusOwner() { return target; }
    }
    static void applyState(KitView view, Fixture fixture, GalleryFocus focus) {
        if(fixture.name().equals("credential-vault") || fixture.name().startsWith("stored-secret")) {
            String id=fixture.name().equals("credential-vault")?"passphrase":"stored-secret";
            char[] value={'e','x','a','m','p','l','e','-','o','n','l','y'};
            try{view.replaceSecret(id,value);}finally{java.util.Arrays.fill(value,(char)0);}
            if(fixture.state().equals("stored-secret-revealed"))((JButton)find(view,id+".reveal")).doClick(0);
        }
        if (fixture.name().equals("discreet-secret-populated")) {
            char[] value = {'e', 'x', 'a', 'm', 'p', 'l', 'e'};
            try { view.replaceSecret("discreet-secret", value); }
            finally { Arrays.fill(value, (char) 0); }
        }
        if (fixture.state().equals("focus")) {
            JComponent root = find(view, fixture.name().equals("unlock-vault") ? "password" : fixture.name().startsWith("discreet-secret-") ? "discreet-secret" : "state-field");
            Component target = descendants(root).stream().filter(c -> c instanceof JTextField).findFirst().orElse(root);
            focus.target = target;
            var gained = new FocusEvent(target, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.TRAVERSAL_FORWARD);
            for (var listener : target.getFocusListeners()) listener.focusGained(gained);
        }
        if (fixture.state().equals("hover") || fixture.state().equals("pressed")) {
            var b = (AbstractButton)find(view, "state-button");
            b.getModel().setRollover(true);
            if (fixture.state().equals("pressed")) { b.getModel().setArmed(true); b.getModel().setPressed(true); }
        }
    }
    static JComponent find(Container root, String name) {
        return descendants(root).stream().filter(c -> name.equals(c.getName())).filter(JComponent.class::isInstance).map(JComponent.class::cast).findFirst().orElseThrow(() -> new AssertionError("Missing mounted node: " + name));
    }
    static List<Component> descendants(Container root) {
        var result = new ArrayList<Component>(); result.add(root);
        for (var child : root.getComponents()) {
            if (child instanceof Container nested) result.addAll(descendants(nested)); else result.add(child);
        }
        return result;
    }
    private static void registerCustom(KitView view, Node node, KitContext context) {
        if (node instanceof Custom custom) {
            if (custom.kind() == CustomKind.EDITOR) {
                var editor = new JTextArea("// owned editor content\nJasper preserves this document.");
                editor.setForeground(context.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
                editor.setBackground(context.theme().semantic().color(SemanticColor.SURFACE_INPUT));
                view.registerContent(custom.contentId(), editor, () -> {});
                return;
            }
            view.registerContent(custom.contentId(), new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(context.theme().semantic().color(SemanticColor.SURFACE_TERMINAL)); graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(context.theme().semantic().color(SemanticColor.TEXT_PRIMARY)); graphics.drawString(custom.kind() + " · owned specialized content", UIScale.scale(20), UIScale.scale(35));
            }
            @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(480), UIScale.scale(180)); }
        }, () -> {});
        }
    }
    static String metadata(Fixture fixture, KitView view, KitContext context, float scale, BufferedImage rendered) {
        return metadata(fixture, view, context, scale, rendered, view);
    }
    static String metadata(Fixture fixture, KitView view, KitContext context, float scale, BufferedImage rendered, JComponent paintedRoot) {
        var result = new StringBuilder("{\"fixture\":" + quote(fixture.name()) + ",\"reference\":" + quote(fixture.reference()) + ",\"state\":" + quote(fixture.state()) + ",\"route\":" + quote(context.route().name()) + ",\"scale\":" + scale + ",\"deviceScale\":1,\"width\":" + rendered.getWidth() + ",\"height\":" + rendered.getHeight() + ",\"font\":" + quote(context.typography().family()) + ",\"bodySize\":" + context.typography().bodySize() + ",\"motionEnabled\":false,\"nodes\":[");
        boolean first = true;
        for (var component : descendants(paintedRoot)) if (component instanceof JComponent c && (KitOwnership.owns(c) || paintedRoot != view)) {
            if (!first) result.append(','); first = false;
            var bounds = c == paintedRoot ? new Rectangle(0, 0, c.getWidth(), c.getHeight()) : SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), paintedRoot);
            result.append("{\"id\":").append(quote(c.getName())).append(",\"class\":").append(quote(c.getClass().getName())).append(",\"visible\":").append(c.isVisible()).append(",\"enabled\":").append(c.isEnabled()).append(",\"focus\":").append(c.isFocusOwner()).append(",\"bounds\":[").append(bounds.x).append(',').append(bounds.y).append(',').append(bounds.width).append(',').append(bounds.height).append("]}");
        }
        result.append("],\"descriptorStates\":[");
        appendStates(fixture.spec().root(), result, new boolean[]{true});
        return result.append("]}").toString();
    }
    private static void appendStates(Object value, StringBuilder out, boolean[] first) {
        if (value instanceof Node node) {
            if (!first[0]) out.append(','); first[0] = false;
            out.append("{\"id\":").append(quote(node.id())).append(",\"kind\":").append(quote(node.getClass().getSimpleName())).append(",\"visible\":").append(node.state().visible()).append(",\"enabled\":").append(node.state().enabled()).append(",\"busy\":").append(node.state().busy()).append(",\"error\":").append(quote(node.state().error())).append('}');
        }
        if (value instanceof Optional<?> optional) optional.ifPresent(v -> appendStates(v, out, first));
        else if (value instanceof Iterable<?> iterable) iterable.forEach(v -> appendStates(v, out, first));
        else if (value instanceof Record record && !(value instanceof ControlState)) try {
            for (var component : record.getClass().getRecordComponents()) {
                Object child = component.getAccessor().invoke(record);
                if (child instanceof Node || child instanceof ListItem || child instanceof Optional<?> || child instanceof Iterable<?>) appendStates(child, out, first);
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static BufferedImage onSurface(BufferedImage content, KitContext context) {
        var image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(context.theme().semantic().color(SemanticColor.SURFACE_PANEL)); g.fillRect(0, 0, image.getWidth(), image.getHeight()); g.drawImage(content, 0, 0, null); }
        finally { g.dispose(); }
        return image;
    }
    static String quote(String value) { if (value == null) return "null"; return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; }
    private record Rendered(String name, BufferedImage image) {}
    private static void writeSheets(Path output, List<Rendered> renders, float scale) throws IOException {
        // Full-resolution sheets; no thumbnail transform can conceal scale/layout regressions.
        for (int start = 0; start < renders.size(); start += 8) {
            var group = renders.subList(start, Math.min(start + 8, renders.size()));
            int cellWidth = group.stream().mapToInt(r -> r.image().getWidth()).max().orElseThrow() + UIScale.scale(24);
            int cellHeight = group.stream().mapToInt(r -> r.image().getHeight()).max().orElseThrow() + UIScale.scale(40);
            var sheet = new BufferedImage(cellWidth * 4, cellHeight * ((group.size() + 3) / 4), BufferedImage.TYPE_INT_RGB);
            var g = sheet.createGraphics();
            try {
                g.setColor(new Color(0x777777)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, UIScale.scale(12))); g.setColor(Color.WHITE);
                for (int i = 0; i < group.size(); i++) { var r = group.get(i); int x = (i % 4) * cellWidth, y = (i / 4) * cellHeight; g.drawString(r.name(), x + UIScale.scale(8), y + UIScale.scale(18)); g.drawImage(r.image(), x + UIScale.scale(8), y + UIScale.scale(28), null); }
            } finally { g.dispose(); }
            ImageIO.write(sheet, "png", output.resolve("sheet-" + (start / 8 + 1) + ".png").toFile());
        }
    }
    private static void layout(Container component) {
        component.doLayout();for(var child:component.getComponents())if(child instanceof Container nested)layout(nested);
    }
    static BufferedImage paint(JComponent component, int width, int height) {
        component.setSize(width, height); layout(component);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); } return image;
    }
}
