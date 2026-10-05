package dev.jasper.app.themesui;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Concrete, owner-bound kit dialogs. All text is literal; all methods EDT only. */
final class ThemeDialogs implements AutoCloseable {
    private final AuxiliaryWindows windows;
    private final Supplier<AuxiliarySurface> owner;
    private final Supplier<KitContext> kit;
    private final List<AuxiliarySurface> open = new ArrayList<>();

    ThemeDialogs(AuxiliaryWindows windows, Supplier<AuxiliarySurface> owner, Supplier<KitContext> kit) {
        this.windows = windows; this.owner = owner; this.kit = kit;
    }

    boolean available() { var parent = owner.get(); return parent != null && !parent.closed() && windows.open().contains(parent); }

    KitContext kit() { return kit.get(); }

    /** Shows the kit view {@code contents} makes from accept/reject; the view closes with the dialog. */
    void view(String title, BiFunction<Runnable, Runnable, KitView> contents, Runnable cancel) {
        if (!available()) { cancel.run(); return; }
        var surface = windows.dialog(title, true, owner.get()); open.add(surface); var answered = new AtomicBoolean();
        Runnable accept = () -> { answered.set(true); surface.close(); };
        Runnable reject = surface::close;
        var view = contents.apply(accept, reject);
        surface.onClosed(() -> { open.remove(surface); view.close(); if (answered.compareAndSet(false, true)) cancel.run(); });
        surface.setContent(view); surface.show();
    }

    /** Shows literal {@code text} with a Close button. */
    void message(String title, String text) {
        view(title, (accept, reject) -> new KitView(kit(), new ViewSpec(new Nodes.Form("message", ControlState.READY, Nodes.FormKind.SHEET, title,
            Optional.empty(), List.of(new Nodes.Text("message-text", ControlState.READY, text, Nodes.TextRole.DESCRIPTION)), Optional.empty(),
            List.of(new Nodes.Button("close", ControlState.READY, "Close", "", Nodes.ButtonKind.PRIMARY, "close", "")), "close")),
            event -> { if (event instanceof UiEvent.Action action && action.id().equals("close")) accept.run(); }), () -> { });
    }

    @Override public void close() { for (var surface : List.copyOf(open)) surface.close(); }
}
