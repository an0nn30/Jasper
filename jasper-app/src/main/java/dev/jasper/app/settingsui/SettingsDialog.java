package dev.jasper.app.settingsui;

import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Component;
import java.awt.Dimension;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import javax.swing.SwingUtilities;

/**
 * The Settings dialog: a modal, singleton surface with a page tree, search, back and forward, banners and
 * Apply/OK/Cancel. It owns the pages it creates for each opening and disposes them when it closes. EDT only.
 */
public final class SettingsDialog {
    /** The configuration guide; a page's help anchor follows the {@code #}. */
    public static final String HELP_URL = "https://github.com/an0nn30/Jasper/blob/main/docs/configuration.md#";

    /**
     * What the dialog needs from the application.
     *
     * @param save saves core settings; completes on any thread
     * @param state the configuration state now
     * @param onState subscribes to later states
     * @param openConfigFile opens config.toml in the user's editor
     * @param migrate runs Migrate Config
     * @param browse opens a web page
     * @param confirmDiscard asks, over a component, whether to discard that many unsaved changes
     * @param kit the application's chrome kit context, read on the EDT each time the dialog opens
     * @param chrome the look's Settings projection (spec 12.1)
     * @param composition how the look composes the app's pages (spec 12.2), handed to every page
     */
    public record Hooks(Function<SettingsEdit, CompletableFuture<ConfigService.State>> save, Supplier<ConfigService.State> state,
                        Function<Consumer<ConfigService.State>, Subscription> onState, Runnable openConfigFile, Runnable migrate,
                        Consumer<URI> browse, BiPredicate<Component, Integer> confirmDiscard, Supplier<KitContext> kit,
                        SettingsChrome chrome, Composition composition) {
        public Hooks {
            Objects.requireNonNull(save); Objects.requireNonNull(state); Objects.requireNonNull(onState);
            Objects.requireNonNull(openConfigFile); Objects.requireNonNull(migrate); Objects.requireNonNull(browse);
            Objects.requireNonNull(confirmDiscard); Objects.requireNonNull(kit);
            Objects.requireNonNull(chrome); Objects.requireNonNull(composition);
        }

        /** Hooks under the default look on this host ({@link PageContext#standard()}). */
        public Hooks(Function<SettingsEdit, CompletableFuture<ConfigService.State>> save, Supplier<ConfigService.State> state,
                     Function<Consumer<ConfigService.State>, Subscription> onState, Runnable openConfigFile, Runnable migrate,
                     Consumer<URI> browse, BiPredicate<Component, Integer> confirmDiscard, Supplier<KitContext> kit) {
            this(save, state, onState, openConfigFile, migrate, browse, confirmDiscard, kit,
                PageContext.standard().settings(), PageContext.standard().composition());
        }
    }

    private final AuxiliaryWindows windows;
    private final Function<PageContext, List<SettingsPage>> pageFactory;
    private final Hooks hooks;
    private final Deque<String> backStack = new ArrayDeque<>();
    private final Deque<String> forwardStack = new ArrayDeque<>();
    private AuxiliarySurface surface;
    private SettingsView view;
    private SettingsDraft draft;
    private List<SettingsPage> pages = List.of();
    private Subscription draftListening;
    private Subscription stateListening;
    private String current;
    private String lastPage;
    private String query = "";
    private boolean rejected;
    private boolean applying;
    private Runnable bannerAction = () -> { };
    /** The chrome kit context of the current opening. */
    private KitContext kit;

    /** Reads the installed font families in the background, so the first Settings opening and its font lists are quick. */
    public static void prepareFonts() { Fonts.prepare(); }

    public SettingsDialog(AuxiliaryWindows windows, Function<PageContext, List<SettingsPage>> pages, Hooks hooks) {
        this.windows = Objects.requireNonNull(windows);
        this.pageFactory = Objects.requireNonNull(pages);
        this.hooks = Objects.requireNonNull(hooks);
    }

