package dev.jasper.app.commands;

import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandSearchTest {
    @Test void titlePrefixOutranksRecentKeywordAndReturnsOnlyFive() {
        var entries = new ArrayList<CommandSearch.Entry>();
        for (int i = 0; i < 8; i++) {
            String label = i == 7 ? "Split Right" : "Other " + i;
            var action = new AbstractAction(label) {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
            };
            entries.add(CommandSearch.entry(new Command("test." + i, action, List.of("split"))));
        }
        var matches = CommandSearch.find(entries, " SPLIT ", List.of("test.0"), 5);
        assertThat(matches).hasSize(5);
        assertThat(matches.getFirst().id()).isEqualTo("test.7");
        assertThat(matches.get(1).id()).isEqualTo("test.0");
    }

    @Test void theResultLimitIsConfigurableAndDefaultsToFive() {
        var entries = new ArrayList<CommandSearch.Entry>();
        for (int i = 0; i < 8; i++) {
            var action = new AbstractAction("Other " + i) {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
            };
            entries.add(CommandSearch.entry(new Command("test." + i, action, List.of())));
        }
        assertThat(CommandSearch.find(entries, "other", List.of(), 5)).hasSize(5);
        assertThat(CommandSearch.find(entries, "other", List.of(), 2)).hasSize(2);
        assertThat(CommandSearch.find(entries, "other", List.of(), 20)).hasSize(8);
    }

    @Test void exactPrefixWordPrefixSubstringKeywordAndFuzzyTiersAreOrdered() {
        var entries = List.of(
            entry("test.fuzzy", "SxPliT", List.of()),
            entry("test.keyword", "Configure", List.of("split")),
            entry("test.substring", "Unsplit", List.of()),
            entry("test.word", "Resize Split", List.of()),
            entry("test.prefix", "Split Right", List.of()),
            entry("test.exact", "Split", List.of()));
        assertThat(CommandSearch.find(entries, "split", List.of(), 5))
            .extracting(Command::id)
            .containsExactly("test.exact", "test.prefix", "test.word", "test.substring", "test.keyword");
        assertThat(CommandSearch.find(entries, "sxpt", List.of(), 5))
            .extracting(Command::id)
            .containsExactly("test.fuzzy");
    }

    @Test void everyWordMustMatchAndWorstWordTierDeterminesRelevance() {
        var entries = List.of(
            entry("test.mixed", "Alpha Command", List.of("z")),
            entry("test.keyword", "Other", List.of("ac", "z")),
            entry("test.missing", "Alpha Command", List.of()));
        assertThat(CommandSearch.find(entries, "ac z", List.of(), 5))
            .extracting(Command::id)
            .containsExactly("test.keyword", "test.mixed");
        assertThat(CommandSearch.find(entries, "open split", List.of(), 5))
            .isEmpty();
    }

    @Test void disabledActionsDoNotProduceResultsAndUnicodeSearchIsCaseInsensitive() {
        var disabled = entry("test.disabled", "Connect", List.of());
        disabled.command().action().setEnabled(false);
        var unicode = entry("test.unicode", "Café Session", List.of());
        assertThat(CommandSearch.find(List.of(disabled, unicode), " CAFÉ ", List.of(), 5))
            .extracting(Command::id)
            .containsExactly("test.unicode");
    }

    @Test void recencyBreaksEqualRelevanceAndResultsAreBoundedAndImmutable() {
        var entries = new ArrayList<CommandSearch.Entry>();
        for (int i = 0; i < 8; i++) entries.add(entry("test." + i, "Open", List.of()));
        var matches = CommandSearch.find(entries, "open", List.of("test.5", "test.2"), 5);
        assertThat(matches).hasSize(5);
        assertThat(matches.subList(0, 2)).extracting(Command::id)
            .containsExactly("test.5", "test.2");
        assertThatThrownBy(() -> matches.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void emptyAndNoMatchQueriesReturnEmptyImmutableResults() {
        var entries = List.of(entry("test.open", "Open", List.of()));
        for (String query : List.of("", "   ", "missing")) {
            var results = CommandSearch.find(entries, query, List.of(), 5);
            assertThat(results).isEmpty();
            assertThatThrownBy(results::clear).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test void customTitleAndIconMetadataAreSeparateFromMenuName() {
        var action = new AbstractAction("Menu Label") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
        var icon = new javax.swing.ImageIcon();
        action.putValue(Command.TITLE, "Palette Label");
        action.putValue(Command.ICON, icon);
        var command = new Command("test.metadata", action, List.of());
        assertThat(command.title()).isEqualTo("Palette Label");
        assertThat(command.icon()).isSameAs(icon);
        assertThat(action.getValue(Action.NAME)).isEqualTo("Menu Label");
    }

    private static CommandSearch.Entry entry(String id, String title, List<String> keywords) {
        var action = new AbstractAction(title) {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
        return CommandSearch.entry(new Command(id, action, keywords));
    }
}
