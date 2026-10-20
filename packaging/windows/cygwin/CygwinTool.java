import dev.jasper.app.cygwin.*;
import java.nio.file.Path;
import java.util.*;

/** Headless provisioning entry point used by MSI, Gradle user installation and portable packages. */
public final class CygwinTool {
    public static void main(String[] args) throws Exception {
        if (!CygwinLayout.windows()) throw new IllegalStateException("Cygwin provisioning requires Windows");
        if (args.length < 1) throw new IllegalArgumentException("CygwinTool ensure --seed path | status | manage");
        var options = new HashMap<String,String>();
        for (int i = 1; i < args.length; i += 2) {
            if (i + 1 >= args.length || !Set.of("--seed", "--test-root", "--operation").contains(args[i]) || options.put(args[i], args[i+1]) != null)
                throw new IllegalArgumentException("Invalid helper arguments");
        }
        var layout = options.containsKey("--test-root") ? CygwinLayout.at(Path.of(options.get("--test-root"))) : CygwinLayout.system();
        switch (args[0]) {
            case "ensure" -> {
                if (!options.containsKey("--seed")) throw new IllegalArgumentException("Missing --seed");
                var result = new CygwinProvisioner().ensure(layout, Path.of(options.get("--seed")));
                System.out.println(result.diagnostic());
                if (!result.success()) System.exit(result.outcome() == CygwinProvisioner.Outcome.RESTART_REQUIRED ? 2 : 1);
            }
            case "manage" -> {
                if (!options.containsKey("--operation")) throw new IllegalArgumentException("Missing operation directory");
                CygwinManager.supervise(layout, Path.of(options.get("--operation")), (command, log) -> {
                    try { return CygwinProcess.run(command, log, java.time.Duration.ofHours(24)); }
                    catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                });
            }
            case "status" -> { var state = CygwinState.inspect(layout); System.out.println(state.phase() + ": " + state.diagnostic()); }
            default -> throw new IllegalArgumentException("Unknown Cygwin operation");
        }
    }
}
