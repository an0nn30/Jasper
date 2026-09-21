package dev.jasper.terminal.internal.desktop;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Clipboard access and shared, lazy, bounded browser dispatch. Never runs a browser request on the caller. */
public final class DesktopServices {
    private static final System.Logger LOG = System.getLogger(DesktopServices.class.getName());
    private DesktopServices() { }
    public static String readClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException | IllegalStateException | HeadlessException e) {
            LOG.log(System.Logger.Level.WARNING, "Clipboard read failed", e);
            return null;
        }
    }

    public static void writeClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException | HeadlessException e) {
            LOG.log(System.Logger.Level.WARNING, "Clipboard write failed", e);
        }
    }

    public static void openBrowser(String uri) {
        dispatchBrowserAction(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI(uri));
                } else {
                    LOG.log(System.Logger.Level.WARNING, "Browser opening is unsupported");
                }
            } catch (IOException | URISyntaxException failure) {
                // Desktop exceptions can contain the URI. Keep diagnostics fixed and free of terminal content.
                LOG.log(System.Logger.Level.WARNING, "Browser open failed");
            }
        });
    }

    public static void dispatchBrowserAction(Runnable action) {
        try {
            BrowserWorker.EXECUTOR.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Browser open failed");
                }
            });
        } catch (RejectedExecutionException busy) {
            LOG.log(System.Logger.Level.WARNING, "Browser request dropped: pending request limit reached");
        }
    }

    /** Shared across views, lazily created, and never falls back to running Desktop calls on the EDT. */
    private static final class BrowserWorker {
        static final ThreadPoolExecutor EXECUTOR = createExecutor();

        private static ThreadPoolExecutor createExecutor() {
            var executor = new ThreadPoolExecutor(1, 1, 1_000, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(8), action -> {
                    var thread = new Thread(action, "jasper-terminal-browser");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }
    }
}
