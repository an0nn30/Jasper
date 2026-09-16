package dev.jasper.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class ShellIntegrationLaunchTest {
    @TempDir Path dir;

    private Map<String, String> base(Path home) {
        var env = new HashMap<String, String>();
        env.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        env.put("HOME", home.toString());
        env.put("TERM", "xterm-256color");
        env.put("TERM_PROGRAM", "Jasper");
        env.put("LANG", "en_US.UTF-8");
        return env;
    }

    @Test void zshWrappersLoadTheUsersFilesThenTheIntegrationAndRestoreZdotdir() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path scripts = ShellIntegrationScripts.install(dir.resolve("si"));
        Path dots = Files.createDirectories(dir.resolve("dots"));
        Files.writeString(dots.resolve(".zshenv"), "export ENV_RAN=yes\n");
        Files.writeString(dots.resolve(".zprofile"), "export PROFILE_RAN=yes\n");
        Files.writeString(dots.resolve(".zshrc"), "export RC_RAN=yes\nPROMPT='rc%% '\n");
        Files.writeString(dots.resolve(".zlogin"), "export LOGIN_RAN=yes\n");
        var env = base(dir);
        env.put("ZDOTDIR", dots.toString());
        env.put("JASPER_SHELL_INTEGRATION", scripts.toString());
        var command = new ArrayList<>(List.of("/bin/zsh", "-l", "-i"));
        LaunchSettings.inject(command, env, scripts);
        String output = ShellRun.run(command, env, dir, "echo $ENV_RAN $PROFILE_RAN $RC_RAN $LOGIN_RAN $ZDOTDIR\nexit\n");
        assertThat(output).contains("yes yes yes yes " + dots);
        assertThat(output).contains(ShellRun.A).contains("rc% ").contains(ShellRun.B).contains(ShellRun.CMD("echo $ENV_RAN $PROFILE_RAN $RC_RAN $LOGIN_RAN $ZDOTDIR")).contains(ShellRun.D(0));
        var plain = new ArrayList<>(List.of("/bin/zsh", "-i"));
        var plainEnv = base(dir);
        plainEnv.put("JASPER_SHELL_INTEGRATION", scripts.toString());
        Files.writeString(dir.resolve(".zshrc"), "export RC_RAN=home\n");
        LaunchSettings.inject(plain, plainEnv, scripts);
        String homeOutput = ShellRun.run(plain, plainEnv, dir, "echo $RC_RAN ${ZDOTDIR:-unset} $JASPER_INTEGRATION_LOADED\nexit\n");
        assertThat(homeOutput).contains("home unset 1").contains(ShellRun.C);
    }

    @Test void bashRcfileLoadsBashrcOrTheLoginFilesThenTheIntegration() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path scripts = ShellIntegrationScripts.install(dir.resolve("si"));
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.writeString(home.resolve(".bashrc"), "export RC_RAN=yes\nPS1='rc$ '\n");
        Files.writeString(home.resolve(".bash_profile"), "export PROFILE_RAN=yes\nsource ~/.bashrc\n");
        var env = base(home);
        env.put("JASPER_SHELL_INTEGRATION", scripts.toString());
        var interactive = new ArrayList<>(List.of("/bin/bash", "-i"));
        LaunchSettings.inject(interactive, env, scripts);
        String output = ShellRun.run(interactive, env, home, "echo ${RC_RAN}-${PROFILE_RAN:-no}\nexit\n");
        assertThat(output).contains("yes-no").contains(ShellRun.A).contains("rc$ ").contains(ShellRun.B).contains(ShellRun.C).contains(ShellRun.D(0));
        var login = new ArrayList<>(List.of("/bin/bash", "-l", "-i"));
        var loginEnv = base(home);
        loginEnv.put("JASPER_SHELL_INTEGRATION", scripts.toString());
        LaunchSettings.inject(login, loginEnv, scripts);
        assertThat(login).doesNotContain("-l");
        String loginOutput = ShellRun.run(login, loginEnv, home, "echo ${RC_RAN}-${PROFILE_RAN} ${JASPER_LOGIN_SHELL:-unset}\nexit\n");
        assertThat(loginOutput).contains("yes-yes unset").contains(ShellRun.C);
    }
}
