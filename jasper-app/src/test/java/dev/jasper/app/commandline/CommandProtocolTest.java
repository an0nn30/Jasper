package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandProtocolTest {
    @Test void aRequestRoundTrips(@TempDir Path dir) {
        var request = new CommandProtocol.Request("token", dir, Optional.of(UUID.randomUUID()),
            List.of("editor", "--wait", "notes ü.txt", "", "tab\there"));
        String line = request.encode();
        assertThat(line).startsWith("jasper-command\t1\ttoken\t").endsWith("\n");
        String body = line.substring(0, line.length() - 1);
        assertThat(CommandProtocol.version(body)).isEqualTo(1);
        assertThat(CommandProtocol.decode(body)).isEqualTo(request);
    }

    /** packaging/cli's core builds the same lines from the same inputs (test/protocol-vectors.txt). */
    @Test void requestsMatchTheNativeClientsVectors() throws Exception {
        boolean windowsHost = System.getProperty("os.name").startsWith("Windows");
        Path vectors = Path.of(System.getProperty("jasper.projectDir"), "packaging", "cli", "test", "protocol-vectors.txt");
        String token = null, cwd = null, pane = null;
        List<String> arguments = new ArrayList<>();
        int checked = 0;
        for (String line : Files.readAllLines(vectors, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int equals = line.indexOf('=');
            String key = line.substring(0, equals), value = line.substring(equals + 1);
            switch (key) {
                case "token" -> token = value;
                case "cwd" -> cwd = value;
                case "pane" -> pane = value;
                case "arg" -> arguments.add(value);
                case "expect" -> {
                    // Windows writes every "/" of a Path as "\", so only vectors in its own spelling apply there.
                    if (!(windowsHost && cwd.startsWith("/"))) {
                        String expected = Arrays.stream(value.split(" ", -1)).map(field -> field.equals("-") ? "" : field)
                            .collect(Collectors.joining("\t")) + "\n";
                        var request = new CommandProtocol.Request(token, Path.of(cwd),
                            pane.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(pane)), arguments);
                        assertThat(request.encode()).as(value).isEqualTo(expected);
                        checked++;
                    }
                    arguments = new ArrayList<>();
                }
                default -> throw new AssertionError("Unknown vector key: " + line);
            }
        }
        assertThat(checked).isPositive();
    }

    @Test void theVersionOfALineIsReadBeforeAnythingElse() {
        assertThat(CommandProtocol.version("jasper-command\t2\tt\tL3RtcA==\t")).isEqualTo(2);
        assertThat(CommandProtocol.decode("jasper-command\t2\tt\tL3RtcA==\t")).isNull();
        assertThat(CommandProtocol.version("jasper\t1\tt")).isEqualTo(-1);
        assertThat(CommandProtocol.version("jasper-command\tone")).isEqualTo(-1);
        assertThat(CommandProtocol.version("")).isEqualTo(-1);
    }

    @Test void malformedRequestsDecodeToNothing() {
        String cwd = CommandProtocol.text(Path.of("/tmp").toAbsolutePath().toString());
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + cwd)).as("too few fields").isNull();
        assertThat(CommandProtocol.decode("jasper-command\t1\t\t" + cwd + "\t")).as("no token").isNull();
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t%%%\t")).as("bad base64").isNull();
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + CommandProtocol.text("relative") + "\t"))
            .as("a relative working directory").isNull();
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + cwd + "\t\t@@")).as("a bad argument").isNull();
    }

    @Test void aPaneThatIsNotAUuidIsNoPane() {
        String cwd = CommandProtocol.text(Path.of("/tmp").toAbsolutePath().toString());
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + cwd + "\tnot-a-uuid").pane()).isEmpty();
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + cwd + "\t1-1-1-1-1").pane()).isEmpty();
    }

    @Test void framesCarryBase64TextAndTheExitCode() {
        assertThat(CommandProtocol.out("hello ü\n")).isEqualTo("out\taGVsbG8gw7wK\n");
        assertThat(CommandProtocol.err("")).isEqualTo("err\t\n");
        assertThat(CommandProtocol.exit(3)).isEqualTo("exit\t3\n");
        assertThat(CommandProtocol.STALE).isEqualTo("stale\n");
        assertThat(CommandProtocol.REFUSED).isEqualTo("refused\n");
    }
}
