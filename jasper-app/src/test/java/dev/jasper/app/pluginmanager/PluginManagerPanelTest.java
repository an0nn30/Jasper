package dev.jasper.app.pluginmanager;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.ThemeRoute;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class PluginManagerPanelTest {
    private final List<String> events = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private final List<URI> browsed = new ArrayList<>();
    private final PluginManagerPanel.Handlers handlers = new PluginManagerPanel.Handlers(
        row -> events.add("toggle:" + row.id()), row -> events.add("review:" + row.id()), row -> events.add("remove:" + row.id()),
        row -> events.add("discard:" + row.id()), () -> events.add("install"),
        row -> opened.add("settings:" + row.id()), row -> opened.add("folder:" + row.id()), row -> opened.add("data:" + row.id()),
        browsed::add);
    private PluginManagerPanel panel;

    /** Kit views are EDT confined; the extension runs only the test methods there. */
    @BeforeEach void open() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> panel = new PluginManagerPanel(KitTestSupport.context(), handlers)); }

    @AfterEach void close() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> panel.close()); }

    private static PluginRuntime.Snapshot snapshot(PluginRuntime.Row... rows) { return new PluginRuntime.Snapshot(List.of(rows), true, false); }

    private JButton primary() { return PluginKit.button(panel.detailsPane().header(), PluginDetails.PRIMARY); }

    @Test void actualSourceCopyUsesTwoReadableOverviewLinesAtTheSourceAllocation() throws Exception {
        var themes=new dev.jasper.app.appearance.ThemeController();
        themes.configure(dev.jasper.app.appearance.Theme.DARK,dev.jasper.app.config.UiFontConfig.defaults());
        var actual=new PluginManagerPanel(themes.appearance().context(ThemeRoute.CHROME),handlers);
        try {
            String copy="Accounts, SSH keys and secure notes in one encrypted file bound to this device; other plugins fetch credentials under per-plugin grants.";
            actual.show(snapshot(TestRows.plugin("dev.jasper.vault","Credential Vault").description(copy).row()));
            actual.setSize(744,582);for(int i=0;i<4;i++)KitTestSupport.layout(actual);
            var area=actual.detailsPane().textArea();
            var first=area.modelToView2D(0);var last=area.modelToView2D(copy.length()-1);
            assertThat(last.getY()-first.getY()).as("same literal source description occupies two lines at744logical content allocation")
                .isLessThanOrEqualTo(area.getFontMetrics(area.getFont()).getHeight());
            assertThat(area.getText()).isEqualTo(copy);
        }finally{actual.close();themes.close();}
    }

    @Test void nativeDescriptionUsesExactlyOnceKitFontAndRetainsItsDocument() {
        var themes=new dev.jasper.app.appearance.ThemeController();
        themes.configure(dev.jasper.app.appearance.Theme.DARK,dev.jasper.app.config.UiFontConfig.defaults());
        var actual=new PluginManagerPanel(themes.appearance().context(ThemeRoute.CHROME),handlers);
        try {
            actual.show(snapshot(TestRows.plugin("a.font","Font fixture").description("Literal plugin overview").row()));
            var text=actual.detailsPane().textArea();var document=text.getDocument();text.select(1,4);
            for(float size:List.of(20f,32f,13f)) {
                themes.configure(dev.jasper.app.appearance.Theme.DARK,new dev.jasper.app.config.UiFontConfig(size==32?"Serif":"system",size));
                if(size==32)assertThat(text.getFont().getFamily()).isEqualTo(java.awt.Font.SERIF);
                assertThat(text.getFont().getSize2D()).isEqualTo(size);
                assertThat(text.getDocument()).isSameAs(document);assertThat(text.getSelectedText()).isEqualTo("ite");
            }
        }finally{actual.close();themes.configure(dev.jasper.app.appearance.Theme.DARK,dev.jasper.app.config.UiFontConfig.defaults());themes.close();}
    }

    @Test void narrowViewportPreservesReadableDetailAndRevealsFocusedActionsBothWays() {
        panel.show(snapshot(TestRows.plugin("a.art","Authored").row()));
        panel.setSize(com.formdev.flatlaf.util.UIScale.scale(744),600);KitTestSupport.layout(panel);
        var page=new dev.jasper.app.settingsui.FillingPage(panel);
        var scroller=new javax.swing.JScrollPane(page);scroller.setSize(427,534);
        for(int i=0;i<3;i++)KitTestSupport.layout(scroller);
        var header=panel.detailsPane().header();var primary=primary();
        var overview=KitTestSupport.require(header,"section.option.overview",AbstractButton.class);
        for(String id:List.of("name","vendor","version","state")) {
            var label=KitTestSupport.require(header,id,JLabel.class);
            assertThat(label.getWidth()).as("readable identity "+id).isGreaterThan(0);
        }
        assertThat(overview.getWidth()).as("unelided section label at constrained viewport")
            .isGreaterThanOrEqualTo(overview.getFontMetrics(overview.getFont()).stringWidth(overview.getText()));
        assertThat(primary.getWidth()).isGreaterThanOrEqualTo(primary.getPreferredSize().width);
        var document=panel.detailsPane().textArea().getDocument();
        header.reveal(PluginDetails.PRIMARY);
        var bounds=javax.swing.SwingUtilities.convertRectangle(primary.getParent(),primary.getBounds(),page);
        assertThat(scroller.getViewport().getViewRect().contains(bounds)).isTrue();
        var search=PluginKit.field(panel.searchView(),PluginManagerPanel.SEARCH);
        panel.searchView().reveal(PluginManagerPanel.SEARCH);
        assertThat(scroller.getViewport().getViewRect().contains(javax.swing.SwingUtilities.convertRectangle(search.getParent(),search.getBounds(),page))).isTrue();
        var focus=new java.awt.event.FocusEvent(primary,java.awt.event.FocusEvent.FOCUS_GAINED,false,null,java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD);
        for(var listener:primary.getFocusListeners())listener.focusGained(focus);
        assertThat(scroller.getViewport().getViewRect().contains(bounds)).as("Tab focus reveals detail action").isTrue();
        panel.show(snapshot(TestRows.plugin("a.art","Authored").row()));
        assertThat(panel.detailsPane().textArea().getDocument()).isSameAs(document);
        panel.searchView().reveal(PluginManagerPanel.SEARCH);
        assertThat(scroller.getViewport().getViewRect().contains(javax.swing.SwingUtilities.convertRectangle(search.getParent(),search.getBounds(),page))).isTrue();
    }
    @Test void actualSplitDividerPaintsAtItsAllocatedBoundary() {
        panel.show(snapshot(TestRows.plugin("a.art","Authored").row()));
        panel.setSize(744,600);KitTestSupport.layout(panel);
        var split=panel.split();var ui=(javax.swing.plaf.basic.BasicSplitPaneUI)split.getUI();var divider=ui.getDivider();
        assertThat(divider.getWidth()).isGreaterThan(0);
        assertThat(divider.getX()).isEqualTo(split.getLeftComponent().getWidth());
        for(boolean print:new boolean[]{false,true})for(int orientation:new int[]{javax.swing.JSplitPane.HORIZONTAL_SPLIT,javax.swing.JSplitPane.VERTICAL_SPLIT}) {
            split.setOrientation(orientation);split.setDividerLocation(200);split.doLayout();
            var image=new java.awt.image.BufferedImage(split.getWidth(),split.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics=image.createGraphics();if(print)split.printAll(graphics);else split.paint(graphics);graphics.dispose();
            int x=orientation==javax.swing.JSplitPane.HORIZONTAL_SPLIT?divider.getX():split.getWidth()-20;
            int y=orientation==javax.swing.JSplitPane.VERTICAL_SPLIT?divider.getY():split.getHeight()-20;
            int dx=orientation==javax.swing.JSplitPane.HORIZONTAL_SPLIT?3:0,dy=orientation==javax.swing.JSplitPane.VERTICAL_SPLIT?3:0;
            assertThat(image.getRGB(x,y)).as("allocated divider; print="+print+"; bounds="+divider.getBounds()).isNotEqualTo(image.getRGB(x+dx,y+dy));
        }
    }
    @Test void actualHeroReceivesManifestArtworkAndSearchStaysInListLane() {
        var art=new PluginRuntime.PluginIcon("art.svg",PluginIconsTest.SVG.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        panel.show(snapshot(TestRows.plugin("a.art","Authored").icon(art).row()));
        panel.setSize(744,600);KitTestSupport.layout(panel);
        var icon=KitTestSupport.find(panel,"icon",JLabel.class);
        assertThat(icon).as("shared receiving icon replaces private canvas").isNotNull();
        assertThat(icon.getWidth()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(54));
        var search=PluginKit.field(panel.searchView(),PluginManagerPanel.SEARCH);
        var point=javax.swing.SwingUtilities.convertPoint(search,search.getWidth(),0,panel);
        var detail=javax.swing.SwingUtilities.convertPoint(panel.detailsPane().component(),0,0,panel);
        assertThat(point.x).isLessThanOrEqualTo(detail.x);
    }

    @Test void sameByteHueRefreshChangesTheActualHeaderWithoutRecoloringItsGlyph() {
        var bytes=PluginIconsTest.SVG.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.util.function.Function<Nodes.Hue,int[]> render=hue->{
            panel.show(snapshot(TestRows.plugin("external.art","Authored").icon(new PluginRuntime.PluginIcon("art.svg",bytes,hue)).row()));
            panel.setSize(744,600);KitTestSupport.layout(panel);
            return PluginIconsTest.pixels(KitTestSupport.find(panel,"icon",JLabel.class).getIcon());
        };
        var blue=render.apply(Nodes.Hue.BLUE);var purple=render.apply(Nodes.Hue.PURPLE);
        assertThat(blue).isNotEqualTo(purple);assertThat(blue[blue.length/2+(int)Math.sqrt(blue.length)/2]).isEqualTo(purple[purple.length/2+(int)Math.sqrt(purple.length)/2]);
    }
    @Test void thePrimaryButtonAndTheMenuFollowTheSelectedPlugin() {
        panel.show(snapshot(
            TestRows.plugin("dev.example.active", "Active").errors(2).capabilities(List.of("terminal.inject", "made.up"))
                .requires(List.of("dev.example.base >=1.0.0")).exports(List.of("dev.example.active.api")).row(),
            TestRows.plugin("dev.example.bundled", "Bundled One").origin("Bundled")
                .state("SKIPPED", "requires dev.example.base, which is not available").row(),
            TestRows.plugin("dev.example.found", "Found").needsReview("terminal.inject").row(),
            TestRows.plugin("dev.example.doomed", "Doomed").removing().row(),
            TestRows.plugin("dev.example.staged", "Staged").installing().row()));
        panel.select("dev.example.active");
        assertThat(primary().getText()).isEqualTo("Disable");
        assertThat(panel.menuLabels()).containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder", "-", "Remove…");
        assertThat(PluginKit.menuItems(panel.detailsPane().header(), PluginDetails.MORE)).extracting(AbstractButton::getText)
            .as("the mounted More actions menu").containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder", "Remove…");
        assertThat(panel.details()).contains("Active", "1.0.0", "Example", "Does things", "Type into your terminals (terminal.inject)",
            "made.up", "dev.example.base >=1.0.0", "dev.example.active.api", "2 errors", java.nio.file.Path.of("/plugins/dev.example.active/data").toString());
        panel.select("dev.example.bundled");
        assertThat(primary().getText()).isEqualTo("Disable");
        assertThat(panel.menuLabels()).containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder");
        assertThat(panel.details()).contains("requires dev.example.base, which is not available", "Bundled with Jasper");
        panel.select("dev.example.found");
        assertThat(primary().getText()).isEqualTo("Review…");
        panel.select("dev.example.doomed");
        assertThat(primary().isVisible()).isFalse();
        assertThat(panel.menuLabels()).endsWith("Keep");
        panel.select("dev.example.staged");
        assertThat(panel.menuLabels()).endsWith("Discard Install");

        panel.select("dev.example.found");
        primary().doClick(0);
        panel.select("dev.example.active");
        primary().doClick(0);
        PluginKit.clickMenu(panel, "Remove…");
        panel.select("dev.example.staged");
        PluginKit.clickMenu(panel, "Discard Install");
        PluginKit.clickMenu(panel, "Open Settings");
        PluginKit.clickMenu(panel, "Open Plugin Folder");
        PluginKit.clickMenu(panel, "Open Data Folder");
        assertThat(panel.gearLabels()).containsExactly("Install from Zip…");
        PluginKit.clickGear(panel, "Install from Zip…");
        assertThat(events).as("each press acts exactly once").containsExactly("review:dev.example.found", "toggle:dev.example.active", "remove:dev.example.active",
            "discard:dev.example.staged", "install");
        assertThat(opened).containsExactly("settings:dev.example.staged", "folder:dev.example.staged", "data:dev.example.staged");
        assertThat(KitTestSupport.require(panel.gearView(), PluginManagerPanel.GEAR + ".main", JButton.class).getAccessibleContext().getAccessibleName())
            .isEqualTo("Plugin options");
        assertThat(KitTestSupport.require(panel.detailsPane().header(), PluginDetails.MORE + ".main", JButton.class).getAccessibleContext().getAccessibleName())
            .isEqualTo("More actions");
    }

    @Test void aRowsSwitchReviewsOrTogglesItsMenuActsOnItAndTheListIsGroupedAndSearchable() {
        panel.show(snapshot(TestRows.plugin("dev.example.found", "Found").needsReview("terminal.inject").row(),
            TestRows.plugin("dev.example.on", "On").row(), TestRows.plugin("dev.jasper.remote", "Remote").origin("Bundled").row()));
        assertThat(panel.groupTitles()).as("a plugin waiting for review is not enabled yet")
            .containsExactly("User-installed (1 of 2 enabled)", "Bundled (1 of 1 enabled)");
        assertThat(PluginKit.toggle(panel, "dev.example.found").isSelected()).isFalse();
        PluginKit.toggle(panel, "dev.example.found").doClick(0);
        PluginKit.toggle(panel, "dev.example.on").doClick(0);
        assertThat(events).containsExactly("review:dev.example.found", "toggle:dev.example.on");
        // A right click on a row that is not selected acts on that row, and the details keep showing the selection.
        String before = panel.selected();
        PluginKit.choose(PluginKit.rowMenu(panel, "dev.jasper.remote"), "Open Plugin Folder");
        assertThat(opened).containsExactly("folder:dev.jasper.remote");
        assertThat(panel.selected()).isEqualTo(before);
        PluginKit.field(panel.searchView(), PluginManagerPanel.SEARCH).setText("remote");
        assertThat(panel.listed()).containsExactly("Remote  1.0.0");
        assertThat(panel.selected()).isEqualTo("dev.jasper.remote");
        assertThat(PluginKit.field(panel.searchView(), PluginManagerPanel.SEARCH).getAccessibleContext().getAccessibleName())
            .isEqualTo("Search plugins");
    }

    @Test void theHomepageLinkShowsOnlyForAnHttpOrHttpsAddress() {
        panel.show(snapshot(TestRows.plugin("a.web", "Web").homepage("https://example.dev/web").row(),
            TestRows.plugin("a.ftp", "Ftp").homepage("ftp://example.dev").row(), TestRows.plugin("a.none", "None").row()));
        panel.select("a.web");
        KitTestSupport.require(panel.detailsPane().header(),"section.option.details",javax.swing.AbstractButton.class).doClick(0);
        assertThat(panel.homepageShown()).isTrue();
        assertThat(PluginKit.button(panel.detailsPane().header(), PluginDetails.HOMEPAGE).isVisible()).isTrue();
        PluginKit.press(panel.detailsPane().header(), PluginDetails.HOMEPAGE);
        assertThat(browsed).containsExactly(URI.create("https://example.dev/web"));
        panel.select("a.ftp");
        assertThat(panel.homepageShown()).isFalse();
        assertThat(PluginKit.button(panel.detailsPane().header(), PluginDetails.HOMEPAGE).isVisible()).isFalse();
        panel.select("a.none");
        assertThat(panel.homepageShown()).isFalse();
    }

    @Test void theOverviewAndDetailsChoiceSwapsTheOneTextArea() {
        panel.show(snapshot(TestRows.plugin("a.quiet", "Quiet").description("").row()));
        var choice = (Nodes.Choice) find(panel.detailsPane().header().spec().root(), PluginDetails.SECTION);
        assertThat(choice.kind()).isEqualTo(Nodes.ChoiceKind.SEGMENTED);
        assertThat(choice.options()).extracting(dev.jasper.ui.model.Option::label).containsExactly("Overview", "Details");
        assertThat(choice.selectedId()).isEqualTo(PluginDetails.OVERVIEW);
        assertThat(panel.detailsPane().shownText()).isEqualTo("No description.");
        PluginKit.press(panel.detailsPane().header(), PluginDetails.SECTION + ".option." + PluginDetails.DETAILS);
        assertThat(panel.detailsPane().section()).isEqualTo(PluginDetails.DETAILS);
        assertThat(panel.detailsPane().shownText()).contains("Capabilities", "None", "Requires", "Exports", "Locations",
            java.nio.file.Path.of("/plugins/a.quiet/a.quiet.toml").toString());
        assertThat(panel.details()).contains("No description.", "Capabilities", "None", "Requires", "Exports", "Locations",
            java.nio.file.Path.of("/plugins/a.quiet/a.quiet.toml").toString());
        assertThat(panel.detailsPane().textArea().isEditable()).as("read-only, still selectable").isFalse();
    }

    static Nodes.Node find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        for (var child : switch (node) { case Nodes.Stack stack -> stack.children(); case Nodes.Card card -> card.children(); default -> List.<Nodes.Node>of(); }) {
            var found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }

    @Test void busyDisablesEveryControlAndTheSelectionSurvivesARefresh() {
        var rows = new PluginRuntime.Row[]{TestRows.plugin("dev.example.a", "A").row(), TestRows.plugin("dev.example.b", "B").row()};
        panel.show(snapshot(rows));
        panel.select("dev.example.b");
        panel.show(snapshot(rows));
        assertThat(panel.selected()).isEqualTo("dev.example.b");
        panel.busy(true);
        assertThat(primary().isEnabled() || PluginKit.toggle(panel, "dev.example.a").isEnabled()
            || PluginKit.menuItems(panel.gearView(), PluginManagerPanel.GEAR).getFirst().isEnabled()).isFalse();
        PluginKit.clickGear(panel, "Install from Zip…");
        PluginKit.choose(PluginKit.rowMenu(panel, "dev.example.a"), "Open Plugin Folder");
        PluginKit.toggle(panel, "dev.example.a").doClick(0);
        PluginKit.key(panel, "SPACE");
        assertThat(events).as("nothing runs while busy").isEmpty();
        assertThat(opened).isEmpty();
        panel.busy(false);
        assertThat(primary().isEnabled() && PluginKit.toggle(panel, "dev.example.a").isEnabled()
            && PluginKit.menuItems(panel.gearView(), PluginManagerPanel.GEAR).getFirst().isEnabled()).isTrue();
        panel.show(snapshot());
        assertThat(panel.selected()).isNull();
        assertThat(panel.details()).isEqualTo("No plugins installed");
    }

    @Test void pluginTextIsNeverMarkup() {
        panel.show(snapshot(TestRows.plugin("a.h", "<html><b>Bold").vendor("<html><i>Acme").row()));
        var name = KitTestSupport.require(PluginKit.laidOut(panel), "name:a.h", JLabel.class);
        assertThat(name.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(KitTestSupport.require(panel.detailsPane().header(), "vendor", JLabel.class).getText()).isEqualTo("<html><i>Acme");
        assertThat(KitTestSupport.require(panel.detailsPane().header(), "vendor", JLabel.class).getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(panel.details()).contains("<html><b>Bold", "<html><i>Acme");
    }

    @Test void theBannerTheNoticeAndTheMessageAreIndependent() {
        List<String> clicks = new ArrayList<>();
        assertThat(panel.bannerText()).isEmpty();
        assertThat(KitTestSupport.require(PluginKit.laidOut(panel.headerView()), "banner", javax.swing.JComponent.class).isVisible()).isFalse();
        panel.banner("Restart Jasper to apply your changes.", List.of(new PluginManagerPanel.BannerAction("Restart Now", () -> clicks.add("restart"))));
        assertThat(panel.bannerText()).isEqualTo("Restart Jasper to apply your changes.");
        assertThat(panel.bannerLabels()).containsExactly("Restart Now");
        assertThat(PluginKit.texts(panel.headerView())).contains("Restart Jasper to apply your changes.");
        assertThat(PluginKit.button(panel.headerView(), PluginManagerPanel.BANNER + 0).getText()).isEqualTo("Restart Now");
        PluginKit.press(panel.headerView(), PluginManagerPanel.BANNER + 0);
        assertThat(clicks).containsExactly("restart");
        panel.notice("Another Jasper process is still running.");
        panel.message("The zip holds no jar files", true);
        assertThat(panel.noticeText()).isEqualTo("Another Jasper process is still running.");
        assertThat(panel.messageText()).isEqualTo("The zip holds no jar files");
        assertThat(panel.messageIsError()).isTrue();
        assertThat(KitTestSupport.require(PluginKit.laidOut(panel.messageView()), "message-dot", javax.swing.JComponent.class).isVisible()).isTrue();
        panel.message("Installed.", false);
        assertThat(KitTestSupport.require(PluginKit.laidOut(panel.messageView()), "message-dot", javax.swing.JComponent.class).isVisible()).isFalse();
        panel.banner("", List.of());
        assertThat(panel.bannerText()).isEmpty();
        assertThat(panel.bannerLabels()).isEmpty();
        assertThat(KitTestSupport.require(PluginKit.laidOut(panel.headerView()), "banner", javax.swing.JComponent.class).isVisible()).isFalse();
        assertThat(panel.noticeText()).isNotEmpty();
    }

    @Test void theConsentViewSaysWhatIsAskedAndThatItIsNoSandbox() {
        List<String> decisions = new ArrayList<>();
        var view = new ConsentView(KitTestSupport.context(), "<html>Tool", "2.0.0", "Example", List.of("terminal.inject", "made.up"), true, "Install",
            () -> decisions.add("allow"), () -> decisions.add("cancel"));
        assertThat(view.text()).contains("<html>Tool 2.0.0", "Example", "replaces the installed version",
            "Type into your terminals (terminal.inject)", "made.up", "unrestricted code", "not a sandbox");
        assertThat(PluginKit.button(view.view(), ConsentView.ALLOW).getText()).isEqualTo("Install");
        assertThat(PluginKit.texts(view.view())).contains("<html>Tool 2.0.0");
        PluginKit.press(view.view(), ConsentView.ALLOW);
        PluginKit.press(view.view(), ConsentView.CANCEL);
        assertThat(decisions).containsExactly("allow", "cancel");
        view.close();
        var quiet = new ConsentView(KitTestSupport.context(), "Quiet", "1.0.0", "", List.of(), false, "Allow and Enable", () -> { }, () -> { });
        assertThat(quiet.text()).contains("asks for no access to your terminals").doesNotContain("replaces");
        quiet.close();
    }

    /** The sheets follow the dialog rules: Enter gives the primary answer, Escape cancels, a busy answer ignores Enter. */
    @Test void theSheetsAnswerEnterAndEscapeAndABusyRemovalIgnoresEnter() {
        List<String> decisions = new ArrayList<>();
        var confirm = new ConfirmView(KitTestSupport.context(), "Remove Tool 1.0?", "Its jars are deleted.", "Remove",
            () -> decisions.add("remove"), () -> decisions.add("cancel"));
        var consent = new ConsentView(KitTestSupport.context(), "Tool", "1.0", "", List.of(), false, "Allow and Enable",
            () -> decisions.add("allow"), () -> decisions.add("refuse"));
        try {
            key(confirm.view(), "confirm", "primary");
            key(confirm.view(), "confirm", "cancel");
            key(consent.view(), "consent", "primary");
            key(consent.view(), "consent", "cancel");
            assertThat(decisions).containsExactly("remove", "cancel", "allow", "refuse");
            confirm.busy(true);
            key(confirm.view(), "confirm", "primary");
            PluginKit.press(confirm.view(), ConfirmView.OK);
            assertThat(decisions).as("a busy answer cannot be given again").containsExactly("remove", "cancel", "allow", "refuse");
            assertThat(confirm.text()).isEqualTo("Remove Tool 1.0?\n\nIts jars are deleted.");
            assertThat(PluginKit.texts(confirm.view())).contains("Remove Tool 1.0?", "Its jars are deleted.");
        } finally {
            confirm.close();
            consent.close();
        }
    }

    private static void key(javax.swing.JComponent view, String form, String action) {
        var sheet = KitTestSupport.require(PluginKit.laidOut(view), form, javax.swing.JComponent.class);
        sheet.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(sheet, 0, ""));
    }

    /** Theme changes keep the search, the selection, the details choice, the text selection and the list's scroll. */
    @Test void aThemeSwitchKeepsSearchSelectionChoiceTextSelectionAndScroll() throws Exception {
        var bindings = KitTestSupport.bindings(true);
        var own = new PluginManagerPanel(bindings.context(ThemeRoute.CHROME), handlers);
        try {
            var rows = new ArrayList<PluginRuntime.Row>();
            for (int i = 0; i < 30; i++) rows.add(TestRows.plugin("dev.example.p" + (100 + i), "Plugin " + (100 + i)).description("Plugin number " + i).row());
            own.show(new PluginRuntime.Snapshot(rows, false, false));
            own.setSize(900, 400);
            PluginKit.laidOut(own);
            var field = PluginKit.field(own.searchView(), PluginManagerPanel.SEARCH);
            field.setText("Plugin 1");
            own.select("dev.example.p125");
            PluginKit.press(own.detailsPane().header(), PluginDetails.SECTION + ".option." + PluginDetails.DETAILS);
            JTextArea text = own.detailsPane().textArea();
            text.select(2, 9);
            PluginKit.laidOut(own);
            var scroll = (javax.swing.JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class,
                PluginKit.canvas(own.listView()));
            int offset = scroll.getVerticalScrollBar().getValue();
            assertThat(offset).as("the host-selected row was scrolled into view").isPositive();
            var listed = own.listed();
            var foreground = text.getForeground();
            bindings.binding().publish(dev.jasper.app.appearance.ThemeTestSupport.pair(false));
            PluginKit.laidOut(own);
            assertThat(field.getText()).isEqualTo("Plugin 1");
            assertThat(own.listed()).isEqualTo(listed);
            assertThat(own.selected()).isEqualTo("dev.example.p125");
            assertThat(PluginKit.list(own).selectedIds()).containsExactly("dev.example.p125");
            assertThat(own.detailsPane().section()).isEqualTo(PluginDetails.DETAILS);
            assertThat(text.getSelectionStart()).isEqualTo(2);
            assertThat(text.getSelectionEnd()).isEqualTo(9);
            assertThat(scroll.getVerticalScrollBar().getValue()).isEqualTo(offset);
            assertThat(text.getForeground()).as("the text area follows the theme").isNotEqualTo(foreground);
        } finally {
            own.close();
            bindings.close();
        }
    }
}
