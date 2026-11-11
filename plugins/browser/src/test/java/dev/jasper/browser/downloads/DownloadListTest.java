package dev.jasper.browser.downloads;

import dev.jasper.browser.downloads.Download.State;
import dev.jasper.browser.downloads.DownloadList.Outcome;
import dev.jasper.browser.page.DownloadProgress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec section 10, with rulings F2, F6, F19 and D11: the downloads in the order they started, each reporting one
 * outcome, a completed one running at 100% until it is marked and finished, a list of at most the 20 newest and every
 * running one, and the names Chromium's intermediate file and placeholder must not show. Paths are under a
 * {@code @TempDir} and never created (F15).
 */
class DownloadListTest {
    private static final URI SOURCE = URI.create("https://example.com/files/report.pdf");
    private static final DownloadKey KEY = new DownloadKey(7, 1);
    @TempDir Path folder;

    @Test void aDownloadBeginsRunningWithNothingReceived() {
        var list = new DownloadList();
        Download first = list.begin(KEY, "report.pdf", folder.resolve("report.pdf"), SOURCE);
        assertThat(first).isEqualTo(new Download(first.serial(), KEY, "report.pdf", folder.resolve("report.pdf"), SOURCE, State.RUNNING, 0, 0,
            false));
        assertThat(first.serial()).isPositive();
        Download second = list.begin(new DownloadKey(7, 2), "b.zip", folder.resolve("b.zip"), SOURCE);
        assertThat(second.serial()).isGreaterThan(first.serial());
        assertThat(list.begin(KEY, "other.pdf", folder.resolve("other.pdf"), SOURCE)).as("a key already listed").isEqualTo(first);
        assertThat(list.all()).containsExactly(first, second);
        assertThat(new DownloadList().begin(new DownloadKey(8, 1), "x", folder.resolve("x"), SOURCE).serial())
            .as("serials are per list").isEqualTo(first.serial());
    }

    @Test void progressUpdatesTheBytesAndReportsNoOutcome() {
        var list = new DownloadList();
        list.begin(KEY, "report.pdf", folder.resolve("report.pdf"), SOURCE);
        assertThat(list.update(KEY, running(42, 100, ""))).isEmpty();
        assertThat(only(list)).extracting(Download::state, Download::received, Download::total).containsExactly(State.RUNNING, 42L, 100L);
        assertThat(list.update(KEY, running(1_234_567, -1, ""))).isEmpty();
        assertThat(only(list)).as("an unknown size").extracting(Download::received, Download::total).containsExactly(1_234_567L, 0L);
        assertThat(list.update(KEY, running(-5, 0, ""))).isEmpty();
        assertThat(only(list).received()).as("never negative").isZero();
    }

    @Test void aCompletedDownloadRunsAtOneHundredPercentUntilItIsFinished() {
        var list = new DownloadList();
        list.begin(KEY, "report.pdf", folder.resolve("report.pdf"), SOURCE);
        list.update(KEY, running(50, 100, ""));
        assertThat(list.update(KEY, completed(100, 100, folder.resolve("report.pdf").toString()))).map(DownloadList.Ended::outcome).contains(Outcome.COMPLETED);
        assertThat(only(list)).extracting(Download::state, Download::received, Download::total, Download::openable)
            .containsExactly(State.RUNNING, 100L, 100L, false);
        assertThat(list.update(KEY, completed(100, 100, ""))).as("one outcome").isEmpty();
        assertThat(list.update(KEY, interrupted())).as("later updates are ignored").isEmpty();
        assertThat(list.update(KEY, canceled())).isEmpty();
        assertThat(only(list).state()).isEqualTo(State.RUNNING);
        assertThat(list.remove(only(list).serial())).as("still running").isFalse();
        list.clearAll();
        assertThat(list.all()).as("Clear All keeps it").hasSize(1);

        list.finish(KEY, true);
        assertThat(only(list)).extracting(Download::state, Download::openable).containsExactly(State.FINISHED, true);
        list.finish(KEY, false);
        assertThat(only(list).openable()).as("finished once").isTrue();
        assertThat(list.update(KEY, running(1, 2, ""))).isEmpty();
        assertThat(only(list).state()).isEqualTo(State.FINISHED);
    }

    @Test void aCompletionOfUnknownSizeShowsWhatArrivedAsTheWhole() {
        var list = new DownloadList();
        list.begin(KEY, "stream.bin", folder.resolve("stream.bin"), SOURCE);
        assertThat(list.update(KEY, completed(5000, 0, ""))).map(DownloadList.Ended::outcome).contains(Outcome.COMPLETED);
        assertThat(only(list)).extracting(Download::received, Download::total).containsExactly(5000L, 5000L);
    }

