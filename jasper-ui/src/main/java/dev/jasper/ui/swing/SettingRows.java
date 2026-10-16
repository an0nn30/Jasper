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
        boolean panelFooter, prompt, fullForm, detail, settings, grouped;
        SecretFields.SecretControl secret;
        @Override void prompt(boolean value) { prompt=value; }

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
            Composition.style(title, context(), detail ? Typography.Role.SETTING_DESCRIPTION : Typography.Role.LABEL, detail ? SemanticColor.TEXT_SECONDARY : SemanticColor.TEXT_STRONG_SECONDARY);
            Composition.style(
                    description, context(), detail ? Typography.Role.BODY : Typography.Role.SETTING_DESCRIPTION, detail ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY);
            if (row.highlighted()) {
                title.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ONSELECTION));
                description.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ONSELECTION_SECONDARY));
                title.noteColor = ThemePainting.color(context(), SemanticColor.TEXT_ONSELECTION_SECONDARY);
            }
            title.setEnabled(component.isEnabled());
            description.setEnabled(component.isEnabled());
            int side = ThemePainting.metric(context(), detail ? DesignMetrics.Role.DETAIL_ROW_SIDE : settings ? DesignMetrics.Role.SETTINGS_ROW_SIDE : fullForm ? DesignMetrics.Role.FULL_SETTING_SIDE_PADDING : DesignMetrics.Role.SETTING_SIDE_PADDING),
                    vertical =
                            ThemePainting.metric(
                                    context(), grouped ? DesignMetrics.Role.SETTINGS_GROUP_ROW_PADDING : DesignMetrics.Role.SETTING_VERTICAL_PADDING);
            // A panel footer row reserves its separator above its content; the footer paints the full-width rule there.
            component.setBorder(panelFooter ? new EmptyBorder(ThemePainting.metric(context(), DesignMetrics.Role.HAIRLINE),0,0,0) : new EmptyBorder(vertical, side + (row.dependent() ? ThemePainting.metric(context(), DesignMetrics.Role.SETTING_DEPENDENT_INDENT) : 0), vertical, side));
            component.setLayout(new RowLayout(this));
            ((SearchHighlight) component).highlight(row.highlighted());
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            controls.forEach(c -> Composition.label(c, true));
            secret = controls.getFirst() instanceof SecretFields.SecretControl value && value.detail ? value : null;
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
                    // A search match is a plain rectangle across the row, its text white (spec 2).
                    g.setColor(ThemePainting.color(context, SemanticColor.SELECTION_BG));
                    g.fillRect(0, 0, getWidth(), getHeight());
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

        int actionWidth(Container parent) {
            return secretDetail() ? owner.secret.detailActionWidth() : parent.getComponentCount()>2 ? parent.getComponent(2).getPreferredSize().width : 0;
        }
        int actionHeight(Container parent) {
            return secretDetail() ? owner.secret.detailActionHeight() : parent.getComponentCount()>2 ? parent.getComponent(2).getPreferredSize().height : 0;
        }
        Insets detailInsets(Container parent,int width) {
            Insets padding=parent.getInsets();int side=Math.max(0,(width-actionWidth(parent))/2);
            return new Insets(padding.top,Math.min(padding.left,side),padding.bottom,Math.min(padding.right,side));
        }
        boolean stackedDetail(Container parent,int content) {
            return content<owner.title.getPreferredSize().width+gap()+actionWidth(parent);
        }
        @Override public Dimension minimumLayoutSize(Container parent) {
            return owner.settings ? new Dimension(0, 0) : owner.detail ? new Dimension(actionWidth(parent),height(parent,Math.max(actionWidth(parent),parent.getWidth()))) : Composition.WidthLayout.super.minimumLayoutSize(parent);
        }

        boolean fixedControl() { return fixed(((Nodes.SettingRow)owner.node).control()); }
        static boolean fixed(Nodes.Node node) { return node instanceof Nodes.ItemList || node instanceof Nodes.Table || ControlRegistry.children(node).stream().anyMatch(RowLayout::fixed); }
        int trailing(Container p, int width) {
            if (owner.settings && p.getComponentCount() > 2)
                return Math.min(p.getComponent(2).getPreferredSize().width, Math.max(0, width - p.getInsets().left - p.getInsets().right - gap() - ThemePainting.metric(owner.context(), DesignMetrics.Role.FORM_LABEL_WIDTH)));

            return p.getComponentCount() > 2
                    ? Math.min(
                            Math.max(p.getComponent(2).getPreferredSize().width, ((Nodes.SettingRow)owner.node).control() instanceof Nodes.Choice ? ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_CHOICE_WIDTH) : 0),
                            Math.max(
                                    0,
                                    (width - p.getInsets().left - p.getInsets().right - gap()) / 2))
                    : 0;
        }

        boolean stackedSettings(Container p,int width) {
            if(!owner.settings||p.getComponentCount()<3||fixedControl())return false;
            var i=p.getInsets();int content=Math.max(0,width-i.left-i.right);
            return content<Math.max(ThemePainting.metric(owner.context(),DesignMetrics.Role.FORM_LABEL_WIDTH),owner.title.getPreferredSize().width)+gap()+p.getComponent(2).getPreferredSize().width;
        }
        /** What a row needs across. A Settings list editor sits beside its title at its natural width (the half-row rule of
         *  other forms would widen the page past its reading width); elsewhere the row's natural width. */
        int fixedWidth(Container p){var i=p.getInsets();return owner.settings&&p.getComponentCount()>2?i.left+i.right+Math.max(ThemePainting.metric(owner.context(),DesignMetrics.Role.FORM_LABEL_WIDTH),owner.title.getPreferredSize().width)+gap()+p.getComponent(2).getPreferredSize().width:p.getPreferredSize().width;}
        int settingsTextHeight(int width){return owner.title.height(width)+(owner.description.isVisible()?owner.description.height(width)+ThemePainting.metric(owner.context(),DesignMetrics.Role.SETTING_DESCRIPTION_GAP):0);}

        /** A switch reserves its own halo; align its painted track while keeping the halo in the row padding. */
        int paintInset(Container parent) {
            // A panel footer row has no side padding of its own: the footer's padding holds the switch's halo.
            return parent.getComponentCount() > 2 && parent.getComponent(2) instanceof Switches.SwitchButton
                ? owner.panelFooter ? ThemePainting.metric(owner.context(), DesignMetrics.Role.FOCUS_RING)
                : Math.min(parent.getInsets().right, ThemePainting.metric(owner.context(), DesignMetrics.Role.FOCUS_RING)) : 0;
        }

        @Override
        public int height(Container p, int width) {
            Insets i = p.getInsets();
            if(stackedSettings(p,width)){int content=Math.max(0,width-i.left-i.right);return i.top+i.bottom+settingsTextHeight(content)+gap()+Composition.height(p.getComponent(2),Math.min(content,p.getComponent(2).getPreferredSize().width));}
            if (owner.detail) {
                i=detailInsets(p,width);
                int content=Math.max(0,width-i.left-i.right), gap=ThemePainting.metric(owner.context(),DesignMetrics.Role.DETAIL_VALUE_GAP);
                if(stackedDetail(p,content))return Math.max(ThemePainting.metric(owner.context(),DesignMetrics.Role.DETAIL_ROW_HEIGHT),i.top+i.bottom+owner.title.height(content)+gap+(secretDetail()?owner.secret.detailTextHeight():owner.description.height(content))+gap+actionHeight(p));
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
                    owner.panelFooter ? ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE) + ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_FOOTER_ROW_HEIGHT) : ThemePainting.metric(
                            owner.context(),
                            owner.settings ? owner.description.isVisible() ? DesignMetrics.Role.SETTINGS_DESCRIPTION_HEIGHT : owner.grouped ? DesignMetrics.Role.SETTINGS_GROUP_ROW_HEIGHT : DesignMetrics.Role.SETTINGS_ROW_HEIGHT : owner.prompt && owner.description.isVisible() ? DesignMetrics.Role.PROMPT_SETTING_HEIGHT : owner.description.isVisible()
                                    ? owner.fullForm ? DesignMetrics.Role.FULL_SETTING_DESCRIPTION_HEIGHT : DesignMetrics.Role.SETTING_DESCRIPTION_HEIGHT
                                    : DesignMetrics.Role.SETTING_HEIGHT) + (owner.fullForm && !owner.prompt ? ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE) : 0),
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
            if(stackedSettings(p,p.getWidth())){
                int content=Math.max(0,p.getWidth()-i.left-i.right),title=owner.title.height(content),text=settingsTextHeight(content);
                owner.title.setBounds(i.left,i.top,content,title);
                owner.description.setBounds(i.left,i.top+title+ThemePainting.metric(owner.context(),DesignMetrics.Role.SETTING_DESCRIPTION_GAP),content,owner.description.isVisible()?owner.description.height(content):0);
                var control=p.getComponent(2);int width=Math.min(content,control.getPreferredSize().width);control.setBounds(p.getWidth()-i.right-width,i.top+text+gap(),width,Composition.height(control,width));return;
            }
            if (owner.detail) {
                i=detailInsets(p,p.getWidth());
                int content=Math.max(0,p.getWidth()-i.left-i.right), gap=ThemePainting.metric(owner.context(),DesignMetrics.Role.DETAIL_VALUE_GAP);
                if(stackedDetail(p,content)) {
                    int titleHeight=owner.title.height(content),below=secretDetail()?owner.secret.detailTextHeight():owner.description.height(content);
                    owner.title.setBounds(i.left,i.top,content,titleHeight);
                    owner.description.setBounds(i.left,i.top+titleHeight+gap,content,below);
                    if(secretDetail()) {
                        owner.secret.detailValueTop=i.top+titleHeight+gap;owner.secret.detailActionsBelow=true;
                        p.getComponent(2).setBounds(i.left,0,content,p.getHeight());
                    } else if(p.getComponentCount()>2)p.getComponent(2).setBounds(i.left,i.top+titleHeight+gap+below+gap,actionWidth(p),actionHeight(p));
                    return;
                }
                if(secretDetail())owner.secret.detailActionsBelow=false;
                int tw=secretDetail()?content:trailing(p,p.getWidth()), textWidth=secretDetail()?content:Math.max(0,content-tw-gap());
                int titleHeight=owner.title.height(textWidth), below=secretDetail()?owner.secret.detailTextHeight():owner.description.height(textWidth);
                // Centre the label/value block in the row below its top stroke (rounding down the remaining space).
                int stroke=ThemePainting.metric(owner.context(),DesignMetrics.Role.HAIRLINE);
                int y=Math.max(i.top,stroke+(p.getHeight()-stroke-titleHeight-gap-below+1)/2);
                int actionInset=Math.min(i.right,ThemePainting.metric(owner.context(),DesignMetrics.Role.DETAIL_ACTION_INSET));
                owner.title.setBounds(i.left,y,textWidth,titleHeight);
                owner.description.setBounds(i.left,y+titleHeight+gap,textWidth,below);
                if(p.getComponentCount()>2) {
                    Component c=p.getComponent(2);int h=Composition.height(c,tw);
                    if (secretDetail()) {
                        owner.secret.detailValueTop=y+titleHeight+gap;
                        c.setBounds(i.left,0,Math.max(0,p.getWidth()-i.left-actionInset),p.getHeight());
                    } else c.setBounds(p.getWidth()-actionInset-tw,(p.getHeight()-h)/2,tw,h);
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
