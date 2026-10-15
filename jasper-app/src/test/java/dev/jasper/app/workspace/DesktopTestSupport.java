package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

public final class DesktopTestSupport {
    private static final List<WindowContent> OWNERS = new ArrayList<>();
    public static final Path HOME = Path.of(System.getProperty("user.home"));
    public static TerminalSession shell(Path directory) {
        try {
            return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c", "printf 'alpha alpha\\n'; read answer")).environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
        } catch (Exception e) { throw new CompletionException(e); }
    }
    static void setStatusMetadata(WindowStatusBar status, String shell, String directory, String dimensions, boolean running, boolean integration) {
        // Treat hostile display text as a remote string, never as a platform filesystem path.
        boolean remote = directory.startsWith("<html>");
        var snapshot = new dev.jasper.app.terminals.PaneSnapshot("", remote ? Optional.empty() : Optional.of(Path.of(directory)),
            80, 24, integration, dev.jasper.app.terminals.PaneSnapshot.State.RUNNING, OptionalInt.empty(), Optional.empty(),
            remote ? Optional.of(new dev.jasper.app.terminals.RemoteLocation("host", directory)) : Optional.empty(), shell);
        status.setMetadata(snapshot, dimensions, running);
    }
    public static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }
    public static void until(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            boolean[] done = {false};
            edt(() -> done[0] = condition.getAsBoolean());
            if (done[0]) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Condition did not complete within 5 seconds");
    }
    public static ShellLauncher launcher(Queue<Runnable> pending) {
        return new ShellLauncher(pending::add, DesktopTestSupport::shell, "sh");
    }
    /** A launcher whose shells are idle in-process sessions, so they run on every platform, Windows included. */
    public static ShellLauncher idleLauncher(Queue<Runnable> pending) {
        return new ShellLauncher(pending::add, directory -> IdleTerminalSession.start(), "idle-test-session");
    }
    public static WindowContent content(ShellLauncher launcher) {
        WindowContent owner = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {});
        OWNERS.add(owner);
        return owner;
    }
    /** A window with one platform's keymap and shortcut text, whatever the host is. */
    public static WindowContent content(ShellLauncher launcher, boolean macOs) {
        WindowContent owner = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {}, ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs);
        OWNERS.add(owner);
        return owner;
    }
    /** A window like a new one from the application: its tab starts where a new shell starts, not in a named directory. */
    public static WindowContent content(ShellLauncher launcher, java.util.function.Consumer<Path> newWindow) {
        WindowContent owner = new WindowContent(launcher, null, false, newWindow, () -> {}, () -> {}, ThemeTestSupport.controller(true),
            KeyBindings.defaults(false), new CommandHistory(), false);
        OWNERS.add(owner);
        return owner;
    }
    public static WindowContent content(ShellLauncher launcher, ThemeController themes) {
        WindowContent owner = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {}, themes);
        OWNERS.add(owner);
        return owner;
    }
    public static void closeOwners() throws Exception {
        edt(() -> { OWNERS.forEach(WindowContent::close); OWNERS.clear(); });
    }
    /** Real title/status owners at source allocations, mounted around the actual staged Remote content. */
    public static void captureIconOwners(WindowContent owner,String theme,int width,int height) {
        owner.selectVariant(theme.equals("dark"));
        // Standalone owners may already hold this theme while another fixture changed the installed chrome.
        dev.jasper.ui.swing.FlatLafInstaller.install(owner.theme().chrome(), dev.jasper.ui.kit.Kit.DEFAULT);
        owner.connectConfiguration(() -> {}, () -> {}, () -> {});
        var root=new javax.swing.JRootPane();
        try(var title=WindowContent.installTitleBar(root,owner,true,ignored->{})) {
            var status=owner.status();
            status.setMetadata(new dev.jasper.app.terminals.PaneSnapshot("Fixture",java.util.Optional.of(java.nio.file.Path.of("/Users/example/projects/jasper/src")),80,24,true,dev.jasper.app.terminals.PaneSnapshot.State.RUNNING,java.util.OptionalInt.empty(),java.util.Optional.empty(),java.util.Optional.empty(),"zsh"),"80 × 24",true);
            root.setSize(width,height);for(int n=0;n<3;n++)dev.jasper.app.testsupport.KitTestSupport.layout(root);
            var image=new java.awt.image.BufferedImage(width*2,height*2,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
            try{g.scale(2,2);root.printAll(g);}finally{g.dispose();}
            var output=java.nio.file.Path.of("build/theme-migration-evidence/icon-owners",theme);java.nio.file.Files.createDirectories(output);
            javax.imageio.ImageIO.write(image,"png",output.resolve(width+"x"+height+".png").toFile());
            var icons=new java.util.ArrayList<String>();captureIconBounds(root,root,icons);
            java.nio.file.Files.writeString(output.resolve(width+"x"+height+".json"),"{\"owner\":\"WindowContent+stagedRemote+WindowStatusBar\",\"deviceScale\":2,\"logicalWidth\":"+width+",\"logicalHeight\":"+height+",\"nativeWindow\":false,\"icons\":["+String.join(",",icons)+"]}");
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }

    private static void captureIconBounds(java.awt.Component component,javax.swing.JComponent root,java.util.List<String> result) {
        javax.swing.Icon icon=component instanceof javax.swing.JLabel label?label.getIcon():component instanceof javax.swing.AbstractButton button?button.getIcon():null;
        if(icon!=null && component.isVisible() && component.getWidth()>0) {
            var point=javax.swing.SwingUtilities.convertPoint(component,0,0,root);
            String semantic=dev.jasper.app.platform.AppIcons.kitName(icon).orElse("");
            result.add("{\"name\":\""+String.valueOf(component.getName())+"\",\"semantic\":\""+semantic+"\",\"component\":["+point.x+","+point.y+","+component.getWidth()+","+component.getHeight()+"],\"icon\":["+icon.getIconWidth()+","+icon.getIconHeight()+"]}");
        }
        if(component instanceof java.awt.Container container)for(var child:container.getComponents())captureIconBounds(child,root,result);
    }

}