    /** F6: a download whose mark failed on macOS or Windows finishes not openable. */
    @Test void aFinishedDownloadIsOpenableOnlyWhenFinishSaysSo() {
        var list = new DownloadList();
        list.begin(KEY, "a.dmg", folder.resolve("a.dmg"), SOURCE);
        list.update(KEY, completed(10, 10, ""));
        list.finish(KEY, false);
        assertThat(only(list)).extracting(Download::state, Download::openable).containsExactly(State.FINISHED, false);
    }

    @Test void finishBeforeCompletionOrForAnUnknownKeyIsIgnored() {
        var list = new DownloadList();
        list.begin(KEY, "a.zip", folder.resolve("a.zip"), SOURCE);
        list.finish(KEY, true);
        assertThat(only(list).state()).as("not completed yet").isEqualTo(State.RUNNING);
        list.finish(new DownloadKey(7, 99), true);
        list.update(KEY, interrupted());
        list.finish(KEY, true);
        assertThat(only(list).state()).as("a failed one stays failed").isEqualTo(State.FAILED);
    }

    @Test void anInterruptedDownloadFailsOnce() {
        var list = new DownloadList();
        list.begin(KEY, "a.zip", folder.resolve("a.zip"), SOURCE);
        list.update(KEY, running(10, 100, ""));
        assertThat(list.update(KEY, interrupted())).map(DownloadList.Ended::outcome).contains(Outcome.FAILED);
        assertThat(only(list).state()).isEqualTo(State.FAILED);
        assertThat(list.update(KEY, interrupted())).as("reported once").isEmpty();
        assertThat(list.update(KEY, completed(100, 100, ""))).isEmpty();
        assertThat(list.update(KEY, running(50, 100, ""))).isEmpty();
        assertThat(only(list).state()).isEqualTo(State.FAILED);
    }

    @Test void aCancelledDownloadStopsOnce() {
        var list = new DownloadList();
        list.begin(KEY, "a.zip", folder.resolve("a.zip"), SOURCE);
        assertThat(list.update(KEY, canceled())).map(DownloadList.Ended::outcome).contains(Outcome.STOPPED);
        assertThat(only(list).state()).isEqualTo(State.STOPPED);
        assertThat(list.update(KEY, canceled())).isEmpty();
        assertThat(list.update(KEY, interrupted())).isEmpty();
        assertThat(only(list).state()).isEqualTo(State.STOPPED);
    }

    @Test void anUpdateForAnUnknownKeyIsIgnored() {
        var list = new DownloadList();
        assertThat(list.update(KEY, completed(1, 1, ""))).as("nothing begun").isEmpty();
        list.begin(KEY, "a.zip", folder.resolve("a.zip"), SOURCE);
        assertThat(list.update(new DownloadKey(8, 1), canceled(new DownloadKey(8, 1)))).as("another browser's id 1").isEmpty();
        assertThat(list.update(new DownloadKey(7, 2), canceled(new DownloadKey(7, 2)))).isEmpty();
        assertThat(only(list).state()).isEqualTo(State.RUNNING);
    }

    /** F2: while a download runs, Chromium names its intermediate file, which the title must not show. */
    @Test void whileRunningAnIntermediateFileNamesTheDownloadButTheTargetStays() {
        var list = new DownloadList();
        Path target = folder.resolve("report.pdf");
        list.begin(KEY, "report.pdf", target, SOURCE);
        list.update(KEY, running(1, 10, folder.resolve("chosen.pdf.crdownload").toString()));
        assertThat(only(list)).as("an asked download's chosen name").extracting(Download::name, Download::file)
            .containsExactly("chosen.pdf", target);
        list.update(KEY, running(2, 10, ""));
        assertThat(only(list).name()).as("an empty path changes nothing").isEqualTo("chosen.pdf");
        list.update(KEY, running(3, 10, folder.resolve(".crdownload").toString()));
        assertThat(only(list).name()).as("no name before the suffix").isEqualTo("chosen.pdf");
    }

    /** D11: Chromium's own placeholder, before the target is set, keeps the shown name. */
    @Test void whileRunningChromiumsUnconfirmedPlaceholderKeepsTheName() {
        var list = new DownloadList();
        Path target = folder.resolve("report.pdf");
        list.begin(KEY, "report.pdf", target, SOURCE);
        list.update(KEY, running(1, 10, folder.resolve("Unconfirmed 123456.crdownload").toString()));
        assertThat(only(list)).extracting(Download::name, Download::file).containsExactly("report.pdf", target);
        list.update(KEY, running(2, 10, folder.resolve("Unconfirmed .crdownload").toString()));
        assertThat(only(list).name()).as("no digits: not the placeholder").isEqualTo("Unconfirmed ");
    }

