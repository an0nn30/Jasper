package dev.jasper.editor.ui;

import dev.jasper.editor.syntax.Languages;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every language by display name, with a filter field (spec 3.1), on an owned kit form. Up and Down in the filter move
 * the list's selection while the field keeps focus ({@code Field.listId}); Enter in the filter or on a row, or a double
 * click, chooses. There is no default button, as before; Escape cancels. UI thread only; the surface that mounts
 * {@link #view()} disposes it, and {@link #close()} makes late calls inert.
 */
public final class LanguagePicker implements AutoCloseable {
    public static final String FORM = "language", FILTER = "language.filter", LIST = "language.list", CHOOSE = "choose", CANCEL = "cancel";
    /** Rows the list shows before it scrolls, the former list's visible row count. */
    static final int VISIBLE_ROWS = 14;
    private final List<Languages.Language> all = Languages.all();
    private final List<Languages.Language> shown = new ArrayList<>();
    private final Consumer<String> chosen;
    private final UiView view;
    private String query = "";
    private Languages.Language selected;
    private boolean closed;

    /**
     * @param kit the plugin's kit
     * @param current the tab's style, selected at first
     * @param chosen receives the chosen style
     * @param cancel closes the dialog
     */
    public LanguagePicker(UiKit kit, String current, Consumer<String> chosen, Runnable cancel) {
        this.chosen = chosen;
        match("");
        for (var language : shown) if (language.style().equals(current)) selected = language;
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Edited edited when edited.id().equals(FILTER) -> filter(edited.text());
                case UiEvent.Selected picked when picked.id().equals(LIST) -> select(picked.ids());
                case UiEvent.Action action when action.id().equals(CHOOSE) -> chooseSelected();
                case UiEvent.Action action when action.id().equals(CANCEL) -> cancel.run();
                default -> { }
            }
        });
    }

    /** @param style a language's style @return its row's id in the list */
    static String row(String style) { return "language:" + style; }

    /** @return the owned view to mount */
    public UiView view() { return view; }

    /** @param text shows the languages whose names contain it, ignoring case; selects the first */
    public void filter(String text) {
        if (closed) return;
        query = text;
        match(text);
        render();
    }

    /** Chooses the selected language, if any. */
    public void chooseSelected() {
        if (!closed && selected != null) chosen.accept(selected.style());
    }

    List<String> visibleNames() { return shown.stream().map(Languages.Language::name).toList(); }

    String selectedName() { return selected == null ? "" : selected.name(); }

    private void match(String text) {
        String needle = text.strip().toLowerCase(Locale.ROOT);
        shown.clear();
        for (var language : all) if (language.name().toLowerCase(Locale.ROOT).contains(needle)) shown.add(language);
        selected = shown.isEmpty() ? null : shown.getFirst();
    }

    private void select(Set<String> ids) {
        Languages.Language next = null;
        for (var language : shown) if (ids.contains(row(language.style()))) next = language;
        if (next == null || next == selected) return;
        selected = next;
        render();
    }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        var filter = new Nodes.Field(FILTER, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Filter languages", "Filter languages", CHOOSE,
            Optional.empty(), Nodes.FieldPresentation.STANDARD, "", "", LIST);
        var items = new ArrayList<ListItem>();
        for (var language : shown) {
            String id = row(language.style());
            items.add(new ListItem(id, Optional.empty(), List.of(new Nodes.Text(id + ".name", ControlState.READY, language.name(), Nodes.TextRole.BODY)),
                List.of(), List.of()));
        }
        var list = new Nodes.ItemList(LIST, ControlState.READY, Nodes.ListKind.COMPACT, items,
            selected == null ? Set.of() : Set.of(row(selected.style())), CHOOSE, List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.of(VISIBLE_ROWS));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Language", Optional.empty(), List.of(filter, list), Optional.empty(),
            List.of(), CANCEL);
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
