package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.Color;
import java.awt.KeyboardFocusManager;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.swing.TransferHandler;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plan 4b Task 1 (SDK 0.27.0): a field's leading image stands in its kind's glyph box under both shipped kits, a drag of
 * it offers the field's drag text, and a click into a select-on-click field selects all its text.
 */
class LeadingImageFieldsTest {
    static final ControlState READY = ControlState.READY;

    /** A {@code size} by {@code size} PNG in one colour. */
    static PixelImage png(int size, Color color) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(color); g.fillRect(0, 0, size, size); } finally { g.dispose(); }
        var out = new ByteArrayOutputStream();
        try { javax.imageio.ImageIO.write(image, "png", out); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        return new PixelImage(out.toByteArray());
    }

    static Field field(String id, FieldKind kind, String value, Optional<PixelImage> image, Optional<String> drag, boolean selectOnClick) {
        return new Field(id, READY, kind, value, "", "Address", "", Optional.empty(), FieldPresentation.STANDARD, "", "", "", false, image, drag, selectOnClick);
    }

    static ViewSpec fields(Optional<PixelImage> image) {
        return new ViewSpec(new Nodes.Stack("fields", READY, LayoutRole.VERTICAL, List.of(
            field("address", FieldKind.TEXT, "https://example.com/", image, Optional.empty(), false),
            field("find", FieldKind.SEARCH, "", image, Optional.empty(), false))));
    }

    /** The colour at the middle of the icon, painted at 1x. */
    static Color middle(javax.swing.Icon icon) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        return new Color(image.getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2), true);
    }

    @Test void aLeadingImageTakesTheKindsGlyphBoxUnderBothKitsAndGoesWhenRemoved() {
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), kit);
            try (var view = new KitView(context, fields(Optional.of(png(32, Color.RED))), e -> {})) {
                KitFixtures.render(view, 420, 160, 1);
                int extent = com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CONTROL));
                for (String id : List.of("address", "find")) {
                    var field = (TextFields.FieldControl) view.control(id);
                    assertThat(field.leading.isVisible()).as("%s under %s", id, kit.id()).isTrue();
                    assertThat(field.leading.getParent()).isSameAs(field.surface);
                    assertThat(field.leading.getText()).isEmpty();
                    assertThat(field.leading.getIcon().getIconWidth()).as("the glyph's box").isEqualTo(extent);
                    assertThat(field.leading.getIcon().getIconHeight()).isEqualTo(extent);
                    assertThat(middle(field.leading.getIcon())).as("the image, scaled into the box").isEqualTo(Color.RED);
                    assertThat(field.leading.getX()).as("before the text").isLessThan(field.input.getX());
                }
                view.update(fields(Optional.empty()));
                KitFixtures.render(view, 420, 160, 1);
                var address = (TextFields.FieldControl) view.control("address");
                assertThat(address.leading.isVisible()).as("a text field has no glyph").isFalse();
                var find = (TextFields.FieldControl) view.control("find");
                assertThat(find.leading.isVisible()).as("the search glyph under the default kit only").isEqualTo(kit == Kit.DEFAULT);
                if (kit == Kit.DEFAULT) assertThat(middle(find.leading.getIcon())).isNotEqualTo(Color.RED);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void anImageThatDoesNotDecodeLeavesTheKindsGlyph() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            byte[] signatureOnly = new byte[64];
            System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, 0, signatureOnly, 0, 8);
            try (var view = new KitView(context, fields(Optional.of(new PixelImage(signatureOnly))), e -> {})) {
                KitFixtures.render(view, 420, 160, 1);
                assertThat(((TextFields.FieldControl) view.control("address")).leading.isVisible()).isFalse();
                var find = (TextFields.FieldControl) view.control("find");
                assertThat(find.leading.isVisible()).isTrue();
                assertThat(middle(find.leading.getIcon())).isNotEqualTo(Color.RED);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void draggingTheLeadingImageOffersItsTextAsAUrlAsTextAndAsTheFileOfAFileUrl(@TempDir Path folder) throws Exception {
        Path page = Files.writeString(folder.resolve("page.html"), "<p>Hi</p>");
        String file = page.toUri().toString();
        Edt.run(() -> {
            var context = KitFixtures.context();
            var image = Optional.of(png(16, Color.BLUE));
            var spec = new ViewSpec(new Nodes.Stack("fields", READY, LayoutRole.VERTICAL, List.of(
                field("web", FieldKind.TEXT, "example.com", image, Optional.of("https://example.com/a?b=1"), false),
                field("local", FieldKind.TEXT, "page.html", image, Optional.of(file), false),
                field("about", FieldKind.TEXT, "Welcome", image, Optional.of("jasper-about:welcome"), false),
                field("still", FieldKind.TEXT, "example.com", image, Optional.empty(), false))));
            try (var view = new KitView(context, spec, e -> {})) {
                var url = new DataFlavor("application/x-java-url; class=java.net.URL");
                var web = ((TextFields.FieldControl) view.control("web")).leadingTransfer();
                assertThat(web.getTransferDataFlavors()).containsExactly(url, DataFlavor.stringFlavor);
                assertThat(web.getTransferData(url)).isEqualTo(java.net.URI.create("https://example.com/a?b=1").toURL());
                assertThat(web.getTransferData(DataFlavor.stringFlavor)).isEqualTo("https://example.com/a?b=1");
                var local = ((TextFields.FieldControl) view.control("local")).leadingTransfer();
                assertThat(local.getTransferDataFlavors()).containsExactly(url, DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor);
                assertThat(local.getTransferData(DataFlavor.javaFileListFlavor)).isEqualTo(List.of(page.toFile()));
                assertThat(local.getTransferData(DataFlavor.stringFlavor)).isEqualTo(file);
                var about = ((TextFields.FieldControl) view.control("about")).leadingTransfer();
                assertThat(about.getTransferDataFlavors()).as("no java.net.URL for the scheme").containsExactly(DataFlavor.stringFlavor);
                assertThatThrownBy(() -> about.getTransferData(url)).isInstanceOf(java.awt.datatransfer.UnsupportedFlavorException.class);
                var still = (TextFields.FieldControl) view.control("still");
                assertThat(still.leadingTransfer()).isNull();
                assertThat(still.leading.getTransferHandler().getSourceActions(still.leading)).isEqualTo(TransferHandler.NONE);
                var drags = (TextFields.FieldControl) view.control("web");
                assertThat(drags.leading.getTransferHandler().getSourceActions(drags.leading)).isEqualTo(TransferHandler.COPY);
            } catch (Exception failure) { throw new AssertionError(failure); }
            finally { KitViewTest.release(context); }
        });
    }

    static void click(JTextComponent input, int x) {
        long when = System.currentTimeMillis();
        input.dispatchEvent(new MouseEvent(input, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON1_DOWN_MASK, x, 4, 1, false, MouseEvent.BUTTON1));
        input.dispatchEvent(new MouseEvent(input, MouseEvent.MOUSE_RELEASED, when, 0, x, 4, 1, false, MouseEvent.BUTTON1));
        input.dispatchEvent(new MouseEvent(input, MouseEvent.MOUSE_CLICKED, when, 0, x, 4, 1, false, MouseEvent.BUTTON1));
    }

    @Test void aClickIntoASelectOnClickFieldSelectsAllItsTextAndAClickInsideItPlacesTheCaret() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var focus = new SettingsKitTest.Focus();
            var spec = new ViewSpec(new Nodes.Stack("fields", READY, LayoutRole.VERTICAL, List.of(
                field("address", FieldKind.TEXT, "https://example.com/", Optional.empty(), Optional.empty(), true),
                field("plain", FieldKind.TEXT, "https://example.com/", Optional.empty(), Optional.empty(), false))));
            try (var view = new KitView(context, spec, e -> {})) {
                KitFixtures.render(view, 420, 160, 1);
                var address = ((TextFields.FieldControl) view.control("address")).input;
                var plain = ((TextFields.FieldControl) view.control("plain")).input;
                address.setCaretPosition(0); plain.setCaretPosition(0);
                click(address, 30);
                assertThat(address.getSelectedText()).as("a click that brings the focus in").isEqualTo("https://example.com/");
                click(plain, 30);
                assertThat(plain.getSelectedText()).as("a plain field places the caret").isNull();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(focus); focus.focus(address);
                click(address, 30);
                assertThat(address.getSelectedText()).as("a click in the focused field places the caret").isNull();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(prior);
                // A press that drags out a selection of its own keeps it.
                address.setCaretPosition(0);
                long when = System.currentTimeMillis();
                address.dispatchEvent(new MouseEvent(address, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON1_DOWN_MASK, 30, 4, 1, false, MouseEvent.BUTTON1));
                address.select(2, 6);
                address.dispatchEvent(new MouseEvent(address, MouseEvent.MOUSE_RELEASED, when, 0, 60, 4, 1, false, MouseEvent.BUTTON1));
                assertThat(address.getSelectedText()).isEqualTo("tps:");
                // A right press is not a click into the field.
                address.setCaretPosition(0);
                address.dispatchEvent(new MouseEvent(address, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON3_DOWN_MASK, 30, 4, 1, true, MouseEvent.BUTTON3));
                address.dispatchEvent(new MouseEvent(address, MouseEvent.MOUSE_RELEASED, when, 0, 30, 4, 1, true, MouseEvent.BUTTON3));
                assertThat(address.getSelectedText()).isNull();
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); KitViewTest.release(context); }
        });
    }
}
