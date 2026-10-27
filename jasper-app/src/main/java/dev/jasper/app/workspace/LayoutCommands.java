package dev.jasper.app.workspace;

import dev.jasper.app.persistence.LayoutsFile;
import dev.jasper.app.persistence.WorkspaceFile;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Window › Layouts for one window (full windows plan 4): applying the default or a named layout to it, saving it as a new
 * layout or as the default, renaming and deleting a layout and resetting the default. The questions go through the
 * window's {@link WindowContent#askLayoutName} and {@link WindowContent#confirmLayout}; what goes wrong is said through
 * its {@code onError}, and the tabs a saved default leaves out through its {@code onInfo}. Nothing happens in a window outside a window manager. EDT only.
 */
final class LayoutCommands {
    /** The name dialog's question. */
    static final String NAME = "Layout name:";
    /** The name a new layout is offered. */
    static final String FIRST_NAME = "Custom";
    /** What a window says of a layout {@code layouts.toml} has no room for. */
    static final String TOO_LARGE = "This layout is too large to save.";

    /** Asks for a layout's name. */
    @FunctionalInterface interface NameQuestion {
        /**
         * @param title the dialog's title
         * @param message its question
         * @param initial the name it offers
         * @return what was typed, or null when cancelled
         */
        String ask(String title, String message, String initial);
    }

    /** Asks before a layout is deleted or the default reset. */
    @FunctionalInterface interface Confirmation {
        /**
         * @param title the dialog's title
         * @param message its question
         * @param button the button that goes ahead; the other cancels
         * @return whether {@code button} was chosen
         */
        boolean ask(String title, String message, String button);
    }

    private final WindowContent owner;

    LayoutCommands(WindowContent owner) { this.owner = owner; }

    private Optional<WindowManager> manager() { return owner.manager(); }

    private Optional<JasperWindow> window() { return manager().flatMap(manager -> manager.windowOf(owner)); }

    private Optional<LayoutStore> store() { return manager().flatMap(WindowManager::layoutStore); }

    /** Whether this window can take a layout: a window manager's open window. */
    boolean applicable() { return !owner.closed() && window().isPresent(); }

    /** Whether this window's layout can be saved: {@link #applicable()}, with the saved layouts. */
    boolean savable() { return applicable() && store().isPresent(); }

    /** Whether a default layout is saved, so that there is one to reset. */
    boolean defaultSaved() { return store().flatMap(LayoutStore::defaultLayout).isPresent(); }

    /**
     * Whether Reset Default Layout is offered: {@link #savable()}, and a default is saved or {@code layouts.toml} is still
     * unread, which this never reads, so a window's first actions do not read it on the way to the first window.
     */
    boolean resettable() {
        return savable() && store().map(store -> !store.read() || store.defaultLayout().isPresent()).orElse(false);
    }

    /** The named layouts' names, in order; none without the saved layouts. */
    List<String> names() {
        return store().map(store -> store.named().stream().map(LayoutsFile.Named::name).toList()).orElse(List.of());
    }

    /** Default: the default layout replaces this window's contents, after its tabs' guards. */
    void applyDefault() {
        var manager = manager();
        if (manager.isEmpty() || !applicable()) return;
        apply(manager.get().defaultLayout(null), "the default layout");
    }

    /** Apply: the layout {@code name} replaces this window's contents, after its tabs' guards; nothing without one. */
    void apply(String name) {
        if (!applicable()) return;
        store().flatMap(store -> store.named().stream().filter(named -> named.name().equals(name)).findFirst())
            .ifPresent(named -> apply(named.layout(), "the layout “" + name + "”"));
    }

    private void apply(WorkspaceFile.WindowRecord layout, String what) {
        JasperWindow window = window().orElseThrow();
        if (manager().orElseThrow().applyLayout(window, layout) == WindowManager.LayoutApplied.FAILED)
            owner.onError.accept("Layout not applied", "Jasper could not apply " + what + ".");
    }

