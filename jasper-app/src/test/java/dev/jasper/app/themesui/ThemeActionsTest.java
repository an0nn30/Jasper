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

class ThemeActionsTest {
    @TempDir Path home;
    final QueueWorker worker = new QueueWorker();
    AuxiliaryWindows windows; AuxiliarySurface owner; ThemeActions actions; Path chosen;
    final AtomicInteger refreshes = new AtomicInteger(); final List<Path> opened = new ArrayList<>();
    final AtomicReference<ThemeImporter.ImportReport> report = new AtomicReference<>();
    final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
    java.util.function.Predicate<Path> trash = p -> false;
    Runnable afterRefresh = () -> {};
    PageContext context;
    @BeforeEach void start() throws Exception {
        edt(() -> {
            windows = new AuxiliaryWindows(UiState.inMemory(), s -> new AuxiliarySurface.Shell(() -> {}, () -> {}, () -> {}, t -> {}, () -> new Rectangle(0,0,10,10), (t,p) -> Optional.empty()));
            owner = windows.window("settings", "Settings", new Dimension(600,600), false); owner.show();
            context = new PageContext(draft, s -> {}, Runnable::run, (t,p) -> Optional.ofNullable(chosen), () -> {}, () -> owner);
            actions = new ThemeActions(context, home, windows, () -> { assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); refreshes.incrementAndGet(); afterRefresh.run(); return CompletableFuture.completedFuture(null); },
                p -> { assertThat(SwingUtilities.isEventDispatchThread()).isFalse(); opened.add(p); }, p -> trash.test(p), worker);
        });
    }
    @AfterEach void stop() throws Exception { edt(() -> { actions.close(); windows.close(); }); }
    Path source(String name) throws Exception { Path p=home.resolve("input.toml"); Files.writeString(p, "name = \""+name+"\"\nextends = \"Jasper Dark\"\n"); return p; }
    void next() throws Exception { worker.next(); edt(() -> {}); edt(() -> {}); }
    static void edt(Runnable run) throws Exception { var thrown=new AtomicReference<Throwable>(); SwingUtilities.invokeAndWait(() -> {try{run.run();}catch(Throwable t){thrown.set(t);}}); if(thrown.get()!=null)throw new AssertionError(thrown.get()); }
    <T extends Component> T find(Class<T> type) { return find(windows.open().getLast().holder(),type); }
    static <T extends Component> T find(Container root,Class<T> type) { if(type.isInstance(root))return type.cast(root);for(var c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container nested){var found=find(nested,type);if(found!=null)return found;}}return null; }
    void click(String label) throws Exception { edt(() -> { var buttons = buttons(windows.open().getLast().holder()); buttons.stream().filter(b -> b.getText().equals(label)).findFirst().orElseThrow().doClick(); }); }
    static List<JButton> buttons(Container root){var out=new ArrayList<JButton>();for(var c:root.getComponents()){if(c instanceof JButton b)out.add(b);if(c instanceof Container nested)out.addAll(buttons(nested));}return out;}
    @Test void importUsesFrozenPlanAndBusyGatesDoubleClickWithoutConfigWrite() throws Exception {
        chosen=source("Example"); Files.writeString(home.resolve("config.toml"), "# untouched\n");
        edt(() -> { actions.importThemes(report::set); actions.importThemes(report::set); }); assertThat(worker.jobs).hasSize(1);
        next(); Files.delete(chosen); click("Import"); next();
        assertThat(report.get().imported()).extracting(ThemeImporter.Imported::name).containsExactly("Example");
        assertThat(Files.readString(home.resolve("config.toml"))).isEqualTo("# untouched\n"); assertThat(refreshes).hasValue(1);
        edt(() -> assertThat(actions.busy()).isFalse());
    }
    @Test void cancellationAndClosingBeforeWorkNeverWriteOrResurrectDialogs() throws Exception {
        chosen=source("Example"); edt(() -> actions.importThemes(report::set)); next(); click("Cancel");
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Example")).isEmpty();
        edt(() -> { actions.importThemes(report::set); actions.close(); }); next();
        edt(() -> assertThat(windows.open()).containsExactly(owner)); assertThat(report).hasValue(null);
    }
    @Test void closingDuringCommittedRefreshSuppressesCallbackButPreservesFile() throws Exception {
        chosen=source("Example"); edt(() -> actions.importThemes(report::set)); next(); click("Import");
        afterRefresh=actions::close; next();
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Example")).isPresent();
        assertThat(refreshes).hasValue(1); assertThat(report).hasValue(null);
    }
    @Test void folderUsesOverrideHomeAndErrorsResetBusy() throws Exception {
        edt(() -> actions.openFolder(s -> fail(s))); next(); assertThat(opened).containsExactly(home.resolve("themes").toRealPath());
        chosen=home.resolve("bad.exe"); edt(() -> {actions.importThemes(report::set);assertThat(actions.busy()).isFalse();}); assertThat(worker.jobs).isEmpty();
    }
    @Test void duplicateValidatesNameAndReturnsCreatedName() throws Exception {
        var created=new AtomicReference<String>();
        edt(() -> actions.duplicate(Theme.DARK,ThemeManager.generated(Theme.DARK),created::set)); next();
        edt(() -> {var field=find(JTextField.class); assertThat(field.getText()).isEqualTo("Jasper Dark Copy"); field.setText("Jasper Light");}); click("Duplicate"); assertThat(worker.jobs).isEmpty();
        edt(() -> find(JTextField.class).setText("Mine")); click("Duplicate"); next();
        assertThat(created).hasValue("Mine"); assertThat(Files.readString(home.resolve("themes/mine.toml"))).contains("\"extends\" = \"Jasper Dark\"", "# [tokens]");
    }
    Theme user() throws Exception {new ThemeImporter(home).commit(new ThemeImporter(home).plan(source("Mine")),Map.of());return ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Mine").orElseThrow();}
    @Test void trashFailureRequiresSeparateCancelablePermanentConfirmation() throws Exception {
        var user=user(); var path=((Theme.UserFile)user.source()).file();
        edt(() -> actions.remove(user,s -> {})); next(); click("Move to Trash"); next(); assertThat(path).exists(); click("Cancel"); assertThat(path).exists();
        edt(() -> actions.remove(user,s -> {})); next(); click("Move to Trash"); next(); click("Delete permanently"); next(); assertThat(path).doesNotExist();
    }
    @Test void staleIntentCannotTrashOrDeleteReplacement() throws Exception {
        var user=user(); var path=((Theme.UserFile)user.source()).file(); var calls=new AtomicInteger(); trash=p->{calls.incrementAndGet();return false;};
        edt(() -> actions.remove(user,s -> {})); next(); Files.writeString(path,"#changed\n"+Files.readString(path)); click("Move to Trash"); next();
        click("Delete permanently"); next(); assertThat(path).exists(); assertThat(calls).hasValue(0); edt(() -> assertThat(actions.busy()).isFalse());
    }
    @Test void successfulTrashNeverOffersPermanentDeleteAndBundledIsRefused() throws Exception {
        var user=user(); trash=p->{try{Files.delete(p);return true;}catch(Exception e){throw new RuntimeException(e);}};
        edt(() -> actions.remove(user,s -> {})); next(); click("Move to Trash"); next();
        edt(() -> {assertThat(windows.open()).containsExactly(owner); actions.remove(Theme.DARK,s -> {});assertThat(actions.busy()).isFalse();}); assertThat(worker.jobs).isEmpty();
    }
    @Test void duplicateStagesOnlyCapturedPickerAndPreservesNewerSelection() throws Exception {
        final ThemesPage[] page=new ThemesPage[1];
        edt(() -> {page[0]=new ThemesPage(context,()->ThemeCatalog.builtIn().latest(),()->ConfigSnapshot.defaults().viewOptions(12,Theme.DARK.palette()),actions);page[0].component();page[0].duplicate.doClick();});
        next(); edt(() -> draft.stage(CoreSettings.UI_THEME_NAME,Optional.of("Jasper Light"))); click("Duplicate"); next();
        edt(() -> {assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Jasper Light");assertThat(page[0].report.getText()).contains("Created Jasper Dark Copy");page[0].dispose();});
    }
    @Test void removeButtonRequiresWorkerValidationOfCanonicalRegularFile() throws Exception {
        var user=user(); var target=((Theme.UserFile)user.source()).file();
        Path outside=home.resolve("outside.toml"); Files.move(target,outside); Files.createSymbolicLink(target,outside);
        var catalog=ThemeCatalog.beside(home.resolve("config.toml"));catalog.scan();
        final ThemesPage[] page=new ThemesPage[1];
        edt(() -> {draft.stage(CoreSettings.UI_THEME_NAME,Optional.of("Mine"));page[0]=new ThemesPage(context,catalog::latest,()->ConfigSnapshot.defaults().viewOptions(12,Theme.DARK.palette()),actions);page[0].component();assertThat(page[0].remove.isEnabled()).isFalse();});
        next(); edt(() -> {assertThat(page[0].remove.isEnabled()).isFalse();page[0].dispose();});assertThat(outside).exists();
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
