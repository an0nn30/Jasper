package dev.jasper.app.plugins;
/** Exhaustive, value-only SDK projection. */
final class KitValues {
 private KitValues() {}
 static dev.jasper.ui.model.Nodes.Button toModule(dev.jasper.sdk.ui.Nodes.Button n) { return (dev.jasper.ui.model.Nodes.Button)toModule((dev.jasper.sdk.ui.Nodes.Node)n); }
 static dev.jasper.ui.model.ViewSpec toModule(dev.jasper.sdk.ui.ViewSpec spec) { return new dev.jasper.ui.model.ViewSpec(toModule(spec.root())); }
 static dev.jasper.ui.model.Nodes.Node toModule(dev.jasper.sdk.ui.Nodes.Node node) { return switch(node) {
 case dev.jasper.sdk.ui.Nodes.Text n -> new dev.jasper.ui.model.Nodes.Text(n.id(), toModule(n.state()), n.text(), dev.jasper.ui.model.Nodes.TextRole.valueOf(n.role().name()), n.severity().map(s -> dev.jasper.ui.model.Nodes.Severity.valueOf(s.name())), n.caret());
 case dev.jasper.sdk.ui.Nodes.Icon n -> new dev.jasper.ui.model.Nodes.Icon(n.id(), toModule(n.state()), n.name(), dev.jasper.ui.model.Nodes.Hue.valueOf(n.hue().name()), dev.jasper.ui.model.Nodes.IconSize.valueOf(n.size().name()), n.accessibleName(), n.severity().map(s -> dev.jasper.ui.model.Nodes.Severity.valueOf(s.name())));
 case dev.jasper.sdk.ui.Nodes.Button n -> new dev.jasper.ui.model.Nodes.Button(n.id(), toModule(n.state()), n.label(), n.icon(), dev.jasper.ui.model.Nodes.ButtonKind.valueOf(n.kind().name()), n.action(), n.tooltip(), dev.jasper.ui.model.Nodes.ButtonPresentation.valueOf(n.presentation().name()), n.iconSeverity().map(s -> dev.jasper.ui.model.Nodes.Severity.valueOf(s.name())), n.selected());
 case dev.jasper.sdk.ui.Nodes.MenuButton n -> new dev.jasper.ui.model.Nodes.MenuButton(n.id(), toModule(n.state()), n.label(), n.icon(), dev.jasper.ui.model.Nodes.ButtonKind.valueOf(n.kind().name()), n.action(), n.items().stream().map(KitValues::toModule).toList(), n.tooltip(), dev.jasper.ui.model.Nodes.ButtonPresentation.valueOf(n.presentation().name()));
 case dev.jasper.sdk.ui.Nodes.StatusDot n -> new dev.jasper.ui.model.Nodes.StatusDot(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.StatusKind.valueOf(n.kind().name()));
 case dev.jasper.sdk.ui.Nodes.Field n -> new dev.jasper.ui.model.Nodes.Field(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.FieldKind.valueOf(n.kind().name()), n.value(), n.hint(), n.label(), n.trailingAction(), n.numeric().map(KitValues::toModule), dev.jasper.ui.model.Nodes.FieldPresentation.valueOf(n.presentation().name()), n.alternateAction(), n.cancelAction(), n.listId());
 case dev.jasper.sdk.ui.Nodes.Secret n -> new dev.jasper.ui.model.Nodes.Secret(n.id(), toModule(n.state()), n.hint(), n.label(), n.revealGroup(), dev.jasper.ui.model.Nodes.SecretKind.valueOf(n.kind().name()), n.copyAction(), n.submitAction());
 case dev.jasper.sdk.ui.Nodes.Choice n -> new dev.jasper.ui.model.Nodes.Choice(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.ChoiceKind.valueOf(n.kind().name()), n.options().stream().map(KitValues::toModule).toList(), n.selectedId(), n.label(), n.action(), n.icon(), n.search().map(KitValues::toModule));
 case dev.jasper.sdk.ui.Nodes.Toggle n -> new dev.jasper.ui.model.Nodes.Toggle(n.id(), toModule(n.state()), n.label(), n.checked(), n.action(), n.compact(), n.tooltip(), n.pending());
 case dev.jasper.sdk.ui.Nodes.Chip n -> new dev.jasper.ui.model.Nodes.Chip(n.id(), toModule(n.state()), n.label(), dev.jasper.ui.model.Nodes.Hue.valueOf(n.hue().name()));
 case dev.jasper.sdk.ui.Nodes.Progress n -> new dev.jasper.ui.model.Nodes.Progress(n.id(), toModule(n.state()), n.fraction(), n.label());
 case dev.jasper.sdk.ui.Nodes.Strength n -> new dev.jasper.ui.model.Nodes.Strength(n.id(), toModule(n.state()), n.filled(), n.label());
 case dev.jasper.sdk.ui.Nodes.Stack n -> new dev.jasper.ui.model.Nodes.Stack(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.LayoutRole.valueOf(n.role().name()), n.children().stream().map(KitValues::toModule).toList(), n.cancelAction());
 case dev.jasper.sdk.ui.Nodes.FormRow n -> new dev.jasper.ui.model.Nodes.FormRow(n.id(), toModule(n.state()), n.label(), n.caption(), toModule(n.control()), n.trailing().map(KitValues::toModule), n.highlighted());
 case dev.jasper.sdk.ui.Nodes.SettingRow n -> new dev.jasper.ui.model.Nodes.SettingRow(n.id(), toModule(n.state()), n.title(), n.description(), toModule(n.control()), n.dependent(), n.highlighted());
 case dev.jasper.sdk.ui.Nodes.Card n -> new dev.jasper.ui.model.Nodes.Card(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.CardKind.valueOf(n.kind().name()), n.children().stream().map(KitValues::toModule).toList());
 case dev.jasper.sdk.ui.Nodes.Section n -> new dev.jasper.ui.model.Nodes.Section(n.id(), toModule(n.state()), n.title(), toModule(n.body()));
 case dev.jasper.sdk.ui.Nodes.Empty n -> new dev.jasper.ui.model.Nodes.Empty(n.id(), toModule(n.state()), toModule(n.icon()), n.title(), n.description(), n.actions().stream().map(KitValues::toModule).toList());
 case dev.jasper.sdk.ui.Nodes.ItemList n -> new dev.jasper.ui.model.Nodes.ItemList(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.ListKind.valueOf(n.kind().name()), n.items().stream().map(KitValues::toModule).toList(), n.selectedIds(), n.activateAction(), n.contextActions().stream().map(KitValues::toModule).toList(), dev.jasper.ui.model.Nodes.SelectionMode.valueOf(n.selectionMode().name()), n.addAction().map(KitValues::toModule), n.visibleRows(), n.enterAction(), n.deleteAction(), n.detailsAction(), n.copyAction(), n.cancelAction());
 case dev.jasper.sdk.ui.Nodes.Header n -> new dev.jasper.ui.model.Nodes.Header(n.id(), toModule(n.state()), n.title(), n.count(), n.context().map(KitValues::toModule), n.primary().map(KitValues::toModule), n.overflow().stream().map(KitValues::toModule).toList());
 case dev.jasper.sdk.ui.Nodes.Panel n -> new dev.jasper.ui.model.Nodes.Panel(n.id(), toModule(n.state()), (dev.jasper.ui.model.Nodes.Header) toModule(n.header()), n.searchOrNavigation().map(KitValues::toModule), toModule(n.body()), n.footer().map(KitValues::toModule));
 case dev.jasper.sdk.ui.Nodes.Form n -> new dev.jasper.ui.model.Nodes.Form(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.FormKind.valueOf(n.kind().name()), n.title(), n.hero().map(KitValues::toModule), n.sections().stream().map(KitValues::toModule).toList(), n.preview().map(KitValues::toModule), n.actions().stream().map(KitValues::toModule).toList(), n.cancelAction());
 case dev.jasper.sdk.ui.Nodes.Split n -> new dev.jasper.ui.model.Nodes.Split(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.SplitKind.valueOf(n.kind().name()), n.children().stream().map(KitValues::toModule).toList());
 case dev.jasper.sdk.ui.Nodes.Table n -> new dev.jasper.ui.model.Nodes.Table(n.id(), toModule(n.state()), n.columns().stream().map(KitValues::toModule).toList(), n.rows().stream().map(KitValues::toModule).toList(), n.selectedIds(), n.activateAction(), n.contextActions().stream().map(KitValues::toModule).toList(), dev.jasper.ui.model.Nodes.SelectionMode.valueOf(n.selectionMode().name()), dev.jasper.ui.model.Nodes.ScrollPolicy.valueOf(n.scroll().name()));
 case dev.jasper.sdk.ui.Nodes.Tree n -> new dev.jasper.ui.model.Nodes.Tree(n.id(), toModule(n.state()), n.roots().stream().map(KitValues::toModule).toList(), n.expandedIds(), n.selectedIds(), n.activateAction());
 case dev.jasper.sdk.ui.Nodes.Breadcrumbs n -> new dev.jasper.ui.model.Nodes.Breadcrumbs(n.id(), toModule(n.state()), n.crumbs().stream().map(KitValues::toModule).toList(), n.label(), n.editing(), n.value(), n.editAction(), n.submitAction(), n.cancelAction());
 case dev.jasper.sdk.ui.Nodes.Custom n -> new dev.jasper.ui.model.Nodes.Custom(n.id(), toModule(n.state()), n.contentId(), dev.jasper.ui.model.Nodes.CustomKind.valueOf(n.kind().name()), n.accessibleName());
 }; }
 static dev.jasper.ui.model.ControlState toModule(dev.jasper.sdk.ui.ControlState n) { return new dev.jasper.ui.model.ControlState(n.visible(), n.enabled(), n.busy(), n.error()); }
 static dev.jasper.ui.model.Option toModule(dev.jasper.sdk.ui.Option n) { return new dev.jasper.ui.model.Option(n.id(), n.label(), n.hue().map(KitValues::toModule)); }
 static dev.jasper.ui.model.MenuItem toModule(dev.jasper.sdk.ui.MenuItem n) { return new dev.jasper.ui.model.MenuItem(n.id(), n.label(), n.icon(), n.action(), n.enabled(), n.separator(), dev.jasper.ui.model.Nodes.Hue.valueOf(n.hue().name())); }
 static dev.jasper.ui.model.ListItem toModule(dev.jasper.sdk.ui.ListItem n) { return new dev.jasper.ui.model.ListItem(n.id(), n.leading().map(KitValues::toModule), n.title().stream().map(KitValues::toModule).toList(), n.subtitle().stream().map(KitValues::toModule).toList(), n.trailing().stream().map(KitValues::toModule).toList(), n.heading(), n.contextActions().map(items -> items.stream().map(KitValues::toModule).toList()), n.tooltip()); }
 static dev.jasper.ui.model.Column toModule(dev.jasper.sdk.ui.Column n) { return new dev.jasper.ui.model.Column(n.id(), n.title(), toModule(n.role()), n.numeric(), n.weight(), n.sorted()); }
 static dev.jasper.ui.model.TableRow toModule(dev.jasper.sdk.ui.TableRow n) { return new dev.jasper.ui.model.TableRow(n.id(), n.cells(), n.icon(), n.muted(), n.contextActions().map(items -> items.stream().map(KitValues::toModule).toList()), n.tooltip()); }
 static dev.jasper.ui.model.TreeItem toModule(dev.jasper.sdk.ui.TreeItem n) { return new dev.jasper.ui.model.TreeItem(n.id(), n.title(), n.icon(), n.leaf(), n.children().stream().map(KitValues::toModule).toList(), n.count(), n.emphasized(), n.tooltip(), n.status().map(KitValues::toModule), n.trailing(), n.contextActions().map(items -> items.stream().map(KitValues::toModule).toList())); }
 static dev.jasper.ui.model.Crumb toModule(dev.jasper.sdk.ui.Crumb n) { return new dev.jasper.ui.model.Crumb(n.id(), n.label(), n.contextActions().stream().map(KitValues::toModule).toList()); }
 static dev.jasper.ui.model.Nodes.Numeric toModule(dev.jasper.sdk.ui.Nodes.Numeric n) { return new dev.jasper.ui.model.Nodes.Numeric(n.min(), n.max(), n.step(), n.decimals()); }
 static dev.jasper.ui.model.Nodes.ChoiceSearch toModule(dev.jasper.sdk.ui.Nodes.ChoiceSearch n) { return new dev.jasper.ui.model.Nodes.ChoiceSearch(n.pinned(), n.noMatches(), n.entry()); }
 static dev.jasper.ui.model.Nodes.FieldKind toModule(dev.jasper.sdk.ui.Nodes.FieldKind n) { return dev.jasper.ui.model.Nodes.FieldKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.ChoiceKind toModule(dev.jasper.sdk.ui.Nodes.ChoiceKind n) { return dev.jasper.ui.model.Nodes.ChoiceKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.ListKind toModule(dev.jasper.sdk.ui.Nodes.ListKind n) { return dev.jasper.ui.model.Nodes.ListKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.CardKind toModule(dev.jasper.sdk.ui.Nodes.CardKind n) { return dev.jasper.ui.model.Nodes.CardKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.SelectionMode toModule(dev.jasper.sdk.ui.Nodes.SelectionMode n) { return dev.jasper.ui.model.Nodes.SelectionMode.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.ButtonKind toModule(dev.jasper.sdk.ui.Nodes.ButtonKind n) { return dev.jasper.ui.model.Nodes.ButtonKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.FormKind toModule(dev.jasper.sdk.ui.Nodes.FormKind n) { return dev.jasper.ui.model.Nodes.FormKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.SplitKind toModule(dev.jasper.sdk.ui.Nodes.SplitKind n) { return dev.jasper.ui.model.Nodes.SplitKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.CustomKind toModule(dev.jasper.sdk.ui.Nodes.CustomKind n) { return dev.jasper.ui.model.Nodes.CustomKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.IconSize toModule(dev.jasper.sdk.ui.Nodes.IconSize n) { return dev.jasper.ui.model.Nodes.IconSize.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.StatusKind toModule(dev.jasper.sdk.ui.Nodes.StatusKind n) { return dev.jasper.ui.model.Nodes.StatusKind.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.Hue toModule(dev.jasper.sdk.ui.Nodes.Hue n) { return dev.jasper.ui.model.Nodes.Hue.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.TextRole toModule(dev.jasper.sdk.ui.Nodes.TextRole n) { return dev.jasper.ui.model.Nodes.TextRole.valueOf(n.name()); }
 static dev.jasper.ui.model.Nodes.LayoutRole toModule(dev.jasper.sdk.ui.Nodes.LayoutRole n) { return dev.jasper.ui.model.Nodes.LayoutRole.valueOf(n.name()); }
 static dev.jasper.sdk.ui.UiEvent toSdk(dev.jasper.ui.model.UiEvent event) { return switch(event) {
 case dev.jasper.ui.model.UiEvent.Action n -> new dev.jasper.sdk.ui.UiEvent.Action(n.id());
 case dev.jasper.ui.model.UiEvent.Edited n -> new dev.jasper.sdk.ui.UiEvent.Edited(n.id(), n.text());
 case dev.jasper.ui.model.UiEvent.Selected n -> new dev.jasper.sdk.ui.UiEvent.Selected(n.id(), n.ids());
 case dev.jasper.ui.model.UiEvent.Toggled n -> new dev.jasper.sdk.ui.UiEvent.Toggled(n.id(), n.checked());
 case dev.jasper.ui.model.UiEvent.Expanded n -> new dev.jasper.sdk.ui.UiEvent.Expanded(n.id(), n.itemId(), n.expanded());
 case dev.jasper.ui.model.UiEvent.Removed n -> new dev.jasper.sdk.ui.UiEvent.Removed(n.id(), n.itemId());
 case dev.jasper.ui.model.UiEvent.Reordered n -> new dev.jasper.sdk.ui.UiEvent.Reordered(n.id(), n.itemId(), n.destination());
 case dev.jasper.ui.model.UiEvent.SecretChanged n -> new dev.jasper.sdk.ui.UiEvent.SecretChanged(n.id());
 case dev.jasper.ui.model.UiEvent.Sorted n -> new dev.jasper.sdk.ui.UiEvent.Sorted(n.id(), n.columnId());
 case dev.jasper.ui.model.UiEvent.Clicked n -> new dev.jasper.sdk.ui.UiEvent.Clicked(n.id(), n.itemId());
 }; }
}
