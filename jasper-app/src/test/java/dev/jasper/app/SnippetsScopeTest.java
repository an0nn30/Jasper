package dev.jasper.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnippetsScopeTest {
    @TempDir Path dir;

    private SnippetStore storeWith(String text, List<Path> opened) throws Exception {
        Path file = dir.resolve("snippets.toml");
        Files.writeString(file, text);
        return new SnippetStore(file, opened::add, SnippetStoreTest.inlineWorker(), Runnable::run);
    }

    private static PaletteTarget target(List<String> pasted, AtomicInteger returns) {
        return new PaletteTarget(pasted::add, returns::incrementAndGet, Optional::empty, () -> "zsh", () -> true);
    }

    private static PaletteTarget deadTarget(List<String> pasted, AtomicInteger returns) {
        return new PaletteTarget(pasted::add, returns::incrementAndGet, Optional::empty, () -> "zsh", () -> false);
    }

    private static final String FILE = """
        [[snippet]]
        name = "Rebase onto main"
        command = "git fetch origin && git rebase origin/{{branch}}"
        keywords = ["git"]

        [[snippet]]
        name = "List all"
        command = "ls -la"

        [[snippet]]
        name = "Deploy"
        command = "make deploy ENV={{env}} TAG={{tag}} # {{env}}"
        keywords = ["release", "ship"]

        [[snippet]]
        name = "Git status"
        command = "git status --short"
        """;

    @Test void searchRanksNamesThenKeywordsThenCommandsAndTagsPlaceholderCounts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var store = storeWith(FILE, new ArrayList<>())) {
                store.reload();
                var scope = new SnippetsScope(store, message -> {}, null);
                var context = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger()));
                assertThat(scope.id()).isEqualTo(PaletteScope.SNIPPETS_ID);
                assertThat(scope.verbs()).containsExactly(SnippetsScope.PASTE, SnippetsScope.PASTE_RUN, SnippetsScope.EDIT);
                var all = scope.search("", context);
                assertThat(all.sectionLabel()).isEqualTo("Snippets");
                assertThat(all.rows()).extracting(PaletteRow::title).containsExactly("Rebase onto main", "List all", "Deploy", "Git status");
                assertThat(all.rows().getFirst().tag()).isEqualTo("1 field");
                assertThat(all.rows().get(2).tag()).isEqualTo("2 fields");
                assertThat(all.rows().get(1).tag()).isNull();
                assertThat(all.rows().get(1).detail()).isEqualTo("ls -la");
                assertThat(all.rows().getFirst().id()).isEqualTo(SnippetsScope.rowId("rebase ONTO main "));
                assertThat(scope.search("git", context).rows()).extracting(PaletteRow::title)
                    .containsExactly("Git status", "Rebase onto main");
                assertThat(scope.search("ship", context).rows()).extracting(PaletteRow::title).containsExactly("Deploy");
                assertThat(scope.search("rebase origin", context).rows()).extracting(PaletteRow::title).containsExactly("Rebase onto main");
                assertThat(scope.search("nothing", context).rows()).isEmpty();
                assertThat(scope.search("", new PaletteContext(true, PaletteTarget.none(), 2)).rows()).hasSize(2);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void placeholdersOpenAFillInStepThatRemembersValuesAndPlainSnippetsPasteAtOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var store = storeWith(FILE, new ArrayList<>())) {
                store.reload();
                var scope = new SnippetsScope(store, message -> {}, null);
                var pasted = new ArrayList<String>(); var returns = new AtomicInteger();
                var context = new PaletteContext(true, target(pasted, returns));
                var rows = scope.search("", context).rows();
                assertThat(scope.step(rows.get(1), SnippetsScope.PASTE, context)).isNull();
                scope.execute(rows.get(1), SnippetsScope.PASTE_RUN, context);
                assertThat(pasted).containsExactly("ls -la");
                assertThat(returns.get()).isEqualTo(1);
                store.lastValues().put("env", "staging");
                var step = scope.step(rows.get(2), SnippetsScope.PASTE, context);
                assertThat(step.title()).isEqualTo("Deploy");
                assertThat(step.fields()).extracting(PaletteStep.Field::name).containsExactly("env", "tag");
                assertThat(step.fields().getFirst().prefill()).isEqualTo("staging");
                var result = new AtomicReference<PaletteStep.Result>();
                step.complete().accept(Map.of("env", "prod", "tag", "v2"), result::set);
                assertThat(pasted).containsExactly("ls -la", "make deploy ENV=prod TAG=v2 # prod");
                assertThat(returns.get()).isEqualTo(1);
                assertThat(result.get().error()).isNull();
                assertThat(store.lastValues()).containsEntry("env", "prod").containsEntry("tag", "v2");
                assertThat(scope.step(rows.get(2), SnippetsScope.EDIT, context)).isNull();
                assertThat(scope.available(rows.get(2), SnippetsScope.PASTE, context)).isTrue();
                assertThat(scope.available(PaletteRow.of("x", "x"), SnippetsScope.PASTE, context)).isFalse();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void editFileOpensTheEditorAndABrokenFileShowsAnErrorRow() throws Exception {
        var opened = new ArrayList<Path>();
        SwingUtilities.invokeAndWait(() -> {
            try (var store = storeWith("[[snippet]\nbroken", opened)) {
                store.reload();
                var scope = new SnippetsScope(store, message -> {}, null);
                var context = new PaletteContext(true, PaletteTarget.none());
                var rows = scope.search("", context).rows();
                assertThat(rows).hasSize(1);
                assertThat(rows.getFirst().title()).isEqualTo("Snippets file has errors");
                assertThat(rows.getFirst().enabled()).isFalse();
                assertThat(scope.available(rows.getFirst(), SnippetsScope.EDIT, context)).isTrue();
                assertThat(scope.available(rows.getFirst(), SnippetsScope.PASTE, context)).isFalse();
                scope.execute(rows.getFirst(), SnippetsScope.EDIT, context);
                assertThat(opened).containsExactly(store.file());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void pasteVerbsRequireALiveTargetButEditFileDoesNot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var store = storeWith(FILE, new ArrayList<>())) {
                store.reload();
                var scope = new SnippetsScope(store, message -> {}, null);
                var dead = new PaletteContext(true, deadTarget(new ArrayList<>(), new AtomicInteger()));
                var row = scope.search("", dead).rows().getFirst();
                assertThat(scope.available(row, SnippetsScope.PASTE, dead)).isFalse();
                assertThat(scope.available(row, SnippetsScope.PASTE_RUN, dead)).isFalse();
                assertThat(scope.available(row, SnippetsScope.EDIT, dead)).isTrue();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void pasteUnescapesLiteralBracesEvenWithoutPlaceholders() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var store = storeWith("""
                [[snippet]]
                name = "Echo brace"
                command = 'echo \\{{x}}'
                """, new ArrayList<>())) {
                store.reload();
                var scope = new SnippetsScope(store, message -> {}, null);
                var pasted = new ArrayList<String>(); var returns = new AtomicInteger();
                var context = new PaletteContext(true, target(pasted, returns));
                var row = scope.search("", context).rows().getFirst();
                assertThat(scope.step(row, SnippetsScope.PASTE, context)).isNull();
                scope.execute(row, SnippetsScope.PASTE, context);
                assertThat(pasted).containsExactly("echo {{x}}");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
