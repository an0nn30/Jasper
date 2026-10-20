package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The chooser's replace and new-folder questions are kit sheets (Plan 2 Task 6c), answered here by a fake modal window
 * that plays the user while the sheet is showing, as a real modal dialog blocks until it closes.
 */
@ExtendWith(EdtTestExtension.class)
class ChooserPromptsTest {
    private final List<String> log = new ArrayList<>();

    /** A modal that runs {@code user} on the mounted sheet while "showing", then closes like a dismissed window. */
    private ChooserPrompts prompts(Consumer<JComponent> user) {
        return new ChooserPrompts(KitTestSupport::context, title -> new ChooserPrompts.Modal() {
            JComponent content;
            boolean closed;
            @Override public void setContent(JComponent value) { content = value; log.add("title:" + title); }
            @Override public void show(Runnable shown) {
                content.setSize(content.getPreferredSize());
                KitTestSupport.layout(content);
                shown.run();
                user.accept(content);
                log.add(closed ? "closed" : "dismissed");
            }
            @Override public void close() { if (!closed) log.add("close"); closed = true; }
        });
    }

    private static void press(JComponent sheet, String name) { KitTestSupport.require(sheet, name, AbstractButton.class).doClick(0); }

    private static void key(JComponent sheet, String form, String action) {
        var root = KitTestSupport.require(sheet, form, JComponent.class);
        root.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
    }

    private static JTextComponent input(Component root) {
        if (root instanceof JTextComponent text) return text;
        if (root instanceof Container container) for (var child : container.getComponents()) {
            var found = input(child);
            if (found != null) return found;
        }
        return null;
    }

    @Test void replaceAsksByNameAndOnlyReplaceSaysYes() {
        assertThat(prompts(sheet -> press(sheet, ChooserPrompts.REPLACE)).confirmReplace("notes.txt")).isTrue();
        assertThat(log).containsExactly("title:Confirm Save As", "close", "closed");
        assertThat(prompts(sheet -> {
            assertThat(KitTestSupport.texts(sheet)).contains("notes.txt already exists. Do you want to replace it?");
            press(sheet, ChooserPrompts.CANCEL);
        }).confirmReplace("notes.txt")).isFalse();
        assertThat(prompts(sheet -> key(sheet, "replace-sheet", "primary")).confirmReplace("a")).as("Enter replaces, as the old default Yes").isTrue();
        assertThat(prompts(sheet -> key(sheet, "replace-sheet", "cancel")).confirmReplace("a")).as("Escape answers no").isFalse();
        assertThat(prompts(sheet -> { }).confirmReplace("a")).as("closing the window answers no").isFalse();
    }

    @Test void aNewFolderNeedsANameCreateIsDisabledWhileBlankAndCancelGivesNothing() {
        assertThat(prompts(sheet -> {
            var create = KitTestSupport.require(sheet, ChooserPrompts.CREATE, AbstractButton.class);
            assertThat(create.isEnabled()).as("disabled while the name is blank").isFalse();
            key(sheet, "folder-sheet", "primary");
            assertThat(log).as("Enter does nothing while the primary is disabled").doesNotContain("close");
            input(sheet).setText("  ");
            assertThat(create.isEnabled()).isFalse();
            input(sheet).setText("  reports  ");
            assertThat(create.isEnabled()).isTrue();
            key(sheet, "folder-sheet", "primary");
        }).askFolderName()).contains("reports");
        log.clear();
        assertThat(prompts(sheet -> { input(sheet).setText("drafts"); press(sheet, ChooserPrompts.CREATE); }).askFolderName()).contains("drafts");
        assertThat(prompts(sheet -> { input(sheet).setText("drafts"); key(sheet, "folder-sheet", "cancel"); }).askFolderName()).isEmpty();
        assertThat(prompts(sheet -> { input(sheet).setText("drafts"); press(sheet, ChooserPrompts.CANCEL); }).askFolderName()).isEmpty();
        assertThat(prompts(sheet -> input(sheet).setText("drafts")).askFolderName()).as("closing the window cancels").isEqualTo(Optional.empty());
        assertThat(prompts(sheet -> {
            assertThat(KitTestSupport.require(sheet, ChooserPrompts.NAME, JComponent.class)).isNotNull();
            assertThat(input(sheet).getAccessibleContext().getAccessibleName()).isEqualTo("Folder name");
        }).askFolderName()).isEmpty();
    }
}
