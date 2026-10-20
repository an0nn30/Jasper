package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MacAdministratorTest {
    @Test void theShellCommandBecomesOneAppleScriptString() {
        assertThat(MacAdministrator.script("ln -sfn '/A \"b\" \\c' '/usr/local/bin/jasper'"))
            .isEqualTo("do shell script \"ln -sfn '/A \\\"b\\\" \\\\c' '/usr/local/bin/jasper'\" with administrator privileges");
    }
}
