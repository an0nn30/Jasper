package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Domain-supplied dependency state; descriptions wrap around the trailing control. */
final class SettingRows {
    private SettingRows() {}

    static final class RowControl extends ControlRegistry.Control {
        final Composition.WrappedText title, description;
        boolean panelFooter, hero, fullForm, detail;
        SecretFields.SecretControl secret;
        @Override void hero(boolean value) { hero=value; }

        RowControl(KitView view, Nodes.SettingRow node) {
            super(view, node, new SearchHighlight(view.context()));
            title = Composition.text(node.id() + ".title");
            description = Composition.text(node.id() + ".description");
            component.setOpaque(false);
            component.add(title);
            component.add(description);
            refresh();
        }

        @Override
        void refresh() {
            state();
            var row = (Nodes.SettingRow) node;
            title.text(row.title() + (row.titleNote().isEmpty() ? "" : " · " + row.titleNote()));
            title.noteStart = row.titleNote().isEmpty() ? -1 : row.title().length();
            title.noteColor = ThemePainting.color(context(), SemanticColor.TEXT_TERTIARY);
            description.text(row.description());
            description.setVisible(!row.description().isEmpty());
            Composition.style(title, context(), detail ? Typography.Role.SETTING_DESCRIPTION : Typography.Role.LABEL, detail ? SemanticColor.TEXT_SECONDARY : SemanticColor.TEXT_PRIMARY);
            Composition.style(
                    description, context(), detail ? Typography.Role.BODY : Typography.Role.SETTING_DESCRIPTION, detail ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_TERTIARY);
            title.setEnabled(component.isEnabled());
            description.setEnabled(component.isEnabled());
            int side = ThemePainting.metric(context(), detail ? DesignMetrics.Role.CARD_PADDING : fullForm ? DesignMetrics.Role.FULL_SETTING_SIDE_PADDING : DesignMetrics.Role.SETTING_SIDE_PADDING),
                    vertical =
                            ThemePainting.metric(
                                    context(), DesignMetrics.Role.SETTING_VERTICAL_PADDING);
            component.setBorder(panelFooter ? new EmptyBorder(0,0,0,0) : new EmptyBorder(vertical, side + (row.dependent() ? ThemePainting.metric(context(), DesignMetrics.Role.SETTING_DEPENDENT_INDENT) : 0), vertical, side));
            component.setLayout(new RowLayout(this));
            ((SearchHighlight) component).highlight(row.highlighted());
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            controls.forEach(c -> Composition.label(c, true));
            secret = controls.getFirst() instanceof SecretFields.SecretControl value && value.detail ? value : null;
            if (secret != null) secret.detailValueTop = -1;
            Stacks.mount(component, List.of(title, description, controls.getFirst().component));
        }
    }

    /** A row surface that paints the host's search highlight behind its content, in the theme's selection color. */
    static final class SearchHighlight extends JPanel {
        final KitContext context;
        boolean highlighted;

        SearchHighlight(KitContext context) {
            this.context = context;
        }

