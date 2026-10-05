package dev.jasper.app.themesui;

import dev.jasper.app.themeimport.ThemeImporter;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Immutable providers and explicit per-provider choices, as a kit sheet: one conflict dropdown per theme, the warnings
 * and failures as literal text, and Cancel/Import. No file operations. EDT only.
 */
final class ThemeImportView {
    static final String IMPORT = "import", CANCEL = "cancel", DETAILS = "details";
    private final ThemeImporter.ImportPlan plan;
    private final Map<String, ThemeImporter.ConflictChoice> decisions = new LinkedHashMap<>();
    private final String details;
    private boolean committing;
    final KitView view;

    ThemeImportView(KitContext context, ThemeImporter.ImportPlan plan, Consumer<Map<String, ThemeImporter.ConflictChoice>> accept, Runnable cancel) {
        this.plan = plan;
        var text = new StringBuilder();
        for (var item : plan.items()) {
            decisions.put(item.id(), item.target().isPresent() ? ThemeImporter.ConflictChoice.SKIP : ThemeImporter.ConflictChoice.KEEP_BOTH);
            for (var warning : item.conversion().warnings()) text.append(item.conversion().sourceEntry()).append(": ").append(warning).append('\n');
            if (item.shadowsBundled()) text.append(item.conversion().document().name()).append(": overrides a bundled theme\n");
        }
        for (var failure : plan.failures()) text.append(failure.sourceEntry()).append(": ").append(failure.message()).append('\n');
        details = text.toString().strip();
        view = new KitView(context, spec(), event -> {
            switch (event) {
                case UiEvent.Selected picked when picked.id().startsWith("decision:") -> {
                    picked.ids().stream().findFirst().ifPresent(choice -> decisions.put(picked.id().substring("decision:".length()),
                        ThemeImporter.ConflictChoice.valueOf(choice)));
                    render();
                }
                case UiEvent.Action action when action.id().equals(IMPORT) -> {
                    if (committing || plan.items().isEmpty()) return;
                    committing = true;
                    render();
                    accept.accept(choices());
                }
                case UiEvent.Action action when action.id().equals(CANCEL) -> cancel.run();
                default -> { }
            }
        });
    }

    private static String label(ThemeImporter.ConflictChoice choice) {
        return switch (choice) { case REPLACE -> "Replace"; case KEEP_BOTH -> "Keep both"; case SKIP -> "Skip"; };
    }

    private ViewSpec spec() {
        var sections = new ArrayList<Nodes.Node>();
        for (var item : plan.items()) {
            var offered = item.target().isPresent() ? List.of(ThemeImporter.ConflictChoice.values())
                : List.of(ThemeImporter.ConflictChoice.KEEP_BOTH, ThemeImporter.ConflictChoice.SKIP);
            var options = offered.stream().map(choice -> new Option(choice.name(), label(choice), Optional.<Nodes.Hue>empty())).toList();
            String name = item.conversion().document().name();
            sections.add(new Nodes.FormRow("row:" + item.id(), ControlState.READY, name, item.conversion().sourceEntry(),
                new Nodes.Choice("decision:" + item.id(), ControlState.READY, Nodes.ChoiceKind.DROPDOWN, options,
                    decisions.get(item.id()).name(), name + " conflict choice", ""), Optional.empty()));
        }
        if (!details.isEmpty()) sections.add(new Nodes.Text(DETAILS, ControlState.READY, details, Nodes.TextRole.DESCRIPTION));
        var importing = new ControlState(true, !plan.items().isEmpty() && !committing, false, "");
        return new ViewSpec(new Nodes.Form("theme-import", ControlState.READY, Nodes.FormKind.SHEET, "Import themes", Optional.empty(), sections,
            Optional.empty(), List.of(new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
                new Nodes.Button(IMPORT, importing, "Import", "", Nodes.ButtonKind.PRIMARY, IMPORT, "")), CANCEL));
    }

    private void render() { if (!view.isClosed()) view.update(spec()); }

    String details() { return details; }

    Map<String, ThemeImporter.ConflictChoice> choices() { return Map.copyOf(decisions); }
}
