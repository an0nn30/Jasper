package dev.jasper.indexer.ui;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.index.IndexStore;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerSettingsPanelTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    @TempDir Path data;

    static <T extends Component> T find(Container root, Class<T> type, String name) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) {
                T found = find(container, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void listsProjectsAndReindexesOrRemovesTheSelectedOne() {
        var projects = new Projects();
        var io = new ManualExecutor();
        var ui = new ManualExecutor();
        var listed = new ArrayList<Indexer.Job>();
        var notifications = new FakeNotifications();
        var indexer = new Indexer(projects, new IndexStore(data.resolve("store")), job -> { listed.add(job); return new CompletableFuture<Listing>(); },
            io, ui, Instant::now, text -> { }, notifications.group("r"), notifications.group("f"), System.getLogger("test"));
        projects.loaded(new Project(APP, "app", false), Optional.of(new ProjectIndex(APP, List.of("a", "b"), Strategy.GIT, Instant.EPOCH, false)));
        var adds = new AtomicInteger();
        var allow = new AtomicBoolean(false);
        var questions = new ArrayList<String>();
        try (var host = new FakePluginHost(data)) {
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> {
                var settings = new IndexerSettings(context.settings());
                context.settings().page(new IndexerSettingsPanel(settings, projects, indexer, key -> "at " + key.root(), adds::incrementAndGet,
                    question -> { questions.add(question); return allow.get(); }));
            });
            JComponent page = host.settingsPanel(IndexerTesting.INFO.id()).orElseThrow();
            JTable table = find(page, JTable.class, "projects");
            assertThat(table.getRowCount()).isEqualTo(1);
            assertThat(table.getValueAt(0, 0)).isEqualTo("app");
            assertThat(table.getValueAt(0, 1)).isEqualTo("at /w/app");
            assertThat(table.getValueAt(0, 2)).isEqualTo("2");
            assertThat(table.getValueAt(0, 4)).isEqualTo("Fresh");
            JButton reindex = find(page, JButton.class, "reindex");
            JButton remove = find(page, JButton.class, "remove");
            assertThat(reindex.isEnabled()).as("nothing selected").isFalse();
            find(page, JButton.class, "add-folder").doClick();
            assertThat(adds).hasValue(1);
            table.setRowSelectionInterval(0, 0);
            reindex.doClick();
            assertThat(listed).singleElement().satisfies(job -> assertThat(job.foreground()).isTrue());
            assertThat(table.getValueAt(0, 4)).as("the table follows the model").isEqualTo("Indexing…");
            assertThat(remove.isEnabled()).as("a model change clears the selection").isFalse();
            table.setRowSelectionInterval(0, 0);
            remove.doClick();
            assertThat(questions).containsExactly("Remove app from Project Index? Its saved index is deleted.");
            assertThat(projects.entries()).as("declined").hasSize(1);
            allow.set(true);
            table.setRowSelectionInterval(0, 0);
            remove.doClick();
            assertThat(projects.entries()).isEmpty();
            assertThat(table.getRowCount()).isZero();
        }
    }
}
