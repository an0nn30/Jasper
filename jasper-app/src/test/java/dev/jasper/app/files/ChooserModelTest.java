package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserRequest.Mode.*;
import dev.jasper.app.files.ChooserModel.Outcome.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ChooserModelTest {
    @TempDir Path dir;

    @BeforeEach void tree() throws IOException {
        Files.createDirectories(dir.resolve("b-folder/inner"));
        Files.createDirectory(dir.resolve("A-folder"));
        Files.writeString(dir.resolve("c.txt"), "12345");
        Files.writeString(dir.resolve("B.md"), "1");
        Files.writeString(dir.resolve(".hidden"), "");
    }

    static ChooserModel started(ChooserRequest request) {
        var model = new ChooserModel(request, new LocalFiles(), Runnable::run, Runnable::run, true);
        model.start();
        return model;
    }

    private ChooserModel model(ChooserRequest.Mode mode, boolean multiple) {
        return started(new ChooserRequest("Choose", mode, multiple, dir, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    static List<String> names(ChooserModel model) { return model.entries().stream().map(ChooserEntry::name).toList(); }

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
        var markdown = started(new ChooserRequest("Install", OPEN, false, dir, Optional.empty(), Optional.empty(), Optional.of(".MD")));
        assertThat(names(markdown)).containsExactly("A-folder", "b-folder", "B.md");
    }

    @Test void navigatesWithBackForwardAndUp() {
        var model = model(OPEN, false);
        assertThat(model.canGoBack()).isFalse();
        model.navigate(dir.resolve("b-folder"));
        assertThat(names(model)).containsExactly("inner");
        model.navigate(dir.resolve("b-folder/inner"));
        model.goBack();
        assertThat(model.folder()).isEqualTo(dir.resolve("b-folder"));
        assertThat(model.canGoForward()).isTrue();
        model.goForward();
        assertThat(model.folder()).isEqualTo(dir.resolve("b-folder/inner"));
        model.goUp();
        model.goUp();
        assertThat(model.folder()).isEqualTo(dir);
        assertThat(model.canGoForward()).isFalse();
        assertThat(model.canGoBack()).isTrue();
    }

    @Test void navigatingClearsTheFilter() {
        var model = model(OPEN, false);
        model.setFilter("b");
        model.navigate(dir.resolve("b-folder"));
        assertThat(model.filter()).isEmpty();
        assertThat(names(model)).containsExactly("inner");
    }

    @Test void anUnreadableFolderReportsAnError() {
        var model = started(new ChooserRequest("Open", OPEN, false, dir.resolve("missing"), Optional.empty(), Optional.empty(), Optional.empty()));
        assertThat(model.error()).contains("This folder doesn't exist.");
        assertThat(model.entries()).isEmpty();
        assertThat(model.loading()).isFalse();
    }

    @Test void aSlowListingForAFolderYouLeftIsDropped() {
        var pending = new ArrayList<Runnable>();
        var request = new ChooserRequest("Open", OPEN, false, dir, Optional.empty(), Optional.empty(), Optional.empty());
        var model = new ChooserModel(request, new LocalFiles(), pending::add, Runnable::run, true);
        model.start();
        model.navigate(dir.resolve("b-folder"));
        assertThat(model.loading()).isTrue();
        pending.get(1).run();
        assertThat(names(model)).containsExactly("inner");
        pending.get(0).run();
        assertThat(names(model)).as("the first listing arrived late").containsExactly("inner");
        assertThat(model.folder()).isEqualTo(dir.resolve("b-folder"));
    }

    @Test void selectionKeepsOneEntryUnlessMultipleAndNotifiesListeners() {
        var single = model(OPEN, false);
        var changes = new AtomicInteger();
        single.addListener(changes::incrementAndGet);
        single.select(single.entries().subList(3, 5));
        assertThat(single.selection()).extracting(ChooserEntry::name).containsExactly("B.md");
        assertThat(changes).hasValue(1);
        single.select(single.entries().subList(3, 4));
        assertThat(changes).as("the same selection again").hasValue(1);
        var multiple = model(OPEN, true);
        multiple.select(multiple.entries().subList(3, 5));
        assertThat(multiple.selection()).extracting(ChooserEntry::name).containsExactly("B.md", "c.txt");
    }

    @Test void openChoosesSelectedFilesOrATypedPath() {
        var model = model(OPEN, true);
        model.select(model.entries().subList(3, 5));
        assertThat(model.nameForSelection()).contains("\"B.md\" \"c.txt\"");
        assertThat(model.accept("\"B.md\" \"c.txt\"")).isEqualTo(new Chosen(List.of(dir.resolve("B.md"), dir.resolve("c.txt"))));
        assertThat(model.accept("c.txt")).isEqualTo(new Chosen(List.of(dir.resolve("c.txt"))));
        assertThat(model.accept(dir.resolve("B.md").toString())).isEqualTo(new Chosen(List.of(dir.resolve("B.md"))));
        assertThat(model.accept("nope.txt")).isEqualTo(new Invalid("There is no file named \"nope.txt\"."));
        assertThat(model.accept("")).isEqualTo(new Invalid("Select a file."));
    }

    @Test void typingAFolderOrTildeNavigates() {
        var model = model(OPEN, false);
        assertThat(model.accept("b-folder")).isEqualTo(new Navigated(dir.resolve("b-folder")));
        assertThat(model.accept("..")).isEqualTo(new Navigated(dir));
        assertThat(model.resolve("~")).isEqualTo(new LocalFiles().home());
        assertThat(model.resolve("~/x")).isEqualTo(new LocalFiles().home().resolve("x"));
    }

    @Test void saveAsksBeforeReplacingAndRejectsMissingFolders() {
        var model = model(SAVE, false);
        assertThat(model.accept("new.txt")).isEqualTo(new Chosen(List.of(dir.resolve("new.txt"))));
        assertThat(model.accept("c.txt")).isEqualTo(new ConfirmReplace(dir.resolve("c.txt")));
        assertThat(model.accept("missing/new.txt")).isEqualTo(new Invalid("The folder for \"missing/new.txt\" doesn't exist."));
        assertThat(model.accept(" ")).isEqualTo(new Invalid("Enter a file name."));
        ChooserEntry file = model.entries().get(4);
        model.select(List.of(file));
        assertThat(model.nameForSelection()).contains("c.txt");
        assertThat(model.activate(file)).isEqualTo(new ConfirmReplace(dir.resolve("c.txt")));
    }

    @Test void directoryChoosesTheSelectedTypedOrCurrentFolder() {
        var model = model(DIRECTORY, false);
        assertThat(model.accept("")).isEqualTo(new Chosen(List.of(dir)));
        model.select(List.of(model.entries().get(1)));
        assertThat(model.nameForSelection()).contains("b-folder");
        assertThat(model.accept("b-folder")).isEqualTo(new Chosen(List.of(dir.resolve("b-folder"))));
        assertThat(model.accept("A-folder")).isEqualTo(new Chosen(List.of(dir.resolve("A-folder"))));
        assertThat(model.accept("nothing")).isEqualTo(new Invalid("There is no folder named \"nothing\"."));
        assertThat(model.activate(model.entries().get(0))).isEqualTo(new Navigated(dir.resolve("A-folder")));
    }

    @Test void openSelectsTheSuggestedFile() {
        var model = started(new ChooserRequest("Open", OPEN, false, dir, Optional.empty(), Optional.of("c.txt"), Optional.empty()));
        assertThat(model.selection()).extracting(ChooserEntry::name).containsExactly("c.txt");
    }

    @Test void previewsTheSingleSelectionAndDropsStaleResults() {
        var pending = new ArrayList<Runnable>();
        var request = new ChooserRequest("Open", OPEN, false, dir, Optional.empty(), Optional.empty(), Optional.empty());
        var model = new ChooserModel(request, new LocalFiles(), pending::add, Runnable::run, true);
        model.start();
        pending.removeFirst().run();
        model.select(List.of(model.entries().get(4)));
        model.requestPreview();
        model.select(List.of(model.entries().get(3)));
        pending.removeFirst().run();
        assertThat(model.preview()).as("the selection moved on").isEmpty();
        model.requestPreview();
        pending.removeFirst().run();
        assertThat(model.preview()).hasValueSatisfying(preview -> assertThat(preview.text()).contains("1"));
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
}
