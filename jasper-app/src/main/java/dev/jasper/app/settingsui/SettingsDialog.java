package dev.jasper.app.settingsui;

import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The Settings dialog: a modal, singleton surface with a page tree, search, back and forward, banners and
 * Apply/OK/Cancel. It owns the pages it creates for each opening and disposes them when it closes. EDT only.
 */
public final class SettingsDialog {
    /** The configuration guide; a page's help anchor follows the {@code #}. */
    public static final String HELP_URL = "https://github.com/an0nn30/moray/blob/main/docs/configuration.md#";

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
     */
    public record Hooks(Function<SettingsEdit, CompletableFuture<ConfigService.State>> save, Supplier<ConfigService.State> state,
                        Function<Consumer<ConfigService.State>, Subscription> onState, Runnable openConfigFile, Runnable migrate,
                        Consumer<URI> browse, BiPredicate<Component, Integer> confirmDiscard) {
        public Hooks {
            Objects.requireNonNull(save); Objects.requireNonNull(state); Objects.requireNonNull(onState);
            Objects.requireNonNull(openConfigFile); Objects.requireNonNull(migrate); Objects.requireNonNull(browse);
            Objects.requireNonNull(confirmDiscard);
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

    public SettingsDialog(AuxiliaryWindows windows, Function<PageContext, List<SettingsPage>> pages, Hooks hooks) {
        this.windows = Objects.requireNonNull(windows);
        this.pageFactory = Objects.requireNonNull(pages);
        this.hooks = Objects.requireNonNull(hooks);
    }

    /** Asks with a standard Yes/No dialog; the default {@code confirmDiscard}. */
    public static boolean askDiscard(Component parent, Integer count) {
        String changes = count == 1 ? "1 unsaved change" : count + " unsaved changes";
        return JOptionPane.showConfirmDialog(parent, "Discard " + changes + "?", "Settings", JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
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
        draft = new SettingsDraft(state.values());
        view = new SettingsView(this::select, this::cancel);
        pages = List.copyOf(pageFactory.apply(new PageContext(draft, this::select, this::closeThen, this::chooseFile, this::refreshNow,
            () -> isOpen() ? surface : null)));
        backStack.clear();
        forwardStack.clear();
        current = null;
        query = "";
        surface = windows.modalDialog("app.settings", "Settings", new Dimension(1000, 700), ownerOrNull);
        surface.setContent(view);
        surface.onClosing(this::mayClose);
        surface.onClosed(this::closed);
        wire();
        draftListening = draft.onChanged(this::refreshNow);
        stateListening = hooks.onState().apply(this::accept);
        accept(state);
        view.tree.show(pages, page -> true, Map.of(), false);
        select(pageOrNull != null ? pageOrNull : lastPage != null ? lastPage : CorePages.DEFAULT_PAGE);
        surface.show();
    }

    private void wire() {
        view.back.addActionListener(event -> back());
        view.forward.addActionListener(event -> forward());
        view.apply.addActionListener(event -> apply());
        view.ok.addActionListener(event -> ok());
        view.cancel.addActionListener(event -> cancel());
        view.help.addActionListener(event -> page(current).ifPresent(page -> hooks.browse().accept(URI.create(HELP_URL + page.helpAnchor()))));
        view.search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { search(view.search.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { search(view.search.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { search(view.search.getText()); }
        });
    }

    SettingsView view() { return view; }
    SettingsDraft draft() { return draft; }
    List<SettingsPage> pages() { return pages; }
    String currentPage() { return current; }

    private Optional<SettingsPage> page(String id) {
        return pages.stream().filter(page -> page.id().equals(id)).findFirst();
    }

    /** Selects a page, remembering the one it leaves for Back; {@code id#fragment} then reveals that part of the page. */
    void select(String id) {
        int hash = id.indexOf('#');
        String base = hash < 0 ? id : id.substring(0, hash);
        var target = page(base).or(() -> pages.stream().findFirst()).orElseThrow();
        if (!target.id().equals(current)) {
            if (current != null) { backStack.push(current); forwardStack.clear(); }
            show(target);
        }
        if (hash >= 0 && target.id().equals(base)) target.reveal(id.substring(hash + 1));
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
        view.breadcrumb.setText(breadcrumb(page));
        view.tree.select(page.id());
        page.highlight(query);
        page.selected();
        refreshNow();
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
        if (query.isBlank()) {
            view.tree.show(pages, page -> true, Map.of(), false);
            if (current != null) view.tree.select(current);
        } else {
            var counts = new LinkedHashMap<String, Integer>();
            for (SettingsPage page : pages) {
                int matches = (int) page.searchTerms().stream().filter(term -> SearchText.matches(term, query)).count();
                if (matches > 0) counts.put(page.id(), matches);
            }
            view.tree.show(pages, page -> counts.containsKey(page.id()), counts, true);
            if (!counts.containsKey(current) && !counts.isEmpty()) {
                select(counts.keySet().iterator().next());
                return;
            }
            if (current != null) view.tree.select(current);
        }
        page(current).ifPresent(page -> page.highlight(query));
    }

    private boolean modified() { return draft.modified() || pages.stream().anyMatch(SettingsPage::modified); }

    private int pendingCount() {
        return draft.size() + (int) pages.stream().filter(page -> !(page instanceof SchemaPage) && page.modified()).count();
    }

    private boolean valid() { return pages.stream().allMatch(SettingsPage::valid); }

    /** Re-evaluates the modified marks and the buttons. */
    void refreshNow() {
        if (view == null) return;
        view.tree.modifiedBy(id -> page(id).map(SettingsPage::modified).orElse(false));
        boolean blocked = rejected || !valid() || applying;
        view.apply.setEnabled(modified() && !blocked);
        view.ok.setEnabled(!blocked);
        view.back.setEnabled(!backStack.isEmpty());
        view.forward.setEnabled(!forwardStack.isEmpty());
    }

    private void accept(ConfigService.State state) {
        rejected = state.rejected();
        if (!rejected) draft.rebase(state.values());
        if (rejected) view.banner("config.toml has errors, so changes can't be saved until they're fixed.", "Open config.toml", hooks.openConfigFile());
        else if (state.migratable()) view.banner("config.toml uses old setting names.", "Migrate Config…", hooks.migrate());
        else view.banner(null, null, null);
        pages.forEach(SettingsPage::refresh);
        refreshNow();
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
        view.message.setText("");
        refreshNow();
        CompletableFuture<ConfigService.State> core = draft.modified() ? hooks.save().apply(draft.edit()) : CompletableFuture.completedFuture(null);
        core.whenComplete((state, failure) -> SwingUtilities.invokeLater(() -> {
            if (!isOpen()) return;
            if (failure != null) { failed(null, failure); return; }
            if (state != null) draft.rebase(state.values());
            var remaining = new ArrayDeque<SettingsPage>();
            for (SettingsPage page : pages) if (!(page instanceof SchemaPage) && page.modified()) remaining.add(page);
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
        view.message.setText((page == null ? "" : page.title() + ": ") + cause.getMessage());
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
        surface = null;
    }
}
