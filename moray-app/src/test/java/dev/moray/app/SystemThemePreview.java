package dev.moray.app;

import dev.moray.terminal.TerminalSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.moray.app.DesktopTestSupport.*;

/** Reproducible actual Swing render; no JFrame, login shell or fabricated native controls. */
public final class SystemThemePreview {
    private SystemThemePreview() {}
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "../docs/design/plan-4c-themes" : args[0]);
        Files.createDirectories(output);
        var pending = new ArrayDeque<Runnable>();
        var sessions = new ArrayList<TerminalSession>();
        ThemeController[] themes = new ThemeController[1];
        WindowContent[] owner = new WindowContent[1];
        JRootPane[] root = new JRootPane[1];
        MacTitleBar[] header = new MacTitleBar[1];
        try {
            edt(() -> {
                var launcher = new ShellLauncher(pending::add, directory -> {
                    try {
                        // Fixture-only prompt and metadata. The real application never embeds them.
                        String script = "printf '\033[32m\u256d\u2500(\033[34mdustin \033[32m\u2022 \033[34mmbp\033[32m)\u2500[~]\r\n\u2570\u2500\033[34m\u03bb \033[0m\033]7;file://localhost/Users/dustin\007\033]2;dustin\007'; read answer";
                        TerminalSession session = TerminalSession.start(List.of("/bin/sh", "-c", script),
                            System.getenv(), HOME, 80, 24, 100);
                        sessions.add(session); return session;
                    } catch (Exception failure) { throw new CompletionException(failure); }
                }, "bash");
                themes[0] = new ThemeController();
                owner[0] = content(launcher, themes[0]);
                owner[0].newTab(HOME);
                owner[0].currentTab().rename("moray");
                owner[0].tabStrip().setSelectedIndex(0);
                owner[0].currentTab().rename("dustin");
                root[0] = new JRootPane();
                header[0] = MacTitleBar.install(root[0], owner[0], true, title -> {});
                owner[0].installRootBindings(root[0]);
            });
            while (!pending.isEmpty()) pending.remove().run();
            until(() -> owner[0].currentPane().view() != null && sessions.size() == 2
                && sessions.stream().allMatch(session -> session.title().equals("dustin")));
            for (String variant : List.of("dark", "light", "custom")) {
                edt(() -> {
                    themes[0].configure(new ColorsConfig(Appearance.SYSTEM, variant.equals("custom") ? "custom" : "moray-dark"),
                        new dev.moray.terminal.Palette(Color.WHITE, new Color(0x101820), Color.YELLOW,
                            new Color(0x334455), BuiltinTheme.DARK.palette().ansi()));
                    themes[0].systemChanged(variant.equals("dark") ? BuiltinTheme.DARK : BuiltinTheme.LIGHT);
                    owner[0].setConfigurationState(new ConfigService.State(ConfigSnapshot.defaults(), List.of(), Path.of("fixture/config.toml"), true));
                    root[0].setSize(958, 958); MockUiTest.layoutTree(root[0]);
                });
                edt(() -> { MockUiTest.layoutTree(root[0]); owner[0].update(); });
                edt(() -> {
                    BufferedImage image = new BufferedImage(1916, 1916, BufferedImage.TYPE_INT_RGB);
                    var g = image.createGraphics();
                    try { g.scale(2, 2); root[0].printAll(g); } finally { g.dispose(); }
                    Path file = output.resolve("theme-" + variant + ".png");
                    try { ImageIO.write(image, "png", file.toFile()); }
                    catch (java.io.IOException failure) { throw new CompletionException(failure); }
                    System.out.println(file.toAbsolutePath());
                    System.out.println("title=" + header[0].getBounds() + " toolbar=" + owner[0].toolbar().getBounds()
                        + " pane=" + owner[0].currentPane().getBounds() + " view=" + owner[0].currentPane().view().getBounds()
                        + " status=" + owner[0].status().getBounds() + " grid=" + owner[0].status().getText());
                    for (Component child : owner[0].toolbar().getComponents())
                        if (child instanceof JButton button) System.out.println(button.getText() + " " + button.getBounds());
                });
            }
        } finally {
            edt(() -> { if (header[0] != null) header[0].close(); });
            closeOwners();
            for (TerminalSession session : sessions) session.exitFuture().get(5, TimeUnit.SECONDS);
        }
    }
}
