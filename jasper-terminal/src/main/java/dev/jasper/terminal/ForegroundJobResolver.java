package dev.jasper.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.unix.UnixPtyProcess;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import java.util.concurrent.atomic.AtomicBoolean;

/** Resolves foreground process metadata without helper processes or terminal reads. */
final class ForegroundJobResolver {
    private final PtyProcess process;
    private final String initialProgram;
    private final boolean loginShell;
    private final AtomicBoolean closing;

    ForegroundJobResolver(PtyProcess process, List<String> command, AtomicBoolean closing) {
        this.process = process;
        this.closing = closing;
        this.initialProgram = command.isEmpty() ? "" : Path.of(command.getFirst()).getFileName().toString();
        this.loginShell = List.of("zsh", "bash", "fish", "sh", "ksh").contains(initialProgram)
            && loginOption(command);
    }

    private static boolean loginOption(List<String> command) {
        boolean login = false;
        for (int i = 1; i < command.size(); i++) {
            String option = command.get(i);
            if (option.equals("--") || option.equals("-") || !option.startsWith("-")) break;
            if (option.equals("--login")) login = true;
            else if (option.equals("--rcfile") || option.equals("--init-file")) i++;
            else if (!option.startsWith("--")) {
                login |= option.indexOf('l') > 0;
                if (option.indexOf('c') > 0) break;
                if (option.endsWith("o") || option.endsWith("O")) i++;
            }
        }
        return login;
    }

    /** Native process metadata only; never starts a helper process or reads terminal output. */
    Optional<String> foregroundJob() {
        if (closing.get() || !process.isAlive() || !(process instanceof UnixPtyProcess unix)) return Optional.empty();
        try {
            Function function = ForegroundGroup.GET;
            if (function == null) return Optional.empty();
            int group = function.invokeInt(new Object[]{unix.getPty().getMasterFD()});
            if (group <= 0 || closing.get()) return Optional.empty();
            return ProcessHandle.of(group).flatMap(handle -> handle.info().command()).map(command -> {
                String name = Path.of(command).getFileName().toString();
                return group == process.pid() && loginShell && name.equals(initialProgram) ? "-" + name : name;
            });
        } catch (RuntimeException | LinkageError unavailable) {
            // Unsupported OS/runtime, a disappearing process, or an already closed terminal.
            return Optional.empty();
        }
    }

    private static final class ForegroundGroup {
        static final Function GET = find();
        private static Function find() {
            try { return Platform.isWindows() ? null : NativeLibrary.getInstance(Platform.C_LIBRARY_NAME).getFunction("tcgetpgrp"); }
            catch (RuntimeException | LinkageError unavailable) { return null; }
        }
    }

}
