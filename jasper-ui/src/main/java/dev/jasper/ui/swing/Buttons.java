package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Five button treatments sharing semantics, content fitting and owned action/focus listeners. */
final class Buttons {
    private Buttons() {}
    /** One fully validated host-artwork snapshot; its registry is retired only after a successful header commit. */
    static final class HostArtwork implements AutoCloseable {
        final javax.swing.Icon raw;
        final PanelHostHeader.Artwork semantic;
        final dev.jasper.ui.icons.IconRegistry icons;
        private boolean closed;
        private HostArtwork(javax.swing.Icon raw, PanelHostHeader.Artwork semantic, dev.jasper.ui.icons.IconRegistry icons) {
            this.raw = raw; this.semantic = semantic; this.icons = icons;
        }
        static HostArtwork prepare(KitContext context, PanelHostHeader.Action action) {
            if (action.artwork() == null) return new HostArtwork(action.icon(), null, null);
            var icons = new dev.jasper.ui.icons.IconRegistry();
            var snapshot = new HostArtwork(action.icon(), action.artwork(), icons);
            try {
                if (action.artwork().pathData() != null) icons.register(action.artwork().name(), action.artwork().pathData());
                snapshot.icon(context, SemanticColor.HUE_NEUTRAL);
                return snapshot;
            } catch (Throwable failure) { snapshot.close(); throw failure; }
        }
        javax.swing.Icon icon(KitContext context, SemanticColor tint) { return icon(context, tint, null); }
        /** As {@link #icon(KitContext, SemanticColor)}, or on a header strip in the strip's action ink when {@code stripInk} is set. */
        javax.swing.Icon icon(KitContext context, SemanticColor tint, Color stripInk) {
            int glyph = ThemePainting.metric(context, DesignMetrics.Role.PANEL_HEADER_CONTRIBUTED_GLYPH);
            if (semantic == null) return raw;
            return stripInk != null ? icons.panel(semantic.name(), context.theme().semantic(), stripInk, glyph)
                : icons.panel(semantic.name(), context.theme(), tint, glyph);
        }
        @Override public void close() { if (!closed) { closed = true; if (icons != null) icons.close(); } }
    }
    static final class ButtonControl extends ControlRegistry.Control {
        final KitButton button;
        /** Validated borrowed host artwork; null for ordinary named icons. */
        HostArtwork hostArtwork;
        KeycapSequence keycaps;
        ButtonControl(KitView view, Nodes.Button node) {
            this(view, node, view::dispatch);
        }
        ButtonControl(KitView view, Nodes.Button node, java.util.function.Consumer<UiEvent> events) { this(view, node, events, null); }
        ButtonControl(KitView view, Nodes.Button node, java.util.function.Consumer<UiEvent> events, javax.swing.Icon artwork) {
            super(view, node, new KitButton(view.context())); button = (KitButton)component;
            hostArtwork = artwork == null ? null : new HostArtwork(artwork, null, null);
            own(() -> { if (hostArtwork != null) hostArtwork.close(); });
            button.putClientProperty("html.disable", true);
            button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
            button.setRolloverEnabled(true); button.setHorizontalAlignment(SwingConstants.CENTER);
            button.focus = new ThemePainting.Focus(button, button, view); own(button.focus);
            ActionListener listener = event -> {
                if (interactive()) try { events.accept(new UiEvent.Action(((Nodes.Button)this.node).action())); }
                catch (Throwable failure) { view.report(failure); }
            };
            button.addActionListener(listener); own(() -> button.removeActionListener(listener)); refresh();
        }
        @Override public void close() {
            keycaps = null; button.valuePresentation = ButtonValuePresentation.DEFAULT;
            button.setIcon(null); button.setDisabledIcon(null); button.putClientProperty("Jasper.button.icon", null);
            super.close();
        }
        @Override void prompt(boolean value) { button.prompt=value; }
        @Override void refresh() {
            var value = (Nodes.Button)node; state(); button.kind = value.kind(); button.iconSeverity = value.iconSeverity().orElse(null);
            button.toggle = value.selected().isPresent(); button.getModel().setSelected(value.selected().orElse(false));
            button.setText(value.label()); button.setFont(ThemePainting.font(context(), context().faces().buttons.label(value.kind(), button.hint ? Typography.Role.PALETTE_HINT : button.panelDetail || button.panel || button.panelEmpty ? Typography.Role.PANEL_DETAIL_LABEL : button.orderedAction ? Typography.Role.ORDERED_ACTION : Typography.Role.LABEL)));
            button.setForeground(button.textColor(false));
            boolean embedded = value.presentation() == Nodes.ButtonPresentation.EMBEDDED || value.presentation() == Nodes.ButtonPresentation.CONTEXTUAL_EMBEDDED;
            button.circular=value.presentation()==Nodes.ButtonPresentation.HELP;
            button.contextual = value.presentation() == Nodes.ButtonPresentation.CONTEXTUAL_EMBEDDED;
            button.embedded = embedded;
            if (hostArtwork != null) { button.setIcon(hostArtwork.icon(context(), button.iconRole(false), button.stripInk)); button.putClientProperty("Jasper.button.icon", null); }
            else {
                int extent=UIScale.scale(button.stripInk != null ? context().metrics().value(DesignMetrics.Role.PANEL_HEADER_CONTRIBUTED_GLYPH) : button.orderedAction || button.orderedCommand ? context().metrics().value(DesignMetrics.Role.ORDERED_GLYPH) : button.panelDetail && value.label().isEmpty() ? context().metrics().value(DesignMetrics.Role.PANEL_DETAIL_ACTION_GLYPH) : context().icons().size(context().metrics(), (embedded || button.panel) && value.label().isEmpty() ? Nodes.IconSize.RAIL : Nodes.IconSize.CONTROL));
                button.setIcon(value.icon().isEmpty() ? null : button.stripInk != null ? context().icons().panel(value.icon(), context().theme().semantic(), button.stripInk, extent)
                    : button.panel || button.panelDetail || button.panelRow
                    ? context().icons().panel(value.icon(),context().theme(),button.iconRole(false),extent)
                    : context().icons().icon(value.icon(), context().theme(), button.iconRole(false),extent));
                button.putClientProperty("Jasper.button.icon", value.icon().isEmpty() ? null : value.icon());
            }
            if(keycaps!=null){button.setText("");button.setIcon(new Keycaps(context(),keycaps));button.putClientProperty("Jasper.button.icon",null);}
            button.setHorizontalAlignment(button.recorder() ? SwingConstants.LEFT
                    : button.orderedAction ? SwingConstants.LEADING : SwingConstants.CENTER);
            button.setDisabledIcon(button.getIcon());
            button.setToolTipText(value.tooltip().isEmpty() ? null : value.tooltip());
            button.getAccessibleContext().setAccessibleName(keycaps!=null?keycaps.accessibleName():value.label().isEmpty() ? value.tooltip() : value.label());
            button.getAccessibleContext().setAccessibleDescription(value.state().error());
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
            int bar = button.documentBar && !value.label().isEmpty() ? context().faces().buttons.barPadding(context()) : -1;
            int horizontal = bar >= 0 ? bar : button.gridAction ? ThemePainting.metric(context(), DesignMetrics.Role.DETAIL_ACTION_PADDING) : ThemePainting.metric(context(), button.armed() ? DesignMetrics.Role.KEYCAP_RECORDER_PADDING : button.orderedAction ? DesignMetrics.Role.ORDERED_ROW_SIDE : button.recorder() ? DesignMetrics.Role.FIELD_HORIZONTAL_PADDING : button.panelDetail ? DesignMetrics.Role.PANEL_DETAIL_ACTION_PADDING : view.runWindow() ? DesignMetrics.Role.RUN_WINDOW_BUTTON_PADDING : value.kind() == Nodes.ButtonKind.PRIMARY ? DesignMetrics.Role.PRIMARY_BUTTON_HORIZONTAL_PADDING : DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
            button.setBorder(embedded ? new EmptyBorder(0, 0, 0, 0)
                : new EmptyBorder(ring, value.label().isEmpty() ? ring : horizontal, ring, value.label().isEmpty() ? ring : horizontal));
            button.setIconTextGap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            button.setCursor(Cursor.getPredefinedCursor(button.isEnabled() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            if (button.sideColumn) {
                button.setIcon(null); button.setDisabledIcon(null); button.putClientProperty("Jasper.button.icon", null);
                button.setHorizontalAlignment(SwingConstants.CENTER);
            }
            button.revalidate(); button.repaint();
        }
    }
    static class KitButton extends JButton {
        private final KitContext context;
        Nodes.ButtonKind kind;
        /** Where this button's drop was last painted, in {@link #dropView}'s coordinates; kept to erase it. */
        private Rectangle priorDrop;
        private KitView dropView;
        ButtonValuePresentation valuePresentation = ButtonValuePresentation.DEFAULT;
        boolean recorder() { return valuePresentation != ButtonValuePresentation.DEFAULT; }
        boolean armed() { return valuePresentation == ButtonValuePresentation.RECORDER_ARMED; }
        /** The armed recorder's dot and its soft halo, then the usual icon gap before the prompt. */
        int recorderHalo() { return ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_RECORDER_DOT) + 2*ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_RECORDER_HALO); }
        int recorderIndicator() { return armed() ? recorderHalo() + ThemePainting.metric(context,DesignMetrics.Role.ICON_GAP) : 0; }
        final Composition.WrappedText recorderText=new Composition.WrappedText();
        int heightAt(int width) {
            if (!recorder()) return getPreferredSize().height;
            var insets=getInsets();int available=Math.max(1,width-insets.left-insets.right-recorderIndicator());
            recorderText.text=getText();recorderText.setFont(getFont());
            int content=getIcon() instanceof Keycaps caps?caps.heightAt(available):recorderText.height(available);
            return Math.max(getPreferredSize().height,content+insets.top+insets.bottom);
        }
        boolean prompt, panel, fullFooter, orderedAction, panelDetail, circular, hint;
        /** On a header strip (spec 10.7): the strip's action ink, its glyph drawn at the strip's glyph size; null elsewhere. */
        Color stripInk;
        /** An ORDERED row's compact up/down/remove command. */
        boolean orderedCommand;
        /** A classic ordered list's side-column button: a standard button without its glyph, whatever its kind (plan 4c). */
        boolean sideColumn;
        /** A classic tree's expand button: its drawn triangle alone, as wide as the glyph and its gap to the title (plan 4c). */
        boolean disclosure;
        /** A disclosure's width when its owner sets one (the Settings navigation, plan 9a); 0 keeps the glyph and its gap. */
        int disclosureWidth;
        /** A WINDOW form footer action keeps the window footer's measured minimum width. */
        boolean windowFooter;
        /**
         * In a DOCUMENT split's bar row (set by the reconciler, cleared before each update): a text button takes the face's
         * bar padding ({@link ButtonFace#barPadding}) and a menu part the face's bar arrow, where the face has them.
         */
        boolean documentBar;
        /** A value's action in a manager detail's grid row (set by the reconciler, cleared before each update): {@code DETAIL_ACTION_PADDING} inside its face. */
        boolean gridAction;
        /** A text button in a row of a manager detail's actions (set by the reconciler, cleared before each update): at least {@code DETAIL_ACTION_MIN_WIDTH} wide. */
        boolean detailAction;
        /** Paints its kind's fill without the kind's outline (a toolbar strip's menu surface). */
        boolean borderless;
        /**
         * An inner part of a composite control (a secret's reveal and copy, a crumb), not an action standing in a layout:
         * the classic face keeps it flat until hovered, as spec 6.4's toolbar ghosts, wherever its control stands.
         */
        boolean inner;
        /** An action of a panel body's empty state: the panel references' smaller label and height. */
        boolean panelEmpty;
        /** Content-sized for a host arrangement (ButtonPresentation.EMBEDDED): no minimum height and no resting or hover fill. */
        boolean embedded, contextual, panelRow;
        float disabledOpacity=.42f;
        /** A severity tinting the glyph at rest and on hover (a favourite's warning star); null takes the kind's text colour. */
        Nodes.Severity iconSeverity;
        /** A toggle button (Button.selected present): assistive technology reads it as one, checked while selected. */
        boolean toggle;
        ThemePainting.Focus focus;
        KitButton(KitContext context) {
            this.context = context;
            // Leaving the view (this button or an ancestor removed) erases the drop the view painted for it.
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.PARENT_CHANGED) == 0) return;
                if (SwingUtilities.getAncestorOfClass(KitView.class, this) == null) putClientProperty(ThemePainting.DROP, null);
                repaintDrop();
            });
        }
        /**
         * A surface that binds Enter, such as a form submitting its PRIMARY, takes it from every control, this button
         * included; Space presses the focused button. FlatLaf binds Enter to a focused button's press off macOS only.
         * A button's own Enter binding (a picker opening) still wins.
         */
        @Override protected boolean processKeyBinding(KeyStroke stroke, java.awt.event.KeyEvent event, int condition, boolean pressed) {
            if (condition == WHEN_FOCUSED && event.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                var press = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, stroke.getModifiers());
                if (!bindsItself(press) && ancestorBinds(press)) return false;
            }
            return super.processKeyBinding(stroke, event, condition, pressed);
        }
        /** Whether this button's own focused map, not the look and feel's parent map, binds {@code stroke}. */
        private boolean bindsItself(KeyStroke stroke) {
            KeyStroke[] own = getInputMap(WHEN_FOCUSED).keys();
            return own != null && java.util.Arrays.asList(own).contains(stroke);
        }
        private boolean ancestorBinds(KeyStroke stroke) {
            for (Container parent = getParent(); parent != null; parent = parent.getParent())
                if (parent instanceof JComponent component && component.getConditionForKeyStroke(stroke) == WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    return true;
            return false;
        }
        // A push-button model never flips its selection on a click: the owner confirms a toggle's state in its next update.
        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJButton() {
                @Override public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return toggle ? javax.accessibility.AccessibleRole.TOGGLE_BUTTON : super.getAccessibleRole();
                }
                @Override public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
                    var states = super.getAccessibleStateSet();
                    if (toggle && isSelected()) { states.add(javax.accessibility.AccessibleState.CHECKED); states.add(javax.accessibility.AccessibleState.PRESSED); }
                    return states;
                }
            };
            return accessibleContext;
        }
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        /** On a selected row's fill (set by the row before each refresh): a ghost or link button takes the fill's ink. */
        boolean onSelection;
        /** The selected row's fill and ink, while {@link #onSelection}. */
        InteractiveRows.Ink ink = InteractiveRows.Ink.SELECTION;
        SemanticColor textRole(boolean hover) {
            if (onSelection && (kind == Nodes.ButtonKind.GHOST || kind == Nodes.ButtonKind.DESTRUCTIVE_GHOST || kind == Nodes.ButtonKind.LINK)) return ink.text;
            if (recorder()) return armed() && getIcon() == null ? SemanticColor.TEXT_PLACEHOLDER : SemanticColor.TEXT_PRIMARY;
            if (toggle && isSelected()) return SemanticColor.TEXT_PRIMARY;
            return switch (kind) {
                case PRIMARY -> SemanticColor.TEXT_ONACCENT; case SECONDARY -> SemanticColor.TEXT_STRONG_SECONDARY;
                case GHOST -> hover ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY;
                case DESTRUCTIVE_GHOST -> hover ? SemanticColor.STATUS_DANGER : SemanticColor.TEXT_SECONDARY;
                case LINK -> hover ? SemanticColor.TEXT_LINK_HOVER : SemanticColor.TEXT_LINK;
            };
        }
        /** A Notice's action (set by the notice after each update): the severity whose ground it sits on, or null. */
        Nodes.Severity noticeGround;
        /** Final paint color; specialized shared controls may apply a surface-specific readability floor. */
        Color textColor(boolean hover) {
            // A Notice's link sits on the severity's ground: its link colour is held to the text floor there.
            if (noticeGround != null && kind == Nodes.ButtonKind.LINK && !onSelection)
                return SurfaceContrast.painted(context.theme(), Notices.link(noticeGround, hover));
            return context.faces().buttons.text(this, hover);
        }
        /** The context this button paints in. */
        KitContext context() { return context; }
        /** The component standing in its parent's layout for this button: itself, or a menu button's surface for its parts and face. */
        Component placement() { return this; }
        /** The glyph's colour: the severity's (as on a kit Icon) when present, otherwise the face's (by default the text colour). */
        SemanticColor iconRole(boolean hover) {
            if (onSelection) return ink.text;
            return iconSeverity == null ? panel || panelDetail ? SemanticColor.HUE_NEUTRAL : context.faces().buttons.iconRole(this, hover) : Labels.tint(iconSeverity);
        }
        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            if(getText()==null || getText().isEmpty()) {
                if(disclosure) {int glyph=ThemePainting.metric(context,DesignMetrics.Role.DISCLOSURE_GLYPH);
                    return new Dimension(disclosureWidth>0?disclosureWidth:Math.max(glyph,glyph+ThemePainting.metric(context,DesignMetrics.Role.DISCLOSURE_GAP)-ThemePainting.metric(context,DesignMetrics.Role.ICON_GAP)),ThemePainting.metric(context,DesignMetrics.Role.ROW_HEIGHT));}
                if(orderedCommand) {int extent=ThemePainting.metric(context,DesignMetrics.Role.ORDERED_ACTION);return new Dimension(extent,extent);}
                if(circular) {int extent=ThemePainting.metric(context,DesignMetrics.Role.CIRCULAR_ACTION_SIZE);return new Dimension(extent,extent);}
                if(getParent() instanceof Stacks.StackSurface surface && surface.joined)
                    return new Dimension(ThemePainting.metric(context,DesignMetrics.Role.JOINED_ACTION_WIDTH),ThemePainting.metric(context,DesignMetrics.Role.JOINED_ACTION_HEIGHT));
            }
            // The shared painter uses fractional metrics; Swing's integer preferred width can
            // truncate even an unconstrained content-sized label (for example “Choose…”).
            if (getText() != null && !getText().isEmpty()) {
                var insets = getInsets();
                int textWidth = (int)Math.ceil(getFont().getStringBounds(getText(), new java.awt.font.FontRenderContext(null, true, true)).getWidth());
                int iconWidth = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
                size.width = Math.max(size.width, textWidth + iconWidth + insets.left + insets.right);
            }
            if (recorder()) {
                // A recording field stands a little taller than an ordinary field.
                int height=Math.max(ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_RECORDER_HEIGHT),ThemePainting.height(context,DesignMetrics.Role.FIELD_HEIGHT,Typography.Role.BODY));
                return new Dimension(size.width+recorderIndicator(),Math.max(height,size.height));
            }
            if (embedded) return size;
            int height = ThemePainting.height(context, panelDetail ? DesignMetrics.Role.PANEL_DETAIL_ACTION : prompt ? DesignMetrics.Role.PROMPT_BUTTON_HEIGHT : fullFooter ? DesignMetrics.Role.FULL_FOOTER_BUTTON_HEIGHT : orderedAction ? DesignMetrics.Role.ORDERED_ADD_HEIGHT : panel ? DesignMetrics.Role.PANEL_HEADER_ACTION : panelEmpty ? DesignMetrics.Role.PANEL_EMPTY_ACTION_HEIGHT : DesignMetrics.Role.BUTTON_HEIGHT, panelDetail || panel || panelEmpty ? Typography.Role.PANEL_DETAIL_LABEL : Typography.Role.LABEL);
            int reservation = panelDetail ? 2*ThemePainting.metric(context,DesignMetrics.Role.FOCUS_RING) : 0;
            height += reservation;
            size.width += reservation;
            if (windowFooter) size.width = Math.max(size.width, ThemePainting.metric(context, DesignMetrics.Role.WINDOW_ACTION_MIN_WIDTH));
            if (detailAction) size.width = Math.max(size.width, ThemePainting.metric(context, DesignMetrics.Role.DETAIL_ACTION_MIN_WIDTH));
            return new Dimension(getText() == null || getText().isEmpty() ? height : size.width, Math.max(height, size.height));
        }
        /** Shared painter permits a real specialized owner to select its semantic surface. */
        Color fill(boolean hover) {
            if (recorder()) return ThemePainting.color(context,SemanticColor.SURFACE_INPUT);
            return switch (kind) {
                case PRIMARY -> ThemePainting.color(context, hover ? SemanticColor.ACCENT_HOVER : SemanticColor.ACCENT);
                case SECONDARY -> ThemePainting.color(context, hover ? SemanticColor.BUTTON_SECONDARY_BG_HOVER : SemanticColor.BUTTON_SECONDARY_BG);
                case GHOST -> hover ? ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .08) : null;
                case DESTRUCTIVE_GHOST -> hover ? ColorMath.opacity(ThemePainting.color(context, SemanticColor.STATUS_DANGER), .15) : null;
                case LINK -> hover ? ColorMath.opacity(ThemePainting.color(context, SemanticColor.ACCENT), .08) : null;
            };
        }
        Color border() { if(borderless) return null; if(recorder()) return ThemePainting.color(context,getAccessibleContext().getAccessibleDescription()!=null && !getAccessibleContext().getAccessibleDescription().isEmpty()?SemanticColor.BORDER_ERROR:SemanticColor.BORDER_INPUT); return kind == Nodes.ButtonKind.SECONDARY ? ThemePainting.color(context, SemanticColor.BUTTON_SECONDARY_BORDER) : null; }
        boolean contextualVisible() {
            if (!contextual || isFocusOwner() || getModel().isRollover()) return true;
            for (Container parent=getParent();parent!=null;parent=parent.getParent())
                if (parent instanceof InteractiveRows.Row row) return row.hover || row.owner.selected.contains(row.id)
                    || row.id.equals(row.owner.focusItem) && row.owner.canvas.isFocusOwner();
            return true;
        }
        /**
         * theme.md's primary button: a white inset line along the face's top, and a soft 1 px black drop below the face
         * that the kit view paints outside the bounds (as {@code box-shadow} does), so the face keeps its calibrated extent.
         */
        private void primaryShadows(Graphics2D g, Color fill) {
            if (kind != Nodes.ButtonKind.PRIMARY || embedded || fill == null || circular) {
                if (getClientProperty(ThemePainting.DROP) != null) { putClientProperty(ThemePainting.DROP, null); repaintDrop(); }
                return;
            }
            double radius = ThemePainting.metric(context, DesignMetrics.Role.CONTROL_RADIUS), inset = 0, trim = 0;
            if (panelDetail) {
                radius = UIScale.scale((float) context.metrics().value(DesignMetrics.Role.PANEL_DETAIL_ACTION_ARC)) / 2.0;
                inset = ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING);
                trim = UIScale.scale((float) context.metrics().value(DesignMetrics.Role.PANEL_DETAIL_ACTION_BOTTOM_TRIM));
            }
            var face = new java.awt.geom.RoundRectangle2D.Double(inset, inset, getWidth() - 2 * inset, getHeight() - trim - 2 * inset, 2 * radius, 2 * radius);
            var top = (Graphics2D) g.create();
            try {
                ThemePainting.antialias(top);
                // The face minus itself moved down one pixel: the inset line follows the rounded corners, antialiased.
                var line = new java.awt.geom.Area(face);
                line.subtract(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(face.x, face.y + UIScale.scale(1),
                    face.width, face.height, face.arcwidth, face.archeight)));
                top.setColor(ColorMath.opacity(Color.WHITE, context.metrics().value(DesignMetrics.Role.BUTTON_INSET_PERCENT) / 100.0));
                top.fill(line);
            } finally { top.dispose(); }
            var drop = new ThemePainting.Drop(context, face);
            if (!drop.equals(getClientProperty(ThemePainting.DROP))) { putClientProperty(ThemePainting.DROP, drop); repaintDrop(); }
        }

        /**
         * The view paints the drop outside this button, so the view repaints over where it was and where it is now: on
         * a change of enablement, visibility, bounds, kind or parent (as the focus halo does).
         */
        private void repaintDrop() {
            Rectangle dirty = priorDrop;
            KitView view = dropView;
            priorDrop = null;
            var owner = (KitView) SwingUtilities.getAncestorOfClass(KitView.class, this);
            if (owner != null && isVisible() && getClientProperty(ThemePainting.DROP) != null) {
                // The drop reaches its blur past the face on every side, and its offset further below.
                int reach = UIScale.scale(4);
                var bounds = SwingUtilities.convertRectangle(this, new Rectangle(0, 0, getWidth(), getHeight()), owner);
                bounds.grow(reach, reach);
                priorDrop = bounds; dropView = owner;
                if (view == owner) dirty = dirty == null ? bounds : dirty.union(bounds);
                else { if (view != null && dirty != null) view.repaint(dirty); view = owner; dirty = bounds; }
            }
            if (view != null && dirty != null) view.repaint(dirty);
        }
        @Override public void setEnabled(boolean enabled) { boolean changed = enabled != isEnabled(); super.setEnabled(enabled); if (changed) repaintDrop(); }
        @Override public void setVisible(boolean visible) { boolean changed = visible != isVisible(); super.setVisible(visible); if (changed) repaintDrop(); }
        @SuppressWarnings("deprecation")
        @Override public void reshape(int x, int y, int width, int height) {
            boolean changed = x != getX() || y != getY() || width != getWidth() || height != getHeight();
            super.reshape(x, y, width, height);
            if (changed && priorDrop != null) repaintDrop();
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (kind == null || !contextualVisible()) return;
            // Disabled is the whole button at disabledOpacity, faded as one layer, unless the face draws its own disabled state.
            if (!isEnabled() && fadesDisabled()) { ThemePainting.faded(graphics, this, disabledOpacity, this::paintFace); return; }
            var g = (Graphics2D)graphics.create();
            try { paintFace(g); } finally { g.dispose(); }
        }
        /** The button's face, text and glyph at full opacity. */
        private void paintFace(Graphics2D g) {
            boolean hover = isEnabled() && getModel().isRollover();
            boolean focused = !embedded && focus != null && focus.visible();
            paintKitFace(g, hover, focused);
            g.setFont(getFont()); g.setColor(textColor(hover));
            paintContent(g, hover);
        }
        /** Whether a disabled button fades as one layer: the buttons face's choice; an option control keeps its own. */
        boolean fadesDisabled() { return context.faces().buttons.fades(this); }
        /** The face behind the glyph and label: the buttons face's; an option control paints its own. */
        void paintKitFace(Graphics2D g, boolean hover, boolean focused) { context.faces().buttons.paintFace(this, g, hover, focused); }
        /** The raised face: fill, border, the primary's inset line and drop (the default buttons face). */
        void paintRaised(Graphics2D g, boolean hover, boolean focused) {
            // A selected toggle shows the segmented control's selected fill, embedded or not.
            Color fill = toggle && isSelected() ? ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG) : embedded ? null : fill(hover);
            if (isEnabled() && getModel().isPressed() && fill != null) {
                Color darkened = ColorMath.mix(ThemePainting.color(context, SemanticColor.SURFACE_APP), fill, .08);
                fill = new Color(darkened.getRed(), darkened.getGreen(), darkened.getBlue(), fill.getAlpha());
            }
            Color border = toggle && isSelected() ? ThemePainting.color(context, SemanticColor.BORDER_CONTROL) : embedded ? null : border();
            // The outlined circle fills its bounds; like other buttons its focus halo lies outside them.
