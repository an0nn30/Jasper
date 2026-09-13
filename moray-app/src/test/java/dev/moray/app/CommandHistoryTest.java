package dev.moray.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandHistoryTest {
    @TempDir Path directory;

    @Test void mergeKeepsNewestDistinctIdsBeforeOlderIds() {
        assertThat(CommandHistory.merge(List.of("c", "a"), List.of("a", "b")))
            .containsExactly("c", "a", "b");
    }

    @Test void repeatedAndFourthRecordsKeepThreeDistinctMostRecentCommands() throws Exception {
        AtomicReference<CommandHistory> result = new AtomicReference<>();
        edt(() -> {
            var history = new CommandHistory();
            history.record("one");
            history.record("two");
            history.record("one");
            history.record("three");
            history.record("four");
            assertThat(history.recent()).containsExactly("four", "three", "one");
            history.close();
            result.set(history);
        });
        assertThat(result.get().closedFuture()).isCompleted();
    }

    @Test void listenersObserveTheSameUpdatedOrderAndCanUnsubscribe() throws Exception {
        edt(() -> {
            var history = new CommandHistory();
            var first = new ArrayList<List<String>>();
            var second = new ArrayList<List<String>>();
            CommandRegistry.Subscription subscription = history.onChanged(
                () -> first.add(history.recent()));
            history.onChanged(() -> second.add(history.recent()));

            history.record("new_tab");
            subscription.close();
            history.record("split_right");

            assertThat(first).containsExactly(List.of("new_tab"));
            assertThat(second).containsExactly(List.of("new_tab"),
                List.of("split_right", "new_tab"));
            history.close();
        });
    }

    @Test void invalidRecordIsRejectedButPostCloseRecordIsIgnored() throws Exception {
        edt(() -> {
            var history = new CommandHistory();
            assertThatThrownBy(() -> history.record("Query text"))
                .isInstanceOf(IllegalArgumentException.class);
            history.close();
            history.record("valid_after_close");
            assertThat(history.recent()).isEmpty();
            assertThatThrownBy(() -> history.onChanged(() -> {}))
                .isInstanceOf(IllegalStateException.class);
        });
    }

    @Test void closedListenersReceiveNoLateLoadNotification() throws Exception {
        Path file = directory.resolve("command-history.toml");
        CommandHistoryFile.write(file, List.of("saved"));
        ManualExecutor worker = new ManualExecutor();
        DeliveryQueue deliveries = new DeliveryQueue();
        CommandHistory history = create(file, worker, deliveries);
        List<List<String>> changes = new ArrayList<>();
        edt(() -> {
            history.onChanged(() -> changes.add(history.recent()));
            history.close();
        });

        worker.runNext();
        deliveries.runNext();

        assertThat(changes).isEmpty();
        assertThat(history.closedFuture()).isCompleted();
        assertThat(worker.isShutdown()).isTrue();
    }

    @Test void invocationBeforeLoadStaysNewestAndMergesSavedHistory() throws Exception {
        Path file = directory.resolve("command-history.toml");
        CommandHistoryFile.write(file, List.of("saved_one", "saved_two"));
        ManualExecutor worker = new ManualExecutor();
        DeliveryQueue deliveries = new DeliveryQueue();
        CommandHistory history = create(file, worker, deliveries);

        edt(() -> history.record("invoked"));
        assertRecent(history, "invoked");
        worker.runNext();
        deliveries.runNext();
        assertRecent(history, "invoked", "saved_one", "saved_two");
        worker.runNext();
        deliveries.runNext();
        close(history);

        assertThat(CommandHistoryFile.read(file))
            .containsExactly("invoked", "saved_one", "saved_two");
    }

    @Test void finalWriteSurvivesCloseBeforeInitialLoad() throws Exception {
        Path file = directory.resolve("command-history.toml");
        CommandHistoryFile.write(file, List.of("saved"));
        ManualExecutor worker = new ManualExecutor();
        DeliveryQueue deliveries = new DeliveryQueue();
        CommandHistory history = create(file, worker, deliveries);

        edt(() -> {
            history.record("invoked");
            history.close();
        });
        worker.runNext();
        deliveries.runNext();
        worker.runNext();
        deliveries.runNext();

        assertThat(CommandHistoryFile.read(file)).containsExactly("invoked", "saved");
        assertThat(history.closedFuture()).isCompleted();
        assertThat(worker.isShutdown()).isTrue();
    }

    @Test void recordsQueuedForWritingCoalesceIntoLatestSnapshot() throws Exception {
        Path file = directory.resolve("command-history.toml");
        ManualExecutor worker = new ManualExecutor();
        DeliveryQueue deliveries = new DeliveryQueue();
        CommandHistory history = create(file, worker, deliveries);
        worker.runNext();
        deliveries.runNext();

        edt(() -> {
            history.record("one");
            history.record("two");
            history.record("three");
        });
        assertThat(worker.queued()).isOne();
        worker.runNext();
        deliveries.runNext();
        close(history);

        assertThat(CommandHistoryFile.read(file)).containsExactly("three", "two", "one");
    }

    @Test void separateServiceInstancesReloadUnknownCommandIdsWithoutPurgingThem() throws Exception {
        Path file = directory.resolve("command-history.toml");
        runAndPersist(file, "not_registered.anywhere");

        ManualExecutor worker = new ManualExecutor();
        DeliveryQueue deliveries = new DeliveryQueue();
        CommandHistory reloaded = create(file, worker, deliveries);
        worker.runNext();
        deliveries.runNext();

        assertRecent(reloaded, "not_registered.anywhere");
        close(reloaded);
    }

    @Test void saveFailureIsLoggedAndDoesNotPreventMemoryRecency() throws Exception {
        Path blockedParent = directory.resolve("blocked");
        Files.writeString(blockedParent, "not a directory");
        Path file = blockedParent.resolve("command-history.toml");
        ManualExecutor worker = new ManualExecutor();
        DeliveryQueue deliveries = new DeliveryQueue();

        try (CapturedLog logs = new CapturedLog(CommandHistory.class.getName())) {
            CommandHistory history = create(file, worker, deliveries);
            worker.runNext();
            deliveries.runNext();
            edt(() -> history.record("new_tab"));
            worker.runNext();
            deliveries.runNext();

            assertRecent(history, "new_tab");
            assertThat(logs.records)
                .extracting(LogRecord::getMessage)
                .containsExactly("Could not read command history", "Could not save command history");
            assertThat(logs.records)
                .allSatisfy(record -> assertThat(record.getThrown())
                    .isInstanceOf(java.io.IOException.class));
            close(history);
        }
    }

    private void runAndPersist(Path file, String id) throws Exception {
        ManualExecutor worker = new ManualExecutor();
        DeliveryQueue deliveries = new DeliveryQueue();
        CommandHistory history = create(file, worker, deliveries);
        worker.runNext();
        deliveries.runNext();
        edt(() -> history.record(id));
        worker.runNext();
        deliveries.runNext();
        close(history);
    }

    private static CommandHistory create(Path file, ManualExecutor worker,
                                         DeliveryQueue deliveries) throws Exception {
        AtomicReference<CommandHistory> result = new AtomicReference<>();
        edt(() -> result.set(new CommandHistory(file, worker, deliveries)));
        return result.get();
    }

    private static void close(CommandHistory history) throws Exception {
        edt(history::close);
        assertThat(history.closedFuture()).isCompleted();
    }

    private static void assertRecent(CommandHistory history, String... ids) throws Exception {
        edt(() -> assertThat(history.recent()).containsExactly(ids));
    }

    private static void edt(Runnable task) throws Exception {
        SwingUtilities.invokeAndWait(task);
    }

    private static final class DeliveryQueue implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override public void execute(Runnable task) {
            tasks.add(task);
        }

        void runNext() throws Exception {
            assertThat(tasks).isNotEmpty();
            edt(tasks.remove());
        }
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override public void execute(Runnable task) {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            if (shutdown) throw new java.util.concurrent.RejectedExecutionException();
            tasks.add(task);
        }

        void runNext() {
            assertThat(tasks).isNotEmpty();
            tasks.remove().run();
        }

        int queued() {
            return tasks.size();
        }

        @Override public void shutdown() {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            shutdown = true;
        }

        @Override public List<Runnable> shutdownNow() {
            shutdown = true;
            var remaining = List.copyOf(tasks);
            tasks.clear();
            return remaining;
        }

        @Override public boolean isShutdown() {
            return shutdown;
        }

        @Override public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }
    }

    private static final class CapturedLog implements AutoCloseable {
        private final Logger logger;
        private final Handler handler;
        private final Level priorLevel;
        private final boolean priorUseParentHandlers;
        private final List<LogRecord> records = new ArrayList<>();

        CapturedLog(String name) {
            logger = Logger.getLogger(name);
            priorLevel = logger.getLevel();
            priorUseParentHandlers = logger.getUseParentHandlers();
            handler = new Handler() {
                @Override public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override public void flush() {}
                @Override public void close() {}
            };
            handler.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
        }

        @Override public void close() {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(priorUseParentHandlers);
            logger.setLevel(priorLevel);
        }
    }
}