    /**
     * Save Current Layout as New…: asks for a name, first offering "Custom", and saves this window's layout under it;
     * the question says how many tabs a layout leaves out.
     */
    void saveAsNew() {
        if (!savable()) return;
        WindowManager manager = manager().orElseThrow();
        JasperWindow window = window().orElseThrow();
        Optional<WorkspaceFile.WindowRecord> layout = manager.layoutOf(window);
        if (layout.isEmpty()) return;
        int leftOut = manager.leftOutCount(window);
        String note = leftOut > 0 ? "\n" + leftOut(leftOut) : "";
        LayoutStore store = store().orElseThrow();
        askName("New Layout", FIRST_NAME, note, name -> store.saveAsNew(name, layout.get()));
    }

    /**
     * Save Current Layout as Default: this window's layout becomes what a new window opens; a notice after the save says
     * how many tabs a layout leaves out.
     */
    void saveAsDefault() {
        if (!savable()) return;
        WindowManager manager = manager().orElseThrow();
        JasperWindow window = window().orElseThrow();
        manager.layoutOf(window).ifPresent(layout -> {
            int leftOut = manager.leftOutCount(window);
            switch (store().orElseThrow().saveAsDefault(layout)) {
                case TOO_LARGE -> tooLarge();
                case SAVED -> { if (leftOut > 0) owner.onInfo.accept("Default layout saved", leftOut(leftOut)); }
                case NAME_IN_USE, MISSING -> { }
            }
        });
    }

    /** What a layout of a window says of the {@code count} tabs it leaves out. */
    static String leftOut(int count) { return count + " tab(s) can't be kept in a layout and are left out."; }

    /** Reset Default Layout: asks, then forgets the saved default, so that new windows open the built-in layout. */
    void resetDefault() {
        Optional<LayoutStore> store = store();
        if (store.isEmpty() || !defaultSaved()) return;
        if (owner.confirmLayout.ask("Reset Default Layout", "Reset the default layout to the built-in layout?", "Reset"))
            store.get().resetDefault();
    }

    /** Rename…: asks for the layout {@code name}'s new name, as Save Current Layout as New… does. */
    void rename(String name) {
        Optional<LayoutStore> store = store();
        if (store.isEmpty() || !names().contains(name)) return;
        askName("Rename Layout", name, "", to -> to.equals(name) ? LayoutStore.Saved.SAVED : store.get().rename(name, to));
    }

    /** Delete…: asks, then deletes the layout {@code name}. */
    void delete(String name) {
        Optional<LayoutStore> store = store();
        if (store.isEmpty() || !names().contains(name)) return;
        if (owner.confirmLayout.ask("Delete Layout", "Delete the layout “" + name + "”?", "Delete"))
            store.get().delete(name);
    }

    /**
     * Asks for a name until {@code save} takes one or the question is cancelled: a blank or too long name, or one in use,
     * is asked again with the reason above the question.
     */
    private void askName(String title, String initial, String note, Function<String, LayoutStore.Saved> save) {
        String reason = "";
        String offered = initial;
        while (true) {
            String typed = owner.askLayoutName.ask(title, reason + NAME + note, offered);
            if (typed == null) return;
            Optional<String> name = LayoutStore.validName(typed);
            if (name.isEmpty()) {
                reason = "A layout name has 1 to " + LayoutsFile.MAX_NAME + " characters.\n";
                offered = typed;
                continue;
            }
            switch (save.apply(name.get())) {
                case SAVED, MISSING -> { return; }
                case TOO_LARGE -> { tooLarge(); return; }
                case NAME_IN_USE -> {
                    reason = "A layout named “" + name.get() + "” already exists.\n";
                    offered = name.get();
                }
            }
        }
    }

    private void tooLarge() { owner.onError.accept("Layout not saved", TOO_LARGE); }
}
