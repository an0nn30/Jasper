package dev.jasper.app;

import dev.jasper.terminal.TerminalSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.jasper.app.DesktopTestSupport.*;

/** Reproducible actual Swing render; no JFrame, login shell or fabricated native controls. */
public final class MockUiPreview {
    private MockUiPreview() {}
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "../docs/design" : args[0]);
        Files.createDirectories(output);
        var pending = new ArrayDeque<Runnable>();
        var sessions = new ArrayList<TerminalSession>();
        ThemeController[] themes = new ThemeController[1];
        WindowContent[] owner = new WindowContent[1];
        JRootPane[] root = new JRootPane[1];
        try {
            edt(() -> {
                var launcher = new ShellLauncher(pending::add, directory -> {
                    try {
                        // Fixture-only prompt and metadata. The real application never embeds them.
                        String script = "printf '\033[32m\u256d\u2500(\033[34mdustin \033[32m\u2022 \033[34mmbp\033[32m)\u2500[~]\r\n\u2570\u2500\033[34m\u03bb \033[0m\033]7;file://localhost/Users/dustin\007\033]2;dustin\007'; read answer";
                        if (java.util.Arrays.asList(args).contains("--palette")) {
                            StringBuilder sample = new StringBuilder("\\nJasper terminal  /  color palette\\n\\n");
                            String[] names = {"black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"};
                            for (int row = 0; row < 2; row++) {
                                for (int color = 0; color < 8; color++) {
                                    sample.append("\\033[").append((row == 0 ? 40 : 100) + color).append("m     ");
                                }
                                sample.append("\\033[0m\\n");
                            }
                            sample.append("\\n");
                            for (int color = 1; color < 8; color++) {
                                sample.append("\\033[").append(30 + color).append("m")
                                    .append(names[color]).append("  ");
                            }
                            sample.append("\\033[0m\\n\\n")
                                .append("\\033[35m~/projects/jasper\\033[0m  on  \\033[34mmain\\033[0m\\n")
                                .append("\\033[32mPASS\\033[0m  Terminal ready\\n")
                                .append("\\033[33mWARN\\033[0m  Example diagnostic\\n")
                                .append("\\033[31mFAIL\\033[0m  Example diagnostic\\n\\n")
                                .append("\\033[1mBright text\\033[0m  Normal text  \\033[90mMuted text\\033[0m\\n\\n")
                                .append("\\033[35m>\\033[0m ");
                            script = script.replace("; read answer", "; printf '" + sample + "'; read answer");
                        }
                        TerminalSession session = TerminalSession.start(List.of("/bin/sh", "-c", script),
                            System.getenv(), HOME, 80, 24, 100);
                        sessions.add(session); return session;
                    } catch (Exception failure) { throw new CompletionException(failure); }
                }, "bash");
                themes[0] = new ThemeController();
                owner[0] = content(launcher, themes[0]);
                owner[0].newTab(HOME);
                owner[0].currentTab().rename("jasper");
                owner[0].tabStrip().setSelectedIndex(0);
                owner[0].currentTab().rename("dustin");
                root[0] = new JRootPane();
                root[0].setContentPane(owner[0]); root[0].setJMenuBar(owner[0].menuBar());
                owner[0].installRootBindings(root[0]);
            });
            while (!pending.isEmpty()) pending.remove().run();
            until(() -> owner[0].currentPane().view() != null && sessions.size() == 2
                && sessions.stream().allMatch(session -> session.title().equals("dustin")));
            if (java.util.Arrays.asList(args).contains("--splits")) {
                edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT));
                while (!pending.isEmpty()) pending.remove().run();
                until(() -> owner[0].currentPane().view() != null);
            }
            for (UiLookAndFeel theme : new UiLookAndFeel[]{UiLookAndFeel.METAL, UiLookAndFeel.NIMBUS, UiLookAndFeel.MOTIF}) {
                edt(() -> {
                    owner[0].selectLaf(theme);
                    root[0].setSize(1000, 650); MockUiTest.layoutTree(root[0]);
                    if (java.util.Arrays.asList(args).contains("--commands") && !owner[0].commandPalette().isOpen())
                        owner[0].commandPalette().toggle();
                    MockUiTest.layoutTree(root[0]);
                });
                edt(() -> { MockUiTest.layoutTree(root[0]); owner[0].update(); });
                edt(() -> {
                    BufferedImage image = new BufferedImage(2000, 1300, BufferedImage.TYPE_INT_RGB);
                    var g = image.createGraphics();
                    try { g.scale(2, 2); root[0].printAll(g); paintDividers(root[0], g); } finally { g.dispose(); }
                    Path file = output.resolve("mock-ui-" + theme.name().toLowerCase(Locale.ROOT) + ".png");
                    try { ImageIO.write(image, "png", file.toFile()); }
                    catch (java.io.IOException failure) { throw new CompletionException(failure); }
                    System.out.println(file.toAbsolutePath());
                    System.out.println("toolbar=" + owner[0].toolbar().getBounds()
                        + " pane=" + owner[0].currentPane().getBounds() + " view=" + owner[0].currentPane().view().getBounds()
                        + " status=" + owner[0].status().getBounds() + " grid=" + owner[0].status().getText());
                    for (Component child : owner[0].toolbar().getComponents())
                        if (child instanceof JButton button) System.out.println(button.getText() + " " + button.getBounds());
                });
            }
        } finally {
            closeOwners();
            for (TerminalSession session : sessions) session.exitFuture().get(5, TimeUnit.SECONDS);
        }
    }
    private static void paintDividers(Container container, Graphics graphics) {
        // Peerless Swing printing skips the AWT divider. Use its actual LAF painter.
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            Graphics g = graphics.create(child.getX(), child.getY(), child.getWidth(), child.getHeight());
            try {
                if (child instanceof javax.swing.plaf.basic.BasicSplitPaneDivider) child.paint(g);
                else if (child instanceof Container nested) paintDividers(nested, g);
            } finally { g.dispose(); }
        }
    }
}
