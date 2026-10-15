package dev.jasper.app.about;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * The Licenses window's content: Jasper and the third-party components it ships, beside the selected one's name,
 * version, license and homepage link, then its NOTICE text when it has one and its license text. One chrome kit view
 * composes it as a manager window (the Errors window's arrangement); the texts are read-only selectable text, the
 * license scrolling inside its card. Texts are read when a component is selected. EDT only; {@link #close} stops it.
 */
public final class LicensesView extends JPanel {
    /**
     * What the view asks of the application.
     *
     * @param browse opens a homepage
     * @param done closes the window (Done and Escape)
     */
    public record Hooks(Consumer<URI> browse, Runnable done) {
        public Hooks {
            Objects.requireNonNull(browse, "browse");
            Objects.requireNonNull(done, "done");
        }
    }

    static final String LIST = "licenses", TITLE = "detail.title", LICENSE_NAME = "detail.license", HOMEPAGE = "homepage", DONE = "done";
    private static final String NOTICE_SLOT = "licenses.notice", TEXT_SLOT = "licenses.text";
    private final KitContext kit;
    private final List<ThirdPartyNotices.Component> components;
    private final Hooks hooks;
    private final KitView view;
    private final JTextArea notice = new JTextArea(), text = new JTextArea();
    private final JScrollPane textScroll;
    private final AutoCloseable theme;
    private final AutoCloseable typography;
    private int selected, shown = -1;
    /** Whether the notice and text widgets are registered with their mounted Custom slots. */
    private boolean noticeMounted, textMounted, closed;

    /**
     * @param kit the chrome kit context
     * @param components what it lists, in order; the first is selected
     * @param hooks what it asks of the application
     */
    public LicensesView(KitContext kit, List<ThirdPartyNotices.Component> components, Hooks hooks) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        this.components = List.copyOf(components);
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        if (this.components.isEmpty()) throw new IllegalArgumentException("Nothing to list");
        setOpaque(false);
        notice.setLineWrap(true);
        notice.setWrapStyleWord(true);
        notice.getAccessibleContext().setAccessibleName("Notice");
        text.getAccessibleContext().setAccessibleName("License text");
        // A narrow preferred width: the card gives the text its width and the scroll pane shows the long lines.
        text.setColumns(1);
        for (var area : List.of(notice, text)) { area.setEditable(false); area.setOpaque(false); area.setBorder(BorderFactory.createEmptyBorder()); }
        textScroll = new JScrollPane(text, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        textScroll.setBorder(BorderFactory.createEmptyBorder());
        textScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        textScroll.setOpaque(false);
        textScroll.getViewport().setOpaque(false);
        view = new KitView(kit, spec(), this::event);
        view.getAccessibleContext().setAccessibleName("Licenses");
        add(view, BorderLayout.CENTER);
        theme = kit.binding().subscribe(pair -> restyle());
        typography = NativeStyles.installed().observeTypography(this::restyle);
        restyle();
        render();
    }

    /** The texts take the module's mono roles and the theme's text and selection colours. */
    private void restyle() {
        if (closed) return;
        notice.setFont(NativeStyles.installed().font(kit, Typography.Role.MONO_SMALL));
        text.setFont(NativeStyles.installed().font(kit, Typography.Role.MONO_SMALL));
        for (var area : List.of(notice, text)) {
            area.setForeground(kit.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
            area.setCaretColor(area.getForeground());
            area.setSelectionColor(kit.theme().semantic().color(SemanticColor.SELECTION_BG));
            area.setSelectedTextColor(kit.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
        }
        revalidate();
        repaint();
    }

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Selected chosen when chosen.id().equals(LIST) ->
                chosen.ids().stream().findFirst().map(LicensesView::index).ifPresent(this::select);
            case UiEvent.Action action -> {
                switch (action.id()) {
                    case HOMEPAGE -> hooks.browse().accept(URI.create(current().homepage()));
                    case DONE -> hooks.done().run();
                    default -> { }
                }
            }
            default -> { }
        }
    }

    /** Shows the component at {@code index}; an index outside the list is ignored. */
    void select(int index) {
        if (closed || index < 0 || index >= components.size()) return;
        selected = index;
        render();
    }

    /** Stops listening and closes the kit view; the window calls this when it closes. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the view */ }
        view.close();
    }

    private ThirdPartyNotices.Component current() { return components.get(selected); }

    /** Shows the list and the selection; a newly selected component's texts are read once, from the top. */
    private void render() {
        if (closed) return;
        ThirdPartyNotices.Component component = current();
        if (shown != selected) {
            notice.setText(component.notice().map(LicensesView::readOrSay).orElse(""));
            notice.setCaretPosition(0);
            text.setText(readOrSay(component.text()));
            text.setCaretPosition(0);
            text.setRows(Math.clamp(text.getLineCount(), DesignMetrics.NATIVE_BODY_VISIBLE_UNITS,
                kit.metrics().value(DesignMetrics.Role.ERRORS_TRACE_ROWS)));
            shown = selected;
        }
        view.update(spec());
        // The notice's Custom slot comes and goes with the selection; the same widget returns to it.
        if (component.notice().isPresent() && !noticeMounted) { view.registerContent(NOTICE_SLOT, notice, () -> noticeMounted = false); noticeMounted = true; }
        if (!textMounted) { view.registerContent(TEXT_SLOT, textScroll, () -> textMounted = false); textMounted = true; }
    }

    /** A text, or a line saying it is missing from this build (verifyThirdPartyNotices should have caught that). */
    private static String readOrSay(String path) {
        try {
            return ThirdPartyNotices.read(path);
        } catch (IllegalStateException missing) {
            return "This build is missing " + path + ".";
        }
    }

    private static String id(int index) { return "component:" + index; }

    private static int index(String id) {
        try {
            return Integer.parseInt(id.substring(id.indexOf(':') + 1));
        } catch (NumberFormatException | StringIndexOutOfBoundsException unknown) {
            return -1;
        }
    }

    private ViewSpec spec() {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            items.add(new ListItem(id(i), Optional.empty(), List.of(text("row:" + i, component.title(), Nodes.TextRole.BODY)),
                List.of(text("row.license:" + i, component.licenseName(), Nodes.TextRole.CAPTION)), List.of()));
        }
        var list = new Nodes.ItemList(LIST, ControlState.READY, Nodes.ListKind.DOUBLE, items, Set.of(id(selected)), "", List.of());
        var listPanel = new Nodes.Panel("licenses.list", ControlState.READY, hiddenHeader("licenses.list.header", "Components"),
            Optional.empty(), list, Optional.empty());
        var detailPanel = new Nodes.Panel("licenses.detail", ControlState.READY, hiddenHeader("licenses.detail.header", "License"),
            Optional.empty(), detail(), Optional.empty());
        var columns = new Nodes.Split("licenses.columns", ControlState.READY, Nodes.SplitKind.SIDEBAR_DETAIL, List.of(listPanel, detailPanel));
        return new ViewSpec(new Nodes.Form("licenses.window", ControlState.READY, Nodes.FormKind.WINDOW, "Licenses", Optional.empty(),
            List.of(columns), Optional.empty(), List.of(new Nodes.Button(DONE, ControlState.READY, "Done", "", Nodes.ButtonKind.PRIMARY, DONE, "")),
            DONE, Nodes.FormSubmission.NONE));
    }

    private Nodes.Node detail() {
        var component = current();
        var heading = new Nodes.Stack("detail.heading", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Stack("detail.identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                text(TITLE, component.title(), Nodes.TextRole.DETAIL_TITLE),
                text(LICENSE_NAME, component.licenseName(), Nodes.TextRole.DESCRIPTION))),
            new Nodes.Button(HOMEPAGE, ControlState.READY, component.homepage().replaceFirst("^https?://", ""), "",
                Nodes.ButtonKind.LINK, HOMEPAGE, component.homepage())));
        var parts = new ArrayList<Nodes.Node>(List.of(heading));
        if (component.notice().isPresent())
            parts.add(card("notice", "Notice", new Nodes.Custom("notice", ControlState.READY, NOTICE_SLOT, Nodes.CustomKind.EDITOR, "Notice")));
        parts.add(card("text", "License", new Nodes.Custom("text", ControlState.READY, TEXT_SLOT, Nodes.CustomKind.EDITOR, "License text")));
        return new Nodes.Stack("detail.content", ControlState.READY, Nodes.LayoutRole.DETAIL, parts);
    }

    /** A detail card holding one captioned value, as the Errors window's detail rows. */
    private static Nodes.Card card(String id, String caption, Nodes.Node value) {
        return new Nodes.Card("detail." + id + ".card", ControlState.READY, Nodes.CardKind.FORM, List.of(
            new Nodes.Stack("detail." + id + ".row", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                text("detail." + id + ".label", caption, Nodes.TextRole.CAPTION), value))));
    }

    private static Nodes.Header hiddenHeader(String id, String title) {
        return new Nodes.Header(id, new ControlState(false, true, false, ""), title, OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Nodes.Text text(String id, String text, Nodes.TextRole role) {
        return new Nodes.Text(id, ControlState.READY, text, role);
    }

    String rowTitle(int index) {
        var form = (Nodes.Form) view.spec().root();
        var columns = (Nodes.Split) form.sections().getFirst();
        var list = (Nodes.ItemList) ((Nodes.Panel) columns.children().getFirst()).body();
        return ((Nodes.Text) list.items().get(index).title().getFirst()).text();
    }
    String noticeText() { return notice.getText(); }
    String licenseText() { return text.getText(); }
    int licenseCaret() { return text.getCaretPosition(); }
}
