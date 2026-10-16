package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** SDK 0.18's Notice (spec 8.2): the severity's glyph and word, then one sentence, on the severity's ground. */
final class Notices {
    private Notices() {}

    /** The word the presentation puts before a notice's text. */
    static String word(Nodes.Severity severity) {
        return switch (severity) { case INFO -> "Information"; case WARNING -> "Warning"; case DANGER -> "Error"; };
    }

    /** The severity's glyph. */
    static String glyph(Nodes.Severity severity) {
        return switch (severity) { case INFO -> "info"; case WARNING -> "warning"; case DANGER -> "error"; };
    }

    /** The severity's ground, one of spec 7.1's derived roles. */
    static DerivedColor ground(Nodes.Severity severity) {
        return switch (severity) { case INFO -> DerivedColor.INFO_GROUND; case WARNING -> DerivedColor.WARNING_GROUND; case DANGER -> DerivedColor.DANGER_GROUND; };
    }

    /** The severity's border, one of spec 7.1's derived roles. */
    static DerivedColor border(Nodes.Severity severity) {
        return switch (severity) { case INFO -> DerivedColor.INFO_BORDER; case WARNING -> DerivedColor.WARNING_BORDER; case DANGER -> DerivedColor.DANGER_BORDER; };
    }

    /** The notice's text on its ground, held to the text floor. */
    static PaintPair text(Nodes.Severity severity) {
        return new PaintPair(SemanticColor.TEXT_PRIMARY, ground(severity), SurfaceContrast.Guard.TEXT);
    }

    /** A LINK action's text on the notice's ground, held to the text floor. */
    static PaintPair link(Nodes.Severity severity, boolean hover) {
        return new PaintPair(hover ? SemanticColor.TEXT_LINK_HOVER : SemanticColor.TEXT_LINK, ground(severity), SurfaceContrast.Guard.TEXT);
    }

    /** The notice's border on its ground, held to the structural floor (a theme may author the two alike). */
    static PaintPair edge(Nodes.Severity severity) {
        return new PaintPair(border(severity), ground(severity), SurfaceContrast.Guard.STRUCTURAL);
    }

    static final class NoticeControl extends ControlRegistry.Control {
        final JLabel glyph = Labels.literal();
        final Labels.LiteralLabel text = (Labels.LiteralLabel) Labels.literal();
        List<ControlRegistry.Control> actions = List.of();

        NoticeControl(KitView view, Nodes.Notice node) {
            super(view, node, new Surface(view.context()));
            KitOwnership.mark(glyph, node.id() + ".glyph");
            KitOwnership.mark(text, node.id() + ".text");
            text.wrap = true;
            component.setLayout(new NoticeLayout(this));
            Stacks.mount(component, List.<JComponent>of(glyph, text));
            refresh();
        }

        @Override void refresh() {
            state();
            var notice = (Nodes.Notice) node; var theme = context().theme();
            ((Surface) component).severity = notice.severity();
            text.setText(word(notice.severity()) + " · " + notice.text());
            text.setFont(ThemePainting.font(context(), Typography.Role.BODY));
            text.setForeground(SurfaceContrast.painted(theme, Notices.text(notice.severity())));
            text.setEnabled(component.isEnabled());
            text.getAccessibleContext().setAccessibleName(text.getText());
            int size = com.formdev.flatlaf.util.UIScale.scale(context().icons().size(context().metrics(), Nodes.IconSize.CONTROL));
            // The severity colour, held to 3:1 on the ground by the registry's surface tint.
            glyph.setIcon(context().icons().icon(glyph(notice.severity()), ground(notice.severity()).color(theme),
                theme.semantic().color(Labels.tint(notice.severity())), size));
            glyph.setDisabledIcon(glyph.getIcon());
            glyph.setEnabled(component.isEnabled());
            glyph.getAccessibleContext().setAccessibleName(word(notice.severity()));
            int vertical = ThemePainting.metric(context(), DesignMetrics.Role.NOTICE_VERTICAL_PADDING);
            int horizontal = ThemePainting.metric(context(), DesignMetrics.Role.NOTICE_HORIZONTAL_PADDING);
            component.setBorder(new EmptyBorder(vertical, horizontal, vertical, horizontal));
            component.revalidate(); component.repaint();
        }

        @Override void children(List<ControlRegistry.Control> controls) {
            actions = List.copyOf(controls);
            var wanted = new ArrayList<JComponent>(List.of(glyph, text));
            for (var control : controls) {
                if (control instanceof Buttons.ButtonControl button) {
                    button.button.noticeGround = ((Nodes.Notice) node).severity();
                    button.button.setForeground(button.button.textColor(false));
                    button.button.repaint();
                }
                wanted.add(control.component);
            }
            Stacks.mount(component, wanted);
        }
    }

    /** The notice's box: the severity's ground inside its guarded border. */
    static final class Surface extends JPanel {
        final KitContext context;
        Nodes.Severity severity = Nodes.Severity.INFO;

        Surface(KitContext context) { this.context = context; setOpaque(false); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                var theme = context.theme();
                ThemePainting.rounded(g, this, context, ground(severity).color(theme), SurfaceContrast.painted(theme, edge(severity)),
                    false, false, DesignMetrics.Role.CONTROL_RADIUS);
            } finally { g.dispose(); }
        }
    }

    /** The glyph on the text's first line, the wrapping text, then the action centred at the trailing edge. */
    static final class NoticeLayout implements Composition.WidthLayout {
        final NoticeControl owner;

        NoticeLayout(NoticeControl owner) { this.owner = owner; }

        private int gap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP); }
        private int actionGap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.NOTICE_BUTTON_GAP); }

        /** The shown action's component, or null. */
        private Component action() {
            for (var control : owner.actions) if (control.component.isVisible()) return control.component;
            return null;
        }

        private int textWidth(Container parent, int width) {
            Insets insets = parent.getInsets(); var action = action();
            int used = owner.glyph.getPreferredSize().width + gap() + (action == null ? 0 : actionGap() + action.getPreferredSize().width);
            return Math.max(1, width - insets.left - insets.right - used);
        }

        @Override public int height(Container parent, int width) {
            Insets insets = parent.getInsets(); var action = action();
            int content = Math.max(Composition.height(owner.text, textWidth(parent, width)), owner.glyph.getPreferredSize().height);
            if (action != null) content = Math.max(content, action.getPreferredSize().height);
            return insets.top + insets.bottom + content;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets(); var action = action();
            int width = insets.left + insets.right + owner.glyph.getPreferredSize().width + gap() + owner.text.getPreferredSize().width
                + (action == null ? 0 : actionGap() + action.getPreferredSize().width);
            return new Dimension(width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets(); var action = action();
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int textWidth = textWidth(parent, parent.getWidth()), textHeight = Composition.height(owner.text, textWidth);
            int textY = insets.top + Math.max(0, (height - textHeight) / 2);
            int line = owner.text.getFontMetrics(owner.text.getFont()).getHeight();
            var glyph = owner.glyph.getPreferredSize();
            owner.glyph.setBounds(insets.left, textY + Math.max(0, (Math.min(line, textHeight) - glyph.height) / 2), glyph.width, glyph.height);
            owner.text.setBounds(insets.left + glyph.width + gap(), textY, textWidth, textHeight);
            if (action != null) {
                var size = action.getPreferredSize();
                action.setBounds(parent.getWidth() - insets.right - size.width, insets.top + Math.max(0, (height - size.height) / 2), size.width, size.height);
            }
        }
    }
}
