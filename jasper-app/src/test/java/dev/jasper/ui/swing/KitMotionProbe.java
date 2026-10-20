package dev.jasper.ui.swing;

/** Owner-package access for the app's live-policy integration regression. */
public final class KitMotionProbe {
    private KitMotionProbe() {}
    public static double position(KitView view, String id) { return ((Switches.ToggleControl)view.control(id)).button.position; }
    public static boolean running(KitView view, String id) { return ((Switches.ToggleControl)view.control(id)).animation.isRunning(); }
    public static void frame(KitView view, String id) {
        var timer=((Switches.ToggleControl)view.control(id)).animation;
        for(var listener:timer.getActionListeners())listener.actionPerformed(new java.awt.event.ActionEvent(timer,0,"frame"));
    }
}
