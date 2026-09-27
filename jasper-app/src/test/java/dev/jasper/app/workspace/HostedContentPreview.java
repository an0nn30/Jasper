package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.contributions.*;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

/** Shared chrome rendered without native windows or starting a shell. */
public final class HostedContentPreview {
    private HostedContentPreview() {}
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/hosted-previews" : args[0]);
        Files.createDirectories(output);
        try {
            edt(() -> {
                var owner = content(launcher(new ArrayDeque<>()));
                var root = new JRootPane();
                WindowContent.installTitleBar(root, owner, true, title -> {});
                owner.installRootBindings(root);
                var model = new Contributions(); owner.connectContributions(model);
                ContentEntry selected = null;
                for (int index = 0; index < 12; index++) {
                    var text = new JTextArea("#!/usr/bin/env bash\n\n# Remote configuration\nserver=jasper\necho \"Ready: $server\"\n");
                    text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
                    var scroll = new JScrollPane(text); scroll.setBorder(BorderFactory.createEmptyBorder());
                    var entry = model.content().open("dev.jasper.editor", "file:" + index,
                        index == 11 ? "server-config.sh" : "document-" + index + ".txt", owner.id(), scroll);
                    entry.presentation(entry.title(), index == 11 ? "prod" : "", AppIcons.named("FILE"), index == 11,
                        index == 11 ? "deploy@prod:/etc/jasper/server-config.sh" : "/projects/jasper/" + entry.title(), index == 11 ? "prod" : "");
                    entry.status(List.of(new ContentStatus("Ln 4, Col 8", "Caret", null), new ContentStatus("Shell (Bash)", "Language", () -> {}),
                        new ContentStatus("UTF-8", "Encoding", null), new ContentStatus("LF", "Line ending", null), new ContentStatus("Spaces: 4", "Indentation", null)));
                    selected = entry;
                }
                for (Theme theme : List.of(Theme.DARK, Theme.LIGHT)) {
                    owner.selectTheme(theme);
                    for (var entry : model.content().entries()) {
                        var scroll = (JScrollPane)entry.component();
                        var text = (JTextArea)scroll.getViewport().getView();
                        text.setBackground(UIManager.getColor("Jasper.editorBackground"));
                        text.setForeground(UIManager.getColor("Jasper.editorForeground"));
                        scroll.getViewport().setBackground(text.getBackground());
                    }
                    selected.select();
                    for (int width : new int[]{1000, 680}) {
                        owner.setActive(width == 1000);
                        root.setSize(width, 620); LayoutTestSupport.layoutTree(root); owner.update(); LayoutTestSupport.layoutTree(root);
                        var image = new BufferedImage(width * 2, 1240, BufferedImage.TYPE_INT_RGB);
                        var graphics = image.createGraphics();
                        try { graphics.scale(2, 2); root.printAll(graphics); } finally { graphics.dispose(); }
                        Path path = output.resolve("hosted-" + theme.name().toLowerCase() + "-" + width + ".png");
                        try { ImageIO.write(image, "png", path.toFile()); }
                        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                        System.out.println(path.toAbsolutePath());
                    }
                }
            });
        } finally { closeOwners(); }
    }
}
