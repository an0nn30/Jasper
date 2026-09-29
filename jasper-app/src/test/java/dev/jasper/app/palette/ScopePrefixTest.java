package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScopePrefixTest {
    private static PaletteScope scope(String id, String label) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return ""; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("one", "One")); }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    private static final PaletteScope COMMANDS = scope(PaletteScope.COMMANDS_ID, "Commands");
    private static final PaletteScope HISTORY = scope("dev.jasper.history.scope", "History");
    private static final PaletteScope SNIPPETS = scope("dev.jasper.snippets.scope", "Snippets");
    private static final PaletteScope SSH = scope("dev.jasper.remote.scope", "SSH");
    private static final PaletteScope SESSIONS = scope("test.sessions", "Sessions");
    private static final List<PaletteScope> ALL = List.of(COMMANDS, HISTORY, SNIPPETS, SSH, SESSIONS);

    @Test void aLeadingGreaterThanIsTheCommandsChip() {
        assertThat(ScopePrefix.parse(">", ALL)).contains(new ScopePrefix(COMMANDS, ""));
        assertThat(ScopePrefix.parse("> new tab", ALL)).contains(new ScopePrefix(COMMANDS, "new tab"));
        assertThat(ScopePrefix.parse(">split", ALL)).contains(new ScopePrefix(COMMANDS, "split"));
    }

    @Test void aScopeLabelOrItsUniqueStartThenGreaterThanIsThatScopesChip() {
        assertThat(ScopePrefix.parse("history > ls", ALL)).contains(new ScopePrefix(HISTORY, "ls"));
        assertThat(ScopePrefix.parse("HISTORY>", ALL)).contains(new ScopePrefix(HISTORY, ""));
        assertThat(ScopePrefix.parse("hist >", ALL)).contains(new ScopePrefix(HISTORY, ""));
        assertThat(ScopePrefix.parse("sn > deploy", ALL)).contains(new ScopePrefix(SNIPPETS, "deploy"));
        assertThat(ScopePrefix.parse("ssh > prod", ALL)).as("an exact label wins over a longer one").contains(new ScopePrefix(SSH, "prod"));
    }

    @Test void unknownAmbiguousOrMidQueryPrefixesStayText() {
        assertThat(ScopePrefix.parse("s > x", ALL)).as("Snippets, SSH and Sessions").isEmpty();
        assertThat(ScopePrefix.parse("cat foo > bar", ALL)).isEmpty();
        assertThat(ScopePrefix.parse(" >", ALL)).as("only at the very start").isEmpty();
        assertThat(ScopePrefix.parse(" history >", ALL)).isEmpty();
        assertThat(ScopePrefix.parse("history", ALL)).isEmpty();
        assertThat(ScopePrefix.parse("", ALL)).isEmpty();
        assertThat(ScopePrefix.parse(">", List.of(HISTORY))).as("no Commands scope registered").isEmpty();
    }
}
