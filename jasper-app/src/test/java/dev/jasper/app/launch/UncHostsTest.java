package dev.jasper.app.launch;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The host rule is text, so it holds, and is tested, on every OS. */
class UncHostsTest {
    /** Two separators, in any mix, then a host: outside a WSL pane every host is foreign, the WSL shares' too. */
    @Test void outsideAWslPaneEveryHostIsForeign() {
        for (String text : List.of("\\\\attacker\\share\\x", "//attacker/share", "\\/attacker/x", "/\\attacker/x", "\\\\attacker", "//", "\\\\",
            "\\\\wsl$\\Ubuntu\\x", "//wsl.localhost/Ubuntu/x", "\\\\WSL.LOCALHOST\\Ubuntu", "//Wsl$\\Ubuntu\\x", "\\\\wsl$")) {
            assertThat(UncHosts.namesForeignHost(text, false)).as(text).isTrue();
        }
    }

    /** In a WSL pane the WSL shares, in any case or separators, are this machine's own. */
    @Test void inAWslPaneTheWslSharesAreNotForeign() {
        for (String text : List.of("\\\\wsl.localhost\\Ubuntu\\home\\me", "//wsl$/Ubuntu/x", "\\/WSL.LOCALHOST/Ubuntu/x", "//Wsl$\\Ubuntu\\x",
            "\\\\wsl$")) {
            assertThat(UncHosts.namesForeignHost(text, true)).as(text).isFalse();
        }
    }

    /**
     * Even in a WSL pane: any other host; a WSL host whose share is {@code .} or {@code ..}, which Windows resolves to another
     * host; and a host that only {@code equalsIgnoreCase} folds to a WSL one (U+017F, long s, written as a Java escape).
     */
    @Test void inAWslPaneOtherHostsDotSharesAndCaseFoldedNamesAreForeign() {
        for (String text : List.of("\\\\attacker\\share", "//wsl/x", "//wsl.localhost.attacker/x", "\\\\localhost\\c$\\x", "\\\\?\\UNC\\attacker\\x",
            "\\\\wsl.localhost\\..\\x", "\\\\wsl$\\.\\x", "//wsl.localhost//../attacker", "\\\\w\u017fl$\\Ubuntu\\x", "//wsl.localho\u017ft/Ubuntu")) {
            assertThat(UncHosts.namesForeignHost(text, true)).as(text).isTrue();
        }
    }

    @Test void aPathWithoutTwoLeadingSeparatorsNamesNoHost() {
        for (String text : List.of("C:\\src\\Foo.cs", "C:/src", "/tmp/x", "\\tmp\\x", "src/Foo.java", "~/a.txt", "/", "")) {
            assertThat(UncHosts.namesForeignHost(text, false)).as(text).isFalse();
            assertThat(UncHosts.namesForeignHost(text, true)).as(text + " in a WSL pane").isFalse();
        }
    }
}
