package dev.jasper.app;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class LaunchRequestTest {

    @Test void aRequestSurvivesEncodingIncludingPathsWithSeparatorsInThem() {
        // A tab separates fields and a newline ends the line, so a path containing either must
        // still arrive intact; that is what the base64 is for.
        Path awkward = Path.of("/Users/a b\tc/Jasper.app/Contents/app/jasper-app.jar");
        var request = new LaunchRequest("tok-en_123", awkward, 1_726_500_000_000L);
        String line = request.encode();
        assertThat(line).endsWith("\n").startsWith("jasper\t1\ttok-en_123\t");
        assertThat(line.chars().filter(c -> c == '\t').count()).isEqualTo(4);
        assertThat(LaunchRequest.decode(line)).isEqualTo(request);
    }

    @Test void anythingThisBuildCannotUnderstandDecodesToNullRatherThanThrowing() {
        var valid = new LaunchRequest("t", Path.of("/x.jar"), 1L).encode();
        for (String line : new String[]{
                "", "\n", "garbage\n",
                "jasper\t1\tt\n",                                   // too few fields
                "jasper\t1\tt\tAA==\tAA==\t1\textra\n",             // too many fields
                "notjasper\t1\tt\tAA==\tAA==\t1\n",                 // wrong magic
                "jasper\t2\tt\tAA==\tAA==\t1\n",                    // a future protocol
                "jasper\tx\tt\tAA==\tAA==\t1\n",                    // unparseable protocol
                "jasper\t1\tt\tAA==\tAA==\tx\n",                    // unparseable timestamp
                "jasper\t1\tt\t!not base64!\tAA==\t1\n"}) {         // undecodable path
            assertThat(LaunchRequest.decode(line)).as(line.strip()).isNull();
        }
        assertThat(LaunchRequest.decode(valid)).isNotNull();
    }

    @Test void responsesRoundTripAndAnythingUnrecognisedIsAProtocolError() {
        assertThat(LaunchRequest.Response.OK.line()).isEqualTo("ok\n");
        assertThat(LaunchRequest.Response.STALE.line()).isEqualTo("refused\tstale\n");
        assertThat(LaunchRequest.Response.TOKEN.line()).isEqualTo("refused\ttoken\n");
        for (var response : LaunchRequest.Response.values()) {
            assertThat(LaunchRequest.Response.of(response.line())).isEqualTo(response);
        }
        for (String line : new String[]{"", "yes\n", "refused\n", "refused\tok\n", "ok extra\n"}) {
            assertThat(LaunchRequest.Response.of(line)).as(line.strip())
                .isEqualTo(LaunchRequest.Response.PROTOCOL);
        }
    }
}
