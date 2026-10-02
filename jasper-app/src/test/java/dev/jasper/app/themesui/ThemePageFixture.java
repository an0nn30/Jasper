package dev.jasper.app.themesui;

import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.nio.file.Path;
import java.util.concurrent.*;

final class ThemePageFixture {
    static ThemeActions actions(PageContext context) {
        return new ThemeActions(context,Path.of("unused-test-home"),new AuxiliaryWindows(UiState.inMemory(),s->{throw new AssertionError("No dialog expected");}),
            ()->CompletableFuture.completedFuture(null),p->{},p->false,new ThemeActionsTest.QueueWorker());
    }
}
