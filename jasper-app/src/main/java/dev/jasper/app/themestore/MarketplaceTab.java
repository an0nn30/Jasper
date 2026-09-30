package dev.jasper.app.themestore;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;

/** The Marketplace tab: a search over Theme-tagged plugins, the selected plugin's details, and Install. EDT only. */
final class MarketplaceTab {
    private static final Icon BLANK = new ImageIcon(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
    private final ThemeStore store;
    private final Banner banner;
    private final Supplier<Map<String, String>> installedVersions;
    private final Consumer<String> use;
    private final Runnable installedChanged;
    private final JPanel component = new JPanel(new BorderLayout()) {
        @Override public void updateUI() {
            super.updateUI();
            // JPanel's constructor calls this before the fields exist.
            if (name != null) restyle();
        }
    };
    private final JTextField search = new JTextField();
    private final DefaultListModel<MarketplaceClient.Listing> model = new DefaultListModel<>();
    private final JList<MarketplaceClient.Listing> list = new JList<>(model);
    private final JButton more = new JButton("Load more");
    private final JLabel status = new JLabel();
    private final JLabel detailStatus = new JLabel();
    private final JButton detailRetry = new JButton("Retry");
    private final JButton retry = new JButton("Retry");
    private final JLabel name = new JLabel();
    private final JLabel vendor = new JLabel();
    private final JLabel version = new JLabel();
    private final JEditorPane description = new JEditorPane();
    private final JButton install = new JButton("Install");
    private final JButton view = new JButton("View on Marketplace");
    private final Map<String, Icon> icons = new HashMap<>();
    private final Timer debounce;
    private String query = "";
    private int nextOffset;
    private boolean activated;
    private boolean busy;
    private boolean disposed;
    private boolean statusError;
    private boolean detailError;
    private int generation;
    private int detailGeneration;
    private MarketplaceClient.Release release;

    MarketplaceTab(ThemeStore store, Banner banner, Supplier<Map<String, String>> installedVersions, Consumer<String> use,
                   Runnable installedChanged) {
        this.store = store;
        this.banner = banner;
        this.installedVersions = installedVersions;
        this.use = use;
        this.installedChanged = installedChanged;
        debounce = new Timer(300, event -> search(search.getText()));
        debounce.setRepeats(false);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search themes");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { debounce.restart(); }
            @Override public void removeUpdate(DocumentEvent event) { debounce.restart(); }
            @Override public void changedUpdate(DocumentEvent event) { debounce.restart(); }
        });
        search.addActionListener(event -> { debounce.stop(); search(search.getText()); });
        for (JComponent label : List.of(status, detailStatus, detailRetry, name, vendor, version, install, view, more, retry)) label.putClientProperty("html.disable", true);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getAccessibleContext().setAccessibleName("Marketplace themes");
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showDetails(); });
        more.addActionListener(event -> load());
        retry.addActionListener(event -> load());
        detailRetry.addActionListener(event -> showDetails());
        install.addActionListener(event -> install());
        view.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        view.addActionListener(event -> {
            MarketplaceClient.Listing listing = list.getSelectedValue();
            if (listing != null && !listing.link().isEmpty()) store.browse(listing.link());
        });
        description.setContentType("text/html");
        description.setEditable(false);
        description.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        description.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription() != null) store.browse(event.getDescription());
        });

        var statusRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        statusRow.setOpaque(false);
        statusRow.add(status);
        statusRow.add(retry);
        statusRow.add(more);
        var left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        left.add(search, BorderLayout.NORTH);
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        left.add(statusRow, BorderLayout.SOUTH);
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        buttons.setOpaque(false);
        buttons.add(install);
        buttons.add(view);
        var detailRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        detailRow.setOpaque(false);
        detailRow.add(detailStatus);
        detailRow.add(detailRetry);
        for (JComponent part : List.of(name, vendor, version, buttons, detailRow)) { part.setAlignmentX(0f); header.add(part); }
        header.add(Box.createVerticalStrut(4));
        var right = new JPanel(new BorderLayout());
        right.add(header, BorderLayout.NORTH);
        right.add(new JScrollPane(description), BorderLayout.CENTER);
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.45);
        split.setBorder(null);
        component.setOpaque(false);
        component.add(split, BorderLayout.CENTER);
        restyle();
        status("", false);
        more.setVisible(false);
        showDetails();
    }

    JComponent component() { return component; }

    /** Runs the first search the first time the tab is shown. */
    void activate() {
        if (activated) return;
        activated = true;
        search(search.getText());
    }

    /** Refreshes what depends on the installed themes: badges and the Install button. */
    void installedChanged() {
        list.repaint();
        MarketplaceClient.Listing listing = list.getSelectedValue();
        if (listing != null) syncInstall(listing);
    }

    void dispose() { disposed = true; debounce.stop(); generation++; detailGeneration++; }

    /** Starts a new search now. */
    void search(String text) {
        query = text == null ? "" : text.strip();
        model.clear();
        nextOffset = 0;
        load();
    }

    private void load() {
        int asked = ++generation;
        status("Searching\u2026", false);
        more.setVisible(false);
        store.search(query, nextOffset).whenComplete((page, failure) -> {
            if (asked != generation) return;
            if (failure != null) { status(ThemeStore.message(failure), true); return; }
            page.listings().forEach(model::addElement);
            nextOffset = page.nextOffset();
            more.setVisible(page.more());
            status(!model.isEmpty() ? "" : query.isEmpty() ? "The Marketplace lists no themes." : "No themes match \u201c" + query + "\u201d.", false);
            if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0);
        });
    }

    private void status(String text, boolean error) {
        statusError = error;
        status.setText(text);
        status.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.disabledForeground"));
        status.setVisible(!text.isEmpty());
        retry.setVisible(error);
    }

    private void showDetails() {
        MarketplaceClient.Listing listing = list.getSelectedValue();
        int asked = ++detailGeneration;
        release = null;
        detailStatus("", false);
        boolean shown = listing != null;
        install.setVisible(shown);
        view.setVisible(shown && !listing.link().isEmpty());
        if (!shown) {
            name.setText(""); vendor.setText(""); version.setText(""); description.setText("");
            return;
        }
        name.setText(listing.name());
        vendor.setText(listing.vendor());
        version.setText("");
        description.setText("<html><body><p>" + escape(listing.preview()) + "</p></body></html>");
        syncInstall(listing);
        store.details(listing.id()).whenComplete((details, failure) -> {
            if (asked != detailGeneration) return;
            if (failure != null) { detailStatus(ThemeStore.message(failure), true); return; }
            description.setText(safeHtml(details.descriptionHtml()));
            description.setCaretPosition(0);
            if (!details.vendor().isEmpty()) vendor.setText(details.vendor());
        });
        store.latest(listing.id()).whenComplete((found, failure) -> {
            if (asked != detailGeneration) return;
            release = failure != null || found == null ? null : found.orElse(null);
            version.setText(release == null ? "" : "Version " + release.version());
            syncInstall(listing);
        });
    }

    private void detailStatus(String text, boolean error) {
        detailError = error;
        detailStatus.setText(text);
        detailStatus.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.disabledForeground"));
        detailStatus.setVisible(!text.isEmpty());
        detailRetry.setVisible(error);
    }

    private void syncInstall(MarketplaceClient.Listing listing) {
        String have = installedVersions.get().get(listing.xmlId());
        if (busy) { install.setText("Installing\u2026"); install.setEnabled(false); }
        else if (have == null) { install.setText("Install"); install.setEnabled(true); }
        else if (release != null && Versions.newer(release.version(), have)) { install.setText("Update"); install.setEnabled(true); }
        else { install.setText("Installed"); install.setEnabled(false); }
    }

    private void install() { install(list.getSelectedValue()); }

    private void install(MarketplaceClient.Listing listing) {
        if (listing == null || busy) return;
        busy = true;
        syncInstall(listing);
        banner.info("Installing " + listing.name() + "\u2026");
        store.install(listing.id(), listing.xmlId(), listing.name()).whenComplete((result, failure) -> {
            if (disposed) return;
            busy = false;
            if (failure != null) banner.errorOffer(ThemeStore.message(failure), "Retry", () -> install(listing));
            else {
                installedChanged.run();
                String first = result.themes().getFirst();
                banner.offer("Installed " + listing.name() + ".", "Use \u201c" + first + "\u201d", () -> use.accept(first));
            }
            installedChanged();
        });
    }

    private Icon icon(String path) {
        if (path.isEmpty()) return BLANK;
        Icon known = icons.get(path);
        if (known != null) return known;
        icons.put(path, BLANK);
        store.icon(path).whenComplete((bytes, failure) -> {
            if (failure != null) return;
            try { icons.put(path, new FlatSVGIcon(new ByteArrayInputStream(bytes)).derive(32, 32)); }
            catch (IOException | RuntimeException unreadable) { return; }
            list.repaint();
        });
        return BLANK;
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) name.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
        vendor.setForeground(UIManager.getColor("Label.disabledForeground"));
        version.setForeground(UIManager.getColor("Label.disabledForeground"));
        status.setForeground(UIManager.getColor(statusError ? "Actions.Red" : "Label.disabledForeground"));
        detailStatus.setForeground(UIManager.getColor(detailError ? "Actions.Red" : "Label.disabledForeground"));
    }

    private static final java.util.Set<String> KEPT = java.util.Set.of("p", "br", "div", "span", "ul", "ol", "li", "b", "strong", "i", "em",
        "u", "code", "pre", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "a", "hr", "table", "tr", "td", "th", "thead", "tbody");
    private static final java.util.regex.Pattern TAG = java.util.regex.Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
    private static final java.util.regex.Pattern HREF =
        java.util.regex.Pattern.compile("(?is)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");

    /**
     * The description reduced to an allowlist of formatting tags with no attributes, except {@code href} on links. Comments,
     * scripts and styles go entirely; any other tag goes and its text stays. Nothing can load an image, stylesheet or frame.
     */
    static String safeHtml(String html) {
        String text = html.replaceAll("(?s)<!--.*?(-->|$)", "")
            .replaceAll("(?is)<(script|style)\\b.*?(</\\1\\s*>|$)", "");
        var out = new StringBuilder("<html><body>");
        var matcher = TAG.matcher(text);
        int last = 0;
        while (matcher.find()) {
            out.append(text.substring(last, matcher.start()).replace("<", "&lt;"));
            last = matcher.end();
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            if (!KEPT.contains(tag)) continue;
            if (!matcher.group(1).isEmpty()) { out.append("</").append(tag).append('>'); continue; }
            out.append('<').append(tag);
            if (tag.equals("a")) {
                var href = HREF.matcher(matcher.group(3));
                if (href.find()) {
                    String value = href.group(1) != null ? href.group(1) : href.group(2) != null ? href.group(2) : href.group(3);
                    out.append(" href=\"").append(escape(value.replace("&amp;", "&")).replace("\"", "&quot;")).append('"');
                }
            }
            out.append('>');
        }
        out.append(text.substring(last).replaceAll("(?s)<[a-zA-Z/!?].*$", "").replace("<", "&lt;"));
        return out.append("</body></html>").toString();
    }

    static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    /** Vendor, downloads, rating and badges, joined with middle dots. */
    static String meta(MarketplaceClient.Listing listing, boolean installed) {
        var parts = new ArrayList<String>();
        if (!listing.vendor().isEmpty()) parts.add(listing.vendor());
        parts.add(downloads(listing.downloads()) + " downloads");
        if (listing.rating() > 0) parts.add(String.format(Locale.ROOT, "\u2605 %.1f", listing.rating()));
        if (listing.freemium()) parts.add("Freemium");
        if (installed) parts.add("Installed");
        return String.join("  \u00b7  ", parts);
    }

    static String downloads(long count) {
        if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format(Locale.ROOT, "%.0fK", count / 1_000.0);
        return Long.toString(count);
    }

    // Test access.
    List<String> listed() { var names = new ArrayList<String>(); for (int i = 0; i < model.size(); i++) names.add(model.get(i).name()); return names; }
    void select(String listingName) { for (int i = 0; i < model.size(); i++) if (model.get(i).name().equals(listingName)) list.setSelectedIndex(i); }
    String details() {
        try { return String.join("\n", name.getText(), vendor.getText(), version.getText(),
            description.getDocument().getText(0, description.getDocument().getLength())); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }
    JButton installButton() { return install; }
    JButton viewButton() { return view; }
    JButton retryButton() { return retry; }
    String listLine(String listingName) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).name().equals(listingName)) return meta(model.get(i), installedVersions.get().containsKey(model.get(i).xmlId()));
        }
        return "";
    }
    String status() { return status.getText(); }
    String detailStatus() { return detailStatus.getText(); }
    JButton detailRetryButton() { return detailRetry; }

    /** Icon, bold name, then vendor, downloads, rating and badges. */
    private final class Renderer extends JPanel implements ListCellRenderer<MarketplaceClient.Listing> {
        private final JLabel image = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel line = new JLabel();

        Renderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            title.putClientProperty("html.disable", true);
            line.putClientProperty("html.disable", true);
            var text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            text.add(title);
            text.add(line);
            add(image, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override public Component getListCellRendererComponent(JList<? extends MarketplaceClient.Listing> owner,
                                                                MarketplaceClient.Listing value, int index, boolean selected, boolean focused) {
            image.setIcon(icon(value.iconPath()));
            title.setText(value.name());
            title.setFont(owner.getFont().deriveFont(Font.BOLD));
            line.setText(meta(value, installedVersions.get().containsKey(value.xmlId())));
            title.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
            line.setForeground(selected ? owner.getSelectionForeground() : UIManager.getColor("Label.disabledForeground"));
            setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            setOpaque(true);
            return this;
        }
    }
}
