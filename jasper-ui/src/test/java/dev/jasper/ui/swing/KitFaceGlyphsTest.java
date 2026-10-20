package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypographyTest;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/**
 * Under a kit whose own face lacks a glyph (IBM Plex has no macOS modifier symbols and no CJK), the kit-drawn texts that
 * carry such glyphs draw in a face that has them: menu shortcuts, keycaps, data labels and technical fields.
 */
@SuppressWarnings("try")
class KitFaceGlyphsTest {
    private static final String SHORTCUT = "⇧⌘P", CJK = "報告.txt";

    private static KitContext glyphless(String face) {
        var base = KitFixtures.context();
        var kit = base.kit();
        var faces = new Kit(kit.id(), kit.metrics(), kit.type(), FaceSet.of(face, face, face, face, face), kit.controls(),
            kit.colors(), kit.uiKeys());
        return KitContext.of(faces, base.binding(), base.route(), base.icons(), base.animationsEnabled(),
            id -> "open".equals(id) ? SHORTCUT : "");
    }

    @Test void aMenuShortcutAndKeycapsDrawTheirSymbols() throws Exception {
        String face = TypographyTest.registeredWithoutModifierSymbols();
        assertThat(new Font(face, Font.PLAIN, 12).canDisplayUpTo(SHORTCUT)).as("the test face has no modifier symbols").isZero();
        Edt.run(() -> {
            var context = glyphless(face);
            try {
                var item = new Menus.Item(context);
                item.describe(new MenuItem("o", "Open", "", "o", true, false).withShortcut("open"));
                assertThat(item.shortcutText).isEqualTo(SHORTCUT);
                assertThat(item.getFont().getFontName(Locale.ROOT)).as("the label keeps the kit's face").isEqualTo(face);
                assertThat(item.shortcutFont().canDisplayUpTo(SHORTCUT)).as("the shortcut's symbols").isEqualTo(-1);
                var caps = new Keycaps(context, new KeycapSequence(List.of("⌘", "⇧", "P"), "Command Shift P"));
                assertThat(caps.font().canDisplayUpTo("⌘⇧P")).as("the keycaps' symbols").isEqualTo(-1);
                var letters = new Keycaps(context, new KeycapSequence(List.of("Cmd", "P"), "Command P"));
                assertThat(letters.font().getFontName(Locale.ROOT)).as("keys the face draws keep it").isEqualTo(face);
            } finally { release(context); }
        });
    }

    @Test void aDataLabelAndATechnicalFieldDrawCjk() throws Exception {
        String face = TypographyTest.registeredWithoutModifierSymbols();
        assertThat(new Font(face, Font.PLAIN, 12).canDisplayUpTo(CJK)).as("the test face has no CJK").isZero();
        Edt.run(() -> {
            var context = glyphless(face);
            try (var view = new KitView(context, stack(new Text("path", ControlState.READY, CJK, TextRole.MONO),
                    new Text("plain", ControlState.READY, "a.txt", TextRole.MONO), field("where", FieldKind.PATH, "a.txt")), event -> {})) {
                assertThat(find(view, "path", JLabel.class).getFont().canDisplayUpTo(CJK)).as("a MONO label").isEqualTo(-1);
                assertThat(find(view, "plain", JLabel.class).getFont().getFontName(Locale.ROOT)).as("the data face").isEqualTo(face);
                var input = editor(view, "where");
                assertThat(input.getFont().getFontName(Locale.ROOT)).as("a PATH field in the data face").isEqualTo(face);
                input.setText(CJK);
                assertThat(input.getFont().canDisplayUpTo(CJK)).as("typed CJK").isEqualTo(-1);
                input.setText("b.txt");
                assertThat(input.getFont().getFontName(Locale.ROOT)).as("the data face again").isEqualTo(face);
            } finally { release(context); }
        });
    }

    /**
     * A selected result keeps a face that draws its text: the Run window's first suggestion is a selected command with a
     * shortcut tag, and its title and tag both take the platform face when the kit's face lacks a modifier symbol.
     */
    @Test void aSelectedResultsTitleAndTagDrawTheirSymbols() throws Exception {
        String face = TypographyTest.registeredWithoutModifierSymbols();
        var items = List.of(
            new ListItem("a", java.util.Optional.empty(), List.of(new Text("a.title", ControlState.READY, "Copy ⌘C", TextRole.BODY)),
                List.of(), List.of(new Text("a.tag", ControlState.READY, "⌘T", TextRole.CAPTION))),
            new ListItem("b", java.util.Optional.empty(), List.of(new Text("b.title", ControlState.READY, "New Window", TextRole.BODY)),
                List.of(), List.of(new Text("b.tag", ControlState.READY, "N", TextRole.CAPTION))));
        java.util.function.Function<String, ViewSpec> selecting = id -> new ViewSpec(new ItemList("results", ControlState.READY,
            ListKind.RESULTS, items, java.util.Set.of(id), "run", List.of(), SelectionMode.SINGLE, java.util.Optional.empty(),
            java.util.OptionalInt.of(2)));
        Edt.run(() -> {
            var context = glyphless(face);
            try (var view = new KitView(context, selecting.apply("a"), event -> {})) {
                for (boolean runWindow : new boolean[] {false, true}) {
                    view.runWindow(runWindow);
                    view.update(selecting.apply("a"));
                    KitFixtures.render(view, 436, 80, 1);
                    assertThat(find(view, "a.title", JLabel.class).getFont().canDisplayUpTo("Copy ⌘C")).as("the title, run window %s", runWindow).isEqualTo(-1);
                    assertThat(find(view, "a.tag", JLabel.class).getFont().canDisplayUpTo("⌘T")).as("the tag, run window %s", runWindow).isEqualTo(-1);
                    view.update(selecting.apply("b"));
                    KitFixtures.render(view, 436, 80, 1);
                    assertThat(find(view, "b.title", JLabel.class).getFont().getFontName(Locale.ROOT)).as("a selected text the face draws keeps it").isEqualTo(face);
                    assertThat(find(view, "b.tag", JLabel.class).getFont().getFontName(Locale.ROOT)).isEqualTo(face);
                }
            } finally { release(context); }
        });
    }
}
