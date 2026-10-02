package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.themeimport.*;
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
            context = new PageContext(draft, s -> {}, Runnable::run, (t,p) -> Optional.ofNullable(chosen), () -> {}, () -> owner);
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
    static List<String> texts(Container root){var out=new ArrayList<String>();for(var c:root.getComponents()){if(c instanceof javax.swing.text.JTextComponent t)out.add(t.getText());if(c instanceof Container nested)out.addAll(texts(nested));}return out;}
    @Test void importUsesFrozenPlanAndBusyGatesDoubleClickWithoutConfigWrite() throws Exception {
        chosen=source("Example"); Files.writeString(home.resolve("config.toml"), "# untouched\n");
        edt(() -> { imports.importThemes(report::set); imports.importThemes(report::set); }); assertThat(worker.jobs).hasSize(1);
        next(); Files.delete(chosen); click("Import"); next();
        assertThat(report.get().imported()).extracting(ThemeImporter.Imported::name).containsExactly("Example");
        assertThat(Files.readString(home.resolve("config.toml"))).isEqualTo("# untouched\n"); assertThat(refreshes).hasValue(1);
        edt(() -> assertThat(imports.busy()).isFalse());
    }
    @Test void importThemeReportsWhatItImportedOverSettings() throws Exception {
        chosen=source("Example"); edt(imports::start); next(); click("Import"); next();
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Example")).isPresent();
        edt(() -> {
            var shown=windows.open().getLast();
            assertThat(shown).isNotEqualTo(owner);
            assertThat(texts(shown.holder())).anyMatch(text -> text.startsWith("Imported Example — "));
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
    @Test void theReportListsImportsWarningsAndFailuresOrSaysNothingHappened() {
        assertThat(ThemeImport.report(new ThemeImporter.ImportReport(List.of(), List.of(), List.of()))).isEqualTo("Nothing was imported.");
        assertThat(ThemeImport.report(new ThemeImporter.ImportReport(List.of(), List.of("A warning"), List.of()))).isEqualTo("A warning");
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
