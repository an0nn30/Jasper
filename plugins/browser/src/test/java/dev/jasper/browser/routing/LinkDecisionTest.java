package dev.jasper.browser.routing;

import dev.jasper.browser.routing.LinkDecision.Decision;
import dev.jasper.browser.item.SearchEngine;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.settings.BrowserSettings.LinkOpening;
import dev.jasper.browser.settings.BrowserSettings.LocalFileOpening;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.LinkPlacement;
import dev.jasper.sdk.ui.WebLink;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where a link Jasper hands the Browser goes (spec section 9), for every link a {@link WebLink} can be: each origin by
 * placement by web address or local file, under every pair of the two link settings. An address from outside opens
 * with no prompt; a local file goes by Open Local Web Files; a split click opens unless Open Links From Terminal sends
 * links to the default browser; any other click goes by Open Links From Terminal.
 */
class LinkDecisionTest {
    private static final URI WEB = URI.create("https://example.com/a"), FILE = URI.create("file:///tmp/page.html");

    /**
     * The table: origin, placement, web or file, Open Links From Terminal, Open Local Web Files, the decision. A link a
     * {@code WebLink} refuses (a file from outside or in a split, an outside address in a split) has no row.
     */
    private static final String TABLE = """
        CLICK    TAB          web   ASK              ASK             ASK
        CLICK    TAB          web   ASK              BUILT_IN        ASK
        CLICK    TAB          web   ASK              DEFAULT_EDITOR  ASK
        CLICK    TAB          web   BUILT_IN         ASK             OPEN
        CLICK    TAB          web   BUILT_IN         BUILT_IN        OPEN
        CLICK    TAB          web   BUILT_IN         DEFAULT_EDITOR  OPEN
        CLICK    TAB          web   DEFAULT_BROWSER  ASK             HAND_BACK
        CLICK    TAB          web   DEFAULT_BROWSER  BUILT_IN        HAND_BACK
        CLICK    TAB          web   DEFAULT_BROWSER  DEFAULT_EDITOR  HAND_BACK
        CLICK    SPLIT_RIGHT  web   ASK              ASK             OPEN
        CLICK    SPLIT_RIGHT  web   ASK              BUILT_IN        OPEN
        CLICK    SPLIT_RIGHT  web   ASK              DEFAULT_EDITOR  OPEN
        CLICK    SPLIT_RIGHT  web   BUILT_IN         ASK             OPEN
        CLICK    SPLIT_RIGHT  web   BUILT_IN         BUILT_IN        OPEN
        CLICK    SPLIT_RIGHT  web   BUILT_IN         DEFAULT_EDITOR  OPEN
        CLICK    SPLIT_RIGHT  web   DEFAULT_BROWSER  ASK             HAND_BACK
        CLICK    SPLIT_RIGHT  web   DEFAULT_BROWSER  BUILT_IN        HAND_BACK
        CLICK    SPLIT_RIGHT  web   DEFAULT_BROWSER  DEFAULT_EDITOR  HAND_BACK
        CLICK    SPLIT_DOWN   web   ASK              ASK             OPEN
        CLICK    SPLIT_DOWN   web   ASK              BUILT_IN        OPEN
        CLICK    SPLIT_DOWN   web   ASK              DEFAULT_EDITOR  OPEN
        CLICK    SPLIT_DOWN   web   BUILT_IN         ASK             OPEN
        CLICK    SPLIT_DOWN   web   BUILT_IN         BUILT_IN        OPEN
        CLICK    SPLIT_DOWN   web   BUILT_IN         DEFAULT_EDITOR  OPEN
        CLICK    SPLIT_DOWN   web   DEFAULT_BROWSER  ASK             HAND_BACK
        CLICK    SPLIT_DOWN   web   DEFAULT_BROWSER  BUILT_IN        HAND_BACK
        CLICK    SPLIT_DOWN   web   DEFAULT_BROWSER  DEFAULT_EDITOR  HAND_BACK
        CLICK    TAB          file  ASK              ASK             ASK
        CLICK    TAB          file  ASK              BUILT_IN        OPEN
        CLICK    TAB          file  ASK              DEFAULT_EDITOR  HAND_BACK
        CLICK    TAB          file  BUILT_IN         ASK             ASK
        CLICK    TAB          file  BUILT_IN         BUILT_IN        OPEN
        CLICK    TAB          file  BUILT_IN         DEFAULT_EDITOR  HAND_BACK
        CLICK    TAB          file  DEFAULT_BROWSER  ASK             ASK
        CLICK    TAB          file  DEFAULT_BROWSER  BUILT_IN        OPEN
        CLICK    TAB          file  DEFAULT_BROWSER  DEFAULT_EDITOR  HAND_BACK
        EXTERNAL TAB          web   ASK              ASK             OPEN
        EXTERNAL TAB          web   ASK              BUILT_IN        OPEN
        EXTERNAL TAB          web   ASK              DEFAULT_EDITOR  OPEN
        EXTERNAL TAB          web   BUILT_IN         ASK             OPEN
        EXTERNAL TAB          web   BUILT_IN         BUILT_IN        OPEN
        EXTERNAL TAB          web   BUILT_IN         DEFAULT_EDITOR  OPEN
        EXTERNAL TAB          web   DEFAULT_BROWSER  ASK             OPEN
        EXTERNAL TAB          web   DEFAULT_BROWSER  BUILT_IN        OPEN
        EXTERNAL TAB          web   DEFAULT_BROWSER  DEFAULT_EDITOR  OPEN
        """;

