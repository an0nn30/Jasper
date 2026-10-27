package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.notifications.CommandNotice;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.JasperWindow;
import dev.jasper.app.workspace.WindowShell;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * An application with stand-in frames and windows A, B and C, where window A holds two terminal tabs; the frames list
 * records every window brought to the front as {@code front w<index>}.
 */
final class PertainingWindowFixture {
    final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    final ArrayDeque<String> frames = new ArrayDeque<>();
    final JasperApplication application;
    JasperWindow a, b, c;

    PertainingWindowFixture() throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, DesktopTestSupport.idleLauncher(pending), new CommandHistory(), null, () -> { });
            held[0].frames = window -> {
                int index = held[0].windows().windows().indexOf(window);
                return new WindowShell(() -> frames.add("show w" + index), () -> frames.add("front w" + index),
                    () -> frames.add("dispose w" + index), () -> frames.add("discard w" + index), () -> null,
                    component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            };
        });
        application = held[0];
    }

    /** Three windows; A, the one used last at the end of this, holds a second terminal tab. EDT. */
    void open() {
        application.residency(true);
        application.open(List.of(), JasperApplication.HANDOFF);
        a = application.windows().windows().getFirst();
        b = application.windows().openWindow(null).orElseThrow();
        c = application.windows().openWindow(null).orElseThrow();
        a.events().activated().accept(true);
        application.open(List.of(HOME), JasperApplication.HANDOFF);
    }

    /** The first terminal pane of window A. */
    UUID paneOfA() {
        return application.terminals().window(a.id()).orElseThrow().tabs().get().getFirst().terminal().get().orElseThrow().id();
    }

    /** A long command in that pane finished out of sight; its card targets window A. Returns the notification's id. */
    UUID finished(UUID pane, Runnable activate) {
        application.commandNotifier().finished(pane, "make", OptionalInt.of(0), Duration.ofHours(1),
            new CommandNotice.Origin(false, false, false, false), a.id(), activate);
        return shownEverywhere(application.notificationCenter().history().getFirst().id());
    }

    /**
     * Shows the entry's card in every window's layer, as the feature does for a requested card; headless the application
     * is never the active window, so the delivery rule sends no card of its own.
     */
    UUID shownEverywhere(UUID id) {
        var entry = application.notificationCenter().find(id).orElseThrow();
        for (var window : application.windows().windows()) application.notificationViews().layer(window.id()).show(entry);
        return id;
    }

    /** Moves window A's first tab to window C. */
    void moveFirstTabToC() {
        var tab = a.content().windowLayout().parentTabs().getFirst().id();
        application.windows().moveTabToWindow(a.content(), tab, c.content());
    }

    void close() throws Exception { edt(application::quitNow); }
}
