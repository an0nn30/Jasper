package dev.jasper.app.files;

import dev.jasper.app.persistence.UiState;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The chooser's remembered state in {@link UiState}: recent and pinned folders, the preview pane and dotfiles. Every change saves. UI thread. */
public final class ChooserPrefs {
    static final int RECENT_LIMIT = 10;
    private final UiState state;

    public ChooserPrefs(UiState state) { this.state = Objects.requireNonNull(state); }

    public List<Path> recent() { return paths(state.chooser().recent()); }
    public List<Path> pinned() { return paths(state.chooser().pinned()); }
    public boolean preview() { return state.chooser().preview(); }
    public boolean showHidden() { return state.chooser().showHidden(); }
    public boolean isPinned(Path folder) { return pinned().contains(normal(folder)); }

    /** Moves {@code folder} to the front of Recent, which keeps ten. */
    public void recordRecent(Path folder) {
        String value = normal(folder).toString();
        var recent = new ArrayList<String>();
        recent.add(value);
        state.chooser().recent().stream().filter(other -> !other.equals(value)).limit(RECENT_LIMIT - 1).forEach(recent::add);
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(recent, current.pinned(), current.preview(), current.showHidden()));
    }

    public void pin(Path folder) {
        if (isPinned(folder)) return;
        UiState.Chooser current = state.chooser();
        var pinned = new ArrayList<>(current.pinned());
        pinned.add(normal(folder).toString());
        update(new UiState.Chooser(current.recent(), pinned, current.preview(), current.showHidden()));
    }

    public void unpin(Path folder) {
        String value = normal(folder).toString();
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned().stream().filter(other -> !other.equals(value)).toList(),
            current.preview(), current.showHidden()));
    }

    public void setPreview(boolean value) {
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned(), value, current.showHidden()));
    }

    public void setShowHidden(boolean value) {
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned(), current.preview(), value));
    }

    private void update(UiState.Chooser value) {
        state.setChooser(value);
        state.save();
    }

    private static Path normal(Path path) { return path.toAbsolutePath().normalize(); }

    private static List<Path> paths(List<String> values) {
        var result = new ArrayList<Path>();
        for (String value : values) {
            try {
                Path path = Path.of(value);
                if (path.isAbsolute()) result.add(path.normalize());
            } catch (InvalidPathException ignored) {
                // A path from another platform or a damaged file is skipped, not an error.
            }
        }
        return List.copyOf(result);
    }
}
