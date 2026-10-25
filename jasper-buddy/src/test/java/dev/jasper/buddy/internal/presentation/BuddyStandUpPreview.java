package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.animation.BuddyAnimator;
import dev.jasper.buddy.internal.animation.BuddyFrame;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.metadata.IIOMetadataNode;

/** Headless production animator/sprite playback, at window scale 2.5 and enlarged scale 5. */
public final class BuddyStandUpPreview {
    private record Shot(int at, BuddyFrame frame, BufferedImage image) { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/reports/buddy-stand-up").toAbsolutePath();
        Files.createDirectories(output);
        var sprite = BuddySprite.load();
        for (boolean interrupted : new boolean[]{false, true}) {
            var animator = new BuddyAnimator(new Random(7));
            animator.shown(0);
            animator.tick(2_000_000_000L);
            animator.setWorking(true, 2_000_000_000L);
            var shots = new ArrayList<Shot>();
            int[] times = interrupted ? new int[]{-250, 0, 100, 225, 300, 550, 800, 900, 1025, 1150, 1300}
                : new int[]{-250, 0, 100, 225, 350, 500, 1000};
            for (int at : times) {
                long now = 3_000_000_000L + at * 1_000_000L;
                if (at == 0 || interrupted && at == 800) animator.setWorking(false, now);
                else if (interrupted && at == 300) animator.setWorking(true, now);
                else animator.tick(now);
                var image = new BufferedImage(400, 380, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                try {
                    g.setColor(new Color(236, 231, 222)); g.fillRect(0, 0, 400, 380);
                    g.setColor(new Color(55, 60, 64));
                    g.drawString(at + " ms  " + animator.frame(), 12, 22);
                    g.drawString("window size", 18, 48); g.drawString("2x enlarged", 163, 48);
                    sprite.paint(g, animator.frame(), 2.5, 18, 65);
                    sprite.paint(g, animator.frame(), 5, 165, 65);
                } finally { g.dispose(); }
                shots.add(new Shot(at, animator.frame(), image));
            }
            String name = interrupted ? "interrupted" : "normal";
            var sheet = new BufferedImage(400 * shots.size(), 380, BufferedImage.TYPE_INT_RGB);
            var g = sheet.createGraphics();
            try {
                for (int i = 0; i < shots.size(); i++) g.drawImage(shots.get(i).image(), i * 400, 0, null);
            } finally { g.dispose(); }
            ImageIO.write(sheet, "png", output.resolve(name + "-contact.png").toFile());
            writeGif(output.resolve(name + ".gif"), shots);
        }
        System.out.println(output);
    }

    private static void writeGif(Path path, List<Shot> shots) throws Exception {
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var output = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(output); writer.prepareWriteSequence(null);
            for (int i = 0; i < shots.size(); i++) {
                var shot = shots.get(i);
                var type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(shot.image());
                var metadata = writer.getDefaultImageMetadata(type, null);
                String format = metadata.getNativeMetadataFormatName();
                var root = (IIOMetadataNode) metadata.getAsTree(format);
                var control = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                // GIF uses centiseconds: round absolute timestamps, preserving the 500 ms total.
                int delay = i + 1 == shots.size() ? 75
                    : Math.round(shots.get(i + 1).at() / 10f) - Math.round(shot.at() / 10f);
                control.setAttribute("delayTime", Integer.toString(delay));
                if (i == 0) {
                    var extensions = new IIOMetadataNode("ApplicationExtensions");
                    var loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE"); loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0}); extensions.appendChild(loop); root.appendChild(extensions);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(shot.image(), null, metadata), null);
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
    }
}
