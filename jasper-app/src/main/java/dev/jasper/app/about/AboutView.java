package dev.jasper.app.about;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.BuildInfo;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.swing.NativeControlStyles;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * The About window's content: the application icon, the name, the version line, a Development build chip on anything but
 * a release, the branch and commit as selectable text, the commit date, Jasper's license and copyright, then Licenses… and
 * Copy. One chrome kit view composes it as a compact hero form; Escape closes the window. Copy puts the build's
 * description on the clipboard and reads "Copied" for two seconds when that worked. EDT only; {@link #close} stops it.
 */
public final class AboutView extends JPanel {
    /**
     * What the view shows and asks of the application.
     *
     * @param build this build
     * @param os the operating system line error reports use
     * @param java the Java runtime line error reports use
     * @param zone the zone the commit date is shown in
     * @param copy puts text on the clipboard; false when that failed (the application has said so)
     * @param licenses opens the Licenses window
     * @param close closes the window (Escape)
     * @param after runs a task once after a delay, returning what cancels it
     */
    public record Hooks(BuildInfo build, String os, String java, ZoneId zone, Predicate<String> copy, Runnable licenses,
                        Runnable close, BiFunction<Duration, Runnable, Runnable> after) {
        public Hooks {
            Objects.requireNonNull(build, "build");
            Objects.requireNonNull(os, "os");
            Objects.requireNonNull(java, "java");
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(copy, "copy");
            Objects.requireNonNull(licenses, "licenses");
            Objects.requireNonNull(close, "close");
            Objects.requireNonNull(after, "after");
        }
    }

    static final String ICON = "about.icon", NAME = "about.name", VERSION = "about.version", BADGE = "about.badge",
        LOCATION = "about.location", COMMITTED = "about.committed", LICENSE = "about.license", COPYRIGHT = "about.copyright";
    static final String LICENSES = "licenses", COPY = "copy", CLOSE = "close";
    static final Duration COPIED_FOR = Duration.ofSeconds(2);
    private static final String ICON_SLOT = "about.icon.canvas", LOCATION_SLOT = "about.location.text";
    private final KitContext kit;
    private final Hooks hooks;
    private final KitView view;
    private final JTextArea location = new JTextArea();
    private final AutoCloseable theme;
    private final AutoCloseable typography;
    private Runnable revert = () -> { };
    private boolean copied, closed;

    /**
     * @param kit the chrome kit context
     * @param hooks what it shows and asks of the application
     */
    public AboutView(KitContext kit, Hooks hooks) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        setOpaque(false);
        location.setText(hooks.build().location());
        location.setEditable(false);
        location.setOpaque(false);
        location.setBorder(BorderFactory.createEmptyBorder());
        location.getAccessibleContext().setAccessibleName("Branch and commit");
        view = new KitView(kit, spec(), this::event);
        view.getAccessibleContext().setAccessibleName("About Jasper");
        add(view, BorderLayout.CENTER);
        view.registerContent(ICON_SLOT, new IconCanvas(), () -> { });
        view.registerContent(LOCATION_SLOT, location, () -> { });
        theme = kit.binding().subscribe(pair -> restyle());
        typography = NativeControlStyles.observeTypography(this::restyle);
        restyle();
    }

    /** The location text takes the module's small mono role and the theme's secondary text and selection colours. */
    private void restyle() {
        if (closed) return;
        location.setFont(NativeControlStyles.font(kit, Typography.Role.MONO_SMALL));
        location.setForeground(kit.theme().semantic().color(SemanticColor.TEXT_SECONDARY));
        location.setCaretColor(location.getForeground());
        location.setSelectionColor(kit.theme().semantic().color(SemanticColor.SELECTION_BG));
        location.setSelectedTextColor(kit.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
        revalidate();
        repaint();
    }

    private void event(UiEvent event) {
        if (closed || !(event instanceof UiEvent.Action action)) return;
        switch (action.id()) {
            case LICENSES -> hooks.licenses().run();
            case COPY -> copy();
            case CLOSE -> hooks.close().run();
            default -> { }
        }
    }

    /** Copies the report; on success the label reads "Copied" until two seconds after the latest copy. */
    private void copy() {
        if (!hooks.copy().test(report())) return;
        revert.run();
        copied = true;
        view.update(spec());
        revert = hooks.after().apply(COPIED_FOR, () -> {
            if (closed) return;
            copied = false;
            revert = () -> { };
            view.update(spec());
        });
    }

    /** What Copy puts on the clipboard: the build, its commit, then the OS and Java lines as error reports write them. */
    String report() {
        BuildInfo build = hooks.build();
        return "Jasper " + build.summary() + "\n" + build.commitLine(hooks.zone()) + "\nOS: " + hooks.os() + "\nJava: " + hooks.java() + "\n";
    }

    /** Cancels a pending revert and closes the kit view; the window calls this when it closes. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        revert.run();
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the view */ }
        view.close();
    }

    private ViewSpec spec() {
        BuildInfo build = hooks.build();
        var hero = new ArrayList<Nodes.Node>(List.of(
            new Nodes.Custom(ICON, ControlState.READY, ICON_SLOT, Nodes.CustomKind.PREVIEW, "Jasper"),
            text(NAME, "Jasper", Nodes.TextRole.HERO_TITLE)));
        if (!build.versionLine().isEmpty()) hero.add(text(VERSION, build.versionLine(), Nodes.TextRole.DESCRIPTION));
        var facts = new ArrayList<Nodes.Node>();
        if (!build.release()) facts.add(new Nodes.Chip(BADGE, ControlState.READY, "Development build", Nodes.Hue.AMBER));
        facts.add(new Nodes.Custom(LOCATION, ControlState.READY, LOCATION_SLOT, Nodes.CustomKind.EDITOR, "Branch and commit"));
        String committed = build.committedLine(hooks.zone());
        if (!committed.isEmpty()) facts.add(text(COMMITTED, committed, Nodes.TextRole.CAPTION));
        facts.add(text(LICENSE, "PolyForm Noncommercial 1.0.0", Nodes.TextRole.CAPTION));
        facts.add(text(COPYRIGHT, "Copyright 2026 Dustin McKenna-Watts", Nodes.TextRole.CAPTION));
        return new ViewSpec(new Nodes.Form("about", ControlState.READY, Nodes.FormKind.HERO_COMPACT, "About Jasper",
            Optional.of(new Nodes.Stack("about.hero", ControlState.READY, Nodes.LayoutRole.HERO, hero)),
            List.of(new Nodes.Stack("about.facts", ControlState.READY, Nodes.LayoutRole.HERO, facts)), Optional.empty(),
            List.of(button(LICENSES, "Licenses…"), button(COPY, copied ? "Copied" : "Copy")), CLOSE, Nodes.FormSubmission.NONE));
    }

    private static Nodes.Text text(String id, String text, Nodes.TextRole role) {
        return new Nodes.Text(id, ControlState.READY, text, role);
    }

    private static Nodes.Button button(String id, String label) {
        return new Nodes.Button(id, ControlState.READY, label, "", Nodes.ButtonKind.SECONDARY, id, "");
    }

    /** The application icon at 64 logical pixels, drawn from the largest image of the platform's set. */
    static final class IconCanvas extends JComponent {
        private static final int SIZE = 64;
        private final Image image = ApplicationIcon.images().stream().max(Comparator.comparingInt(icon -> icon.getWidth(null))).orElse(null);

        IconCanvas() {
            setOpaque(false);
            getAccessibleContext().setAccessibleName("Jasper");
        }

        /** A plain JComponent has no accessible context; this one is an icon named Jasper. */
        @Override public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
                @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.ICON; }
            };
            return accessibleContext;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(SIZE), UIScale.scale(SIZE)); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics graphics) {
            if (image == null) return;
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int size = Math.min(getWidth(), getHeight());
                g.drawImage(image, (getWidth() - size) / 2, (getHeight() - size) / 2, size, size, null);
            } finally {
                g.dispose();
            }
        }
    }
}
