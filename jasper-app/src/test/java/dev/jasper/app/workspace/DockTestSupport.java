package dev.jasper.app.workspace;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import java.util.Locale;
import javax.swing.JComponent;

/** Puts plain content into a dock without a plugin, as the old {@code regions().show(...)} did. Test only. */
final class DockTestSupport {
    private DockTestSupport() { }

    /** Shows content in a slot, with its side at size; the same slot again replaces the content. */
    static void show(ToolWindowArea area, Slot slot, JComponent content, int size) {
        String id = id(slot);
        DockState state = area.state();
        area.setContent(id, slot.displayName(), content);
        if (state.slot(id).isEmpty()) state.add(id, slot);
        state.resize(slot.side(), size);
        state.show(id);
        area.sync();
    }

    static void hide(ToolWindowArea area, Slot slot) {
        area.state().hide(id(slot));
        area.sync();
    }

    /** The same in a window's own area; a connected window's contributions ignore these ids. */
    static void show(WindowContent owner, Slot slot, JComponent content, int size) { show(owner.area(), slot, content, size); }

    static void hide(WindowContent owner, Slot slot) { hide(owner.area(), slot); }

    /** The id the helper gives a slot's content, such as {@code test.left-top}. */
    static String id(Slot slot) { return "test." + slot.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
}
