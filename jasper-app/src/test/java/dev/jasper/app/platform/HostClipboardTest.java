package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class HostClipboardTest {
    boolean busy;
    /** When set, what the clipboard hands out instead of its real contents. */
    Transferable oddContents;
    final Clipboard system = new Clipboard("test") {
        @Override public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
            if (busy) throw new IllegalStateException("cannot open system clipboard");
            super.setContents(contents, owner);
        }

        @Override public synchronized Transferable getContents(Object requestor) {
            return oddContents != null ? oddContents : super.getContents(requestor);
        }
    };
    final List<Runnable> timers = new ArrayList<>();
    final List<Duration> delays = new ArrayList<>();
    final HostClipboard clipboard = new HostClipboard(() -> system, (delay, task) -> {
        delays.add(delay);
        timers.add(task);
        return () -> timers.remove(task);
    });

    /** Fires every armed clear, as their delays would. */
    void elapse() { List.copyOf(timers).forEach(Runnable::run); }

    /** The user copies in another program. */
    void userCopies(String text) { system.setContents(new StringSelection(text), null); }

    @Test void copiesTextAndReadsItBack() {
        clipboard.copy("line one\nline two \u03bb");
        assertThat(clipboard.text()).contains("line one\nline two \u03bb");
        clipboard.copy("");
        assertThat(clipboard.text()).contains("");
    }

    @Test void nothingOnTheClipboardOrNoTextReadsAsEmpty() {
        assertThat(clipboard.text()).isEmpty();
        system.setContents(image(), null);
        assertThat(clipboard.text()).isEmpty();
    }

    @Test void aSecretIsClearedAfterThirtySecondsIfTheClipboardStillHoldsIt() {
        clipboard.copySecret("hunter2", "dev.example.a");
        assertThat(delays).containsExactly(Duration.ofSeconds(30));
        assertThat(clipboard.text()).contains("hunter2");
        elapse();
        assertThat(clipboard.text()).contains("");
        assertThat(timers).isEmpty();
    }

    @Test void aLaterUserCopyIsLeftAlone() {
        clipboard.copySecret("hunter2", "dev.example.a");
        userCopies("my own text");
        elapse();
        assertThat(clipboard.text()).contains("my own text");
    }

    @Test void aSecretIsLeftAloneWhenTheClipboardNowHoldsSomethingElse() {
        clipboard.copySecret("hunter2", "dev.example.a");
        system.setContents(image(), null);
        elapse();
        assertThat(system.getContents(null).isDataFlavorSupported(DataFlavor.imageFlavor)).isTrue();
    }

    @Test void aLaterCopyEndsThePendingSecret() {
        clipboard.copySecret("hunter2", "dev.example.a");
        clipboard.copy("plain");
        assertThat(timers).as("the clear was cancelled").isEmpty();
        clipboard.ownerStopped("dev.example.a");
        clipboard.quit();
        assertThat(clipboard.text()).contains("plain");
    }

    @Test void aSecondSecretRestartsTheClock() {
        clipboard.copySecret("first", "dev.example.a");
        clipboard.copySecret("second", "dev.example.a");
        assertThat(timers).as("only the second secret's clear is armed").hasSize(1);
        elapse();
        assertThat(clipboard.text()).contains("");
    }

    @Test void stoppingThePluginThatCopiedTheSecretClearsItAndOnlyThatPlugin() {
        clipboard.copySecret("hunter2", "dev.example.a");
        clipboard.ownerStopped("dev.example.b");
        assertThat(clipboard.text()).contains("hunter2");
        clipboard.ownerStopped("dev.example.a");
        assertThat(clipboard.text()).contains("");
        assertThat(timers).isEmpty();
    }

    @Test void quittingClearsAPendingSecret() {
        clipboard.copySecret("hunter2", "dev.example.a");
        clipboard.quit();
        assertThat(clipboard.text()).contains("");
        assertThat(timers).isEmpty();
    }

    @Test void aBusyClipboardThrowsAndTheClearRetriesUntilItCanWrite() {
        clipboard.copySecret("hunter2", "dev.example.a");
        busy = true;
        assertThatThrownBy(() -> clipboard.copy("lost")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> clipboard.copySecret("lost", "dev.example.b")).isInstanceOf(IllegalStateException.class);
        assertThat(timers).as("the first secret's clear is still armed").hasSize(1);
        assertThatCode(this::elapse).as("a refused clear throws nothing").doesNotThrowAnyException();
        assertThat(timers).as("the refused clear is retried").hasSize(1);
        assertThat(delays.get(delays.size() - 1)).isEqualTo(Duration.ofSeconds(1));
        busy = false;
        elapse();
        assertThat(clipboard.text()).contains("");
        assertThat(timers).isEmpty();
    }

    @Test void aClearThatNeverSucceedsGivesUpAfterItsRetries() {
        clipboard.copySecret("hunter2", "dev.example.a");
        busy = true;
        for (int attempt = 1; attempt <= 6; attempt++) {
            assertThatCode(this::elapse).as("attempt %d throws nothing", attempt).doesNotThrowAnyException();
            assertThat(timers).as("retries left after attempt %d", attempt).hasSize(attempt < 6 ? 1 : 0);
        }
        busy = false;
        assertThat(clipboard.text()).as("it could not be cleared").contains("hunter2");
        elapse();
        assertThat(clipboard.text()).contains("hunter2");
    }

    @Test void stoppingWhileBusyTriesOnceAndGivesUp() {
        clipboard.copySecret("hunter2", "dev.example.a");
        busy = true;
        assertThatCode(() -> clipboard.ownerStopped("dev.example.a")).doesNotThrowAnyException();
        assertThat(timers).as("a stop gets one try and no retry").isEmpty();
        busy = false;
        assertThat(clipboard.text()).contains("hunter2");
    }

    @Test void aTransferableThatFailsOddlyNeverBreaksClearOrQuit() {
        clipboard.copySecret("hunter2", "dev.example.a");
        oddContents = stringTransferable(() -> { throw new IllegalArgumentException("odd owner"); });
        assertThatCode(() -> clipboard.ownerStopped("dev.example.a")).doesNotThrowAnyException();
        assertThat(timers).as("a stop gets one try and no retry").isEmpty();
        assertThat(clipboard.text()).as("an unreadable clipboard reads as empty").isEmpty();

        clipboard.copySecret("hunter3", "dev.example.a");
        assertThatCode(clipboard::quit).doesNotThrowAnyException();
        assertThat(timers).isEmpty();

        clipboard.copySecret("hunter4", "dev.example.a");
        assertThatCode(this::elapse).as("a timed clear throws nothing either").doesNotThrowAnyException();
        assertThat(timers).as("an unexpected failure is not retried").isEmpty();
        assertThat(clipboard.text()).isEmpty();
    }

    @Test void aTransferableReturningNullTextReadsAsEmpty() {
        clipboard.copySecret("hunter2", "dev.example.a");
        oddContents = stringTransferable(() -> null);
        assertThat(clipboard.text()).isEmpty();
        assertThatCode(() -> clipboard.ownerStopped("dev.example.a")).doesNotThrowAnyException();
        assertThat(timers).isEmpty();

        oddContents = null;
        clipboard.copySecret("hunter3", "dev.example.a");
        oddContents = stringTransferable(() -> 42);
        assertThat(clipboard.text()).as("a value that is not a string reads as empty").isEmpty();
        assertThatCode(clipboard::quit).doesNotThrowAnyException();
        assertThat(timers).isEmpty();
    }

    @Test void nullTextIsRejected() {
        assertThatThrownBy(() -> clipboard.copy(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> clipboard.copySecret(null, "dev.example.a")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> clipboard.copySecret("x", null)).isInstanceOf(NullPointerException.class);
    }

    /** A transferable that claims string flavor and hands out whatever {@code data} produces. */
    private static Transferable stringTransferable(Supplier<Object> data) {
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.stringFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.stringFlavor.equals(flavor); }
            @Override public Object getTransferData(DataFlavor flavor) { return data.get(); }
        };
    }

    private static Transferable image() {
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
            @Override public Object getTransferData(DataFlavor flavor) { return null; }
        };
    }
}
