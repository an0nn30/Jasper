package dev.jasper.browser.dialogs;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import java.util.Vector;
import java.util.function.Consumer;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefFileDialogCallback;
import org.cef.callback.CefJSDialogCallback;
import org.cef.handler.CefDialogHandler;
import org.cef.handler.CefJSDialogHandler;
import org.cef.handler.CefJSDialogHandlerAdapter;
import org.cef.misc.BoolRef;

/**
 * A page's dialogs (spec section 5): the client's one JavaScript dialog handler and one file dialog handler, which
 * answer every page's dialogs themselves, so Chromium shows none of its own. {@code alert()} and {@code confirm()} are
 * Jasper's modals over the page's item ({@link ScriptDialog}); {@code prompt()} shows nothing and returns null, as
 * iTerm2 has no handler for it; a page that asks before it unloads closes without asking; and a file input opens
 * Jasper's chooser over the item ({@link FileInput}). Chromium calls both handlers on its own UI thread; what Jasper
 * shows is posted to Swing's. A page's fullscreen request is refused by the {@code page} package's display handler, the
 * client's only one.
 *
 * <p>JCEF keeps a client's first handler of each kind, so these are the only ones.
 */
public final class PageDialogs {
    private static final Consumer<CefClient> INSTALL = PageDialogs::install;

    private PageDialogs() { }

    /**
     * Has Chromium install the handlers on its client before any page is made on it; once, however often asked. Call it
     * when a page first asks for Chromium, never at the plugin's start: this class names Chromium's types.
     *
     * @param chromium the plugin's Chromium
     */
    public static void prepare(Chromium chromium) {
        chromium.onClient(INSTALL);
    }

    private static void install(CefClient client) {
        client.addJSDialogHandler(new Scripts());
        client.addDialogHandler(new Files());
    }

    /** The page {@code browser} shows; null for a browser that is not a page's. */
    private static Page page(CefBrowser browser) {
        return browser instanceof PageBrowser shown ? shown.owner() : null;
    }

    /** {@code alert()}, {@code confirm()} and {@code prompt()}, and the question a page asks before it unloads. */
    static final class Scripts extends CefJSDialogHandlerAdapter {
        @Override public boolean onJSDialog(CefBrowser browser, String origin, CefJSDialogHandler.JSDialogType type, String message,
                                            String defaultPrompt, CefJSDialogCallback callback, BoolRef suppress) {
            if (type == CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_ALERT)
                ScriptDialog.ask(page(browser), ScriptDialog.Kind.ALERT, message, callback::Continue);
            else if (type == CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_CONFIRM)
                ScriptDialog.ask(page(browser), ScriptDialog.Kind.CONFIRM, message, callback::Continue);
            else callback.Continue(false, ""); // prompt(): nothing shows, and the page gets null
            return true;
        }

        /** Pages' {@code beforeunload} handlers are not honoured: the page goes without asking. */
        @Override public boolean onBeforeUnloadDialog(CefBrowser browser, String message, boolean reload, CefJSDialogCallback callback) {
            callback.Continue(true, "");
            return true;
        }
    }

    /** File inputs, and saves Chromium asks a file for. */
    static final class Files implements CefDialogHandler {
        @Override public boolean onFileDialog(CefBrowser browser, FileDialogMode mode, String title, String defaultFilePath,
                                              Vector<String> acceptFilters, Vector<String> acceptExtensions,
                                              Vector<String> acceptDescriptions, CefFileDialogCallback callback) {
            FileInput.Mode asked = switch (mode) {
                case FILE_DIALOG_OPEN -> FileInput.Mode.OPEN;
                case FILE_DIALOG_OPEN_MULTIPLE -> FileInput.Mode.OPEN_MULTIPLE;
                case FILE_DIALOG_OPEN_FOLDER -> FileInput.Mode.OPEN_FOLDER;
                case FILE_DIALOG_SAVE -> FileInput.Mode.SAVE;
            };
            FileInput.choose(page(browser), asked, title, defaultFilePath, paths -> callback.Continue(new Vector<>(paths)), callback::Cancel);
            return true;
        }
    }
}
