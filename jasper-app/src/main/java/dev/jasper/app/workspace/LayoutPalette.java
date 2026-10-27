package dev.jasper.app.workspace;

import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.persistence.LayoutsFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The palette's Apply Layout: &lt;name&gt; entries (full windows plan 4): one contributed action per named layout, which
 * applies it to the window it runs in, registered again on every change of the saved layouts. An entry's id is
 * {@value #ID_PREFIX} and a digest of the layout's name, which a palette id can hold whatever the name; it has no dot, so
 * it is not a keymap id and takes no shortcut. EDT only.
 */
final class LayoutPalette {
    /** The start of every entry's id. */
    static final String ID_PREFIX = "layout-apply-";

    private final Contributions model;
    private final LayoutStore store;
    private final WindowManager manager;
    private final List<ActionEntry> entries = new ArrayList<>();

    private LayoutPalette(Contributions model, LayoutStore store, WindowManager manager) {
        this.model = model;
        this.store = store;
        this.manager = manager;
    }

    /** Lists {@code store}'s named layouts in {@code model}'s actions from now on. */
    static void follow(Contributions model, LayoutStore store, WindowManager manager) {
        var palette = new LayoutPalette(model, store, manager);
        store.onChanged(palette::sync);
        palette.sync();
    }

    /** The entry's id for the layout {@code name}. */
    static String id(String name) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ID_PREFIX + java.util.HexFormat.of().formatHex(digest, 0, 12);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Every JDK has SHA-256", impossible);
        }
    }

    private void sync() {
        entries.forEach(ActionEntry::close);
        entries.clear();
        for (LayoutsFile.Named named : store.named()) {
            String name = named.name();
            entries.add(model.addAction(id(name), "Apply Layout: " + name, null, List.of("layout", "apply", name),
                Optional.empty(), invocation -> manager.window(invocation.windowId())
                    .ifPresent(window -> window.content().applyLayout(name))));
        }
    }
}
