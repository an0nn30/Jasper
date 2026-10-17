package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 11.6 and 11.7: what opens the Run window, what Browse… does, and the open state the status item reads. */
class RunWindowOpenersTest {
    /** Review Focus 5. */
    @Test void browseOpensTheChooserAndTheRunWindowStays() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = RunWindowModelessTest.open(PaletteLook.RUN_WINDOW)) {
                var palette = o.owner().commandPalette();
                var asked = new ArrayList<Optional<Path>>();
                var opened = new ArrayList<String>();
                o.owner().fileChooser = folder -> { asked.add(folder); return Optional.empty(); };
                o.owner().onOpenFile = (file, line) -> opened.add(file + ":" + line);
                PaletteTestSupport.handle(palette.view(), "run.browse");
                assertThat(asked).as("the chooser from the pane's folder").hasSize(1);
                assertThat(opened).as("cancelled: nothing opens").isEmpty();
                assertThat(palette.isOpen()).as("the Run window stays open").isTrue();
                Path file = DesktopTestSupport.HOME.resolve("notes.txt");
                o.owner().fileChooser = folder -> Optional.of(file);
                PaletteTestSupport.handle(palette.view(), "run.browse");
                assertThat(opened).as("as a Cmd-click opens it, at no line").containsExactly(file + ":0");
                assertThat(palette.isOpen()).isTrue();
            }
        });
    }

    @Test void theWindowSaysWhetherTheRunWindowIsOpenAndEveryOpenerFocusesIt() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = RunWindowModelessTest.open(PaletteLook.RUN_WINDOW)) {
                var palette = o.owner().commandPalette();
                assertThat(o.owner().paletteOpen()).isTrue();
                o.owner().chrome().search().click();
                assertThat(palette.isOpen()).as("the title field focuses it").isTrue();
                o.owner().action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(o.owner(), 0, "status"));
                assertThat(palette.isOpen()).isTrue();
                palette.dismiss();
                assertThat(o.owner().paletteOpen()).isFalse();
                o.owner().action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(o.owner(), 0, "status"));
                LayoutTestSupport.layoutTree(o.root());
                assertThat(palette.isOpen()).as("closed, the action opens it").isTrue();
            }
        });
    }
}
