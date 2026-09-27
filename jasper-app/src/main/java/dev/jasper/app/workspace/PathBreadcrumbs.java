package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.terminals.PaneSnapshot;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.UIManager;

/** Display-only path segments. Remote paths stay text and are never resolved on the local machine. */
final class PathBreadcrumbs extends JPanel {
    record Crumb(String label, String path, String icon) { }
    record Location(List<Crumb> crumbs, String path) {
        Location { crumbs = List.copyOf(crumbs); }
        static Location from(PaneSnapshot pane, Path home) {
            if (pane == null) return new Location(List.of(), "");
            if (pane.providerId().isPresent() || pane.remoteDirectory().isPresent()) {
                var remote = pane.remoteDirectory();
                String host = pane.providerId().isPresent() ? pane.shell() : remote.orElseThrow().host();
                if (host.isBlank()) host = remote.map(value -> value.host()).filter(value -> !value.isBlank()).orElse("Remote");
                if (remote.isEmpty()) return new Location(List.of(new Crumb(clean(host), host + ": directory not reported", "SERVER")), host + ": directory not reported");
                String path = remote.orElseThrow().path(), prefix = remote.orElseThrow().host();
                String full = remote.orElseThrow().label();
                var crumbs = new ArrayList<Crumb>();
                crumbs.add(new Crumb(clean(host), full, "SERVER"));
                String current = path.startsWith("/") ? "/" : "";
                for (String part : path.split("/")) {
                    if (part.isEmpty()) continue;
                    current += (current.isEmpty() || current.endsWith("/") ? "" : "/") + part;
                    crumbs.add(new Crumb(clean(part), (prefix.isEmpty() ? "" : prefix + ":") + current, null));
                }
                if (crumbs.size() == 1) crumbs.add(new Crumb(path.isEmpty() ? "Unknown directory" : "/", full, null));
                return new Location(crumbs, full);
            }
            return local(pane.workingDirectory().orElse(home), home);
        }
        static Location local(Path path, Path home) {
            Path absolute = path.toAbsolutePath().normalize(), base = home.toAbsolutePath().normalize();
            var crumbs = new ArrayList<Crumb>();
            Path current;
            if (absolute.startsWith(base)) {
                current = base; crumbs.add(new Crumb("~", base.toString(), "FOLDER"));
            } else {
                current = absolute.getRoot(); crumbs.add(new Crumb(clean(current.toString()), current.toString(), "FOLDER"));
            }
            for (Path part : current.relativize(absolute)) {
                if (part.toString().isEmpty()) continue;
                current = current.resolve(part);
                crumbs.add(new Crumb(clean(part.toString()), current.toString(), null));
            }
            return new Location(crumbs, absolute.toString());
        }
    }
    private Location location = new Location(List.of(), "");
    private final List<JLabel> labels = new ArrayList<>(), separators = new ArrayList<>();
    private final JLabel ellipsis = label("\u2026");

    PathBreadcrumbs() {
        super(null); setOpaque(false); setName("pathBreadcrumbs");
        getAccessibleContext().setAccessibleName("Working directory breadcrumbs");
        refreshTheme();
    }
    void setLocation(Location next) {
        if (next.equals(location)) return;
        location = next; removeAll(); labels.clear(); separators.clear();
        for (Crumb crumb : next.crumbs()) {
            var label = label(crumb.label()); label.setToolTipText(crumb.path());
            label.getAccessibleContext().setAccessibleName(crumb.path());
            label.setIcon(crumb.icon() == null ? null : AppIcons.named(crumb.icon()));
            label.setIconTextGap(UIScale.scale(5)); labels.add(label); add(label);
            var separator = new JLabel(AppIcons.named("NEXT")); separators.add(separator); add(separator);
        }
        add(ellipsis); ellipsis.setToolTipText(next.path()); setToolTipText(next.path());
        getAccessibleContext().setAccessibleDescription(next.path());
        refreshTheme(); revalidate(); repaint();
    }
    void refreshTheme() {
        for (var label : labels) { label.setFont(UIManager.getFont("Label.font")); label.setForeground(UIManager.getColor("Jasper.mutedForeground")); }
        ellipsis.setFont(UIManager.getFont("Label.font")); ellipsis.setForeground(UIManager.getColor("Jasper.mutedForeground"));
    }
    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(16)); }
    @Override public Dimension getPreferredSize() {
        int height = labels.stream().mapToInt(label -> label.getPreferredSize().height).max().orElse(0);
        return new Dimension(width(labels), Math.max(UIScale.scale(20), height));
    }
    private int width(List<JLabel> visible) {
        return visible.stream().mapToInt(label -> label.getPreferredSize().width).sum()
            + Math.max(0, visible.size() - 1) * UIScale.scale(22);
    }
    @Override public void doLayout() {
        for (Component child : getComponents()) { child.setVisible(false); child.setBounds(0, 0, 0, 0); }
        if (labels.isEmpty() || getWidth() <= 0) return;
        List<JLabel> shown = labels;
        if (width(shown) > getWidth()) {
            shown = List.of(labels.getLast());
            // Prefer root/host context plus the longest suffix that fits. Then try just a suffix.
            boolean found = false;
            for (boolean keepRoot : new boolean[]{true, false}) {
                for (int start = keepRoot ? 2 : 1; start < labels.size(); start++) {
                    var candidate = new ArrayList<JLabel>();
                    if (keepRoot) candidate.add(labels.getFirst());
                    candidate.add(ellipsis); candidate.addAll(labels.subList(start, labels.size()));
                    if (width(candidate) <= getWidth()) { shown = candidate; found = true; break; }
                }
                if (found) break;
            }
        }
        int x = 0, separator = 0;
        for (int i = 0; i < shown.size(); i++) {
            if (i > 0) {
                var arrow = separators.get(separator++); int space = Math.min(UIScale.scale(22), getWidth() - x);
                arrow.setBounds(x, 0, space, getHeight()); arrow.setVisible(true); x += space;
            }
            var label = shown.get(i); int space = Math.max(0, Math.min(label.getPreferredSize().width, getWidth() - x));
            label.setBounds(x, 0, space, getHeight()); label.setVisible(true); x += space;
        }
    }
    private static JLabel label(String text) {
        var label = new JLabel(text) {
            @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        };
        label.putClientProperty("html.disable", true); return label;
    }
    @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
    private static String clean(String value) {
        var text = new StringBuilder();
        Objects.requireNonNull(value).codePoints().limit(1024).forEach(c -> text.appendCodePoint(Character.isISOControl(c) ? ' ' : c));
        return text.toString();
    }
}
