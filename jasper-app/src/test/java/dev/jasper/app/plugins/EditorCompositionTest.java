package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.ui.swing.KitView;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.EditorEvidence.bounds;
import static dev.jasper.app.plugins.StagedEditorHost.call;
import static dev.jasper.app.plugins.WorkspaceContentRouteTest.named;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 3 Task 8: the actual staged Editor's document chrome in the canonical design language, through the production
 * kit and content-tab host. The find and replace rows are compact document bars whose fields line up and whose match
 * options, count and navigation are icon buttons; keyboard access is unchanged; and none of it touches the document's
 * text, selection, caret, undo history, dirty state or syntax style, through theme changes and font growth. Every
 * state is also captured at device scale 2 ({@link EditorEvidence}).
 */
class EditorCompositionTest {
    static final int WIDTH = 760, HEIGHT = 300, SIDE = 6;
    static final String[] FIND_ANCHORS = {"find.bar", "find.row", "find.query", "find.case", "find.word", "find.regex", "find.status",
        "find.previous", "find.next", "find.close", "find.replace.row", "find.replacement", "find.one", "find.all"};
    @TempDir Path root;

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void findAndReplaceAreAlignedCompactBarsThatLeaveTheDocumentAlone() throws Exception {
        for (String theme : List.of("dark", "light")) findAndReplace(theme, root.resolve(theme));
    }

