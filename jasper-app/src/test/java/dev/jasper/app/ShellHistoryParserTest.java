package dev.jasper.app;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistoryParserTest {
    @Test void zshExtendedFormatWithContinuationMetafiedBytesAndAPartialTail() {
        String text = ": 1700000000:0;echo one\n: 1700000001:5;echo two \\\n  three\nplain line\necho partial";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var parsed = ShellHistoryParser.parse(HistoryShell.ZSH, bytes);
        assertThat(parsed.consumed()).isEqualTo(text.indexOf("echo partial"));
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("echo one", "echo two \n  three", "plain line");
        assertThat(parsed.entries().get(1).timestamp()).isEqualTo(1700000001L);
        assertThat(parsed.entries().get(2).timestamp()).isZero();
        assertThat(parsed.entries().get(0).shells()).containsExactly("zsh");
        byte[] meta = {':', ' ', '1', ':', '0', ';', 'c', 'a', 'f', (byte) 0xC3, (byte) 0x83, (byte) 0x89, '\n'};
        assertThat(ShellHistoryParser.parse(HistoryShell.ZSH, meta).entries().getFirst().command()).isEqualTo("café");
    }

    @Test void bashTimestampsApplyToTheFollowingCommandOnly() {
        var parsed = ShellHistoryParser.parse(HistoryShell.BASH, "#1700000000\nls\ncd /tmp\r\n\n".getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("ls", "cd /tmp");
        assertThat(parsed.entries().get(0).timestamp()).isEqualTo(1700000000L);
        assertThat(parsed.entries().get(1).timestamp()).isZero();
        assertThat(parsed.entries().get(0).shells()).containsExactly("bash");
    }

    @Test void fishBlocksDecodeEscapesAndIgnorePaths() {
        String text = "- cmd: echo hi\n  when: 1700000000\n  paths:\n    - /tmp\n- cmd: printf 'a\\nb' \\\\ done\n  when: 1700000001\n- cmd: tail\n";
        var parsed = ShellHistoryParser.parse(HistoryShell.FISH, text.getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("echo hi", "printf 'a\nb' \\ done", "tail");
        assertThat(parsed.entries().get(1).timestamp()).isEqualTo(1700000001L);
        assertThat(parsed.entries().get(2).timestamp()).isZero();
    }

    @Test void powershellBackticksContinueAndNushellLinesArePlain() {
        var ps = ShellHistoryParser.parse(HistoryShell.POWERSHELL, "Get-ChildItem `\n  -Recurse\nls\n".getBytes(StandardCharsets.UTF_8));
        assertThat(ps.entries()).extracting(ShellHistoryEntry::command).containsExactly("Get-ChildItem \n  -Recurse", "ls");
        var nu = ShellHistoryParser.parse(HistoryShell.NUSHELL, "ls | where size > 1kb\n\nopen x.toml\n".getBytes(StandardCharsets.UTF_8));
        assertThat(nu.entries()).extracting(ShellHistoryEntry::command).containsExactly("ls | where size > 1kb", "open x.toml");
        assertThat(nu.entries().getFirst().shells()).containsExactly("nu");
    }

    @Test void overlongLinesBlankLinesAndMalformedBytesAreTolerated() {
        String text = "x".repeat(ShellHistoryParser.MAX_LINE + 1) + "\n   \nok\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] withBad = new byte[bytes.length + 3];
        System.arraycopy(bytes, 0, withBad, 0, bytes.length);
        withBad[bytes.length] = (byte) 0xFF; withBad[bytes.length + 1] = 'z'; withBad[bytes.length + 2] = '\n';
        var parsed = ShellHistoryParser.parse(HistoryShell.BASH, withBad);
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("ok", "�z");
        assertThat(ShellHistoryParser.parse(HistoryShell.ZSH, new byte[0]).entries()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ShellHistoryEntry.of(" ", 0, "zsh"));
    }

    @Test void zshTrailingContinuationIsNotEmitted() {
        String text = ": 1:0;echo a\n: 2:0;echo b \\\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var parsed = ShellHistoryParser.parse(HistoryShell.ZSH, bytes);
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("echo a");
        assertThat(parsed.consumed()).isEqualTo(text.indexOf(": 2"));
    }

    @Test void powershellTrailingBacktickIsNotEmitted() {
        String text = "cmd one\nGet-ChildItem `\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var parsed = ShellHistoryParser.parse(HistoryShell.POWERSHELL, bytes);
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("cmd one");
        assertThat(parsed.consumed()).isEqualTo(text.indexOf("Get-ChildItem"));
    }

    @Test void fishTrailingBlockWithoutWhenIsReturned() {
        String text = "- cmd: one\n  when: 5\n- cmd: two\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var parsed = ShellHistoryParser.parse(HistoryShell.FISH, bytes);
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("one", "two");
        assertThat(parsed.entries().get(0).timestamp()).isEqualTo(5L);
        assertThat(parsed.entries().get(1).timestamp()).isZero();
        assertThat(parsed.consumed()).isEqualTo(text.indexOf("- cmd: two"));
    }

    @Test void fishTrailingBlockWithWhenDoesNotReparse() {
        String text = "- cmd: one\n  when: 5\n- cmd: two\n  when: 6\n- cmd: three\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var parsed = ShellHistoryParser.parse(HistoryShell.FISH, bytes);
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("one", "two", "three");
        assertThat(parsed.entries().get(0).timestamp()).isEqualTo(5L);
        assertThat(parsed.entries().get(1).timestamp()).isEqualTo(6L);
        assertThat(parsed.entries().get(2).timestamp()).isZero();
        assertThat(parsed.consumed()).isEqualTo(text.indexOf("- cmd: three"));
    }

    @Test void bashEmptyLinesDoNotResetTimestamp() {
        String text = "#1700000111\n\nls\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var parsed = ShellHistoryParser.parse(HistoryShell.BASH, bytes);
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("ls");
        assertThat(parsed.entries().get(0).timestamp()).isEqualTo(1700000111L);
    }
}