        void highlight(boolean value) {
            highlighted = value;
            getAccessibleContext().setAccessibleDescription(value ? "Matches search" : null);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (highlighted) {
                var g = (Graphics2D) graphics.create();
                try {
                    ThemePainting.antialias(g);
                    g.setColor(ThemePainting.color(context, SemanticColor.SELECTION_BG));
                    int r = ThemePainting.metric(context, DesignMetrics.Role.CONTROL_RADIUS);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), r * 2, r * 2);
                } finally {
                    g.dispose();
                }
            }
            super.paintComponent(graphics);
        }
    }

    static final class RowLayout implements Composition.WidthLayout {
        final RowControl owner;

        RowLayout(RowControl owner) {
            this.owner = owner;
        }

        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP);
        }

        boolean secretDetail() { return owner.detail && ((Nodes.SettingRow)owner.node).control() instanceof Nodes.Secret secret && secret.kind()==Nodes.SecretKind.DETAIL; }

        int trailing(Container p, int width) {
            return p.getComponentCount() > 2
                    ? Math.min(
                            Math.max(p.getComponent(2).getPreferredSize().width, ((Nodes.SettingRow)owner.node).control() instanceof Nodes.Choice ? ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_CHOICE_WIDTH) : 0),
                            Math.max(
                                    0,
                                    (width - p.getInsets().left - p.getInsets().right - gap()) / 2))
                    : 0;
        }

        /** A switch reserves its own halo; align its painted track while keeping the halo in the row padding. */
        int paintInset(Container parent) {
            return parent.getComponentCount() > 2 && parent.getComponent(2) instanceof Switches.SwitchButton
                ? Math.min(parent.getInsets().right, ThemePainting.metric(owner.context(), DesignMetrics.Role.FOCUS_RING)) : 0;
        }

        @Override
        public int height(Container p, int width) {
            Insets i = p.getInsets();
            if (owner.detail) {
                int content=Math.max(0,width-i.left-i.right), gap=ThemePainting.metric(owner.context(),DesignMetrics.Role.DETAIL_VALUE_GAP);
                int textWidth=secretDetail()?content:Math.max(0,content-trailing(p,width)-gap());
                int below=secretDetail()?owner.secret.detailTextHeight():owner.description.height(textWidth);
                return Math.max(ThemePainting.metric(owner.context(),DesignMetrics.Role.DETAIL_ROW_HEIGHT),i.top+i.bottom+owner.title.height(textWidth)+gap+below);
            }
            int tw = trailing(p, width), w = Math.max(0, width - i.left - i.right - tw + 2 * paintInset(p) - gap());
            int text =
                    owner.title.height(w)
                            + (owner.description.isVisible() ? owner.description.height(w) + ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_DESCRIPTION_GAP) : 0);
            int control = p.getComponentCount() > 2 ? Composition.height(p.getComponent(2), tw) : 0;
            return Math.max(
                    owner.panelFooter ? 0 : ThemePainting.metric(
                            owner.context(),
                            owner.hero && owner.description.isVisible() ? DesignMetrics.Role.HERO_SETTING_HEIGHT : owner.description.isVisible()
                                    ? owner.fullForm ? DesignMetrics.Role.FULL_SETTING_DESCRIPTION_HEIGHT : DesignMetrics.Role.SETTING_DESCRIPTION_HEIGHT
                                    : DesignMetrics.Role.SETTING_HEIGHT) + (owner.fullForm && !owner.hero ? ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE) : 0),
                    i.top + i.bottom + Math.max(text, control));
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int control = p.getComponentCount() > 2 ? p.getComponent(2).getPreferredSize().width : 0;
            // The trailing control takes at most half the row, so a wide control (a list) needs twice its width.
            int w =
                    Math.max(owner.title.getPreferredSize().width + gap() + control, 2 * control + gap())
                            + p.getInsets().left
                            + p.getInsets().right;
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override
        public void layoutContainer(Container p) {
            Insets i = p.getInsets();
            if (owner.detail) {
                int content=Math.max(0,p.getWidth()-i.left-i.right), gap=ThemePainting.metric(owner.context(),DesignMetrics.Role.DETAIL_VALUE_GAP);
                int tw=secretDetail()?content:trailing(p,p.getWidth()), textWidth=secretDetail()?content:Math.max(0,content-tw-gap());
                int titleHeight=owner.title.height(textWidth), below=secretDetail()?owner.secret.detailTextHeight():owner.description.height(textWidth);
                int y=Math.max(i.top,(p.getHeight()-titleHeight-gap-below)/2);
                owner.title.setBounds(i.left,y,textWidth,titleHeight);
                owner.description.setBounds(i.left,y+titleHeight+gap,textWidth,below);
                if(p.getComponentCount()>2) {
                    Component c=p.getComponent(2);int h=Composition.height(c,tw);
                    if (secretDetail()) {
                        owner.secret.detailValueTop=y+titleHeight+gap;
                        c.setBounds(i.left,0,tw,p.getHeight());
                    } else c.setBounds(p.getWidth()-i.right-tw,(p.getHeight()-h)/2,tw,h);
                }
                return;
            }
            int tw = trailing(p, p.getWidth()),
                    w = Math.max(0, p.getWidth() - i.left - i.right - tw + 2 * paintInset(p) - gap()),
                    th = owner.title.height(w),
                    dh = owner.description.isVisible() ? owner.description.height(w) + ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_DESCRIPTION_GAP) : 0;
            int y = Math.max(i.top, (p.getHeight() - th - dh) / 2);
            owner.title.setBounds(i.left, y, w, th);
            int descriptionGap = owner.description.isVisible() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_DESCRIPTION_GAP) : 0;
            owner.description.setBounds(i.left, y + th + descriptionGap, w, Math.max(0, dh - descriptionGap));
            if (p.getComponentCount() > 2) {
                Component c = p.getComponent(2);
                int h = Composition.height(c, tw);
                c.setBounds(
                        p.getWidth() - i.right - tw + paintInset(p),
                        Math.max(i.top, (p.getHeight() - h) / 2),
                        tw,
                        h);
            }
        }
    }
}
