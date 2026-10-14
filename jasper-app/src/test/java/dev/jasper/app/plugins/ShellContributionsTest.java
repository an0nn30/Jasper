package dev.jasper.app.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShellContributionsTest {
    private final Containment containment = new Containment(() -> true);
    private final ShellContributions contributions = new ShellContributions(containment);

    @Test void theFirstPluginByIdWinsWhicheverRegisteredFirst() {
        contributions.register("dev.x.zed", environment -> Map.of("EDITOR", "zed --wait", "ZED_ONLY", "1"));
        contributions.register("dev.x.alpha", environment -> Map.of("EDITOR", "alpha"));
        contributions.register("dev.x.alpha", environment -> Map.of("EDITOR", "alpha again", "ALPHA", "2"));
        assertThat(contributions.variables(Map.of())).as("within a plugin the earlier registration wins too")
            .isEqualTo(Map.of("EDITOR", "alpha", "ZED_ONLY", "1", "ALPHA", "2"));
    }

    @Test void contributorsSeeJaspersEnvironmentReadOnlyAndAThrowAddsNothing() {
        Map<String, String> jasper = new HashMap<>(Map.of("EDITOR", "vim", "HOME", "/home/me"));
        var seen = new AtomicReference<Map<String, String>>();
        contributions.register("dev.x.a", environment -> {
            seen.set(environment);
            return Map.of("SEEN", environment.get("HOME"));
        });
        contributions.register("dev.x.b", environment -> {
            environment.put("HOME", "/elsewhere");
            return Map.of("NEVER", "1");
        });
        contributions.register("dev.x.c", environment -> { throw new IllegalStateException("broken contributor"); });
        contributions.register("dev.x.d", environment -> null);
        assertThat(contributions.variables(jasper)).isEqualTo(Map.of("SEEN", "/home/me"));
        assertThat(seen.get()).isEqualTo(Map.of("EDITOR", "vim", "HOME", "/home/me"));
        assertThat(jasper).as("the caller's map is untouched").isEqualTo(Map.of("EDITOR", "vim", "HOME", "/home/me"));
        assertThat(containment.failures("dev.x.b")).as("writing to the read-only copy throws").isEqualTo(1);
        assertThat(containment.failures("dev.x.c")).isEqualTo(1);
        assertThat(containment.failures("dev.x.d")).as("a null map counts as empty").isZero();
    }

    @Test void invalidReservedAndNulEntriesAreDroppedAndEachProblemIsLoggedOnce() {
        Map<String, String> offered = new HashMap<>();
        for (String name : List.of("TERM", "COLORTERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION", "JASPER_PANE", "JASPER_ANYTHING",
                "1ST", "WITH-DASH", "WITH SPACE", "", "\u00dcBER"))
            offered.put(name, "x");
        offered.put(null, "x");
        offered.put("NO_VALUE", null);
        offered.put("NUL_VALUE", "a\0b");
        offered.put("EDITOR", "nvim");
        offered.put("_kept_2", "yes");
        contributions.register("dev.x.b", environment -> offered);
        contributions.register("dev.x.a", environment -> Map.of("EDITOR", "code --wait"));
        List<LogRecord> records = logged(() -> {
            assertThat(contributions.variables(Map.of())).isEqualTo(Map.of("EDITOR", "code --wait", "_kept_2", "yes"));
            contributions.variables(Map.of());
        });
        assertThat(records).extracting(LogRecord::getMessage).hasSize(15).doesNotHaveDuplicates()
            .contains("Plugin dev.x.b's shell variable TERM is reserved; it was dropped",
                "Plugin dev.x.b's shell variable JASPER_PANE is reserved; it was dropped",
                "Plugin dev.x.b's shell variable WITH-DASH is not a valid name; it was dropped",
                "Plugin dev.x.b's shell variable null is not a valid name; it was dropped",
                "Plugin dev.x.b's shell variable NO_VALUE has no value; it was dropped",
                "Plugin dev.x.b's shell variable NUL_VALUE holds a NUL character; it was dropped",
                "Plugin dev.x.b's shell variable EDITOR lost to dev.x.a");
        assertThat(records).allSatisfy(record -> assertThat(record.getLevel()).isEqualTo(Level.WARNING));
    }

    @Test void aClosedRegistrationContributesNothing() {
        var first = contributions.register("dev.x.a", environment -> Map.of("A", "1"));
        contributions.register("dev.x.a", environment -> Map.of("B", "2"));
        first.close();
        first.close();
        assertThat(contributions.variables(Map.of())).isEqualTo(Map.of("B", "2"));
    }

    /** What ShellContributions logs while {@code action} runs. */
    private static List<LogRecord> logged(Runnable action) {
        Logger logger = Logger.getLogger(ShellContributions.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
        return records;
    }
}