    /**
     * Asks over Settings, with a kit sheet, whether to discard that many unsaved changes: the application's
     * {@code confirmDiscard}. Discard is the primary answer (Enter); Keep Editing, Escape and closing the sheet keep the
     * edits. The sheet is modal, so this returns once answered; focus then goes back to Settings.
     */
    public boolean askDiscard(Component parent, Integer count) {
        if (!isOpen() || kit == null) return true;
        String changes = count == 1 ? "1 unsaved change" : count + " unsaved changes";
        boolean[] discard = {false};
        var sheet = windows.dialog("Discard Changes", true, surface);
        var sheetView = new KitView(kit, new ViewSpec(new Nodes.Form("discard-sheet", ControlState.READY, Nodes.FormKind.SHEET,
            "Discard changes?", Optional.empty(),
            List.of(new Nodes.Text("discard-message", ControlState.READY, "Discard " + changes + "?", Nodes.TextRole.BODY)), Optional.empty(),
            List.of(new Nodes.Button("keep", ControlState.READY, "Keep Editing", "", Nodes.ButtonKind.SECONDARY, "keep", ""),
                new Nodes.Button("discard", ControlState.READY, "Discard", "", Nodes.ButtonKind.PRIMARY, "discard", "")), "keep")),
            event -> {
                if (!(event instanceof UiEvent.Action action)) return;
                if (action.id().equals("discard")) discard[0] = true;
                if (action.id().equals("discard") || action.id().equals("keep")) sheet.close();
            });
        sheet.onClosed(sheetView::close);
        sheet.setContent(sheetView);
        sheet.show();
        sheet.close();
        if (!discard[0] && isOpen()) surface.toFront();
        return discard[0];
    }

    /** Re-reads what every page shows from the application, for state that changed outside the configuration. */
    public void refreshPages() {
        if (!isOpen()) return;
        pages.forEach(SettingsPage::refresh);
        refreshNow();
    }

    /** Asks a yes-or-no question in a sheet over the dialog; false when the dialog is not open. */
    public boolean confirm(String title, String message, String confirmLabel) {
        if (!isOpen() || kit == null) return false;
        boolean[] confirmed = {false};
        var sheet = windows.dialog(title, true, surface);
        var sheetView = new KitView(kit, new ViewSpec(new Nodes.Form("confirm-sheet", ControlState.READY, Nodes.FormKind.SHEET,
            title, Optional.empty(),
            List.of(new Nodes.Text("confirm-message", ControlState.READY, message, Nodes.TextRole.BODY)), Optional.empty(),
            List.of(new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("confirm", ControlState.READY, confirmLabel, "", Nodes.ButtonKind.PRIMARY, "confirm", "")), "cancel")),
            event -> {
                if (!(event instanceof UiEvent.Action action)) return;
                if (action.id().equals("confirm")) confirmed[0] = true;
                if (action.id().equals("confirm") || action.id().equals("cancel")) sheet.close();
            });
        sheet.onClosed(sheetView::close);
        sheet.setContent(sheetView);
        sheet.show();
        sheet.close();
        if (isOpen()) surface.toFront();
        return confirmed[0];
    }

    public boolean isOpen() { return surface != null && !surface.closed(); }

    /**
     * Shows Settings over {@code ownerOrNull} (no owner when null) at {@code pageOrNull} (the last page visited when null).
     * When it is already open, it comes forward and selects the page. Showing blocks until it closes.
     */
    public void open(UUID ownerOrNull, String pageOrNull) {
        if (isOpen()) {
            if (pageOrNull != null) select(pageOrNull);
            surface.toFront();
            return;
        }
        ConfigService.State state = hooks.state().get();
        draft = new SettingsDraft(state.values(), state.defaults());
        kit = hooks.kit().get();
        view = new SettingsView(kit, new dev.jasper.ui.swing.SettingsShell.Frame(hooks.chrome().integratedTitle(), hooks.chrome().collapsibleNav()),
            this::event, this::cancel);
        view.onEnter(this::ok);
        pages = List.copyOf(pageFactory.apply(new PageContext(draft, this::select, this::closeThen, this::chooseFile, this::refreshNow,
            () -> isOpen() ? surface : null, this::kit, hooks.chrome(), hooks.composition())));
        backStack.clear();
        forwardStack.clear();
        current = null;
        query = "";
        surface = windows.modalDialog("app.settings", "Settings", new Dimension(1000, 700), ownerOrNull);
        surface.setContent(view);
        // The shell's sidebar and header reach the window's top and are its title row; a look with a native title (spec
        // 12.1) keeps the window's "Settings" title above the shell.
        if (hooks.chrome().integratedTitle()) surface.integratedTitle(view::titleRowHeight);
        surface.onClosing(this::mayClose);
        surface.onClosed(this::closed);
        draftListening = draft.onChanged(this::refreshNow);
        stateListening = hooks.onState().apply(this::accept);
        accept(state);
        view.tree.collapsible(hooks.chrome().collapsibleNav());
        view.tree.show(pages, page -> true, Map.of());
        view.tree();
        select(pageOrNull != null ? pageOrNull : lastPage != null ? lastPage : CorePages.DEFAULT_PAGE);
        surface.show();
    }

