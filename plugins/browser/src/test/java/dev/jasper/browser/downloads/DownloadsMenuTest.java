package dev.jasper.browser.downloads;

import dev.jasper.browser.downloads.Download.State;
import dev.jasper.sdk.ui.MenuItem;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 10, with rulings F6 and F37: the Downloads menu's entries are Clear All, a separator, then one submenu
 * per download in the order they started, titled with its name and its progress or result, and each submenu holds
 * Stop, Open, Show and Remove from List as its state allows. Each leaf's action is its id.
 */
class DownloadsMenuTest {
    private static final URI SOURCE = URI.create("https://example.com/report.pdf");
    private static final String EMOJI = "\uD83D\uDE00";

    @Test void clearAllThenASeparatorThenOneSubmenuPerDownloadInItsOrder() {
        List<Download> all = List.of(download(3, "running.zip", State.RUNNING, 42, 100, false),
            download(5, "open.pdf", State.FINISHED, 10, 10, true), download(6, "unmarked.dmg", State.FINISHED, 10, 10, false),
            download(8, "failed.zip", State.FAILED, 1, 10, false), download(9, "stopped.zip", State.STOPPED, 1, 10, false));
        List<MenuItem> items = DownloadsMenu.items(all, "Show in Finder");
        assertThat(items).extracting(MenuItem::id).containsExactly("downloads.clear-all", "downloads.separator", "downloads.3", "downloads.5",
            "downloads.6", "downloads.8", "downloads.9");

        MenuItem clearAll = items.get(0);
        assertThat(clearAll).extracting(MenuItem::label, MenuItem::action, MenuItem::enabled, MenuItem::separator)
            .containsExactly("Clear All", "downloads.clear-all", true, false);
        assertThat(clearAll.children()).isEmpty();
        assertThat(items.get(1).separator()).isTrue();

        assertThat(items.get(2).label()).isEqualTo("running.zip \u2014 42%");
        assertThat(items.get(2).action()).as("a submenu reports nothing").isEmpty();
        assertThat(children(items.get(2))).containsExactly("downloads.3.stop|Stop");
        assertThat(items.get(3).label()).isEqualTo("open.pdf \u2014 Finished");
        assertThat(children(items.get(3))).containsExactly("downloads.5.open|Open", "downloads.5.show|Show in Finder",
            "downloads.5.remove|Remove from List");
        assertThat(children(items.get(4))).as("F6: a failed mark gives no Open").containsExactly("downloads.6.show|Show in Finder",
            "downloads.6.remove|Remove from List");
        assertThat(items.get(5).label()).isEqualTo("failed.zip \u2014 Failed");
        assertThat(children(items.get(5))).containsExactly("downloads.8.remove|Remove from List");
        assertThat(items.get(6).label()).isEqualTo("stopped.zip \u2014 Stopped");
        assertThat(children(items.get(6))).containsExactly("downloads.9.remove|Remove from List");

        for (MenuItem item : items) {
            assertThat(item.enabled()).as(item.id()).isTrue();
            assertThat(item.icon()).as(item.id()).isEmpty();
            assertThat(item.shortcut()).as(item.id()).isEmpty();
            for (MenuItem leaf : item.children()) {
                assertThat(leaf.action()).as("F37: a leaf's action is its id").isEqualTo(leaf.id());
                assertThat(leaf).extracting(MenuItem::enabled, MenuItem::separator, MenuItem::icon, MenuItem::shortcut)
                    .containsExactly(true, false, "", "");
                assertThat(leaf.children()).isEmpty();
            }
        }
    }

