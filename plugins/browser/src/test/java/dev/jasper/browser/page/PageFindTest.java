package dev.jasper.browser.page;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.page.PageFind.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Find in page's Java side (spec section 10; plan 4b Task 5), with a recording page: the frames it lists, the frame
 * that has the focus, and each script Jasper runs in a frame, as the frame and the command {@code find.js} is called
 * with. Nothing here needs Chromium: the frames' answers are fed as the message router hands them on.
 */
class PageFindTest {
    private final List<String> frames = new ArrayList<>(List.of("main", "ad", "chat"));
    private final List<String> ran = new ArrayList<>();
    private final List<Runnable> waits = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();
    private Optional<String> focused = Optional.of("chat");
    private final PageFind find = new PageFind(() -> List.copyOf(frames), () -> focused,
        (frame, script) -> ran.add(frame + " " + PageTestAccess.findCommand(script)), (delay, task) -> {
            delays.add(delay);
            waits.add(task);
            return () -> waits.remove(task);
        });

    /** The query reaches each frame's script only as a JSON string, whatever it holds, with the options as booleans. */
    @Test void theQueryAndOptionsReachEveryFramesScriptAsJson() {
        String query = "Ab \"c\\ ');alert(1);// </script> " + (char) 0x2028 + (char) 0 + "\n" + (char) 0xD83D;
        CompletableFuture<Result> result = find.find(query, true, false);
        assertThat(ran).extracting(each -> each.substring(0, each.indexOf(' '))).containsExactly("main", "ad", "chat");
        long search = search(0);
        for (String each : ran) {
            String json = each.substring(each.indexOf(' ') + 1);
            assertThat(json.chars().filter(c -> c < 0x20 || c == 0x7F || c == 0x2028 || c == 0x2029 || Character.isSurrogate((char) c)))
                .as("raw characters that would end the string or the line").isEmpty();
            assertThat(command(each)).containsExactly(Map.entry("action", "search"), Map.entry("search", search),
                Map.entry("query", query), Map.entry("matchCase", true), Map.entry("regex", false));
        }
        assertThat(result).isNotDone();
        assertThat(delays).containsExactly(PageFind.ANSWER_WAIT);
        find.find("x", false, true);
        assertThat(command(ran.getLast())).containsEntry("matchCase", false).containsEntry("regex", true);
        assertThat(search(ran.size() - 1)).as("each search its own id").isGreaterThan(search);
        assertThat(PageFind.SOURCE).startsWith("/*").contains("CSS.highlights").contains("#FFFF00").contains("#FF9632");
    }

    @Test void countsSumAcrossFramesAndTheFirstMatchBecomesCurrent() {
        CompletableFuture<Result> result = find.find("needle", false, false);
        long search = search(0);
        find.answered("ad", search, 3, false);
        find.answered("main", search, 2, false);
        assertThat(result).as("one frame has not answered").isNotDone();
        assertThat(find.result()).hasValue(new Result(5, 0, false));
        find.answered("chat", search, 0, false);
        assertThat(result).isCompletedWithValue(new Result(5, 1, false));
        assertThat(find.result()).hasValue(new Result(5, 1, false));
        assertThat(ran).last().isEqualTo("main {\"action\":\"select\",\"search\":" + search + ",\"index\":0}");
        assertThat(waits).as("the wait ended with the last answer").isEmpty();
    }

    @Test void theFirstMatchIsInTheFirstFrameWithMatches() {
        CompletableFuture<Result> result = find.find("needle", false, false);
        long search = search(0);
        find.answered("main", search, 0, false);
        find.answered("ad", search, 4, false);
        find.answered("chat", search, 1, false);
        assertThat(result).isCompletedWithValue(new Result(5, 1, false));
        assertThat(ran).last().isEqualTo("ad {\"action\":\"select\",\"search\":" + search + ",\"index\":0}");
    }