    /** Routes the shell's kit events: search, tree navigation and the header, banner and footer buttons. */
    private void event(UiEvent event) {
        if (!isOpen() && surface != null) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(SettingsView.SEARCH) -> search(edited.text());
            case UiEvent.Selected selected when selected.id().equals(PageTree.ID) -> selected.ids().stream().findFirst().ifPresent(this::select);
            case UiEvent.Expanded expanded when expanded.id().equals(PageTree.ID) -> { view.tree.expanded(expanded.itemId(), expanded.expanded()); view.tree(); }
            case UiEvent.Action action -> {
                switch (action.id()) {
                    case SettingsView.BACK -> back();
                    case SettingsView.FORWARD -> forward();
                    case SettingsView.APPLY -> apply();
                    case SettingsView.OK -> ok();
                    case SettingsView.CANCEL -> cancel();
                    case SettingsView.HELP -> page(current).ifPresent(page -> hooks.browse().accept(URI.create(HELP_URL + page.helpAnchor())));
                    case SettingsView.BANNER_ACTION -> bannerAction.run();
                    default -> { }
                }
            }
            default -> { }
        }
    }

    SettingsView view() { return view; }
    /** The look's Settings projection this dialog runs with. */
    SettingsChrome chrome() { return hooks.chrome(); }
    private KitContext kit() { return kit; }
    SettingsDraft draft() { return draft; }
    List<SettingsPage> pages() { return pages; }
    String currentPage() { return current; }

    private Optional<SettingsPage> page(String id) {
        return pages.stream().filter(page -> page.id().equals(id)).findFirst();
    }

    /**
     * Selects a page, remembering the one it leaves for Back. For {@code id#fragment} the page first reveals that part,
     * then is shown (or, already current, told it is selected again), so it loads only what the fragment names.
     */
    void select(String id) {
        int hash = id.indexOf('#');
        String base = hash < 0 ? id : id.substring(0, hash);
        var target = page(base).or(() -> pages.stream().findFirst()).orElseThrow();
        boolean revealing = hash >= 0 && target.id().equals(base);
        if (revealing) target.reveal(id.substring(hash + 1));
        if (!target.id().equals(current)) {
            if (current != null) { backStack.push(current); forwardStack.clear(); }
            show(target);
        } else if (revealing) {
            target.selected();
            refreshNow();
        }
    }

    void back() {
        if (backStack.isEmpty()) return;
        forwardStack.push(current);
        show(page(backStack.pop()).orElseThrow());
    }

    void forward() {
        if (forwardStack.isEmpty()) return;
        backStack.push(current);
        show(page(forwardStack.pop()).orElseThrow());
    }

    private void show(SettingsPage page) {
        current = page.id();
        lastPage = page.id();
        view.page(page.component());
        view.breadcrumb(breadcrumb(page));
        view.tree.reveal(page.id());
        view.tree.select(page.id());
        view.tree();
        highlight(page);
        page.selected();
        refreshNow();
    }

    private void highlight(SettingsPage page) {
        String search = query;
        view.highlight(() -> page.highlight(search), !search.isBlank());
    }

    private String breadcrumb(SettingsPage page) {
        var titles = new ArrayDeque<String>();
        for (Optional<SettingsPage> at = Optional.of(page); at.isPresent(); at = at.get().parent().flatMap(this::page))
            titles.push(at.get().title());
        return String.join(" › ", titles);
    }

    /** Filters the tree to pages matching {@code text}, with match counts, and highlights the visible page. */
    void search(String text) {
        query = text == null ? "" : text;
        if (!view.query().equals(query)) view.query(query);
        if (query.isBlank()) {
            view.tree.show(pages, page -> true, Map.of());
            if (current != null) view.tree.select(current);
            view.tree();
        } else {
            var counts = new LinkedHashMap<String, Integer>();
            for (SettingsPage page : pages) {
                int matches = (int) page.searchTerms().stream().filter(term -> SearchText.matches(term, query)).count();
                if (matches > 0) counts.put(page.id(), matches);
            }
            view.tree.show(pages, page -> counts.containsKey(page.id()), counts);
            view.tree();
            if (!counts.containsKey(current) && !counts.isEmpty()) {
                select(counts.keySet().iterator().next());
                return;
            }
            if (current != null) view.tree.select(current);
            view.tree();
        }
        page(current).ifPresent(this::highlight);
    }

    private boolean modified() { return draft.modified() || pages.stream().anyMatch(SettingsPage::modified); }

    private int pendingCount() {
        return draft.size() + (int) pages.stream().filter(page -> appliedAfterDraft(page) && page.modified()).count();
    }

    /** Pages the dialog applies after the draft: every page but a schema page, which applies only its external rows. */
    private static boolean appliedAfterDraft(SettingsPage page) {
        return !(page instanceof SchemaPage schema) || schema.externalModified();
    }

    private boolean valid() { return pages.stream().allMatch(SettingsPage::valid); }

    /** Re-evaluates the modified marks and the buttons. */
    void refreshNow() {
        if (view == null) return;
        view.tree.modifiedBy(id -> page(id).map(SettingsPage::modified).orElse(false));
        view.tree();
        boolean blocked = rejected || !valid() || applying;
        view.buttons(!backStack.isEmpty(), !forwardStack.isEmpty(), modified() && !blocked, !blocked);
    }

    private void accept(ConfigService.State state) {
        rejected = state.rejected();
        if (!rejected) draft.rebase(state.values());
        if (rejected) banner("config.toml has errors, so changes can't be saved until they're fixed.", "Open config.toml", hooks.openConfigFile());
        else if (state.migratable()) banner("config.toml uses old setting names.", "Migrate Config…", hooks.migrate());
        else banner(null, null, () -> { });
        pages.forEach(SettingsPage::refresh);
        refreshNow();
    }

    private void banner(String text, String button, Runnable action) {
        bannerAction = action;
        view.banner(text, button);
    }

    /** Saves the draft, then every other modified page in tree order; stops at the first failure. */
    void apply() { apply(() -> { }); }

    void ok() {
        if (rejected || !valid() || applying) return;
        if (!modified()) { surface.close(); return; }
        apply(() -> { if (isOpen()) surface.close(); });
    }

    void cancel() { if (isOpen()) surface.requestClose(); }

    private void apply(Runnable done) {
        if (applying || rejected || !valid() || !modified()) return;
        applying = true;
        view.message("");
        refreshNow();
        CompletableFuture<ConfigService.State> core = draft.modified() ? hooks.save().apply(draft.edit()) : CompletableFuture.completedFuture(null);
        core.whenComplete((state, failure) -> SwingUtilities.invokeLater(() -> {
            if (!isOpen()) return;
            if (failure != null) { failed(null, failure); return; }
            if (state != null) draft.rebase(state.values());
            var remaining = new ArrayDeque<SettingsPage>();
            for (SettingsPage page : pages) if (appliedAfterDraft(page) && page.modified()) remaining.add(page);
            applyPages(remaining, done);
        }));
    }

    private void applyPages(Deque<SettingsPage> remaining, Runnable done) {
        SettingsPage next = remaining.poll();
        if (next == null) {
            applying = false;
            refreshNow();
            done.run();
            return;
        }
        next.apply().whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (!isOpen()) return;
            if (failure != null) { failed(next, failure); return; }
            applyPages(remaining, done);
        }));
    }

    private void failed(SettingsPage page, Throwable failure) {
        applying = false;
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        view.message((page == null ? "" : page.title() + ": ") + cause.getMessage());
        refreshNow();
    }

    private boolean mayClose() {
        if (!modified()) return true;
        return hooks.confirmDiscard().test(view, pendingCount());
    }

    private void closeThen(Runnable action) {
        if (!isOpen() || surface.requestClose()) action.run();
    }

    private Optional<Path> chooseFile(String title, Optional<Path> initial) {
        return isOpen() && surface.shown() ? surface.chooseFile(title, initial) : Optional.empty();
    }

    private void closed() {
        if (draftListening != null) draftListening.close();
        if (stateListening != null) stateListening.close();
        pages.forEach(SettingsPage::dispose);
        pages = List.of();
        if (view != null) view.close();
        surface = null;
    }
}