    @Test void whileRunningAnyOtherPathReplacesTheFileAndTheName() {
        var list = new DownloadList();
        list.begin(KEY, "report.pdf", folder.resolve("report.pdf"), SOURCE);
        Path elsewhere = folder.resolve("elsewhere/other.pdf");
        list.update(KEY, running(1, 10, elsewhere.toString()));
        assertThat(only(list)).extracting(Download::name, Download::file).containsExactly("other.pdf", elsewhere);
    }

    /** F2: the completed update's path names the file that is marked. */
    @Test void theCompletedUpdatesPathSetsTheFileAndTheName() {
        var list = new DownloadList();
        list.begin(KEY, "report.pdf", folder.resolve("report.pdf"), SOURCE);
        list.update(KEY, running(1, 10, folder.resolve("report.pdf.crdownload").toString()));
        Path landed = folder.resolve("report (1).pdf");
        list.update(KEY, completed(10, 10, landed.toString()));
        assertThat(only(list)).extracting(Download::name, Download::file).containsExactly("report (1).pdf", landed);

        var other = new DownloadList();
        other.begin(KEY, "a.pdf", folder.resolve("a.pdf"), SOURCE);
        other.update(KEY, completed(10, 10, ""));
        assertThat(only(other)).as("no path: as it was").extracting(Download::name, Download::file).containsExactly("a.pdf", folder.resolve("a.pdf"));
    }

    @Test void removeAndClearAllLeaveRunningDownloads() {
        var list = new DownloadList();
        Download running = list.begin(new DownloadKey(1, 1), "running.zip", folder.resolve("running.zip"), SOURCE);
        Download failed = list.begin(new DownloadKey(1, 2), "failed.zip", folder.resolve("failed.zip"), SOURCE);
        Download stopped = list.begin(new DownloadKey(1, 3), "stopped.zip", folder.resolve("stopped.zip"), SOURCE);
        Download finished = list.begin(new DownloadKey(1, 4), "finished.zip", folder.resolve("finished.zip"), SOURCE);
        Download marking = list.begin(new DownloadKey(1, 5), "marking.zip", folder.resolve("marking.zip"), SOURCE);
        Download last = list.begin(new DownloadKey(1, 6), "last.zip", folder.resolve("last.zip"), SOURCE);
        list.update(failed.key(), interrupted(failed.key()));
        list.update(stopped.key(), canceled(stopped.key()));
        list.update(finished.key(), completed(finished.key(), 1, 1, ""));
        list.finish(finished.key(), true);
        list.update(marking.key(), completed(marking.key(), 1, 1, ""));
        list.update(last.key(), canceled(last.key()));

        assertThat(list.remove(running.serial())).isFalse();
        assertThat(list.remove(marking.serial())).as("completed, not yet finished").isFalse();
        assertThat(list.remove(failed.serial())).isTrue();
        assertThat(list.remove(failed.serial())).as("gone already").isFalse();
        assertThat(list.remove(9_999)).isFalse();
        assertThat(serials(list)).containsExactly(running.serial(), stopped.serial(), finished.serial(), marking.serial(), last.serial());
        list.clearAll();
        assertThat(serials(list)).containsExactly(running.serial(), marking.serial());
        assertThat(list.update(stopped.key(), canceled(stopped.key()))).as("a removed download is unknown").isEmpty();
    }