    @Test void anInvalidPatternReportsInvalid() {
        CompletableFuture<Result> result = find.find("(", false, true);
        long search = search(0);
        for (String frame : frames) find.answered(frame, search, 0, true);
        assertThat(result).isCompletedWithValue(new Result(0, 0, true));
        assertThat(ran).as("nothing to select").hasSize(3);
        assertThat(find.next()).isEqualTo(new Result(0, 0, true));
        assertThat(find.previous()).isEqualTo(new Result(0, 0, true));
        assertThat(ran).hasSize(3);
    }

    @Test void aQueryOfMoreThanAThousandCharactersIsCutToAThousand() {
        find.find("a".repeat(1_001), false, false);
        assertThat((String) command(ran.getFirst()).get("query")).isEqualTo("a".repeat(PageFind.MAX_QUERY));
        find.find("a".repeat(PageFind.MAX_QUERY), false, false);
        assertThat((String) command(ran.getLast()).get("query")).as("1,000 are kept").hasSize(PageFind.MAX_QUERY);
        String emoji = "" + (char) 0xD83D + (char) 0xDE00;
        find.find("a".repeat(999) + emoji, false, false);
        assertThat((String) command(ran.getLast()).get("query")).as("never half a character").isEqualTo("a".repeat(999));
    }

    @Test void anEmptyQuerySearchesNothing() {
        CompletableFuture<Result> result = find.find("", false, false);
        assertThat(result).isCompletedWithValue(new Result(0, 0, false));
        assertThat(ran).isEmpty();
        assertThat(find.result()).isEmpty();
    }

    /** An answer is shown only for the search under way, from a frame it reached, with a count within the bounds. */
    @Test void anAnswerForAnotherSearchFromAnotherFrameOrOutOfBoundsIsDropped() {
        find.find("one", false, false);
        long first = search(0);
        CompletableFuture<Result> result = find.find("two", false, false);
        long second = search(3);
        find.answered("main", first, 9, false);
        find.answered("elsewhere", second, 9, false);
        find.answered("", second, 9, false);
        find.answered("main", second, -1, false);
        find.answered("main", second, PageFind.MAX_MATCHES + 1, false);
        find.answered("main", 0, 9, false);
        assertThat(find.result()).hasValue(new Result(0, 0, false));
        find.answered("main", second, PageFind.MAX_MATCHES, false);
        find.answered("ad", second, 0, false);
        find.answered("chat", second, 0, false);
        assertThat(result).isCompletedWithValue(new Result(PageFind.MAX_MATCHES, 1, false));
    }

    @Test void nextAndPreviousStepFrameByFrameAndWrapAround() {
        settle(2, 0, 1);
        long search = search(0);
        assertThat(find.result()).hasValue(new Result(3, 1, false));
        ran.clear();
        assertThat(find.next()).isEqualTo(new Result(3, 2, false));
        assertThat(ran).containsExactly(select("main", search, 1));
        ran.clear();
        assertThat(find.next()).as("the frame without matches is passed over").isEqualTo(new Result(3, 3, false));
        assertThat(ran).containsExactly(select("main", search, -1), select("chat", search, 0));
        ran.clear();
        assertThat(find.next()).as("round to the first").isEqualTo(new Result(3, 1, false));
        assertThat(ran).containsExactly(select("chat", search, -1), select("main", search, 0));
        ran.clear();
        assertThat(find.previous()).as("round to the last").isEqualTo(new Result(3, 3, false));
        assertThat(ran).containsExactly(select("main", search, -1), select("chat", search, 0));
        ran.clear();
        assertThat(find.previous()).isEqualTo(new Result(3, 2, false));
        assertThat(ran).containsExactly(select("chat", search, -1), select("main", search, 1));
        assertThat(find.previous()).isEqualTo(new Result(3, 1, false));
        assertThat(ran).last().isEqualTo(select("main", search, 0));
    }

