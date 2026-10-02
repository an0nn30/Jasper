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
    private final ThemeActions actions;
    private ThemePicker active;
    private Theme removalChecked;
    private boolean removable;
    final JButton duplicate = new JButton("Duplicate…");
    final JButton remove = new JButton("Remove…");
    private final JButton importThemes = new JButton("Import…");
    private final JButton folder = new JButton("Open themes folder");
    private final JButton about = new JButton("About bundled themes");
    final JTextArea report = new JTextArea();
    private JComponent component;
    private boolean disposed;
    ThemePicker ui;
    ThemePicker workspace;
    final JTextArea diagnostics = new JTextArea();
    ThemePreview preview;
    private boolean previewDark = true;

    public ThemesPage(PageContext context, Supplier<ThemeCatalog.Snapshot> catalog, Supplier<TerminalOptions> terminalOptions, ThemeActions actions) {
        this.context = context; this.catalog = catalog; this.terminalOptions = terminalOptions; this.actions = java.util.Objects.requireNonNull(actions); actions.changed = this::updateActions;
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
        ui = new ThemePicker(false, name -> { active = ui; context.draft().stage(CoreSettings.UI_THEME_NAME, Optional.of(name)); });
        workspace = new ThemePicker(true, name -> { active = workspace; context.draft().stage(CoreSettings.UI_THEME_WORKSPACE, Optional.of(name)); });
        active = ui; watchFocus(ui); watchFocus(workspace);
        var choices = new JPanel(new GridLayout(0, 1, 0, 12)); choices.add(ui); choices.add(workspace);
        var body = new JPanel(new BorderLayout(0, 12)); body.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        body.add(choices, BorderLayout.NORTH); preview = new ThemePreview(terminalOptions.get()); body.add(preview.component(), BorderLayout.CENTER);
        diagnostics.setEditable(false); diagnostics.setLineWrap(true); diagnostics.setWrapStyleWord(true); diagnostics.setOpaque(false);
        diagnostics.getAccessibleContext().setAccessibleName("Theme problems");
        var footer = new JPanel(); footer.setLayout(new BoxLayout(footer,BoxLayout.Y_AXIS));
        var buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
        for (var button : List.of(importThemes,duplicate,folder,remove,about)) buttons.add(button);
        footer.add(buttons); footer.add(ThemePicker.literal("File changes happen immediately. Theme choices apply with Apply or OK."));
        report.setEditable(false); report.setLineWrap(true); report.setWrapStyleWord(true); report.setOpaque(false);
        footer.add(report); footer.add(diagnostics); body.add(footer, BorderLayout.SOUTH);
        importThemes.addActionListener(e -> { report.setText(""); actions.importThemes(result -> {
            var lines = new java.util.ArrayList<String>();
            result.imported().forEach(item -> lines.add("Imported " + item.name() + " — " + item.path()));
            lines.addAll(result.warnings()); result.failures().forEach(f -> lines.add(f.sourceEntry() + ": " + f.message()));
            report.setText(String.join("\n",lines)); refresh();
        }); });
        duplicate.addActionListener(e -> duplicate());
        remove.addActionListener(e -> selectedTheme().ifPresent(theme -> {report.setText(""); actions.remove(theme,message -> {report.setText(message);refresh();});}));
        folder.addActionListener(e -> {report.setText("");actions.openFolder(report::setText);});
        about.addActionListener(e -> {report.setText("");actions.about();});
        component = new JScrollPane(body); refresh(); return component;
    }
    private void watchFocus(ThemePicker picker) {
        var listener = new java.awt.event.FocusAdapter(){@Override public void focusGained(java.awt.event.FocusEvent e){active=picker;updateActions();}};
        for(var widget:List.of(picker.list,picker.query,picker.tone,picker.source))widget.addFocusListener(listener);
    }
    private Optional<Theme> selectedTheme(){return active==null || active==workspace && active.requested().equalsIgnoreCase("match") ? Optional.empty() : catalog.get().find(active.requested());}
    private void updateActions(){
        if(disposed || active==null)return;
        var selected=selectedTheme(); boolean enabled=!actions.busy();
        duplicate.setEnabled(enabled&&selected.isPresent());
        Theme candidate = selected.filter(t -> t.source() instanceof Theme.UserFile).orElse(null);
        if (candidate != removalChecked) {
            removalChecked = candidate; removable = false;
            if (candidate != null) actions.checkRemoval(candidate, valid -> {
                if (!disposed && removalChecked == candidate) { removable = valid; updateActions(); }
            });
        }
        remove.setEnabled(enabled && removable && candidate != null);
        importThemes.setEnabled(enabled); folder.setEnabled(enabled); about.setEnabled(enabled);
    }
    private void duplicate(){
        var selected=selectedTheme(); if(selected.isEmpty())return;
        var picker=active; String captured=picker.requested(); var theme=selected.get(); report.setText("");
        actions.duplicate(theme,ThemeManager.generated(theme),name->{
            if(disposed)return;
            if(picker.requested().equals(captured))context.draft().stage(picker==ui?CoreSettings.UI_THEME_NAME:CoreSettings.UI_THEME_WORKSPACE,Optional.of(name));
            report.setText("Created "+name+".");refresh();
        });
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
        preview.show(new ResolvedTheme(chrome, work)); updateActions();
        diagnostics.setText(String.join("\n", snapshot.problems().stream().map(problem -> problem.file().getFileName()
            + (problem.line() > 0 ? ":" + problem.line() : "") + ": " + problem.message()).toList()));
    }
    @Override public void reveal(String fragment) {
        if (disposed) return; component();
        if (fragment.equals("ui")) {active=ui;ui.reveal();} else if (fragment.equals("workspace")) {active=workspace;workspace.reveal();} updateActions();
    }
    @Override public void highlight(String query) { if (component != null && !disposed) { ui.highlight(query); workspace.highlight(query); } }
    @Override public String helpAnchor() { return "theme"; }
    @Override public void dispose() { if (disposed) return; disposed = true; listening.close(); actions.close(); if (preview != null) preview.close(); }
}