    @Test void clearAllIsEnabledOnlyWhenADownloadIsNotRunning() {
        List<MenuItem> running = DownloadsMenu.items(List.of(download(1, "a.zip", State.RUNNING, 0, 0, false),
            download(2, "b.zip", State.RUNNING, 5, 10, false)), "Show in Folder");
        assertThat(running.getFirst().enabled()).isFalse();
        assertThat(running).extracting(MenuItem::id).containsExactly("downloads.clear-all", "downloads.separator", "downloads.1", "downloads.2");

        List<MenuItem> none = DownloadsMenu.items(List.of(), "Show in Folder");
        assertThat(none).as("no separator without downloads").singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("downloads.clear-all");
            assertThat(item.enabled()).isFalse();
        });
        assertThat(DownloadsMenu.items(List.of(download(1, "a.zip", State.FAILED, 0, 0, false)), "Show in Folder").getFirst().enabled())
            .isTrue();
    }

    @Test void theShowEntryCarriesTheLabelItIsGiven() {
        List<MenuItem> items = DownloadsMenu.items(List.of(download(4, "a.zip", State.FINISHED, 1, 1, true)), "Show in Explorer");
        assertThat(children(items.get(2))).contains("downloads.4.show|Show in Explorer");
    }

    @Test void aRunningTitleShowsItsPercentOrWhatHasArrived() {
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.RUNNING, 42, 100, false))).isEqualTo("report.pdf \u2014 42%");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.RUNNING, 0, 100, false))).isEqualTo("report.pdf \u2014 0%");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.RUNNING, 999, 1000, false))).as("rounded down")
            .isEqualTo("report.pdf \u2014 99%");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.RUNNING, 100, 100, false))).as("completed, being marked")
            .isEqualTo("report.pdf \u2014 100%");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.RUNNING, 150, 100, false))).isEqualTo("report.pdf \u2014 100%");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.RUNNING, 1_234_567, 0, false))).as("an unknown size")
            .isEqualTo("report.pdf \u2014 1.2 MB");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.RUNNING, 4_000_000_000L, 8_000_000_000L, false)))
            .isEqualTo("report.pdf \u2014 50%");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.FINISHED, 1, 1, true))).isEqualTo("report.pdf \u2014 Finished");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.FAILED, 1, 1, false))).isEqualTo("report.pdf \u2014 Failed");
        assertThat(DownloadsMenu.title(download(1, "report.pdf", State.STOPPED, 1, 1, false))).isEqualTo("report.pdf \u2014 Stopped");
    }

    /** F37: a name over 60 code points keeps its first and last 28, never splitting a surrogate pair. */
    @Test void aLongNameKeepsItsFirstAndLastTwentyEightCodePoints() {
        String sixty = "a".repeat(30) + "b".repeat(30);
        assertThat(DownloadsMenu.title(download(1, sixty, State.FAILED, 0, 0, false))).isEqualTo(sixty + " \u2014 Failed");
        String long61 = "a".repeat(28) + "-middle-" + "z".repeat(25);
        assertThat(long61.codePointCount(0, long61.length())).isEqualTo(61);
        assertThat(DownloadsMenu.title(download(1, long61, State.FAILED, 0, 0, false)))
            .isEqualTo("a".repeat(28) + "\u2026" + "le-" + "z".repeat(25) + " \u2014 Failed");

        String emoji = EMOJI.repeat(28) + "x".repeat(10) + EMOJI.repeat(28);
        String title = DownloadsMenu.title(download(1, emoji, State.STOPPED, 0, 0, false));
        assertThat(title).isEqualTo(EMOJI.repeat(28) + "\u2026" + EMOJI.repeat(28) + " \u2014 Stopped");
        assertThat(title.codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF)).as("no surrogate apart").isFalse();
        String around = "a".repeat(27) + EMOJI + "x".repeat(10) + EMOJI + "z".repeat(27);
        assertThat(DownloadsMenu.title(download(1, around, State.STOPPED, 0, 0, false)))
            .isEqualTo("a".repeat(27) + EMOJI + "\u2026" + EMOJI + "z".repeat(27) + " \u2014 Stopped");
    }

    @Test void sizesAreInDecimalUnits() {
        assertThat(DownloadsMenu.size(0)).isEqualTo("0 bytes");
        assertThat(DownloadsMenu.size(1)).isEqualTo("1 byte");
        assertThat(DownloadsMenu.size(999)).isEqualTo("999 bytes");
        assertThat(DownloadsMenu.size(1_000)).isEqualTo("1 KB");
        assertThat(DownloadsMenu.size(1_500)).isEqualTo("2 KB");
        assertThat(DownloadsMenu.size(999_499)).isEqualTo("999 KB");
        assertThat(DownloadsMenu.size(999_500)).as("not 1000 KB").isEqualTo("1.0 MB");
        assertThat(DownloadsMenu.size(1_234_567)).isEqualTo("1.2 MB");
        assertThat(DownloadsMenu.size(999_949_999)).isEqualTo("999.9 MB");
        assertThat(DownloadsMenu.size(999_950_000)).as("not 1000.0 MB").isEqualTo("1.0 GB");
        assertThat(DownloadsMenu.size(5_000_000_000L)).isEqualTo("5.0 GB");
        assertThat(DownloadsMenu.size(2_500_000_000_000L)).isEqualTo("2.5 TB");
        assertThat(DownloadsMenu.size(-1)).as("never negative").isEqualTo("0 bytes");
    }

    /** F37: sizes read the same in every locale: never "1,2 MB". */
    @Test void sizesIgnoreTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(DownloadsMenu.size(1_234_567)).isEqualTo("1.2 MB");
            assertThat(DownloadsMenu.size(5_000_000_000L)).isEqualTo("5.0 GB");
            assertThat(DownloadsMenu.title(download(1, "a.zip", State.RUNNING, 1_234_567, 0, false))).isEqualTo("a.zip \u2014 1.2 MB");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test void theShowLabelNamesThePlatformsFileManager() {
        assertThat(DownloadsMenu.showLabel("Mac OS X")).isEqualTo("Show in Finder");
        assertThat(DownloadsMenu.showLabel("Windows 11")).isEqualTo("Show in Explorer");
        assertThat(DownloadsMenu.showLabel("Windows Server 2022")).isEqualTo("Show in Explorer");
        assertThat(DownloadsMenu.showLabel("Linux")).isEqualTo("Show in Folder");
        assertThat(DownloadsMenu.showLabel("FreeBSD")).isEqualTo("Show in Folder");
    }

    private static Download download(long serial, String name, State state, long received, long total, boolean openable) {
        Path file = Path.of("downloads").resolve("file" + serial);
        return new Download(serial, new DownloadKey(1, (int) serial), name, file, SOURCE, state, received, total, openable);
    }

    private static List<String> children(MenuItem submenu) {
        return submenu.children().stream().map(item -> item.id() + "|" + item.label()).toList();
    }
}
