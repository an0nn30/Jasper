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
}
