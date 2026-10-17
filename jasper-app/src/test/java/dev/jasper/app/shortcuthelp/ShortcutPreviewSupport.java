package dev.jasper.app.shortcuthelp;

import dev.jasper.app.testsupport.KitTestSupport;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;

/** Headless evidence bridge through the actual page's AWT recorder; never a physical-key claim. */
public final class ShortcutPreviewSupport {
    private ShortcutPreviewSupport() {}
    public static void key(KeymapPage page,int code,int modifiers) {
        var panel=page.panel();var source=KitTestSupport.require(panel,"editor.value",JComponent.class);
        var pressed=new KeyEvent(source,KeyEvent.KEY_PRESSED,0,modifiers,code,KeyEvent.CHAR_UNDEFINED);
        if(!panel.dispatch(pressed))throw new AssertionError("Actual recorder did not own press");
        var released=new KeyEvent(source,KeyEvent.KEY_RELEASED,0,modifiers,code,KeyEvent.CHAR_UNDEFINED);
        if(!panel.dispatch(released))throw new AssertionError("Actual recorder did not drain release");
    }
    public static void action(KeymapPage page,String label) {ShortcutKit.editorAction(page.panel(),label);}

    /** Opens {@code section}, as a click on its triangle does. */
    public static void expand(KeymapPage page, String section) {
        page.panel().event(new dev.jasper.ui.model.UiEvent.Expanded(ShortcutTree.ID, ShortcutTree.groupId(section), true));
    }
}