    @Test void everyLinkAWebLinkCanBeIsDecidedAsTheTableSaysUnderEverySetting() {
        Map<String, Decision> table = new HashMap<>();
        for (String line : TABLE.strip().split("\n")) {
            String[] cells = line.strip().split("\\s+");
            assertThat(cells).as(line).hasSize(6);
            assertThat(table.put(key(cells[0], cells[1], cells[2], cells[3], cells[4]), Decision.valueOf(cells[5]))).as("one row each: " + line).isNull();
        }
        Set<String> decided = new HashSet<>();
        int refused = 0;
        for (LinkOrigin origin : LinkOrigin.values())
            for (LinkPlacement placement : LinkPlacement.values())
                for (String kind : new String[] { "web", "file" }) {
                    WebLink link;
                    try {
                        link = new WebLink(kind.equals("web") ? WEB : FILE, origin, placement, Optional.empty(), Optional.empty());
                    } catch (IllegalArgumentException notALink) {
                        refused++;
                        continue;
                    }
                    for (LinkOpening links : LinkOpening.values())
                        for (LocalFileOpening files : LocalFileOpening.values()) {
                            String key = key(origin.name(), placement.name(), kind, links.name(), files.name());
                            assertThat(table).as("a row for " + key).containsKey(key);
                            assertThat(LinkDecision.decide(link, settings(links, files))).as(key).isEqualTo(table.get(key));
                            decided.add(key);
                        }
                }
        assertThat(decided).as("every row is a link a WebLink can be").isEqualTo(table.keySet());
        assertThat(refused).as("a file from outside or in a split, and an outside address in a split, are no links").isEqualTo(7);
    }

    @Test void aTargetChangesNoDecisionAndHttpDecidesAsHttps() {
        var target = new WebLink(URI.create("http://example.com/b"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of("docs"), Optional.empty());
        assertThat(LinkDecision.decide(target, settings(LinkOpening.ASK, LocalFileOpening.BUILT_IN))).isEqualTo(Decision.ASK);
        assertThat(LinkDecision.decide(target, settings(LinkOpening.BUILT_IN, LocalFileOpening.ASK))).isEqualTo(Decision.OPEN);
        assertThat(LinkDecision.decide(target, settings(LinkOpening.DEFAULT_BROWSER, LocalFileOpening.ASK))).isEqualTo(Decision.HAND_BACK);
        var upper = new WebLink(URI.create("FILE:///tmp/page.svg"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty());
        assertThat(LinkDecision.decide(upper, settings(LinkOpening.BUILT_IN, LocalFileOpening.DEFAULT_EDITOR)))
            .as("a file address in any letter case is a local file").isEqualTo(Decision.HAND_BACK);
        assertThat(LinkDecision.localFile(upper)).isTrue();
        assertThat(LinkDecision.localFile(target)).isFalse();
        assertThatThrownBy(() -> LinkDecision.decide(null, settings(LinkOpening.ASK, LocalFileOpening.ASK))).isInstanceOf(NullPointerException.class);
    }

    private static String key(String origin, String placement, String kind, String links, String files) {
        return String.join(" ", origin, placement, kind, links, files);
    }

    private static BrowserSettings settings(LinkOpening links, LocalFileOpening files) {
        return new BrowserSettings(BrowserSettings.WELCOME, 100, links, files, SearchEngine.GOOGLE, "", "");
    }
}
