package dev.jasper.editor.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.FileAssociations;
import dev.jasper.sdk.ui.FileAssociations.Handler;
import dev.jasper.sdk.ui.FileAssociations.Outcome;
import dev.jasper.sdk.ui.FileAssociations.Refusal;
import dev.jasper.sdk.ui.FileAssociations.Unavailable;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTests.class)
class FileAssociationsSectionTest {
    private static final String MAC = "Mac OS X", WINDOWS = "Windows 11", LINUX = "Linux";
    private static final String PY = "public.python-script", GO = "public.go-source", RS = "public.rust-source";

    @TempDir Path home;
    private FakePluginHost host;
    private FileAssociations associations;
    private List<String> user = List.of();
    private int staged, renders;

    @BeforeEach void start() {
        host = new FakePluginHost(home.resolve("plugins"));
        host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.9.0", Set.of(Capabilities.FILE_ASSOCIATIONS)), Set.of(), Set.of(),
            context -> associations = context.platform().fileAssociations());
    }

    @AfterEach void stop() { host.close(); }

    private FileAssociationsSection section(String os) { return section(os, associations); }

    private FileAssociationsSection section(String os, FileAssociations over) {
        return new FileAssociationsSection(over, os, () -> user, list -> { user = List.copyOf(list); staged++; }, () -> renders++);
    }

    private FileAssociationsSection opened(String os) {
        var section = section(os);
        section.open();
        return section;
    }

    @Test void theIntroTheButtonsAndTheNoteFollowThePlatform() {
        var mac = opened(MAC);
        assertThat(text(mac, FileAssociationsSection.INTRO)).isEqualTo("Choose the file types Finder opens with Jasper.");
        assertThat(text(mac, FileAssociationsSection.INTRO, Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.DESCRIPTION);
        assertThat(button(mac, FileAssociationsSection.CLAIM).label()).isEqualTo("Make Jasper the Default");
        assertThat(button(mac, FileAssociationsSection.RESTORE).label()).isEqualTo("Restore Previous");
        assertThat(find(mac, FileAssociationsSection.NOTE)).isNull();
        assertThat(ids(mac)).containsExactly(FileAssociationsSection.INTRO, FileAssociationsSection.LIST, FileAssociationsSection.BUTTONS,
            FileAssociationsSection.ADD_ROW);
        mac.close();

        var windows = opened(WINDOWS);
        assertThat(text(windows, FileAssociationsSection.INTRO)).isEqualTo("Choose the file types File Explorer opens with Jasper.");
        assertThat(button(windows, FileAssociationsSection.CLAIM).label()).isEqualTo("Choose in Windows Settings…");
        assertThat(text(windows, FileAssociationsSection.NOTE)).isEqualTo("Windows asks you to pick Jasper for each type in Settings › Default apps.");
        assertThat(node(windows, FileAssociationsSection.BUTTONS, Nodes.Stack.class).children()).extracting(Nodes.Node::id)
            .containsExactly(FileAssociationsSection.CLAIM, FileAssociationsSection.RESTORE, FileAssociationsSection.NOTE);
        windows.close();

        var linux = opened(LINUX);
        assertThat(text(linux, FileAssociationsSection.INTRO)).isEqualTo("Choose the file types your file manager opens with Jasper.");
        assertThat(button(linux, FileAssociationsSection.CLAIM).label()).isEqualTo("Make Jasper the Default");
        assertThat(find(linux, FileAssociationsSection.NOTE)).isNull();
        var add = node(linux, FileAssociationsSection.ADD_ROW, Nodes.FormRow.class);
        assertThat(((Nodes.Field) add.control()).hint()).isEqualTo(".ext");
        assertThat(((Nodes.Button) add.trailing().orElseThrow()).label()).isEqualTo("Add Extension");
        linux.close();
    }

    @Test void eachUnavailableReasonIsOneLineAndNothingElse() {
        record Case(String os, Unavailable reason, String line) { }
        for (var c : List.of(
                new Case(MAC, Unavailable.DEVELOPMENT_RUN, "File associations work in an installed Jasper."),
                new Case(WINDOWS, Unavailable.NOT_INSTALLED, "Install Jasper with its installer to choose it for file types."),
                new Case(LINUX, Unavailable.NOT_INSTALLED, "Install Jasper's .deb or .rpm package to choose it for file types."),
                new Case(LINUX, Unavailable.NO_XDG_MIME, "Install xdg-utils to choose Jasper for file types."),
                new Case(LINUX, Unavailable.UNSUPPORTED_SYSTEM, "This system has no file associations Jasper can set."))) {
            host.setFileAssociationsUnavailable(Optional.of(c.reason()));
            var section = opened(c.os());
            assertThat(ids(section)).as(c.reason().name()).containsExactly(FileAssociationsSection.INTRO, FileAssociationsSection.UNAVAILABLE);
            assertThat(text(section, FileAssociationsSection.UNAVAILABLE)).as(c.reason().name()).isEqualTo(c.line());
            section.close();
        }
        assertThat(host.fileAssociationCalls()).isEmpty();
    }

    @Test void ticksDefaultFromTheStateAndATouchedTickIsKept() {
        host.setFileType(".py", List.of(PY));
        host.setFileTypeHandler(PY, new Handler.Other("Xcode"));
        host.setFileType(".go", List.of(GO));
        host.setFileTypeHandler(GO, new Handler.Jasper());
        host.setFileType(".rs", List.of(RS));
        var section = section(MAC);
        assertThat(subtitle(section, "python")).as("before the answers").isEqualTo(".py · Checking…");
        assertThat(tick(section, "python").checked()).isFalse();
        assertThat(tick(section, "python").state().enabled()).isFalse();

        section.open();
        assertThat(subtitle(section, "python")).isEqualTo(".py · Xcode");
        assertThat(tick(section, "python").checked()).as("another app's").isTrue();
        assertThat(subtitle(section, "go")).isEqualTo(".go · Jasper");
        assertThat(tick(section, "go").checked()).as("already Jasper's").isFalse();
        assertThat(tick(section, "go").state().enabled()).isTrue();
        assertThat(subtitle(section, "rust")).isEqualTo(".rs · No default app");
        assertThat(tick(section, "rust").checked()).isTrue();
        assertThat(subtitle(section, "kotlin")).isEqualTo(".kt, .kts · macOS has no file type for .kt");
        assertThat(tick(section, "kotlin").checked()).as("not claimable").isFalse();
        assertThat(tick(section, "kotlin").state().enabled()).isFalse();
        assertThat(tick(section, "python").compact()).isTrue();

        assertThat(section.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "python", false))).isTrue();
        host.setFileTypeHandler(RS, new Handler.Jasper());
        host.fileTypesChanged();
        assertThat(tick(section, "python").checked()).as("touched, kept").isFalse();
        assertThat(tick(section, "rust").checked()).as("untouched, recomputed").isFalse();
        assertThat(subtitle(section, "rust")).isEqualTo(".rs · Jasper");
        section.close();
    }

    @Test void claimAndRestoreCallTheSystemAndShowTheirResults() {
        host.setFileType(".py", List.of(PY));
        host.setFileTypeHandler(PY, new Handler.Other("Xcode"));
        host.setFileType(".go", List.of(GO));
        host.failFileTypeClaims(GO, "Not allowed");
        host.setFileType(".rs", List.of(RS));
        var section = opened(MAC);
        assertThat(button(section, FileAssociationsSection.CLAIM).state().enabled()).isTrue();
        assertThat(button(section, FileAssociationsSection.RESTORE).state().enabled()).as("nothing ticked is Jasper's").isFalse();

        section.handle(new UiEvent.Action(FileAssociationsSection.CLAIM));
        assertThat(host.fileAssociationCalls()).containsExactly("claim:.py", "claim:.go", "claim:.rs");
        assertThat(subtitle(section, "python")).isEqualTo(".py · Jasper");
        assertThat(subtitle(section, "go")).isEqualTo(".go · macOS refused: Not allowed");
        assertThat(subtitle(section, "rust")).isEqualTo(".rs · Jasper");

        section.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "python", true));
        section.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "rust", true));
        section.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "go", false));
        assertThat(button(section, FileAssociationsSection.CLAIM).state().enabled()).as("every ticked row is Jasper's").isFalse();
        assertThat(button(section, FileAssociationsSection.RESTORE).state().enabled()).isTrue();
        section.handle(new UiEvent.Action(FileAssociationsSection.RESTORE));
        assertThat(host.fileAssociationCalls()).as("only rows with something to hand back").endsWith("claim:.rs", "restore:.py");
        assertThat(subtitle(section, "python")).isEqualTo(".py · Xcode");
        assertThat(subtitle(section, "rust")).isEqualTo(".rs · Jasper");
        assertThat(subtitle(section, "go")).as("results last until the next claim or restore").isEqualTo(".go · No default app");
        section.close();

        host.setFileAssociationsInSettings(true);
        host.setFileTypeHandler(PY, new Handler.Other("Notepad"));
        var windows = opened(WINDOWS);
        windows.handle(new UiEvent.Action(FileAssociationsSection.CLAIM));
        assertThat(subtitle(windows, "python")).isEqualTo(".py · Pick Jasper for .py in Windows Settings");
        windows.close();

        host.setFileAssociationsInSettings(false);
        var linux = opened(LINUX);
        linux.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "rust", true));
        assertThat(button(linux, FileAssociationsSection.RESTORE).state().enabled()).as("no app counts as something to hand back").isTrue();
        linux.handle(new UiEvent.Action(FileAssociationsSection.RESTORE));
        assertThat(host.fileAssociationCalls()).endsWith("restore:.rs");
        assertThat(subtitle(linux, "rust")).isEqualTo(".rs · No app had this type before Jasper; choose one in your file manager");
        linux.close();
    }

    @Test void everyOutcomeHasItsPlatformsText() {
        assertThat(FileAssociationsSection.outcomeText(new Outcome.NoPrevious(), ".py", MAC))
            .isEqualTo("No app had this type before Jasper; choose one in Finder's Get Info");
        assertThat(FileAssociationsSection.outcomeText(new Outcome.NoPrevious(), ".py", WINDOWS))
            .isEqualTo("No app had this type before Jasper; choose one in Windows Settings › Default apps");
        assertThat(FileAssociationsSection.outcomeText(new Outcome.PreviousGone(), ".py", MAC)).isEqualTo("The app it had before is no longer installed");
        assertThat(FileAssociationsSection.outcomeText(new Outcome.Failed("denied"), ".py", WINDOWS)).isEqualTo("Windows refused: denied");
        assertThat(FileAssociationsSection.outcomeText(new Outcome.Failed("no xdg"), ".py", LINUX)).isEqualTo("Linux refused: no xdg");
        assertThat(FileAssociationsSection.outcomeText(new Outcome.Refused(Refusal.NOT_TEXT), ".py", LINUX))
            .isEqualTo("Linux treats .py as another kind of file");
        assertThat(FileAssociationsSection.outcomeText(new Outcome.Done(), ".py", MAC)).isEmpty();
    }

    @Test void addShowsItsErrorsAndStagesANormalisedExtension() {
        var section = opened(MAC);
        section.handle(new UiEvent.Edited(FileAssociationsSection.FIELD, "foo bar"));
        assertThat(field(section).state().error()).isEqualTo("Use a dot and up to 16 letters, digits, _, + or -");
        assertThat(addButton(section).state().enabled()).isFalse();
        section.handle(new UiEvent.Action(FileAssociationsSection.ADD));
        assertThat(staged).as("a stray Add").isZero();
        section.handle(new UiEvent.Edited(FileAssociationsSection.FIELD, ".java"));
        assertThat(field(section).state().error()).isEqualTo(".java is already in the list");
        section.handle(new UiEvent.Edited(FileAssociationsSection.FIELD, "HTML"));
        assertThat(field(section).state().error()).isEqualTo(".html opens in Jasper's browser");
        section.handle(new UiEvent.Edited(FileAssociationsSection.FIELD, ""));
        assertThat(field(section).state().error()).as("an empty field").isEmpty();
        assertThat(addButton(section).state().enabled()).isFalse();

        host.setFileType(".foo", List.of("public.foo"));
        host.setFileTypeHandler("public.foo", new Handler.Other("TextEdit"));
        section.handle(new UiEvent.Edited(FileAssociationsSection.FIELD, "Foo"));
        assertThat(field(section).state().error()).isEmpty();
        assertThat(section.handle(new UiEvent.Action(FileAssociationsSection.ADD))).isTrue();
        assertThat(user).containsExactly(".foo");
        assertThat(field(section).value()).as("cleared").isEmpty();
        assertThat(subtitle(section, "user:.foo")).isEqualTo(".foo · TextEdit");
        assertThat(item(section, "user:.foo").contextActions().orElseThrow()).extracting(MenuItem::label).containsExactly("Remove");
        assertThat(item(section, "python").contextActions()).as("built-in rows").hasValueSatisfying(menu -> assertThat(menu).isEmpty());
        section.handle(new UiEvent.Edited(FileAssociationsSection.FIELD, ".foo"));
        assertThat(field(section).state().error()).isEqualTo(".foo is already in the list");

        section.handle(new UiEvent.Action(FileAssociationsSection.REMOVE + "user:.foo"));
        assertThat(user).isEmpty();
        assertThat(item(section, "user:.foo")).isNull();
        assertThat(host.fileAssociationCalls()).as("the section never forgets").isEmpty();
        section.close();
    }

    /** Review Focus 1. */
    @Test void aResultAfterThePageClosesIsDropped() {
        host.setFileType(".py", List.of(PY));
        var manual = new Manual(associations);
        var section = section(MAC, manual);
        section.open();
        manual.answerAll();
        section.handle(new UiEvent.Action(FileAssociationsSection.CLAIM));
        assertThat(manual.claims).hasSize(1);
        section.close();
        assertThat(manual.asked.getLast()).as("close stops the re-checks").isEmpty();
        int asked = manual.asked.size(), rendered = renders;
        manual.claims.getFirst().complete(List.of(new FileAssociations.TypeResult(".py", PY, new Outcome.Done())));
        assertThat(manual.asked).as("no status after the page closed").hasSize(asked);
        assertThat(renders).isEqualTo(rendered);
        host.fileTypesChanged();
        assertThat(manual.asked).as("unsubscribed").hasSize(asked);
        assertThat(host.failures()).isEmpty();
    }

    /** Review Focus 2. */
    @Test void aUserRowThatCannotBeClaimedStaysRemovable() {
        host.setFileType(".foo", List.of("public.foo"));
        user = List.of(".foo");
        var section = opened(MAC);
        assertThat(tick(section, "user:.foo").checked()).isTrue();
        host.setFileTypeRefused(".foo", Refusal.NOT_TEXT);
        host.fileTypesChanged();
        assertThat(tick(section, "user:.foo").state().enabled()).isFalse();
        assertThat(tick(section, "user:.foo").checked()).isFalse();
        assertThat(subtitle(section, "user:.foo")).isEqualTo(".foo · macOS treats .foo as another kind of file");
        var remove = item(section, "user:.foo").contextActions().orElseThrow().getFirst();
        assertThat(remove.enabled()).isTrue();
        section.handle(new UiEvent.Action(remove.action()));
        assertThat(user).isEmpty();
        assertThat(staged).isEqualTo(1);
        section.close();
    }

    /** Review Focus 3. */
    @Test void theClaimButtonNeedsATickedClaimableRow() {
        var checking = section(MAC);
        assertThat(button(checking, FileAssociationsSection.CLAIM).state().enabled()).as("every row still checking").isFalse();
        checking.handle(new UiEvent.Action(FileAssociationsSection.CLAIM));
        assertThat(host.fileAssociationCalls()).isEmpty();

        var section = opened(MAC);
        assertThat(button(section, FileAssociationsSection.CLAIM).state().enabled()).as("nothing claimable").isFalse();
        section.handle(new UiEvent.Action(FileAssociationsSection.CLAIM));
        section.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "kotlin", true));
        section.handle(new UiEvent.Action(FileAssociationsSection.CLAIM));
        assertThat(host.fileAssociationCalls()).as("a disabled tick does not count").isEmpty();
        section.close();

        host.setFileType(".py", List.of(PY));
        host.setFileTypeHandler(PY, new Handler.Jasper());
        host.setFileType(".go", List.of(GO));
        var jaspers = opened(MAC);
        jaspers.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "go", false));
        jaspers.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "python", true));
        assertThat(button(jaspers, FileAssociationsSection.CLAIM).state().enabled()).as("only Jasper's row ticked").isFalse();
        jaspers.handle(new UiEvent.Action(FileAssociationsSection.CLAIM));
        assertThat(host.fileAssociationCalls()).isEmpty();
        jaspers.handle(new UiEvent.Toggled(FileAssociationsSection.TICK + "go", true));
        assertThat(button(jaspers, FileAssociationsSection.CLAIM).state().enabled()).isTrue();
        jaspers.handle(new UiEvent.Action(FileAssociationsSection.CLAIM));
        assertThat(host.fileAssociationCalls()).as("Jasper's rows are left out").containsExactly("claim:.go");
        jaspers.close();
    }

    /** Review Focus 4. */
    @Test void aStaleStatusAnswerNeverOverwritesANewerOne() {
        host.setFileType(".py", List.of(PY));
        host.setFileTypeHandler(PY, new Handler.Other("Xcode"));
        var manual = new Manual(associations);
        var section = section(MAC, manual);
        section.open();
        var stale = manual.answer(0);
        host.setFileTypeHandler(PY, new Handler.Jasper());
        host.fileTypesChanged();
        assertThat(manual.pending).as("open's request, then the change's").hasSize(2);
        manual.pending.get(1).complete(associations.status(manual.asked.get(1)).join());
        assertThat(subtitle(section, "python")).isEqualTo(".py · Jasper");
        manual.pending.get(0).complete(stale);
        assertThat(subtitle(section, "python")).as("the older answer is dropped").isEqualTo(".py · Jasper");
        section.close();
    }

    /** The fake's associations, with status and claim answered by the test. */
    private static final class Manual implements FileAssociations {
        private final FileAssociations real;
        final List<List<String>> asked = new ArrayList<>();
        final List<CompletableFuture<List<FileTypeStatus>>> pending = new ArrayList<>();
        final List<CompletableFuture<List<TypeResult>>> claims = new ArrayList<>();

        Manual(FileAssociations real) { this.real = real; }

        /** What the fake would answer to the request {@code index} now. */
        List<FileTypeStatus> answer(int index) { return real.status(asked.get(index)).join(); }

        void answerAll() { for (int i = 0; i < pending.size(); i++) if (!pending.get(i).isDone()) pending.get(i).complete(answer(i)); }

        @Override public Optional<Unavailable> unavailable() { return real.unavailable(); }
        @Override public CompletableFuture<List<FileTypeStatus>> status(List<String> extensions) {
            asked.add(List.copyOf(extensions));
            var future = new CompletableFuture<List<FileTypeStatus>>();
            pending.add(future);
            return future;
        }
        @Override public CompletableFuture<Claimability> claimability(String extension) { return real.claimability(extension); }
        @Override public CompletableFuture<List<TypeResult>> claim(List<String> extensions) {
            var future = new CompletableFuture<List<TypeResult>>();
            claims.add(future);
            return future;
        }
        @Override public CompletableFuture<List<TypeResult>> restore(List<String> extensions) { return real.restore(extensions); }
        @Override public CompletableFuture<Void> forget(String extension) { return real.forget(extension); }
        @Override public Subscription onChanged(Runnable handler) { return real.onChanged(handler); }
    }

    // ---- reading the nodes

    private static List<String> ids(FileAssociationsSection section) { return section.nodes("").stream().map(Nodes.Node::id).toList(); }

    private static Nodes.Button button(FileAssociationsSection section, String id) { return node(section, id, Nodes.Button.class); }

    private static Nodes.Field field(FileAssociationsSection section) {
        return (Nodes.Field) node(section, FileAssociationsSection.ADD_ROW, Nodes.FormRow.class).control();
    }

    private static Nodes.Button addButton(FileAssociationsSection section) {
        return (Nodes.Button) node(section, FileAssociationsSection.ADD_ROW, Nodes.FormRow.class).trailing().orElseThrow();
    }

    private static String text(FileAssociationsSection section, String id) { return text(section, id, Nodes.Text.class).text(); }

    private static Nodes.Text text(FileAssociationsSection section, String id, Class<Nodes.Text> type) { return node(section, id, type); }

    private static ListItem item(FileAssociationsSection section, String rowId) {
        return node(section, FileAssociationsSection.LIST, Nodes.ItemList.class).items().stream()
            .filter(item -> item.id().equals(rowId)).findFirst().orElse(null);
    }

    private static String subtitle(FileAssociationsSection section, String rowId) {
        return item(section, rowId).subtitle().stream().map(node -> ((Nodes.Text) node).text()).reduce("", String::concat);
    }

    private static Nodes.Toggle tick(FileAssociationsSection section, String rowId) {
        return (Nodes.Toggle) item(section, rowId).trailing().getFirst();
    }

    private static <T extends Nodes.Node> T node(FileAssociationsSection section, String id, Class<T> type) {
        Nodes.Node found = find(section, id);
        assertThat(found).as(id).isInstanceOf(type);
        return type.cast(found);
    }

    private static Nodes.Node find(FileAssociationsSection section, String id) {
        return section.nodes("").stream().map(node -> find(node, id)).filter(found -> found != null).findFirst().orElse(null);
    }

    private static Nodes.Node find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        List<Nodes.Node> children = switch (node) {
            case Nodes.Stack stack -> stack.children();
            case Nodes.FormRow row -> Stream.concat(Stream.of(row.control()), row.trailing().stream()).toList();
            default -> List.of();
        };
        for (Nodes.Node child : children) {
            Nodes.Node found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
