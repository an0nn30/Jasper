package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserRequest.Mode.*;
import dev.jasper.app.files.ChooserModel.Outcome;
import dev.jasper.app.files.ChooserModel.Outcome.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ChooserModelTest {
    @TempDir Path dir;
    private final List<CompletableFuture<Browser>> connects = new ArrayList<>();

    @BeforeEach void tree() throws IOException {
        Files.createDirectories(dir.resolve("b-folder/inner"));
        Files.createDirectory(dir.resolve("A-folder"));
        Files.writeString(dir.resolve("c.txt"), "12345");
        Files.writeString(dir.resolve("B.md"), "1");
        Files.writeString(dir.resolve(".hidden"), "");
    }

    Location here(String name) { return Location.local(dir.resolve(name)); }

    /** A source whose single root "box" is a local folder reached only after {@code connects} completes. */
    ChooserSource hosts(Path remoteHome) {
        return new ChooserSource() {
            @Override public String id() { return "dev.x.hosts"; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(new Root("box", "Box", "me@box", false)); }
            @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
            @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) {
                var attempt = new CompletableFuture<Browser>();
                connects.add(attempt);
                return attempt;
            }
        };
    }

    ChooserModel model(ChooserRequest request, List<ChooserSource> extra, java.util.concurrent.Executor worker, java.util.concurrent.Executor ui) {
        var sources = new ArrayList<ChooserSource>();
        sources.add(new LocalSource(new LocalBrowser(dir)));
        sources.addAll(extra);
        var model = new ChooserModel(request, sources, worker, ui, true);
        model.start();
        return model;
    }

    static ChooserRequest request(ChooserRequest.Mode mode, boolean multiple, Location start, Optional<String> name, Optional<String> suffix) {
        return new ChooserRequest("Choose", mode, multiple, start, Optional.empty(), name, suffix, Optional.empty());
    }

    private ChooserModel model(ChooserRequest.Mode mode, boolean multiple) {
        return model(request(mode, multiple, Location.local(dir), Optional.empty(), Optional.empty()), List.of(), Runnable::run, Runnable::run);
    }

    static List<String> names(ChooserModel model) { return model.entries().stream().map(ChooserEntry::name).toList(); }

    static Outcome accept(ChooserModel model, String text) {
        var outcome = new AtomicReference<Outcome>();
        model.accept(text, outcome::set);
        return outcome.get();
    }

    @Test void listsFoldersFirstThenFilesByName() {
        var model = model(OPEN, false);
        assertThat(names(model)).containsExactly("A-folder", "b-folder", ".hidden", "B.md", "c.txt");
        assertThat(model.loading()).isFalse();
        assertThat(model.error()).isEmpty();
    }

    @Test void sortsBySizeAndReversesOnTheSecondChoice() {
        var model = model(OPEN, false);
        model.sortBy(ChooserModel.Column.SIZE);
        assertThat(names(model)).containsExactly("A-folder", "b-folder", ".hidden", "B.md", "c.txt");
        model.sortBy(ChooserModel.Column.SIZE);
        assertThat(model.ascending()).isFalse();
        assertThat(names(model)).containsExactly("A-folder", "b-folder", "c.txt", "B.md", ".hidden");
    }

    @Test void hidesDotfilesFiltersAndHonoursTheModeAndSuffix() {
        var model = model(OPEN, false);
        model.setShowHidden(false);
        assertThat(names(model)).doesNotContain(".hidden");
        model.setFilter("FOLD");
        assertThat(names(model)).containsExactly("A-folder", "b-folder");
        assertThat(names(model(DIRECTORY, false))).containsExactly("A-folder", "b-folder");
        var markdown = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.of(".MD")), List.of(), Runnable::run, Runnable::run);
        assertThat(names(markdown)).containsExactly("A-folder", "b-folder", "B.md");
    }

    @Test void navigatesWithBackForwardAndUp() {
        var model = model(OPEN, false);
        assertThat(model.canGoBack()).isFalse();
        model.navigate(here("b-folder"));
        assertThat(names(model)).containsExactly("inner");
        model.navigate(here("b-folder/inner"));
        model.goBack();
        assertThat(model.location()).isEqualTo(here("b-folder"));
        assertThat(model.canGoForward()).isTrue();
        model.goForward();
        assertThat(model.location()).isEqualTo(here("b-folder/inner"));
        model.goUp();
        model.goUp();
        assertThat(model.location()).isEqualTo(Location.local(dir));
        assertThat(model.canGoForward()).isFalse();
        assertThat(model.chain().getLast()).isEqualTo(Location.local(dir));
        assertThat(model.chain().getFirst().path()).isEqualTo(dir.getRoot().toString());
    }

    @Test void anUnreadableFolderReportsAnError() {
        var model = model(request(OPEN, false, here("missing"), Optional.empty(), Optional.empty()), List.of(), Runnable::run, Runnable::run);
        assertThat(model.error()).contains("This folder doesn't exist.");
        assertThat(model.entries()).isEmpty();
        assertThat(model.loading()).isFalse();
    }

    @Test void aSlowListingForAFolderYouLeftIsDropped() {
        var pending = new ArrayList<Runnable>();
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(), pending::add, Runnable::run);
        model.navigate(here("b-folder"));
        assertThat(model.loading()).isTrue();
        pending.get(1).run();
        assertThat(names(model)).containsExactly("inner");
        pending.get(0).run();
        assertThat(names(model)).as("the first listing arrived late").containsExactly("inner");
    }

    @Test void aLargeFolderArrivesInBatches() throws IOException {
        Path big = Files.createDirectory(dir.resolve("big"));
        for (int i = 0; i < 600; i++) Files.writeString(big.resolve("f" + i), "");
        var applied = new ArrayList<Runnable>();
        var model = model(request(OPEN, false, Location.local(big), Optional.empty(), Optional.empty()), List.of(), Runnable::run, applied::add);
        assertThat(applied).as("connecting to the local disk").hasSize(1);
        applied.removeFirst().run();
        assertThat(applied).as("two full batches and the rest").hasSize(3);
        applied.getFirst().run();
        assertThat(model.entries()).hasSize(256);
        assertThat(model.loading()).isTrue();
        applied.get(1).run();
        applied.get(2).run();
        assertThat(model.entries()).hasSize(600);
        assertThat(model.loading()).isFalse();
    }

    @Test void selectionKeepsOneEntryUnlessMultipleAndNotifiesListeners() {
        var single = model(OPEN, false);
        var changes = new AtomicInteger();
        single.addListener(changes::incrementAndGet);
        single.select(single.entries().subList(3, 5));
        assertThat(single.selection()).extracting(ChooserEntry::name).containsExactly("B.md");
        assertThat(changes).hasValue(1);
        var multiple = model(OPEN, true);
        multiple.select(multiple.entries().subList(3, 5));
        assertThat(multiple.selection()).extracting(ChooserEntry::name).containsExactly("B.md", "c.txt");
    }

    @Test void openChoosesSelectedFilesOrATypedPath() {
        var model = model(OPEN, true);
        model.select(model.entries().subList(3, 5));
        assertThat(model.nameForSelection()).contains("\"B.md\" \"c.txt\"");
        assertThat(accept(model, "\"B.md\" \"c.txt\"")).isEqualTo(new Chosen(List.of(here("B.md"), here("c.txt"))));
        assertThat(accept(model, "c.txt")).isEqualTo(new Chosen(List.of(here("c.txt"))));
        assertThat(accept(model, dir.resolve("B.md").toString())).isEqualTo(new Chosen(List.of(here("B.md"))));
        assertThat(accept(model, "nope.txt")).isEqualTo(new Invalid("There is no file named \"nope.txt\"."));
        assertThat(accept(model, "")).isEqualTo(new Invalid("Select a file."));
    }

    @Test void typingAFolderOrTildeNavigates() {
        var model = model(OPEN, false);
        assertThat(accept(model, "b-folder")).isEqualTo(new Navigated(here("b-folder")));
        assertThat(model.location()).isEqualTo(here("b-folder"));
        assertThat(accept(model, "..")).isEqualTo(new Navigated(Location.local(dir)));
        assertThat(accept(model, "~/A-folder")).isEqualTo(new Navigated(here("A-folder")));
    }

    @Test void saveAsksBeforeReplacingAndRejectsMissingFolders() {
        var model = model(SAVE, false);
        assertThat(accept(model, "new.txt")).isEqualTo(new Chosen(List.of(here("new.txt"))));
        assertThat(accept(model, "c.txt")).isEqualTo(new ConfirmReplace(here("c.txt")));
        assertThat(accept(model, "missing/new.txt")).isEqualTo(new Invalid("The folder for \"missing/new.txt\" doesn't exist."));
        assertThat(accept(model, " ")).isEqualTo(new Invalid("Enter a file name."));
        ChooserEntry file = model.entries().get(4);
        model.select(List.of(file));
        assertThat(model.nameForSelection()).contains("c.txt");
        assertThat(model.activate(file)).isEqualTo(new ConfirmReplace(here("c.txt")));
    }

    @Test void directoryChoosesTheSelectedTypedOrCurrentFolder() {
        var model = model(DIRECTORY, false);
        assertThat(accept(model, "")).isEqualTo(new Chosen(List.of(Location.local(dir))));
        model.select(List.of(model.entries().get(1)));
        assertThat(model.nameForSelection()).contains("b-folder");
        assertThat(accept(model, "b-folder")).isEqualTo(new Chosen(List.of(here("b-folder"))));
        assertThat(accept(model, "A-folder")).isEqualTo(new Chosen(List.of(here("A-folder"))));
        assertThat(accept(model, "nothing")).isEqualTo(new Invalid("There is no folder named \"nothing\"."));
        assertThat(model.activate(model.entries().get(0))).isEqualTo(new Navigated(here("A-folder")));
    }

    @Test void openSelectsTheSuggestedFile() {
        var model = model(request(OPEN, false, Location.local(dir), Optional.of("c.txt"), Optional.empty()), List.of(), Runnable::run, Runnable::run);
        assertThat(model.selection()).extracting(ChooserEntry::name).containsExactly("c.txt");
    }

    @Test void previewsTheSingleSelectionAndDropsStaleResults() {
        var pending = new ArrayList<Runnable>();
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(), pending::add, Runnable::run);
        pending.removeFirst().run();
        model.select(List.of(model.entries().get(4)));
        model.requestPreview();
        model.select(List.of(model.entries().get(3)));
        pending.removeFirst().run();
        assertThat(model.preview()).as("the selection moved on").isEmpty();
        model.requestPreview();
        pending.removeFirst().run();
        assertThat(model.preview()).hasValueSatisfying(preview -> assertThat(preview.text()).contains("1"));
        model.select(List.of(model.entries().get(0)));
        model.requestPreview();
        pending.removeFirst().run();
        assertThat(model.preview()).contains(PreviewInfo.folder(0));
    }

    @Test void createsAFolderAndSelectsIt() {
        var model = model(SAVE, false);
        var results = new ArrayList<Optional<String>>();
        model.createFolder("new folder", results::add);
        assertThat(results).containsExactly(Optional.empty());
        assertThat(model.selection()).extracting(ChooserEntry::name).containsExactly("new folder");
        model.createFolder("new folder", results::add);
        assertThat(results.getLast()).contains("\"new folder\" already exists.");
        model.createFolder("../x", results::add);
        assertThat(results.getLast()).contains("\"../x\" is not a valid folder name.");
    }

    @Test void thePathBarGoesToTypedFoldersOnly() {
        var model = model(OPEN, false);
        var problems = new ArrayList<Optional<String>>();
        model.goTo("b-folder", problems::add);
        assertThat(model.location()).isEqualTo(here("b-folder"));
        model.goTo("nowhere", problems::add);
        assertThat(problems).containsExactly(Optional.empty(), Optional.of("There is no folder at " + dir.resolve("b-folder/nowhere") + "."));
    }

    @Test void aRootConnectsWhenOpenedOffersRetryAndOpensAtItsHome() throws IOException {
        Path remote = Files.createDirectory(dir.resolve("remote-home"));
        Files.writeString(remote.resolve("r.txt"), "r");
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(hosts(remote)), Runnable::run, Runnable::run);
        model.navigate(Location.home("dev.x.hosts", "box"));
        assertThat(model.connectingTo()).contains("Box");
        connects.getLast().completeExceptionally(new IOException("offline"));
        assertThat(model.connectingTo()).isEmpty();
        assertThat(model.error()).contains("Couldn't connect to Box: offline");
        assertThat(model.retryable()).isTrue();
        model.retry();
        assertThat(connects).hasSize(2);
        connects.getLast().complete(new LocalBrowser(remote));
        assertThat(model.location()).isEqualTo(new Location("dev.x.hosts", "box", remote.toString()));
        assertThat(names(model)).containsExactly("r.txt");
        model.select(model.entries());
        assertThat(accept(model, "r.txt")).isEqualTo(new Chosen(List.of(new Location("dev.x.hosts", "box", remote.resolve("r.txt").toString()))));
        model.goBack();
        model.goForward();
        assertThat(connects).as("the root's browser is reused").hasSize(2);
    }

    @Test void aConnectionCanBeCancelled() {
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(hosts(dir)), Runnable::run, Runnable::run);
        model.navigate(Location.home("dev.x.hosts", "box"));
        model.cancelConnection();
        assertThat(connects.getLast()).isCancelled();
        assertThat(model.error()).contains("Connection cancelled.");
        assertThat(model.retryable()).isTrue();
    }

    @Test void closingClosesTheBrowsersItOpened() {
        var closed = new AtomicInteger();
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(hosts(dir)), Runnable::run, Runnable::run);
        model.navigate(Location.home("dev.x.hosts", "box"));
        connects.getLast().complete(new LocalBrowser(dir) { @Override public void close() { closed.incrementAndGet(); } });
        model.close();
        assertThat(closed).hasValue(1);
    }
}
