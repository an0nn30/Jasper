package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.terminal.config.TerminalOptions;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.*;

/** Draft-bound native theme choices. SettingsDialog alone saves the shared draft. EDT only. */
public final class ThemesPage implements SettingsPage {
    public static final String ID = "themes";
    private final PageContext context;
    private final Supplier<ThemeCatalog.Snapshot> catalog;
    private final Supplier<TerminalOptions> terminalOptions;
    private final Subscription listening;
    private JComponent component;
    private boolean disposed;
    ThemePicker ui;
    ThemePicker workspace;
    final JTextArea diagnostics = new JTextArea();
    ThemePreview preview;
    private boolean previewDark = true;

    public ThemesPage(PageContext context, Supplier<ThemeCatalog.Snapshot> catalog, Supplier<TerminalOptions> terminalOptions) {
        this.context = context; this.catalog = catalog; this.terminalOptions = terminalOptions;
        listening = context.draft().onChanged(this::refresh);
    }
    @Override public String id() { return ID; }
    @Override public String title() { return "Themes"; }
    @Override public Optional<String> parent() { return Optional.of("appearance-behavior"); }
    @Override public List<String> searchTerms() {
        return List.of("theme", "colour scheme", "palette", "alacritty", "intellij", "marketplace", "syntax", "workspace", "UI theme", "Workspace theme");
    }
    @Override public JComponent component() {
        if (disposed) throw new IllegalStateException("Themes page is disposed");
        if (component != null) return component;
        ui = new ThemePicker(false, name -> context.draft().stage(CoreSettings.UI_THEME_NAME, Optional.of(name)));
        workspace = new ThemePicker(true, name -> context.draft().stage(CoreSettings.UI_THEME_WORKSPACE, Optional.of(name)));
        var choices = new JPanel(new GridLayout(0, 1, 0, 12)); choices.add(ui); choices.add(workspace);
        var body = new JPanel(new BorderLayout(0, 12)); body.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        body.add(choices, BorderLayout.NORTH); preview = new ThemePreview(terminalOptions.get()); body.add(preview.component(), BorderLayout.CENTER);
        diagnostics.setEditable(false); diagnostics.setLineWrap(true); diagnostics.setWrapStyleWord(true); diagnostics.setOpaque(false);
        diagnostics.getAccessibleContext().setAccessibleName("Theme problems"); body.add(diagnostics, BorderLayout.SOUTH);
        component = new JScrollPane(body); refresh(); return component;
    }
    @Override public boolean modified() { return false; }
    @Override public void reset() { context.draft().reset(List.of(CoreSettings.UI_THEME_NAME, CoreSettings.UI_THEME_WORKSPACE)); }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void refresh() {
        if (disposed || component == null) return;
        var snapshot = catalog.get();
        ui.update(snapshot, context.draft().value(CoreSettings.UI_THEME_NAME));
        workspace.update(snapshot, context.draft().value(CoreSettings.UI_THEME_WORKSPACE));
        var chromeTheme = snapshot.find(ui.requested()).orElse(previewDark ? Theme.DARK : Theme.JASPER_LIGHT);
        var chrome = ThemeManager.generated(chromeTheme); previewDark = chrome.dark();
        var work = workspace.requested().equalsIgnoreCase("match") ? chrome
            : snapshot.find(workspace.requested()).map(ThemeManager::generated).orElse(chrome);
        preview.show(new ResolvedTheme(chrome, work));
        diagnostics.setText(String.join("\n", snapshot.problems().stream().map(problem -> problem.file().getFileName()
            + (problem.line() > 0 ? ":" + problem.line() : "") + ": " + problem.message()).toList()));
    }
    @Override public void reveal(String fragment) {
        if (disposed) return; component();
        if (fragment.equals("ui")) ui.reveal(); else if (fragment.equals("workspace")) workspace.reveal();
    }
    @Override public void highlight(String query) { if (component != null && !disposed) { ui.highlight(query); workspace.highlight(query); } }
    @Override public String helpAnchor() { return "theme"; }
    @Override public void dispose() { if (disposed) return; disposed = true; listening.close(); if (preview != null) preview.close(); }
}