    @Test void oneMatchWrapsToItselfAndNoMatchesStepNowhere() {
        settle(0, 1, 0);
        long search = search(0);
        ran.clear();
        assertThat(find.next()).isEqualTo(new Result(1, 1, false));
        assertThat(ran).containsExactly(select("ad", search, 0));
        ran.clear();
        assertThat(find.previous()).isEqualTo(new Result(1, 1, false));
        assertThat(ran).containsExactly(select("ad", search, 0));
        find.find("nothing", false, false);
        long none = search(ran.size() - 1);
        for (String frame : frames) find.answered(frame, none, 0, false);
        ran.clear();
        assertThat(find.next()).isEqualTo(new Result(0, 0, false));
        assertThat(find.previous()).isEqualTo(new Result(0, 0, false));
        assertThat(ran).isEmpty();
        assertThat(new PageFind(List::of, Optional::empty, (frame, script) -> { }, (delay, task) -> () -> { }).next())
            .as("no search").isEqualTo(new Result(0, 0, false));
    }

    @Test void aFrameThatNeverAnswersLeavesTheSearchToSettleAfterTheWait() {
        var changes = new AtomicInteger();
        find.onChange(changes::incrementAndGet);
        CompletableFuture<Result> result = find.find("needle", false, false);
        long search = search(0);
        find.answered("main", search, 2, false);
        find.answered("ad", search, 1, false);
        assertThat(result).isNotDone();
        waits.getFirst().run();
        assertThat(result).isCompletedWithValue(new Result(3, 1, false));
        assertThat(changes).as("the search's own future tells its end").hasValue(0);
        find.answered("chat", search, 4, false);
        assertThat(find.result()).as("a late answer counts").hasValue(new Result(7, 1, false));
        assertThat(changes).hasValue(1);
    }

    /** A frame searches again when its document changes and answers its new count, which stays within its matches. */
    @Test void aFramesNewCountAfterItsDocumentChangedUpdatesTheResult() {
        var changes = new AtomicInteger();
        find.onChange(changes::incrementAndGet);
        settle(2, 0, 1);
        long search = search(0);
        find.next();
        assertThat(find.result()).hasValue(new Result(3, 2, false));
        find.answered("main", search, 1, false);
        assertThat(find.result()).as("the current match moves onto the frame's last").hasValue(new Result(2, 1, false));
        find.answered("main", search, 0, false);
        assertThat(find.result()).as("no match is current").hasValue(new Result(1, 0, false));
        find.answered("main", search, 0, false);
        assertThat(changes).as("told each change, not a repeat").hasValue(2);
        ran.clear();
        assertThat(find.next()).isEqualTo(new Result(1, 1, false));
        assertThat(ran).containsExactly(select("chat", search, 0));
        find.answered("main", search, 6, false);
        assertThat(find.result()).hasValue(new Result(7, 7, false));
    }

    @Test void clearEndsTheSearchInEveryFrame() {
        CompletableFuture<Result> result = find.find("needle", false, false);
        long search = search(0);
        find.answered("main", search, 2, false);
        ran.clear();
        find.clear();
        assertThat(ran).containsExactly("main {\"action\":\"clear\"}", "ad {\"action\":\"clear\"}", "chat {\"action\":\"clear\"}");
        assertThat(find.result()).isEmpty();
        assertThat(result).isCancelled();
        assertThat(waits).isEmpty();
        find.answered("ad", search, 2, false);
        assertThat(find.result()).isEmpty();
        ran.clear();
        find.clear();
        assertThat(ran).as("nothing to clear").isEmpty();
    }

    /** A new search replaces the last in the frames it reaches, and clears the frames that have gone since. */
    @Test void aNewSearchClearsTheFramesThatHaveGone() {
        find.find("one", false, false);
        frames.remove("ad");
        frames.add("comments");
        ran.clear();
        find.find("two", false, false);
        assertThat(ran).extracting(each -> each.substring(0, each.indexOf(' '))).containsExactly("ad", "main", "chat", "comments");
        assertThat(ran.getFirst()).isEqualTo("ad {\"action\":\"clear\"}");
    }

