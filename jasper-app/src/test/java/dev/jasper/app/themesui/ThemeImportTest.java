package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.*;
import dev.jasper.app.config.*;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.themeimport.*;
import dev.jasper.ui.themeimport.*;
import dev.jasper.app.windows.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ThemeImportTest {
    @TempDir Path home;
    final QueueWorker worker = new QueueWorker();
    AuxiliaryWindows windows; AuxiliarySurface owner; ThemeImport imports; Path chosen;
    final AtomicInteger refreshes = new AtomicInteger();
    final AtomicReference<ThemeImporter.ImportReport> report = new AtomicReference<>();
    final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
    Runnable afterRefresh = () -> {};
    PageContext context;
    @BeforeEach void start() throws Exception {
        edt(() -> {
            windows = new AuxiliaryWindows(UiState.inMemory(), s -> new AuxiliarySurface.Shell(() -> {}, () -> {}, () -> {}, t -> {}, () -> new Rectangle(0,0,10,10), (t,p) -> Optional.empty()));
            owner = windows.window("settings", "Settings", new Dimension(600,600), false); owner.show();
            context = new PageContext(draft, s -> {}, Runnable::run, (t,p) -> Optional.ofNullable(chosen), () -> {}, () -> owner, dev.jasper.app.testsupport.KitTestSupport::context);
            imports = new ThemeImport(context, home, windows, () -> { assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); refreshes.incrementAndGet(); afterRefresh.run(); return CompletableFuture.completedFuture(null); },
                worker);
        });
    }
    @AfterEach void stop() throws Exception { edt(() -> { imports.close(); windows.close(); }); }
    Path source(String name) throws Exception { Path p=home.resolve("input.toml"); Files.writeString(p, "name = \""+name+"\"\nextends = \"Jasper Dark\"\n"); return p; }
    void next() throws Exception { worker.next(); edt(() -> {}); edt(() -> {}); }
    static void edt(Runnable run) throws Exception { var thrown=new AtomicReference<Throwable>(); SwingUtilities.invokeAndWait(() -> {try{run.run();}catch(Throwable t){thrown.set(t);}}); if(thrown.get()!=null)throw new AssertionError(thrown.get()); }
    void click(String label) throws Exception { edt(() -> { var buttons = buttons(windows.open().getLast().holder()); buttons.stream().filter(b -> b.getText().equals(label)).findFirst().orElseThrow().doClick(); }); }
    static List<JButton> buttons(Container root){var out=new ArrayList<JButton>();for(var c:root.getComponents()){if(c instanceof JButton b)out.add(b);if(c instanceof Container nested)out.addAll(buttons(nested));}return out;}
    static List<String> texts(Container root){return dev.jasper.app.testsupport.KitTestSupport.texts(root);}
    /** Plan 2 Task 6c ruling G4: a long message flows over several lines instead of widening the sheet past 44 columns. */
    @Test void dialogAcceptRejectAndOwnerCloseRetainCancelOnceSemantics() throws Exception {
        edt(() -> {
            var cancelled = new AtomicInteger(); var dialogs = new ThemeDialogs(windows, () -> owner, dev.jasper.app.testsupport.KitTestSupport::context);
            Runnable[] accept = {null}, reject = {null};
            java.util.function.BiFunction<Runnable, Runnable, dev.jasper.ui.swing.KitView> builder = (yes, no) -> {
                accept[0] = yes; reject[0] = no;
                return new dev.jasper.ui.swing.KitView(dialogs.kit(), new dev.jasper.ui.model.ViewSpec(
                    new dev.jasper.ui.model.Nodes.Text("text", dev.jasper.ui.model.ControlState.READY, "Body", dev.jasper.ui.model.Nodes.TextRole.BODY)), event -> {});
            };
            dialogs.view("Accept", builder, cancelled::incrementAndGet); accept[0].run(); dialogs.close();
            assertThat(cancelled).hasValue(0); assertThat(windows.open()).containsExactly(owner);
            dialogs.view("Reject", builder, cancelled::incrementAndGet); reject[0].run(); reject[0].run(); dialogs.close();
            assertThat(cancelled).hasValue(1); assertThat(windows.open()).containsExactly(owner);
            dialogs.view("Owner", builder, cancelled::incrementAndGet); dialogs.close(); dialogs.close();
            assertThat(cancelled).hasValue(2); assertThat(windows.open()).containsExactly(owner);
        });
    }
    @Test void failingDialogBuilderClosesSurfaceAndCancelsExactlyOnce() throws Exception {
        edt(() -> {
            var cancelled = new AtomicInteger(); var failure = new IllegalArgumentException("builder");
            var dialogs = new ThemeDialogs(windows, () -> owner, dev.jasper.app.testsupport.KitTestSupport::context);
            assertThatThrownBy(() -> dialogs.view("Broken", (accept, reject) -> { throw failure; }, cancelled::incrementAndGet))
                .isSameAs(failure);
            assertThat(windows.open()).containsExactly(owner); assertThat(cancelled).hasValue(1);
            dialogs.close(); dialogs.close(); assertThat(cancelled).hasValue(1);
        });
    }
    @Test void aLongMessageWrapsInsideItsSheetAndKeepsItsCloseButton() throws Exception {
        String line = "The theme archive could not be read because one of its files is not a valid theme document.";
        String text = line + "\n\n" + line + " " + line;
        edt(() -> {
            var dialogs = new ThemeDialogs(windows, () -> owner, dev.jasper.app.testsupport.KitTestSupport::context);
            dialogs.message("Import failed", text);
            var surface = windows.open().getLast(); var sheet = surface.holder();
            var label = dev.jasper.app.testsupport.KitTestSupport.require(sheet, "message-text", JLabel.class);
            var metrics = label.getFontMetrics(label.getFont());
            int columns = metrics.charWidth('m') * dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.TEXT_FLOW_COLUMNS);
            assertThat(label.getPreferredSize().width).isLessThanOrEqualTo(columns);
            sheet.setSize(sheet.getPreferredSize()); dev.jasper.app.testsupport.KitTestSupport.layout(sheet);
            assertThat(label.getWidth()).isLessThanOrEqualTo(sheet.getWidth());
            int lineHeight=Math.max(metrics.getHeight(),Math.round(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.DESCRIPTION_LINE_HEIGHT)*dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D()/13f));
            int lines = label.getHeight() / lineHeight;
            assertThat(lines).as("both paragraphs, their break and the wrapped tail").isGreaterThanOrEqualTo(4);
            assertThat(label.getHeight() % lineHeight).isZero();
            var close = buttons(sheet).stream().filter(b -> b.getText().equals("Close")).findFirst().orElseThrow();
            assertThat(close.getWidth()).as("the action row is laid out").isPositive();
            assertThat(close.getY() + close.getHeight()).isLessThanOrEqualTo(sheet.getHeight());
            close.doClick();
            assertThat(surface.closed()).as("Close still closes the sheet").isTrue();
            dialogs.close();
        });
    }
    @Test void importUsesFrozenPlanAndBusyGatesDoubleClickWithoutConfigWrite() throws Exception {
        chosen=source("Example"); String config="ui.theme.name='Jasper Dark'\nui.theme.source='bundled'\nui.theme.follow_system=true\n";Files.writeString(home.resolve("config.toml"),config);
        edt(()->draft.rebase(dev.jasper.app.config.ConfigLoader.parse(home.resolve("config.toml"),config,true).values()));
        edt(() -> { imports.importThemes(report::set); imports.importThemes(report::set); }); assertThat(worker.jobs).hasSize(1);
        next(); Files.delete(chosen); click("Import"); next();
        assertThat(report.get().imported()).extracting(ThemeImporter.Imported::name).containsExactly("Example");
        assertThat(Files.readString(home.resolve("config.toml"))).isEqualTo(config);edt(()->{assertThat(draft.value(dev.jasper.app.config.CoreSettings.UI_THEME_FOLLOW_SYSTEM)).isTrue();assertThat(draft.value(dev.jasper.app.config.CoreSettings.UI_THEME_SOURCE)).isEqualTo(dev.jasper.app.config.ThemeSource.BUNDLED);assertThat(draft.modified()).isFalse();}); assertThat(refreshes).hasValue(1);
        edt(() -> assertThat(imports.busy()).isFalse());
    }
    @Test void importThemeReportsWhatItImportedOverSettings() throws Exception {
        chosen=source("Example"); edt(imports::start); next(); click("Import"); next();
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Example")).isPresent();
        edt(() -> {
            var shown=windows.open().getLast();
            assertThat(shown).isNotEqualTo(owner);
            assertThat(shown.title()).isEqualTo("Theme Imported");
            assertThat(texts(shown.holder())).contains("Theme Imported", "Imported", "Example")
                .anyMatch(text -> text.endsWith("Example.toml") || text.endsWith("example.toml"));
        });
    }
    @Test void cancellationAndClosingBeforeWorkNeverWriteOrResurrectDialogs() throws Exception {
        chosen=source("Example"); edt(() -> imports.importThemes(report::set)); next(); click("Cancel");
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Example")).isEmpty();
        edt(() -> { imports.importThemes(report::set); imports.close(); }); next();
        edt(() -> assertThat(windows.open()).containsExactly(owner)); assertThat(report).hasValue(null);
    }
    @Test void closingDuringCommittedRefreshSuppressesCallbackButPreservesFile() throws Exception {
        chosen=source("Example"); edt(() -> imports.importThemes(report::set)); next(); click("Import");
        afterRefresh=imports::close; next();
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Example")).isPresent();
        assertThat(refreshes).hasValue(1); assertThat(report).hasValue(null);
    }
    @Test void anUnsupportedFileIsRefusedWithoutWork() throws Exception {
        chosen=home.resolve("bad.exe"); edt(() -> {imports.importThemes(report::set);assertThat(imports.busy()).isFalse();}); assertThat(worker.jobs).isEmpty();
    }
    /** The report is a sheet of section labels over cards: imported rows, then literal warnings and failures. */
    @Test void theReportListsImportsWarningsAndFailuresOrSaysNothingHappened() {
        var nothing = new ThemeImporter.ImportReport(List.of(), List.of(), List.of());
        assertThat(ThemeImport.reportTitle(nothing)).isEqualTo("Nothing Imported");
        assertThat(ThemeImport.report(nothing)).singleElement().isInstanceOfSatisfying(dev.jasper.ui.model.Nodes.Text.class,
            text -> assertThat(text.text()).isEqualTo("Nothing was imported."));
        var warned = new ThemeImporter.ImportReport(List.of(), List.of("A warning"), List.of());
        assertThat(ThemeImport.report(warned)).singleElement().isInstanceOfSatisfying(dev.jasper.ui.model.Nodes.Section.class, section -> {
            assertThat(section.title()).isEqualTo("Warnings");
            assertThat(section.body()).isInstanceOfSatisfying(dev.jasper.ui.model.Nodes.Card.class, card -> assertThat(card.children())
                .singleElement().isInstanceOfSatisfying(dev.jasper.ui.model.Nodes.Text.class, text -> assertThat(text.text()).isEqualTo("A warning")));
        });
        var full = new ThemeImporter.ImportReport(List.of(new ThemeImporter.Imported("a", "Alpha", Path.of("themes", "alpha.toml")),
            new ThemeImporter.Imported("b", "Beta", Path.of("themes", "beta.toml"))), List.of("A warning"), List.of(new ThemeConverter.Failure("broken.json", "unreadable")));
        assertThat(ThemeImport.reportTitle(full)).isEqualTo("Themes Imported");
        var sections = ThemeImport.report(full);
        assertThat(sections).extracting(node -> ((dev.jasper.ui.model.Nodes.Section) node).title()).containsExactly("Imported", "Warnings", "Not Imported");
        var rows = ((dev.jasper.ui.model.Nodes.Card) ((dev.jasper.ui.model.Nodes.Section) sections.getFirst()).body()).children();
        assertThat(rows).extracting(row -> ((dev.jasper.ui.model.Nodes.FormRow) row).label()).containsExactly("Alpha", "Beta");
        assertThat(rows).extracting(row -> ((dev.jasper.ui.model.Nodes.Text) ((dev.jasper.ui.model.Nodes.FormRow) row).control()).text())
            .containsExactly(Path.of("themes", "alpha.toml").toString(), Path.of("themes", "beta.toml").toString());
        var failures = ((dev.jasper.ui.model.Nodes.Card) ((dev.jasper.ui.model.Nodes.Section) sections.getLast()).body()).children();
        assertThat(((dev.jasper.ui.model.Nodes.Text) failures.getFirst()).text()).isEqualTo("broken.json: unreadable");
    }
    static final class QueueWorker extends AbstractExecutorService {
        final ArrayDeque<Runnable> jobs=new ArrayDeque<>(); boolean shutdown;
        public void execute(Runnable r){if(shutdown)throw new RejectedExecutionException();jobs.add(r);}
        void next(){jobs.remove().run();}
        public void shutdown(){shutdown=true;} public List<Runnable> shutdownNow(){shutdown=true;return List.copyOf(jobs);}
        public boolean isShutdown(){return shutdown;} public boolean isTerminated(){return shutdown&&jobs.isEmpty();}
        public boolean awaitTermination(long time,TimeUnit unit){return isTerminated();}
    }
}
