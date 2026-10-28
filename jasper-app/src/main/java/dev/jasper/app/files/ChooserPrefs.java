package dev.jasper.app.files;

import dev.jasper.app.persistence.UiState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The chooser's remembered state in {@link UiState}: recent and pinned folders, the preview pane and dotfiles. Every change saves. UI thread. */
public final class ChooserPrefs {
    static final int RECENT_LIMIT = 10;
    private final UiState state;

    public ChooserPrefs(UiState state) { this.state = Objects.requireNonNull(state); }

    public List<Location> recent() { return locations(state.chooser().recent()); }
    public List<Location> pinned() { return locations(state.chooser().pinned()); }
    public boolean preview() { return state.chooser().preview(); }
    public boolean showHidden() { return state.chooser().showHidden(); }
    public boolean isPinned(Location folder) { return pinned().contains(folder); }

    /** Moves {@code folder} to the front of Recent, which keeps ten. */
    public void recordRecent(Location folder) {
        String value = folder.stored();
        var recent = new ArrayList<String>();
        recent.add(value);
        state.chooser().recent().stream().filter(other -> !other.equals(value)).limit(RECENT_LIMIT - 1).forEach(recent::add);
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(recent, current.pinned(), current.preview(), current.showHidden(), current.sortColumn(), current.sortAscending(), current.widths()));
    }

    public void pin(Location folder) {
        if (isPinned(folder)) return;
        UiState.Chooser current = state.chooser();
        var pinned = new ArrayList<>(current.pinned());
        pinned.add(folder.stored());
        update(new UiState.Chooser(current.recent(), pinned, current.preview(), current.showHidden(), current.sortColumn(), current.sortAscending(), current.widths()));
    }

    public void unpin(Location folder) {
        String value = folder.stored();
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned().stream().filter(other -> !other.equals(value)).toList(),
            current.preview(), current.showHidden(), current.sortColumn(), current.sortAscending(), current.widths()));
    }

    public void setPreview(boolean value) {
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned(), value, current.showHidden(), current.sortColumn(), current.sortAscending(), current.widths()));
    }

    public void setShowHidden(boolean value) {
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned(), current.preview(), value, current.sortColumn(), current.sortAscending(), current.widths()));
    }

    /** The remembered sort column; an unknown id means the default, Name. */
    public ChooserModel.Column sortColumn() {
        return switch (state.chooser().sortColumn()) { case "modified" -> ChooserModel.Column.MODIFIED; case "size" -> ChooserModel.Column.SIZE; default -> ChooserModel.Column.NAME; };
    }

    public boolean sortAscending() { return state.chooser().sortAscending(); }

    /** The listing's remembered column widths in unscaled pixels by column id. */
    public Map<String, Integer> widths() { return state.chooser().widths(); }

    public void setSort(ChooserModel.Column column, boolean ascending) {
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned(), current.preview(), current.showHidden(),
            column.name().toLowerCase(java.util.Locale.ROOT), ascending, current.widths()));
    }

    public void setWidths(Map<String, Integer> widths) {
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned(), current.preview(), current.showHidden(),
            current.sortColumn(), current.sortAscending(), widths));
    }

    private void update(UiState.Chooser value) {
        state.setChooser(value);
        state.save();
    }

    private static List<Location> locations(List<String> values) {
        return values.stream().map(Location::parse).flatMap(java.util.Optional::stream).toList();
    }
}
