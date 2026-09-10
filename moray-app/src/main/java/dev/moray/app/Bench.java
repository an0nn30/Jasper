package dev.moray.app;

import dev.moray.terminal.TerminalOptions;
import dev.moray.terminal.TerminalSession;
import dev.moray.terminal.TerminalView;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/** Pipes ~100 MB of ANSI-colored text through a Moray terminal window and prints the throughput. */
public final class Bench {
    private static final long TARGET_BYTES = 100L * 1024 * 1024;
    private static final String[] WORDS = ("the quick brown fox jumps over lazy dog lorem ipsum dolor sit amet "
        + "consectetur adipiscing elit sed do eiusmod tempor").split(" ");
    private static final int[] COLORS = {31, 32, 33, 34, 35, 36, 91, 92, 93, 94};

    private Bench() {
    }

    public static void main(String[] args) throws Exception {
        Path data = Path.of(args[0]).toAbsolutePath();
        if (!Files.exists(data) || Files.size(data) < TARGET_BYTES) {
            generate(data);
        }
        long bytes = Files.size(data);
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        List<String> command = windows
            ? List.of("cmd.exe", "/c", "type", data.toString())
            : List.of("/bin/cat", data.toString());
        TerminalOptions options = TerminalOptions.defaults();

        long start = System.nanoTime();
        TerminalSession session = TerminalSession.start(command, System.getenv(), data.getParent(), 120, 36, options.scrollback());
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Moray bench");
            frame.add(new TerminalView(session, options));
            frame.pack();
            frame.setVisible(true);
        });
        session.exitFuture().get();
        double seconds = (System.nanoTime() - start) / 1e9;

        System.out.printf("Moray view: %.1f MB in %.2f s = %.1f MB/s%n", bytes / 1e6, seconds, bytes / 1e6 / seconds);
        System.exit(0);
    }

    static void generate(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Random random = new Random(42);
        StringBuilder line = new StringBuilder();
        long written = 0;
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (int n = 1; written < TARGET_BYTES; n++) {
                line.setLength(0);
                StringBuilder words = new StringBuilder();
                int count = 4 + random.nextInt(15);
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        words.append(' ');
                    }
                    words.append(WORDS[random.nextInt(WORDS.length)]);
                }
                if (n % 4 == 0) {
                    line.append("\033[1;").append(COLORS[random.nextInt(COLORS.length)]).append('m')
                        .append(String.format("%08d", n)).append("\033[0m ").append(words)
                        .append(" \033[38;2;").append(random.nextInt(256)).append(';').append(random.nextInt(256))
                        .append(';').append(random.nextInt(256)).append("m█\033[0m");
                } else {
                    line.append(String.format("%08d", n)).append(' ').append(words);
                }
                line.append('\n');
                out.write(line.toString());
                written += line.length();
            }
        }
    }
}