    private void findAndReplace(String theme, Path data) throws Exception {
        boolean dark = theme.equals("dark");
        var appearance = onEdtValue(WorkspaceAppearanceFixture::new);
        var first = onEdtValue(() -> ThemeManager.generated(dark ? Theme.DARK : Theme.JASPER_LIGHT));
        var next = onEdtValue(() -> ThemeManager.generated(ThemeManager.builtIns().stream()
            .filter(candidate -> candidate.dark() == dark && !candidate.equals(dark ? Theme.DARK : Theme.JASPER_LIGHT)).findFirst().orElseThrow()));
        onEdt(() -> appearance.setPair(new ResolvedTheme(first, first)));
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        try (var editor = new StagedEditorHost(data, dark, appearance)) {
            Object[] state = onEdtValue(() -> {
                call(editor.editorWindow(), "newUntitled");
                JTextArea area = editor.documentText();
                area.setText("int foo = 42;\nfoo += 1;\n// edit\n");
                call(call(editor.selectedTab(), "session"), "setStyle", new Class<?>[]{String.class}, "text/java");
                area.select(4, 7);
                EditorEvidence.measure(area);
                var entry = editor.contributions.content().entries().getFirst();
                var view = (KitView) entry.component();
                EditorEvidence.capture(theme, "document", view, WIDTH, HEIGHT, FIND_ANCHORS);
                entry.commands().stream().filter(c -> c.id().equals("replace")).findFirst().orElseThrow().run().run();
                EditorEvidence.capture(theme, "find-replace", view, WIDTH, HEIGHT, FIND_ANCHORS);
                assertCompactAlignedBars(view);
                assertThat(named(view, "find.status", JLabel.class).getText()).isEqualTo("2 matches");
                // Keyboard: the options are buttons that Space presses; their pressed state is the owner's confirmed state.
                for (String id : List.of("find.case", "find.word", "find.regex")) StagedEditorHost.space(named(view, id, AbstractButton.class));
                for (String id : List.of("find.case", "find.word", "find.regex")) assertThat(named(view, id, AbstractButton.class).isSelected()).as(id).isTrue();
                assertThat(named(view, "find.case", AbstractButton.class).getAccessibleContext().getAccessibleName()).isEqualTo("Match Case");
                assertThat(named(view, "find.previous", AbstractButton.class).getToolTipText()).isEqualTo("Previous Match (Shift+Enter)");
                assertThat(EditorEvidence.focusable(named(view, "find.bar", JComponent.class))).extracting(Component::getName).containsExactly(
                    "find.query.input", "find.case", "find.word", "find.regex", "find.previous", "find.next", "find.close",
                    "find.replacement.input", "find.one", "find.all");
                named(view, "find.replacement.input", JTextComponent.class).setText("bar");
                EditorEvidence.capture(theme, "find-replace-options", view, WIDTH, HEIGHT, FIND_ANCHORS);
                named(view, "find.query.input", JTextComponent.class).setText("(");
                assertThat(named(view, "find.status", JLabel.class).getText()).isEqualTo("Invalid pattern");
                EditorEvidence.capture(theme, "find-invalid-pattern", view, WIDTH, HEIGHT, FIND_ANCHORS);
                named(view, "find.query.input", JTextComponent.class).setText("absent");
                assertThat(named(view, "find.status", JLabel.class).getText()).isEqualTo("No matches");
                EditorEvidence.capture(theme, "find-no-matches", view, WIDTH, HEIGHT, FIND_ANCHORS);
                named(view, "find.query.input", JTextComponent.class).setText("foo");
                named(view, "find.regex", AbstractButton.class).doClick();
                EditorEvidence.capture(theme, "find-narrow", view, 420, HEIGHT, FIND_ANCHORS);
                area.select(1, 3);
                return new Object[]{area, area.getDocument(), view, call(area, "canUndo"), call(call(editor.selectedTab(), "session"), "dirty")};
            });
            // A theme change during editing keeps the document and the bar's own state.
            onEdt(() -> appearance.setPair(new ResolvedTheme(next, next)));
            onEdt(() -> {
                var area = (JTextArea) state[0]; var view = (KitView) state[2];
                EditorEvidence.capture(theme, "find-replace-after-theme", view, WIDTH, HEIGHT, FIND_ANCHORS);
                assertDocumentKept(editor, area, state);
                assertThat(named(view, "find.query.input", JTextComponent.class).getText()).isEqualTo("foo");
                assertThat(named(view, "find.replacement.input", JTextComponent.class).getText()).isEqualTo("bar");
                assertThat(named(view, "find.case", AbstractButton.class).isSelected()).isTrue();
                assertCompactAlignedBars(view);
            });
            // Enlarged interface font: the rows grow with their content and nothing clips.
            onEdt(() -> controller.configure(dark ? Theme.DARK : Theme.JASPER_LIGHT, new dev.jasper.app.config.UiFontConfig("system", 20)));
            onEdt(() -> {
                var view = (KitView) state[2];
                EditorEvidence.capture(theme, "find-replace-font20", view, WIDTH, HEIGHT, FIND_ANCHORS);
                var row = named(view, "find.row", JComponent.class);
                for (String id : List.of("find.query", "find.case", "find.status", "find.close")) {
                    var child = bounds(row, named(view, id, JComponent.class));
                    assertThat(child.y).as("%s inside its row", id).isGreaterThanOrEqualTo(0);
                    assertThat(child.y + child.height).as("%s inside its row", id).isLessThanOrEqualTo(row.getHeight());
                }
                var input = named(view, "find.query.input", JTextComponent.class);
                assertThat(input.getHeight()).as("the enlarged query is not clipped").isGreaterThanOrEqualTo(input.getPreferredSize().height);
                assertThat(bounds(view, named(view, "find.replacement", JComponent.class)).x).isEqualTo(bounds(view, named(view, "find.query", JComponent.class)).x);
                assertDocumentKept(editor, (JTextArea) state[0], state);
            });
            onEdt(() -> controller.configure(dark ? Theme.DARK : Theme.JASPER_LIGHT, dev.jasper.app.config.UiFontConfig.defaults()));
            onEdt(() -> {
                var area = (JTextArea) state[0]; var view = (KitView) state[2];
                var query = named(view, "find.query.input", JTextComponent.class);
                area.setCaretPosition(0);
                StagedEditorHost.enter(query); assertThat(area.getSelectionStart()).isEqualTo(4);
                StagedEditorHost.enter(query); assertThat(area.getSelectionStart()).isEqualTo(14);
                StagedEditorHost.press(query, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK, '\n'); assertThat(area.getSelectionStart()).isEqualTo(4);
                area.select(1, 3);
                StagedEditorHost.escape(named(view, "find.word", AbstractButton.class));
                assertThat(named(view, "find.bar", JComponent.class).isVisible()).as("Escape from an option closes the bar").isFalse();
                assertDocumentKept(editor, area, state);
                EditorEvidence.capture(theme, "document-after-find", view, WIDTH, HEIGHT, FIND_ANCHORS);
                editor.contentTab().close();
            });
            assertThat(editor.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    /**
     * The document's other states in the same language: the changed-on-disk and deleted banners and the Vim strip are
     * compact document bars with a hairline against the document; loading and a failed read are full-body empty states
     * with a short title over one sentence, the secondary action and then the one primary.
     */
    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void bannersVimStripAndLoadingOrFailedStatesUseTheCanonicalComponents() throws Exception {
        for (String theme : List.of("dark", "light")) documentStates(theme, root.resolve("states-" + theme));
    }

    private void documentStates(String theme, Path data) throws Exception {
        boolean dark = theme.equals("dark");
        var appearance = onEdtValue(WorkspaceAppearanceFixture::new);
        var first = onEdtValue(() -> ThemeManager.generated(dark ? Theme.DARK : Theme.JASPER_LIGHT));
        onEdt(() -> appearance.setPair(new ResolvedTheme(first, first)));
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(dark ? Theme.DARK : Theme.JASPER_LIGHT));
        java.nio.file.Files.createDirectories(data);
        Path file = java.nio.file.Files.writeString(data.resolve("notes.yaml"), "a: 1\nb: 2\n");
        try (var editor = new StagedEditorHost(data, dark, appearance)) {
            onEdt(() -> call(editor.editorWindow(), "openLocal", new Class<?>[]{Path.class}, file));
            StagedVaultHost.await(() -> (boolean) call(editor.selectedTab(), "showingText"), "the document");
            onEdt(() -> {
                var area = editor.documentText(); area.setText("a: mine\nb: 2\n");
                var view = (KitView) editor.contributions.content().entries().getFirst().component();
                Object session = call(editor.selectedTab(), "session");
                try {
                    java.nio.file.Files.writeString(file, "a: 22\nb: 2\nc: 3\n");
                    Object revision = editor.loader.loadClass("dev.jasper.editor.local.LocalFiles").getMethod("revision", Path.class).invoke(null, file);
                    call(session, "externalChange", new Class<?>[]{java.util.Optional.class}, revision);
                } catch (ReflectiveOperationException | java.io.IOException failure) { throw new AssertionError(failure); }
                var banner = named(view, "document.banner", JComponent.class);
                assertThat(banner.isVisible()).isTrue();
                EditorEvidence.capture(theme, "banner-changed", view, WIDTH, HEIGHT, "document.banner", "document.banner.text", "document.banner.first", "document.banner.second");
                var text = bounds(view, named(view, "document.banner.text", JComponent.class));
                var reload = bounds(view, named(view, "document.banner.first", JComponent.class));
                var keep = bounds(view, named(view, "document.banner.second", JComponent.class));
                assertThat(text.x).as("the notice is inset by the bar padding").isEqualTo(SIDE);
                assertThat(keep.x + keep.width).as("its actions end at the trailing padding").isEqualTo(WIDTH - SIDE);
                assertThat(reload.x + reload.width + 3).as("adjacent actions sit close together").isEqualTo(keep.x);
                assertThat(banner.getHeight()).isEqualTo(32);
                assertThat(reload.height).isEqualTo(24);
                call(session, "externalChange", new Class<?>[]{java.util.Optional.class}, java.util.Optional.empty());
                EditorEvidence.capture(theme, "banner-deleted", view, WIDTH, HEIGHT, "document.banner", "document.banner.text", "document.banner.first", "document.banner.second");
                call(session, "keepDeleted");
                // Vim: the strip under the text is a document bar whose status fills and whose pending command keeps a stable width.
                call(editor.editorWindow(), "toggleVim");
                var vim = ((java.util.Optional<?>) call(editor.selectedTab(), "vimEditor")).orElseThrow();
                feed(editor, vim, "2d");
                EditorEvidence.capture(theme, "vim-pending-command", view, WIDTH, HEIGHT, "vim.bar", "vim.strip", "vim.status", "vim.command");
                var status = bounds(view, named(view, "vim.status", JComponent.class)); var command = bounds(view, named(view, "vim.command", JComponent.class));
                assertThat(status.x).isEqualTo(SIDE);
                assertThat(command.x + command.width).isEqualTo(WIDTH - SIDE);
                assertThat(command.width).as("the pending command keeps a stable width").isGreaterThanOrEqualTo(84);
                feed(editor, vim, "<Esc>:reg<CR>");
                EditorEvidence.capture(theme, "vim-output", view, WIDTH, 420, "vim.bar", "vim.output", "vim.strip", "vim.status", "vim.output.text.0", "vim.output.text.1");
                var firstLine = bounds(view, named(view, "vim.output.text.0", JComponent.class));
                var secondLine = bounds(view, named(view, "vim.output.text.1", JComponent.class));
                assertThat(firstLine.x).as("output lines align with the strip's text").isEqualTo(SIDE);
                assertThat(secondLine.y - firstLine.y).as("passive output rows are text lines, not list rows").isLessThanOrEqualTo(22);
                feed(editor, vim, "<CR>");
                call(editor.editorWindow(), "toggleVim");
                assertThat(area.getText()).isEqualTo("a: mine\nb: 2\n");
                editor.contentTab().close();
            });
            // A failed read and a read still under way are the canonical empty state.
            onEdt(() -> call(editor.editorWindow(), "openLocal", new Class<?>[]{Path.class}, data.resolve("gone.txt")));
            StagedVaultHost.await(() -> named((KitView) editor.contributions.content().entries().getFirst().component(), "document.retry", AbstractButton.class).isVisible(), "the failed state");
            onEdt(() -> {
                var view = (KitView) editor.contributions.content().entries().getFirst().component();
                EditorEvidence.capture(theme, "load-failed", view, WIDTH, HEIGHT, "document.state", "document.dismiss", "document.retry");
                var dismiss = bounds(view, named(view, "document.dismiss", JComponent.class)); var retry = bounds(view, named(view, "document.retry", JComponent.class));
                assertThat(dismiss.x).as("the secondary, then the primary").isLessThan(retry.x);
                var state = (dev.jasper.sdk.ui.Nodes.Empty) node(editor.documentView().spec().root(), "document.state");
                assertThat(state.title()).isEqualTo("Could Not Open File"); assertThat(state.description()).isEqualTo("gone.txt does not exist.");
                editor.contentTab().close();
            });
            onEdt(() -> call(editor.editorWindow(), "open", new Class<?>[]{loaderClass(editor, "dev.jasper.editor.api.EditorDocument")}, pendingDocument(editor)));
            onEdt(() -> {
                var view = (KitView) editor.contributions.content().entries().getFirst().component();
                EditorEvidence.capture(theme, "loading", view, WIDTH, HEIGHT, "document.state", "document.dismiss");
                assertThat(named(view, "document.retry", AbstractButton.class).isVisible()).isFalse();
                assertThat(((dev.jasper.sdk.ui.Nodes.Empty) node(editor.documentView().spec().root(), "document.state")).title()).isEqualTo("Loading report.log from build-01…");
                call(editor.selectedTab(), "dispose");
            });
            assertThat(editor.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    /** Editor question sheets, Go to Line and the Language picker at their packed sizes, device scale 2, dark and light. */
    @Test @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void dialogsComposeTheCanonicalSheetsAtTheirPackedSizes() throws Exception {
        for (String theme : List.of("dark", "light")) dialogs(theme, root.resolve("dialogs-" + theme));
    }

    private void dialogs(String theme, Path data) throws Exception {
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(theme.equals("dark") ? Theme.DARK : Theme.JASPER_LIGHT));
        try (var editor = new StagedEditorHost(data, theme.equals("dark"))) {
            onEdt(() -> editor.ask("Unsaved changes", "Save changes to example.txt?", List.of("Save", "Don't Save", "Cancel"), false, answer -> { }));
            var unsaved = editor.awaitSurface("Unsaved changes");
            StagedRemoteHost.awaitNamed(unsaved, "question.choice.0");
            onEdt(() -> {
                capture(theme, "unsaved-question", editor, unsaved);
                var save = bounds(unsaved.holder(), EditorEnterBaselineTest.require(unsaved, "question.choice.0"));
                var cancel = bounds(unsaved.holder(), EditorEnterBaselineTest.require(unsaved, "question.choice.2"));
                var discard = bounds(unsaved.holder(), EditorEnterBaselineTest.require(unsaved, "question.choice.1"));
                assertThat(discard.x).isLessThan(cancel.x); assertThat(cancel.x).as("the primary Save is right-most").isLessThan(save.x);
                StagedEditorHost.escape(EditorEnterBaselineTest.require(unsaved, "question.message"));
            });
            onEdt(() -> editor.ask("File changed", "example.txt changed on disk since you opened it.", List.of("Overwrite", "Reload", "Cancel"), true, answer -> { }));
            var changed = editor.awaitSurface("File changed");
            StagedRemoteHost.awaitNamed(changed, "question.choice.2");
            onEdt(() -> { capture(theme, "changed-question", editor, changed); changed.requestClose(); });
            onEdt(() -> editor.ask("Save failed", "Could not save example.txt: permission denied.", List.of("OK"), false, answer -> { }));
            var failed = editor.awaitSurface("Save failed");
            StagedRemoteHost.awaitNamed(failed, "question.choice.0");
            onEdt(() -> { capture(theme, "save-failed", editor, failed); failed.requestClose(); });
            onEdt(() -> editor.goToLine(4, 120, line -> { }));
            var line = editor.awaitSurface("Go to Line");
            StagedRemoteHost.awaitNamed(line, "go-to-line.go");
            onEdt(() -> {
                capture(theme, "go-to-line", editor, line);
                var row = bounds(line.holder(), EditorEnterBaselineTest.require(line, "go-to-line.row"));
                var field = bounds(line.holder(), EditorEnterBaselineTest.require(line, "go-to-line.line"));
                assertThat(field.x - row.x).as("the form row's fixed label column and gap").isEqualTo(96 + 12);
                assertThat(field.x + field.width).as("the field takes the control column").isEqualTo(row.x + row.width);
                assertThat(StagedRemoteHost.labels(line.holder())).contains("Line").noneMatch(label -> label.endsWith(":"));
                var caption = bounds(line.holder(), EditorEnterBaselineTest.require(line, "go-to-line.row.caption"));
                assertThat(caption.x).as("the range caption sits in the label column").isEqualTo(row.x);
                assertThat(caption.width).isLessThanOrEqualTo(96);
                var input = (JTextComponent) EditorEnterBaselineTest.require(line, "go-to-line.line.input");
                input.setText("500"); StagedEditorHost.enter(input);
            });
            StagedVaultHost.await(() -> StagedRemoteHost.labels(line.holder()).contains("Enter a line from 1 to 120"), "the inline error");
            onEdt(() -> {
                capture(theme, "go-to-line-invalid", editor, line);
                // A multi-line label keeps its place beside the field when the error appears under the field.
                var label = bounds(line.holder(), labelNamed(line.holder(), "Line"));
                var field = bounds(line.holder(), EditorEnterBaselineTest.require(line, "go-to-line.line.input").getParent());
                assertThat(label.y).as("the label stays beside the field").isLessThan(field.y + field.height);
                line.requestClose();
            });
            onEdt(() -> editor.language("text/python", style -> { }));
            var language = editor.awaitSurface("Language");
            StagedRemoteHost.awaitNamed(language, "language.list");
            onEdt(() -> { capture(theme, "language", editor, language); language.requestClose(); });
            assertThat(editor.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    private static void capture(String theme, String state, StagedEditorHost editor, dev.jasper.app.windows.AuxiliarySurface surface) {
        var size = editor.dialogSize(surface);
        EditorEvidence.capture(theme, state, surface.holder(), size.width, size.height, "question.message", "question.choice.0", "question.choice.1",
            "question.choice.2", "go-to-line.card", "go-to-line.row", "go-to-line.line", "go-to-line.cancel", "go-to-line.go", "language.filter", "language.list");
    }

    private static JLabel labelNamed(java.awt.Container root, String text) {
        for (var child : root.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText()) && child.isVisible()) return label;
            if (child instanceof java.awt.Container nested) { var found = labelNamed(nested, text); if (found != null) return found; }
        }
        return null;
    }

    private static dev.jasper.sdk.ui.Nodes.Node node(dev.jasper.sdk.ui.Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        List<dev.jasper.sdk.ui.Nodes.Node> children = switch (node) {
            case dev.jasper.sdk.ui.Nodes.Split split -> split.children();
            case dev.jasper.sdk.ui.Nodes.Stack stack -> stack.children();
            default -> List.of();
        };
        for (var child : children) { var found = node(child, id); if (found != null) return found; }
        return null;
    }

    private static Class<?> loaderClass(StagedEditorHost editor, String name) {
        try { return editor.loader.loadClass(name); } catch (ClassNotFoundException missing) { throw new AssertionError(missing); }
    }

    /** A remote-style document whose read never completes: the tab stays in its loading state. Synthetic host and name. */
    private static Object pendingDocument(StagedEditorHost editor) {
        Class<?> type = loaderClass(editor, "dev.jasper.editor.api.EditorDocument");
        return java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "key" -> "fixture:report.log"; case "name" -> "report.log"; case "location" -> "fixture@build-01:/var/log/report.log";
            case "host" -> java.util.Optional.of("build-01");
            case "read", "revision", "write" -> new java.util.concurrent.CompletableFuture<>();
            case "hashCode" -> System.identityHashCode(proxy); case "equals" -> proxy == args[0]; case "toString" -> "pending fixture document";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static void feed(StagedEditorHost editor, Object vim, String keys) {
        try {
            Class<?> key = editor.loader.loadClass("dev.jasper.editor.vim.KeyInput");
            for (Object value : (List<?>) key.getMethod("parse", String.class).invoke(null, keys)) call(vim, "feed", new Class<?>[]{key}, value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** One compact row each, inset by the bar padding; the replace field lines up under the query; 24 icon buttons. */
    static void assertCompactAlignedBars(KitView view) {
        var findRow = named(view, "find.row", JComponent.class); var replaceRow = named(view, "find.replace.row", JComponent.class);
        var query = bounds(view, named(view, "find.query", JComponent.class));
        var replacement = bounds(view, named(view, "find.replacement", JComponent.class));
        assertThat(replacement.x).as("replace field under the query").isEqualTo(query.x);
        assertThat(replacement.width).as("replace field as wide as the query").isEqualTo(query.width);
        assertThat(bounds(view, named(view, "find.one", JComponent.class)).x).as("replace actions under the options")
            .isEqualTo(bounds(view, named(view, "find.case", JComponent.class)).x);
        assertThat(query.x - bounds(view, findRow).x).as("bar side padding").isEqualTo(SIDE);
        var close = bounds(view, named(view, "find.close", JComponent.class));
        assertThat(bounds(view, findRow).x + findRow.getWidth() - close.x - close.width).as("bar trailing padding").isEqualTo(SIDE);
        for (String id : List.of("find.case", "find.word", "find.regex", "find.previous", "find.next", "find.close")) {
            var button = named(view, id, AbstractButton.class);
            assertThat(button.getWidth()).as(id).isEqualTo(24); assertThat(button.getHeight()).as(id).isEqualTo(24);
            assertThat(button.getText()).as("%s is an icon button", id).isEmpty();
            assertThat(button.getIcon()).as(id).isNotNull();
        }
        assertThat(named(view, "find.query", JComponent.class).getHeight()).isEqualTo(26);
        assertThat(findRow.getHeight()).isEqualTo(34); assertThat(replaceRow.getHeight()).isEqualTo(34);
        assertThat(bounds(view, replaceRow).y).as("rows stack without a gap").isEqualTo(bounds(view, findRow).y + findRow.getHeight());
    }

    /** The document's identity, text, selection, caret, undo history, dirty state and style survive presentation work. */
    static void assertDocumentKept(StagedEditorHost editor, JTextArea area, Object[] state) {
        assertThat(area.getDocument()).isSameAs(state[1]);
        assertThat(area.getText()).isEqualTo("int foo = 42;\nfoo += 1;\n// edit\n");
        assertThat(area.getSelectionStart()).isEqualTo(1); assertThat(area.getSelectionEnd()).isEqualTo(3);
        assertThat(call(area, "canUndo")).isEqualTo(state[3]);
        assertThat(call(call(editor.selectedTab(), "session"), "dirty")).isEqualTo(state[4]);
        assertThat(call(area, "getSyntaxEditingStyle")).isEqualTo("text/java");
    }
}