if(circular)ThemePainting.rounded(g,this,context,fill,ThemePainting.color(context,SemanticColor.BORDER_CONTROL),focused,false,Math.min(getWidth(),getHeight())/2.0,0,0);
            else if (panelDetail) ThemePainting.rounded(g, this, context, fill, border, focused, false,
                UIScale.scale((float)context.metrics().value(DesignMetrics.Role.PANEL_DETAIL_ACTION_ARC)) / 2.0,
                ThemePainting.metric(context,DesignMetrics.Role.FOCUS_RING),
                UIScale.scale((float)context.metrics().value(DesignMetrics.Role.PANEL_DETAIL_ACTION_BOTTOM_TRIM)));
            else ThemePainting.rounded(g, this, context, fill, border, focused, false);
            primaryShadows(g, fill);
        }
        /** The glyph and label (and a recorder's dot and keycaps) over the face, in the graphics' font and colour. */
        private void paintContent(Graphics2D g, boolean hover) {
            Insets insets = getInsets();
            if (recorder()) {
                int x=insets.left;
                if (armed()) {
                    int halo=recorderHalo(),ring=ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_RECORDER_HALO),top=(getHeight()-halo)/2;
                    Color danger=ThemePainting.color(context,SemanticColor.STATUS_DANGER);
                    g.setColor(new Color(danger.getRed(),danger.getGreen(),danger.getBlue(),context.metrics().value(DesignMetrics.Role.KEYCAP_RECORDER_HALO_ALPHA)));
                    g.fill(new java.awt.geom.Ellipse2D.Double(x,top,halo,halo));
                    g.setColor(danger);g.fill(new java.awt.geom.Ellipse2D.Double(x+ring,top+ring,halo-2*ring,halo-2*ring));
                    x+=recorderIndicator();
                }
                int available=Math.max(0,getWidth()-x-insets.right);
                if(getIcon() instanceof Keycaps caps) caps.paintWithin(this,g,x,Math.max(insets.top,(getHeight()-caps.heightAt(available))/2),available);
                else {
                    recorderText.text=getText();recorderText.setFont(getFont());recorderText.setForeground(textColor(hover));
                    int height=recorderText.height(available);recorderText.setSize(available,height);
                    g.translate(x,Math.max(insets.top,(getHeight()-height)/2));recorderText.paintComponent(g);
                }
                return;
            }
            Rectangle icon = new Rectangle(), text = new Rectangle();
            String clipped = SwingUtilities.layoutCompoundLabel(this, g.getFontMetrics(), getText(), getIcon(), CENTER, getHorizontalAlignment(), CENTER, RIGHT,
                new Rectangle(insets.left, insets.top, getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom), icon, text, getIconTextGap());
            if (getIcon() != null) {
                // Re-tint the same named icon for hover without altering component identity.
                String name = null;
                if (getClientProperty("Jasper.button.icon") instanceof String value) name = value;
                // On a header strip the glyph takes the strip's action ink; on a selected row, or where the face recolours it,
                // it takes its role whatever its artwork (the palette overload recolours built-ins).
                javax.swing.Icon artwork = name != null && stripInk != null ? context.icons().panel(name, context.theme().semantic(), stripInk, getIcon().getIconWidth())
                    : name != null && (onSelection || context.faces().buttons.recolours(this))
                    ? context.icons().icon(name, context.theme().semantic(), iconRole(hover), getIcon().getIconWidth())
                    : name == null || context.icons().authored(name,panel || panelDetail || panelRow) ? getIcon() : panel || panelDetail || panelRow
                    ? context.icons().panel(name, context.theme().semantic(), iconRole(hover), panelRow ? ThemePainting.metric(context,DesignMetrics.Role.PANEL_ROW_ACTION_GLYPH) : getIcon().getIconWidth())
                    : context.icons().icon(name, context.theme(), iconRole(hover), getIcon().getIconWidth());
                int opticalOffset=panelDetail ? ThemePainting.metric(context,DesignMetrics.Role.PANEL_DETAIL_ACTION_OPTICAL_OFFSET) : panelRow ? -ThemePainting.metric(context,DesignMetrics.Role.PANEL_ROW_OPTICAL_OFFSET) : 0;
                // A disabled button whose face draws its own disabled state shows its glyph at the label's disabled alpha.
                var glyph = isEnabled() || fadesDisabled() ? g : (Graphics2D) g.create();
                if (glyph != g) glyph.setComposite(AlphaComposite.SrcOver.derive(disabledOpacity));
                try { artwork.paintIcon(this, glyph, icon.x-opticalOffset+(getIcon().getIconWidth()-artwork.getIconWidth())/2, icon.y-opticalOffset+(getIcon().getIconHeight()-artwork.getIconHeight())/2); }
                finally { if (glyph != g) glyph.dispose(); }
            }
            if (clipped != null) g.drawString(clipped, text.x, text.y + g.getFontMetrics().getAscent() - (panelDetail ? ThemePainting.metric(context,DesignMetrics.Role.PANEL_DETAIL_TEXT_BASELINE) : 0));
            if (embedded && isEnabled() && focus != null && focus.visible()) {
                // An embedded button has no outer ring room: its keyboard focus cue is a hairline inside its bounds.
                int radius = ThemePainting.metric(context, DesignMetrics.Role.CONTROL_RADIUS);
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_FOCUS));
                g.setStroke(new BasicStroke(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE)));
                g.draw(new java.awt.geom.RoundRectangle2D.Double(.5, .5, getWidth() - 1, getHeight() - 1, radius * 2.0, radius * 2.0));
            }
        }
    }
}
