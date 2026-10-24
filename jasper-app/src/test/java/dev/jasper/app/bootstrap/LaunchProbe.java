package dev.jasper.app.bootstrap;

import dev.jasper.app.platform.AppDirs;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * A launch in a child JVM for {@code SingleInstanceProcessTest}: {@code <home> <handoff millis> [jasper arguments...]},
 * claiming the home with those arguments (its working directory is the home). An owner prints "owner", then each request
 * it is handed as "handoff INTENT PATHS", until its standard input closes. Any other launch prints "failed MESSAGE" for a
 * failure, then "done CODE", and exits with that code.
 */
public final class LaunchProbe {
    private LaunchProbe() { }

    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]);
        var dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var options = AppArguments.parse(Arrays.copyOfRange(args, 2, args.length), home);
        var timing = new SingleInstance.Timing(Duration.ofMillis(Long.parseLong(args[1])), Duration.ofSeconds(30), Duration.ofMillis(50));
        var claim = SingleInstance.claim(options, dirs, timing, message -> say("failed " + message));
        if (claim instanceof SingleInstance.Claim.Done done) {
            say("done " + done.code());
            System.exit(done.code());
        }
        Instance instance = ((SingleInstance.Claim.Owner) claim).instance();
        say("owner");
        instance.serve((request, updated) -> say("handoff " + request.intent() + " " + request.paths().size()), () -> true,
            Runnable::run, invocation -> CompletableFuture.completedFuture(0));
        while (System.in.read() >= 0) { }
        instance.close();
    }

    private static synchronized void say(String line) {
        System.out.println(line);
        System.out.flush();
    }
}
