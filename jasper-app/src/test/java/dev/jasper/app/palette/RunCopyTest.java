package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 11.2 and 11.3: the Run window's headings, nouns, detail line, primary label and "i of n". */
class RunCopyTest {
    static PaletteScope scope(String id, String label, String noun, List<PaletteVerb> verbs) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return ""; }
            @Override public List<PaletteVerb> verbs() { return verbs; }
            @Override public String itemNoun() { return noun; }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    static final PaletteScope HOSTS = scope("t.hosts", "SSH", "session", List.of(new PaletteVerb("connect", "Connect"),
        new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT), new PaletteVerb("edit", "Edit")));
    static final PaletteScope SNIPPETS = scope("t.snippets", "Snippets", "snippet", List.of(new PaletteVerb("insert", "Insert")));
    static final PaletteScope LABELLED = scope("t.other", "File Servers", "File Servers", List.of(new PaletteVerb("run", "Run")));

    static PaletteEntry.Item item(PaletteScope scope, String id, String detail) {
        return new PaletteEntry.Item(scope, new PaletteRow(id, "Row " + id, detail, null, null, true, null));
    }

    @Test void headingsReadTheRunWindowsWords() {
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.RECENT, "Recently used"))).isEqualTo("Recent");
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.USED, "Most used"))).isEqualTo("Most used");
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.SUGGESTED, "Suggested"))).isEqualTo("Suggested");
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.SECTION, "Pinned"))).isEqualTo("Pinned");
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "SSH"))).isEqualTo("Matches");
    }

    /** Review Focus 2. */
    @Test void iOfNCountsMatchingRowsOnlyAndBlanksOnAMoreRow() {
        var entries = List.<PaletteEntry>of(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "SSH"), item(HOSTS, "a", null),
            item(HOSTS, "b", null), new PaletteEntry.More(HOSTS), new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "Snippets"),
            item(SNIPPETS, "c", null));
        assertThat(RunCopy.position(entries, 1)).isEqualTo("1 of 3");
        assertThat(RunCopy.position(entries, 5)).isEqualTo("3 of 3");
        assertThat(RunCopy.position(entries, 3)).as("a More row").isEmpty();
        assertThat(RunCopy.position(entries, -1)).as("no selection").isEmpty();
        var recent = List.<PaletteEntry>of(new PaletteEntry.Header(PaletteEntry.Header.Kind.RECENT, "Recently used"), item(HOSTS, "a", null));
        assertThat(RunCopy.position(recent, 1)).as("only under a query").isEmpty();
    }

    @Test void theDetailLineNamesTheRowByItsNoun() {
        assertThat(RunCopy.detailLabel(item(HOSTS, "mac", "dustin@192.168.1.119:22"))).isEqualTo("Session:");
        assertThat(RunCopy.detailValue(item(HOSTS, "mac", "dustin@192.168.1.119:22"))).isEqualTo("dustin@192.168.1.119:22");
        assertThat(RunCopy.detailValue(item(SNIPPETS, "x", null))).as("no detail: the title").isEqualTo("Row x");
        assertThat(RunCopy.detailLabel(item(LABELLED, "y", null))).as("a label noun keeps its case").isEqualTo("File Servers:");
        assertThat(RunCopy.noun(SNIPPETS)).isEqualTo("Snippet");
        assertThat(RunCopy.detailLabel(new PaletteEntry.More(HOSTS))).isEmpty();
        assertThat(RunCopy.detailLabel(null)).isEmpty();
        assertThat(RunCopy.detailValue(null)).isEmpty();
    }

    @Test void thePrimaryButtonReadsVerbZeroAndOpenOtherwise() {
        assertThat(RunCopy.primaryLabel(item(HOSTS, "mac", null))).isEqualTo("Connect");
        assertThat(RunCopy.primaryEnabled(item(HOSTS, "mac", null))).isTrue();
        assertThat(RunCopy.primaryLabel(new PaletteEntry.More(HOSTS))).isEqualTo("Open");
        assertThat(RunCopy.primaryEnabled(new PaletteEntry.More(HOSTS))).isTrue();
        assertThat(RunCopy.primaryLabel(null)).isEqualTo("Open");
        assertThat(RunCopy.primaryEnabled(null)).isFalse();
        assertThat(RunCopy.splitVerb(HOSTS)).isEqualTo(1);
        assertThat(RunCopy.splitVerb(SNIPPETS)).isEqualTo(-1);
        assertThat(RunCopy.HELP).isEqualTo("Type the name of a session, command, file or snippet, and Jasper will open it for you.");
    }
}
