package dev.jasper.app.themestore;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settingsui.FillingPage;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Settings &gt; Themes &gt; IntelliJ Themes: the {@code themes.intellij} schema page (its settings) on top, a banner, and the
 * Marketplace and Installed tabs. Installs and uninstalls apply at once; only the header's settings wait for Apply.
 * SettingsDialog owns each page; disposing it drops late results. EDT only.
 */
final class IntelliJThemesPage implements SettingsPage {
    static final String MARKETPLACE = "marketplace";
    static final String INSTALLED = "installed";

    private final ThemeStore store;
    private final PageContext context;
    private final SettingsPage header;
    private FillingPage body;
    private JTabbedPane tabs;
    private Banner banner;
    private InstalledTab installed;
    private MarketplaceTab marketplace;
    private Subscription draftListening;
    private boolean revealing;

    IntelliJThemesPage(ThemeStore store, PageContext context, SettingsPage header) {
        this.store = store;
        this.context = context;
        this.header = header;
    }

    @Override public String id() { return header.id(); }
    @Override public String title() { return header.title(); }
    @Override public Optional<String> parent() { return header.parent(); }
    @Override public String helpAnchor() { return header.helpAnchor(); }

    @Override public List<String> searchTerms() {
        var terms = new ArrayList<>(header.searchTerms());
        terms.add("IntelliJ Themes Marketplace Installed install uninstall update theme plugins jar zip");
        return List.copyOf(terms);
    }

    @Override public JComponent component() {
        if (body != null) return body;
        banner = new Banner();
        installed = new InstalledTab(store, context, banner, this::use);
        marketplace = new MarketplaceTab(store, banner, installed::installedVersions, this::use, installed::loadList);
        installed.onChanged(marketplace::installedChanged);
        tabs = new JTabbedPane();
        tabs.addTab("Marketplace", marketplace.component());
        tabs.addTab("Installed", installed.component());
        tabs.addChangeListener(event -> { if (!revealing) activate(); });
        var top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header.component(), BorderLayout.NORTH);
        top.add(banner, BorderLayout.SOUTH);
        var panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(top, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        body = new FillingPage(panel);
        draftListening = context.draft().onChanged(this::syncTabs);
        syncTabs();
        return body;
    }

    @Override public void selected() {
        component();
        store.clearDownloads();
        activate();
    }

    @Override public void reveal(String fragment) {
        component();
        Component target = switch (fragment) {
            case MARKETPLACE -> marketplaceEnabled() ? marketplace.component() : installed.component();
            case INSTALLED -> installed.component();
            default -> null;
        };
        if (target == null || tabs.getSelectedComponent() == target) return;
        // The dialog calls selected() next, which loads the tab now shown; switching here must not load the other one.
        revealing = true;
        try { tabs.setSelectedComponent(target); } finally { revealing = false; }
    }

    @Override public boolean modified() { return header.modified(); }
    @Override public boolean valid() { return header.valid(); }
    @Override public void reset() { header.reset(); }
    @Override public CompletableFuture<Void> apply() { return header.apply(); }
    @Override public void highlight(String query) { header.highlight(query); }
    @Override public void refresh() { header.refresh(); }

    @Override public void dispose() {
        header.dispose();
        if (draftListening != null) draftListening.close();
        if (marketplace != null) marketplace.dispose();
        if (installed != null) installed.dispose();
    }

    private boolean marketplaceEnabled() { return context.draft().value(CoreSettings.THEMES_MARKETPLACE_ENABLED); }

    /** Shows the Marketplace tab only while the setting (as edited) allows it. */
    private void syncTabs() {
        if (tabs == null) return;
        boolean enabled = marketplaceEnabled();
        // First, so a tab change below that loads the Installed tab already knows not to ask the Marketplace.
        installed.marketplaceEnabled(enabled);
        int index = tabs.indexOfComponent(marketplace.component());
        if (enabled && index < 0) tabs.insertTab("Marketplace", null, marketplace.component(), null, 0);
        if (!enabled && index >= 0) tabs.removeTabAt(index);
    }

    /** Loads what the visible tab shows: the Marketplace's first search, or the installed themes and their updates. */
    private void activate() {
        if (tabs.getSelectedComponent() == marketplace.component()) {
            installed.loadList();
            marketplace.activate();
        } else installed.reload();
    }

    /** Chooses {@code theme} on the Appearance page's setting; it switches on Apply or OK. */
    private void use(String theme) {
        context.draft().stage(CoreSettings.UI_THEME_NAME, Optional.of(theme));
        banner.info("\u201c" + theme + "\u201d is chosen. Apply or OK switches to it.");
    }

    // Test access.
    List<String> tabTitles() { var titles = new ArrayList<String>(); for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i)); return titles; }
    String selectedTab() { return tabs.getTitleAt(tabs.getSelectedIndex()); }
    Banner banner() { return banner; }
    InstalledTab installed() { return installed; }
    MarketplaceTab marketplace() { return marketplace; }
}