    @Test void theListIsASnapshotInTheOrderTheDownloadsStarted() {
        var list = new DownloadList();
        list.begin(new DownloadKey(1, 3), "c", folder.resolve("c"), SOURCE);
        list.begin(new DownloadKey(1, 1), "a", folder.resolve("a"), SOURCE);
        list.begin(new DownloadKey(2, 1), "b", folder.resolve("b"), SOURCE);
        List<Download> all = list.all();
        assertThat(all).extracting(Download::name).containsExactly("c", "a", "b");
        list.update(new DownloadKey(1, 1), canceled(new DownloadKey(1, 1)));
        assertThat(all.get(1).state()).as("a snapshot").isEqualTo(State.RUNNING);
        assertThatThrownBy(all::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    /** F19: the 20 newest downloads stay, and every running one however many; an older finished one leaves. */
    @Test void theListKeepsTheTwentyNewestAndEveryRunningOne() {
        var list = new DownloadList();
        var started = new ArrayList<Download>();
        for (int id = 1; id <= 25; id++) started.add(list.begin(new DownloadKey(1, id), "file" + id, folder.resolve("file" + id), SOURCE));
        assertThat(list.all()).as("all 25 running").hasSize(25);
        for (int id = 4; id <= 25; id++) list.update(new DownloadKey(1, id), canceled(new DownloadKey(1, id)));
        assertThat(serials(list)).as("3 old ones still running, then the 20 newest").containsExactlyElementsOf(serials(started, 1, 2, 3, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25));

        list.update(new DownloadKey(1, 1), completed(new DownloadKey(1, 1), 1, 1, ""));
        assertThat(serials(list)).as("completed, it runs until finished").contains(started.get(0).serial());
        list.finish(new DownloadKey(1, 1), true);
        list.update(new DownloadKey(1, 2), interrupted(new DownloadKey(1, 2)));
        assertThat(serials(list)).as("finished and failed, they are older than the 20 newest")
            .containsExactlyElementsOf(serials(started, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25));

        Download newest = list.begin(new DownloadKey(1, 26), "file26", folder.resolve("file26"), SOURCE);
        assertThat(serials(list)).as("a new one pushes the oldest finished one out").startsWith(started.get(2).serial(), started.get(6).serial())
            .endsWith(newest.serial()).hasSize(21);
        list.update(new DownloadKey(1, 3), canceled(new DownloadKey(1, 3)));
        assertThat(list.all()).hasSize(20).noneMatch(download -> download.state() == State.RUNNING && download.serial() != newest.serial());
    }

    /**
     * Ruling D47 (final review A's I2): an outcome and a finish answer the download they made, though the 20-newest trim
     * drops it from the list at once, so its outcome can still be told.
     */
    @Test void anOutcomeAndAFinishAnswerTheDownloadThoughTheTrimDropsIt() {
        var list = new DownloadList();
        var big = new DownloadKey(1, 1);
        var other = new DownloadKey(1, 2);
        list.begin(big, "big.iso", folder.resolve("big.iso"), SOURCE);
        list.begin(other, "other.iso", folder.resolve("other.iso"), SOURCE);
        for (int id = 3; id <= DownloadList.NEWEST + 2; id++) {
            var key = new DownloadKey(1, id);
            list.begin(key, "s" + id, folder.resolve("s" + id), SOURCE);
            list.update(key, completed(key, 1, 1, ""));
            list.finish(key, true);
        }
        DownloadList.Ended failed = list.update(other, interrupted(other)).orElseThrow();
        assertThat(failed.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(failed.download()).extracting(Download::key, Download::name, Download::state).containsExactly(other, "other.iso", State.FAILED);
        assertThat(list.all()).as("dropped at its outcome").noneMatch(download -> download.key().equals(other));
        DownloadList.Ended completed = list.update(big, completed(big, 10, 10, folder.resolve("big.iso").toString())).orElseThrow();
        assertThat(completed.outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(completed.download()).extracting(Download::state, Download::received).containsExactly(State.RUNNING, 10L);
        assertThat(list.finish(big, false)).as("the finished download").hasValueSatisfying(finished -> assertThat(finished)
            .extracting(Download::key, Download::name, Download::state, Download::openable).containsExactly(big, "big.iso", State.FINISHED, false));
        assertThat(list.all()).as("dropped at its finish").noneMatch(download -> download.key().equals(big)).hasSize(DownloadList.NEWEST);
        assertThat(list.finish(big, true)).as("finished once").isEmpty();
        assertThat(list.finish(new DownloadKey(9, 9), true)).as("an unknown key").isEmpty();
    }

    private static Download only(DownloadList list) {
        assertThat(list.all()).hasSize(1);
        return list.all().getFirst();
    }

    private static List<Long> serials(DownloadList list) {
        return list.all().stream().map(Download::serial).toList();
    }

    private static List<Long> serials(List<Download> started, int... ids) {
        return java.util.Arrays.stream(ids).mapToObj(id -> started.get(id - 1).serial()).toList();
    }

    private static DownloadProgress running(long received, long total, String fullPath) {
        return new DownloadProgress(KEY.browser(), KEY.id(), true, false, false, received, total, fullPath);
    }

    private static DownloadProgress completed(long received, long total, String fullPath) {
        return completed(KEY, received, total, fullPath);
    }

    private static DownloadProgress completed(DownloadKey key, long received, long total, String fullPath) {
        return new DownloadProgress(key.browser(), key.id(), false, true, false, received, total, fullPath);
    }

    private static DownloadProgress interrupted() {
        return interrupted(KEY);
    }

    /** Neither in progress, complete nor cancelled: Chromium's interrupted download, as JCEF shows it. */
    private static DownloadProgress interrupted(DownloadKey key) {
        return new DownloadProgress(key.browser(), key.id(), false, false, false, 5, 100, "");
    }

    private static DownloadProgress canceled() {
        return canceled(KEY);
    }

    private static DownloadProgress canceled(DownloadKey key) {
        return new DownloadProgress(key.browser(), key.id(), false, false, true, 5, 100, "");
    }
}
