package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.awt.Container;
import javax.swing.JComponent;

/** Typed renderer dispatch. Each mounted control owns precisely its own registrations. */
final class ControlRegistry {
    private ControlRegistry() {}
    static void validate(Nodes.Node node) {
        switch (node) {
            case Nodes.Custom ignored -> {} case Nodes.Text ignored -> {} case Nodes.Icon ignored -> {} case Nodes.Button ignored -> {} case Nodes.Field ignored -> {}
            case Nodes.PickerMenu ignored -> {} case Nodes.Secret ignored -> {} case Nodes.Choice ignored -> {} case Nodes.Toggle ignored -> {}
            case Nodes.Chip ignored -> {} case Nodes.StatusDot ignored -> {} case Nodes.Progress ignored -> {} case Nodes.Strength ignored -> {}
            case Nodes.MenuButton ignored -> {}
            case Nodes.ItemList n -> { for(var item:n.items()) { item.leading().ifPresent(ControlRegistry::validate); item.title().forEach(ControlRegistry::validate); item.subtitle().forEach(ControlRegistry::validate); item.trailing().forEach(ControlRegistry::validate); } n.addAction().ifPresent(ControlRegistry::validate); }
            case Nodes.Table _, Nodes.Tree _, Nodes.Breadcrumbs _, Nodes.Separator _ -> {}
            case Nodes.Panel _, Nodes.Header _, Nodes.Stack _, Nodes.FormRow _, Nodes.SettingRow _, Nodes.Card _, Nodes.Section _, Nodes.Empty _, Nodes.Form _, Nodes.Split _, Nodes.Notice _ -> children(node).forEach(ControlRegistry::validate);
        }
    }
    static Control create(KitView view, Nodes.Node node) {
        return switch (node) {
            case Nodes.Custom custom -> new CustomContents.CustomControl(view, custom);
            case Nodes.Panel panel -> new Panels.PanelControl(view, panel);
            case Nodes.Header header -> new PanelHeaders.HeaderControl(view, header);
            case Nodes.Text text -> new Labels.TextControl(view, text);
            case Nodes.Icon icon -> new Labels.IconControl(view, icon);
            case Nodes.Button button -> new Buttons.ButtonControl(view, button);
            case Nodes.Field field -> field.presentation() == Nodes.FieldPresentation.STEPPER ? new NumericControls.Stepper(view, field) : field.presentation() == Nodes.FieldPresentation.SLIDER ? new NumericControls.Slider(view, field) : new TextFields.FieldControl(view, field);
            case Nodes.Secret secret -> new SecretFields.SecretControl(view, secret);
            case Nodes.Choice choice -> choice.kind() == Nodes.ChoiceKind.PREVIEWS ? new PreviewChoices(view, choice) : new Choices.ChoiceControl(view, choice);
            case Nodes.PickerMenu menu -> new PickerMenus.MenuControl(view,menu);
            case Nodes.Toggle toggle -> new Switches.ToggleControl(view, toggle);
            case Nodes.Chip chip -> new Indicators.ChipControl(view, chip);
            case Nodes.StatusDot dot -> new Indicators.DotControl(view, dot);
            case Nodes.Progress progress -> new Indicators.ProgressControl(view, progress);
            case Nodes.Strength strength -> new Indicators.StrengthControl(view, strength);
            case Nodes.Stack stack -> new Stacks.StackControl(view, stack);
            case Nodes.FormRow row -> new FormRows.RowControl(view, row);
            case Nodes.SettingRow row -> new SettingRows.RowControl(view, row);
            case Nodes.Card card -> new Cards.CardControl(view, card);
            case Nodes.Section section -> new Cards.SectionControl(view, section);
            case Nodes.Empty empty -> new EmptyStates.EmptyControl(view, empty);
            case Nodes.Form form -> new Forms.FormControl(view, form);
            case Nodes.Split split -> new SplitViews.SplitControl(view, split);
            case Nodes.MenuButton menu -> new Menus.MenuControl(view, menu);
            case Nodes.ItemList list -> new ItemLists.ListControl(view,list);
            case Nodes.Table table -> new Tables.TableControl(view,table);
            case Nodes.Tree tree -> new Trees.TreeControl(view,tree);
            case Nodes.Breadcrumbs crumbs -> new BreadcrumbControls.BreadcrumbsControl(view, crumbs);
            case Nodes.Notice notice -> new Notices.NoticeControl(view, notice);
            case Nodes.Separator separator -> new Separators.SeparatorControl(view, separator);
        };
    }
    static List<Nodes.Node> children(Nodes.Node node) {
        var result = new ArrayList<Nodes.Node>();
        switch (node) {
            case Nodes.Panel n -> { result.add(n.header()); n.searchOrNavigation().ifPresent(result::add); result.add(n.body()); n.footer().ifPresent(result::add); }
            case Nodes.Header n -> { n.context().ifPresent(result::add); n.primary().ifPresent(result::add); }
            case Nodes.Stack n -> result.addAll(n.children());
            case Nodes.ItemList n -> n.addAction().ifPresent(result::add);
            case Nodes.Card n -> result.addAll(n.children());
            case Nodes.Split n -> result.addAll(n.children());
            case Nodes.FormRow n -> { result.add(n.control()); n.trailing().ifPresent(result::add); }
            case Nodes.SettingRow n -> result.add(n.control());
            case Nodes.Section n -> result.add(n.body());
            case Nodes.Empty n -> { result.add(n.icon()); result.addAll(n.actions()); }
            case Nodes.Notice n -> result.addAll(n.actions());
            case Nodes.Form n -> { n.hero().ifPresent(result::add); result.addAll(n.sections()); n.preview().ifPresent(result::add); result.addAll(n.actions()); }
            default -> {}
        }
        return result;
    }
    static Object kind(Nodes.Node node) {
        return switch (node) {
            case Nodes.Custom custom -> java.util.List.of(custom.kind(),custom.contentId());
            case Nodes.Field field -> field.presentation() == Nodes.FieldPresentation.STEPPER || field.presentation() == Nodes.FieldPresentation.SLIDER ? java.util.List.of(field.kind(), field.presentation()) : field.kind();
            case Nodes.Secret secret -> secret.kind();
            case Nodes.Button button -> button.kind();
            case Nodes.Choice choice -> choice.kind();
            case Nodes.Table table -> java.util.List.of(Nodes.Table.class, table.scroll());
            default -> node.getClass();
        };
    }
    abstract static class Control implements AutoCloseable {
        final KitView view;
        final JComponent component;
        final ArrayList<AutoCloseable> registrations = new ArrayList<>();
        Nodes.Node node;
        boolean closed;
        boolean parentEnabled = true;
        Control(KitView view, Nodes.Node node, JComponent component) {
            this.view = view; this.node = node; this.component = component;
            KitOwnership.mark(component, node.id());
        }
        final KitContext context() { return view.context(); }
        void update(Nodes.Node next) { node = next; refresh(); }
        abstract void refresh();
        Container childParent(Nodes.Node child) { return component; }
        void children(List<Control> controls) {}
        void embeddedLabel(boolean embedded) {}
        void prompt(boolean prompt) {}
        JComponent focusTarget() { return component; }
        /**
         * Height of this control's first line (a field's face, a list's first row), which a FormRow label and trailing
         * text centre on so that a later error or caption below the control does not move them; -1 when unknown.
         */
        int firstLineHeight() { return -1; }
        final boolean interactive() { return !closed && parentEnabled && node.state().enabled() && !node.state().busy() && node.state().visible(); }
        final void emit(UiEvent event) { if (interactive()) view.dispatch(event); }
        final void state() { component.setVisible(node.state().visible()); component.setEnabled(parentEnabled && node.state().enabled() && !node.state().busy()); }
        final void own(AutoCloseable lifetime) { registrations.add(lifetime); }
        @Override public void close() {
            if (closed) return; closed = true;
            for (int i = registrations.size() - 1; i >= 0; i--) {
                try { registrations.get(i).close(); } catch (Throwable failure) { view.report(failure); }
            }
            registrations.clear();
        }
    }
}
