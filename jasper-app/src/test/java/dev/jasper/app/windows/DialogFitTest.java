package dev.jasper.app.windows;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Plan 2 Task 8b fix round 1 (I1): a dialog the host packed itself follows its kit content's preferred size, growing and
 * shrinking, clamped to the screen, until the user resizes it; content that is not a kit view and dialogs with remembered
 * bounds are left alone. A stand-in for the native dialog records the bounds the rule sets (tests are headless).
 */
class DialogFitTest {
    /** A native dialog stand-in: frame insets of 10 x 30 around the content, as pack() would add. */
    static final class FakeDialog implements DialogFit.Target {
        final JPanel content;
        Rectangle bounds = new Rectangle(100, 100, 0, 0);
        Rectangle usable = new Rectangle(0, 0, 1440, 900);
        final List<Rectangle> set = new ArrayList<>();
        FakeDialog(JPanel content) { this.content = content; }
        void pack() { var p = packedSize(); bounds = new Rectangle(bounds.x, bounds.y, p.width, p.height); }
        @Override public Dimension size() { return bounds.getSize(); }
        @Override public Dimension packedSize() { var p = content.getPreferredSize(); return new Dimension(p.width + 10, p.height + 30); }
        @Override public Rectangle usableBounds() { return new Rectangle(usable); }
        @Override public Point location() { return bounds.getLocation(); }
        @Override public void setBounds(Rectangle value) { bounds = new Rectangle(value); set.add(new Rectangle(value)); }
    }

    final JPanel body = new JPanel(null);
    AuxiliarySurface surface;
    FakeDialog dialog;
    DialogFit fit;

    /** A shown dialog holding {@code body}; {@code kit} marks the content as a mounted kit view. */
    void show(boolean kit, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var windows = new AuxiliaryWindows(UiState.inMemory(), s -> new AuxiliarySurface.Shell(() -> { dialog.pack(); fit.packed(); }, () -> { },
                () -> { }, title -> { }, () -> dialog.bounds, (title, initial) -> Optional.empty()));
            surface = windows.dialog("Forward a Port", true, java.util.UUID.randomUUID());
            body.setPreferredSize(new Dimension(400, height));
            surface.setContent(body);
            surface.fitContent(kit);
            dialog = new FakeDialog((JPanel) surface.holder());
            fit = new DialogFit(surface.holder(), dialog);
            surface.fitWith(fit);
            surface.show();
        });
    }

    /**
     * The content's preferred height changes, as a kit view's descriptor update does (the host reports it through
     * {@code contentChanged}; in a real window the holder's invalidation does too); then the event queue drains.
     */
    void content(int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> { body.setPreferredSize(new Dimension(400, height)); body.revalidate(); surface.contentChanged(); });
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    @Test void aKitDialogGrowsAndShrinksWithItsContent() throws Exception {
        show(true, 300);
        assertThat(dialog.size()).isEqualTo(new Dimension(410, 330));
        content(400);
        assertThat(dialog.size()).as("More reveals a row: the dialog grows").isEqualTo(new Dimension(410, 430));
        assertThat(dialog.location()).as("in place").isEqualTo(new Point(100, 100));
        content(300);
        assertThat(dialog.size()).as("Less hides it: back to the original").isEqualTo(new Dimension(410, 330));
        int before = dialog.set.size();
        content(300);
        assertThat(dialog.set).as("no change, no re-pack").hasSize(before);
    }

    @Test void itStaysOnItsScreen() throws Exception {
        show(true, 300);
        dialog.usable = new Rectangle(0, 25, 1440, 600);
        content(900);
        assertThat(dialog.bounds).as("clamped to the usable height and moved up only as needed").isEqualTo(new Rectangle(100, 25, 410, 600));
    }

    @Test void aUserResizeEndsTheRule() throws Exception {
        show(true, 300);
        content(400);
        SwingUtilities.invokeAndWait(() -> fit.resized(new Dimension(410, 430)));
        assertThat(fit.userResized()).as("the rule's own size is not a user resize").isFalse();
        SwingUtilities.invokeAndWait(() -> { dialog.bounds.setSize(700, 500); fit.resized(new Dimension(700, 500)); });
        assertThat(fit.userResized()).isTrue();
        int before = dialog.set.size();
        content(200);
        assertThat(dialog.set).as("a user-resized dialog is never re-packed").hasSize(before);
        assertThat(dialog.size()).isEqualTo(new Dimension(700, 500));
    }

    @Test void rawContentAndClosedDialogsAreLeftAlone() throws Exception {
        show(false, 300);
        content(400);
        assertThat(dialog.set).as("raw Swing content keeps its own sizing").isEmpty();
        SwingUtilities.invokeAndWait(() -> surface.fitContent(true));
        content(500);
        assertThat(dialog.size()).isEqualTo(new Dimension(410, 530));
        SwingUtilities.invokeAndWait(() -> surface.close());
        int before = dialog.set.size();
        content(200);
        assertThat(dialog.set).as("a closed dialog follows nothing").hasSize(before);
    }

    @Test void nothingHappensBeforeTheHostPackedTheDialog() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var holder = new JPanel(new java.awt.BorderLayout());
            holder.add(body);
            var target = new FakeDialog(holder);
            var early = new DialogFit(holder, target);
            body.setPreferredSize(new Dimension(400, 500));
            early.contentChanged();
            early.resized(new Dimension(1, 1));
            assertThat(early.userResized()).isFalse();
            dialog = target;
        });
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
        assertThat(dialog.set).isEmpty();
    }
}
