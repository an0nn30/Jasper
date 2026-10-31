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
        return field(id, READY, kind, value, image, "", "", drag, selectOnClick);
    }

    static Field field(String id, ControlState state, FieldKind kind, String value, Optional<PixelImage> image, String icon, String name,
                       Optional<String> drag, boolean selectOnClick) {
        return new Field(id, state, kind, value, "", "Address", "", Optional.empty(), FieldPresentation.STANDARD, "", "", "", false, image, icon, name,
            drag, selectOnClick);
    }

    static int[] pixels(javax.swing.Icon icon) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    static PixelImage signatureOnly() {
        byte[] data = new byte[64];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, 0, data, 0, 8);
        return new PixelImage(data);
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

    @Test void anImageThatDoesNotDecodeLeavesTheLeadingIconElseTheKindsGlyph() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = new ViewSpec(new Nodes.Stack("fields", READY, LayoutRole.VERTICAL, List.of(
                field("address", FieldKind.TEXT, "https://example.com/", Optional.of(signatureOnly()), Optional.empty(), false),
                field("find", FieldKind.SEARCH, "", Optional.of(signatureOnly()), Optional.empty(), false),
                field("page", READY, FieldKind.TEXT, "https://example.com/", Optional.of(signatureOnly()), "network", "Site icon",
                    Optional.of("https://example.com/"), false))));
            try (var view = new KitView(context, spec, e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                assertThat(((TextFields.FieldControl) view.control("address")).leading.isVisible()).isFalse();
                var find = (TextFields.FieldControl) view.control("find");
                assertThat(find.leading.isVisible()).isTrue();
                assertThat(middle(find.leading.getIcon())).isNotEqualTo(Color.RED);
                var page = (TextFields.FieldControl) view.control("page");
                int extent = com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CONTROL));
                assertThat(pixels(page.leading.getIcon())).as("the leading icon")
                    .isEqualTo(pixels(context.icons().icon("network", context.theme(), dev.jasper.ui.theme.SemanticColor.TEXT_PLACEHOLDER, extent)));
                assertThat(page.leadingTransfer()).as("the icon drags as the image would").isNotNull();
            } finally { KitViewTest.release(context); }
        });
    }

    /** Plan 4b Task 1 (P14): without an image the leading slot draws its icon name in the kit's glyph colours, in the glyph's box. */
    @Test void aLeadingIconDrawsInTheKitsGlyphColoursAndAnImageWinsOverIt() {
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), kit);
            java.util.function.Function<Optional<PixelImage>, ViewSpec> spec = image -> new ViewSpec(new Nodes.Stack("fields", READY, LayoutRole.VERTICAL, List.of(
                field("address", READY, FieldKind.TEXT, "https://example.com/", image, "network", "", Optional.empty(), false),
                field("find", READY, FieldKind.SEARCH, "", image, "network", "", Optional.empty(), false))));
            try (var view = new KitView(context, spec.apply(Optional.empty()), e -> {})) {
                KitFixtures.render(view, 420, 160, 1);
                int extent = com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CONTROL));
                var expected = pixels(context.icons().icon("network", context.theme(), dev.jasper.ui.theme.SemanticColor.TEXT_PLACEHOLDER, extent));
                for (String id : List.of("address", "find")) {
                    var field = (TextFields.FieldControl) view.control(id);
                    assertThat(field.leading.isVisible()).as("%s under %s", id, kit.id()).isTrue();
                    assertThat(field.leading.getParent()).isSameAs(field.surface);
                    assertThat(field.leading.getIcon().getIconWidth()).isEqualTo(extent);
                    assertThat(pixels(field.leading.getIcon())).as("the named icon in place of the kind's glyph").isEqualTo(expected);
                }
                view.update(spec.apply(Optional.of(png(32, Color.RED))));
                for (String id : List.of("address", "find"))
                    assertThat(middle(((TextFields.FieldControl) view.control(id)).leading.getIcon())).as("the image wins").isEqualTo(Color.RED);
            } finally { KitViewTest.release(context); }
        });
    }

    /** Plan 4b Task 1 (P13): the leading slot's name is its accessible name and its tooltip; a kind's glyph has neither. */
    @Test void theLeadingSlotsNameIsItsAccessibleNameAndTooltip() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            java.util.function.Function<String, ViewSpec> spec = name -> new ViewSpec(new Nodes.Stack("fields", READY, LayoutRole.VERTICAL, List.of(
                field("address", READY, FieldKind.TEXT, "https://example.com/", Optional.of(png(16, Color.BLUE)), "", name, Optional.empty(), false),
                field("find", FieldKind.SEARCH, "", Optional.empty(), Optional.empty(), false))));
            try (var view = new KitView(context, spec.apply("Site icon - drag to copy URL"), e -> {})) {
                var address = (TextFields.FieldControl) view.control("address");
                assertThat(address.leading.getAccessibleContext().getAccessibleName()).isEqualTo("Site icon - drag to copy URL");
                assertThat(address.leading.getToolTipText()).isEqualTo("Site icon - drag to copy URL");
                var find = (TextFields.FieldControl) view.control("find");
                assertThat(find.leading.getAccessibleContext().getAccessibleName()).as("an unnamed glyph").isNullOrEmpty();
                assertThat(find.leading.getToolTipText()).isNull();
                view.update(spec.apply(""));
                assertThat(address.leading.getAccessibleContext().getAccessibleName()).isNullOrEmpty();
                assertThat(address.leading.getToolTipText()).isNull();
            } finally { KitViewTest.release(context); }
        });
    }

    /** Plan 4b Task 1 (review R9): an update that changes only the text, as each keystroke does, keeps the leading icon. */
    @Test void anUpdateOfTheTextKeepsTheLeadingIcon() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var image = Optional.of(png(16, Color.BLUE));
            java.util.function.Function<String, ViewSpec> spec = text -> new ViewSpec(new Nodes.Stack("fields", READY, LayoutRole.VERTICAL, List.of(
                field("address", READY, FieldKind.TEXT, text, image, "", "", Optional.empty(), false),
                field("loading", READY, FieldKind.TEXT, text, Optional.empty(), "network", "", Optional.empty(), false),
                field("find", READY, FieldKind.SEARCH, text, Optional.empty(), "", "", Optional.empty(), false))));
            try (var view = new KitView(context, spec.apply("e"), e -> {})) {
                var shown = new java.util.HashMap<String, javax.swing.Icon>();
                for (String id : List.of("address", "loading", "find")) shown.put(id, ((TextFields.FieldControl) view.control(id)).leading.getIcon());
                for (String text : List.of("ex", "exa", "exam")) view.update(spec.apply(text));
                for (String id : List.of("address", "loading", "find"))
                    assertThat(((TextFields.FieldControl) view.control(id)).leading.getIcon()).as(id).isSameAs(shown.get(id));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void draggingTheLeadingImageOffersItsTextAsAUrlAsTextAndAsTheFileOfAFileUrl(@TempDir Path folder) throws Exception {
        Path page = Files.writeString(folder.resolve("page.html"), "<p>Hi</p>");
        String file = page.toUri().toString();
        // The kit never touches the file system to offer the file: one that does not exist is offered too.
        Path missing = folder.resolve("missing.html");
        String absent = missing.toUri().toString();
        Edt.run(() -> {
            var context = KitFixtures.context();
            var image = Optional.of(png(16, Color.BLUE));
            var spec = new ViewSpec(new Nodes.Stack("fields", READY, LayoutRole.VERTICAL, List.of(
                field("web", FieldKind.TEXT, "example.com", image, Optional.of("https://example.com/a?b=1"), false),
                field("local", FieldKind.TEXT, "page.html", image, Optional.of(file), false),
                field("about", FieldKind.TEXT, "Welcome", image, Optional.of("jasper-about:welcome"), false),
                field("absent", FieldKind.TEXT, "missing.html", image, Optional.of(absent), false),
                field("loading", READY, FieldKind.TEXT, "example.com", Optional.empty(), "network", "", Optional.of("https://example.com/"), false),
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
                var gone = ((TextFields.FieldControl) view.control("absent")).leadingTransfer();
                assertThat(gone.getTransferDataFlavors()).containsExactly(url, DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor);
                assertThat(gone.getTransferData(DataFlavor.javaFileListFlavor)).isEqualTo(List.of(missing.toFile()));
                assertThat(Files.exists(missing)).isFalse();
                var loading = ((TextFields.FieldControl) view.control("loading")).leadingTransfer();
                assertThat(loading.getTransferData(DataFlavor.stringFlavor)).as("a leading icon drags too").isEqualTo("https://example.com/");
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
                field("plain", FieldKind.TEXT, "https://example.com/", Optional.empty(), Optional.empty(), false),
                field("busy", new ControlState(true, true, true, ""), FieldKind.TEXT, "https://example.com/", Optional.empty(), "", "", Optional.empty(), true))));
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
                // A busy field takes no click, as it takes no typing.
                var busy = ((TextFields.FieldControl) view.control("busy")).input;
                busy.setCaretPosition(0);
                click(busy, 30);
                assertThat(busy.getSelectedText()).isNull();
                // A right press is not a click into the field.
                address.setCaretPosition(0);
                address.dispatchEvent(new MouseEvent(address, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON3_DOWN_MASK, 30, 4, 1, true, MouseEvent.BUTTON3));
                address.dispatchEvent(new MouseEvent(address, MouseEvent.MOUSE_RELEASED, when, 0, 30, 4, 1, true, MouseEvent.BUTTON3));
                assertThat(address.getSelectedText()).isNull();
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); KitViewTest.release(context); }
        });
    }
}
