package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.KitTestSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 11.3: every heading the controller makes says which kind it is; the card keeps its strings. */
class PaletteHeadingsTest {
    static PaletteScope scope(String id, String label, String section, String... rows) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return "Search " + label; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("run", "Run")); }
            @Override public PaletteResults search(String query, PaletteContext context) {
                var found = new ArrayList<PaletteRow>();
                for (String row : rows) if (row.contains(query)) found.add(PaletteRow.of(row, row));
                return new PaletteResults(found.subList(0, Math.min(found.size(), context.maxResults())), section, null);
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    static List<String> headings(PaletteController controller) {
        var texts = new ArrayList<String>();
        for (PaletteEntry entry : ((CardView) controller.view()).entries())
            if (entry instanceof PaletteEntry.Header header) texts.add(header.kind() + " " + header.label());
        return texts;
    }

    @Test void theControllerTypesEveryHeadingAndKeepsTheCardsWords() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry(); var history = new CommandHistory()) {
                registry.register(scope("t.alpha", "Alpha", "Recent", "a1", "a2", "a3", "a4", "a5"));
                registry.register(scope("t.beta", "Beta", null, "b1"));
                long now = System.currentTimeMillis();
                for (int i = 1; i <= 4; i++) history.recordUse("t.alpha", "a" + i, "a" + i, now + i);
                var controller = PaletteTestSupport.controller(KitTestSupport.context(), registry, history);
                try {
                    controller.open(PaletteScope.ALL_ID, PaletteTarget.none(), () -> true);
                    assertThat(headings(controller)).as("three recent rows (3e), then the most used one")
                        .containsExactly("RECENT Recently used", "USED Most used");
                    controller.view().setQueryText("1");
                    assertThat(headings(controller)).as("a query heads each scope by its label")
                        .containsExactly("MATCHES Alpha", "MATCHES Beta");
                    controller.selectTab("t.alpha");
                    controller.view().setQueryText("");
                    assertThat(headings(controller)).as("a chosen scope's own section label").containsExactly("SECTION Recent");
                } finally { controller.close(); }
            }
        });
    }

    @Test void withoutUsesTheEmptyQuerySuggests() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry(); var history = new CommandHistory()) {
                registry.register(scope("t.alpha", "Alpha", null, "a1", "a2"));
                var controller = PaletteTestSupport.controller(KitTestSupport.context(), registry, history);
                try {
                    controller.open(PaletteScope.ALL_ID, PaletteTarget.none(), () -> true);
                    assertThat(headings(controller)).containsExactly("SUGGESTED Suggested");
                } finally { controller.close(); }
            }
        });
    }
}