    @Test void aPageSearchesAtMostAHundredFrames() {
        frames.clear();
        IntStream.range(0, 150).forEach(at -> frames.add("frame-" + at));
        frames.add("frame-3");
        find.find("needle", false, false);
        assertThat(ran).hasSize(PageFind.MAX_FRAMES);
        assertThat(ran.getLast()).startsWith("frame-99 ");
    }

    /** The main frame's next document ends the search: its highlights went with the document before. */
    @Test void theMainFramesNewDocumentEndsTheSearch() {
        var changes = new AtomicInteger();
        find.onChange(changes::incrementAndGet);
        settle(1, 1, 1);
        long search = search(0);
        ran.clear();
        find.dropped();
        assertThat(find.result()).isEmpty();
        assertThat(changes).hasValue(1);
        assertThat(ran).as("nothing is left to clear").isEmpty();
        find.answered("main", search, 3, false);
        assertThat(find.result()).isEmpty();
        find.dropped();
        assertThat(changes).hasValue(1);
    }

    @Test void theSelectionComesFromTheFocusedFrame() {
        CompletableFuture<String> selection = find.selection();
        assertThat(ran).singleElement().asString().startsWith("chat {\"action\":\"selection\",\"search\":");
        long asked = search(0);
        find.selected("main", asked, "other frame");
        find.selected("chat", asked + 1, "other request");
        assertThat(selection).isNotDone();
        find.selected("chat", asked, "needle");
        assertThat(selection).isCompletedWithValue("needle");
        assertThat(waits).isEmpty();

        CompletableFuture<String> unanswered = find.selection();
        waits.getFirst().run();
        assertThat(unanswered).as("a frame that never answers").isCompletedWithValue("");
        CompletableFuture<String> long_ = find.selection();
        find.selected("chat", search(ran.size() - 1), "x".repeat(PageFind.MAX_QUERY + 1));
        assertThat(long_).isCompletedWithValue("");
        CompletableFuture<String> first = find.selection(), second = find.selection();
        assertThat(first).as("superseded").isCancelled();
        assertThat(second).isNotDone();
        focused = Optional.empty();
        assertThat(find.selection()).isCompletedWithValue("");
    }

    @Test void afterItsPageClosesItRunsNothing() {
        var changes = new AtomicInteger();
        find.onChange(changes::incrementAndGet);
        CompletableFuture<Result> result = find.find("needle", false, false);
        long search = search(0);
        ran.clear();
        find.close();
        assertThat(result).isCancelled();
        assertThat(waits).isEmpty();
        assertThat(find.find("again", false, false)).isCompletedWithValue(new Result(0, 0, false));
        find.answered("main", search, 1, false);
        find.next();
        find.clear();
        find.dropped();
        assertThat(find.selection()).isCompletedWithValue("");
        assertThat(ran).isEmpty();
        assertThat(changes).hasValue(0);
        assertThat(find.result()).isEmpty();
    }

    /** A search whose frames answered {@code counts}, in the order {@link #frames} lists them. */
    private void settle(int... counts) {
        find.find("needle", false, false);
        long search = search(ran.size() - 1);
        for (int at = 0; at < counts.length; at++) find.answered(frames.get(at), search, counts[at], false);
    }

    /** The search id of the command {@link #ran} holds at {@code index}. */
    private long search(int index) {
        return (Long) command(ran.get(index)).get("search");
    }

    private static String select(String frame, long search, int index) {
        return frame + " {\"action\":\"select\",\"search\":" + search + ",\"index\":" + index + "}";
    }

    private static Map<String, Object> command(String ran) {
        return StrictJson.object(ran.substring(ran.indexOf(' ') + 1), 1).orElseThrow();
    }
}
