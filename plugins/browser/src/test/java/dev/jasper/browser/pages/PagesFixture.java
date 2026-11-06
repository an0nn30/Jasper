package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * A scheme registry and the pages built on a fake host's appearance, for the tests of the pages that register
 * themselves: a page is served and queried as Chromium's side of the scheme does it, through {@link AboutScheme#answer}
 * and {@link AboutScheme#query}. Closed by its test.
 */
final class PagesFixture implements AutoCloseable {
    final AboutScheme about = new AboutScheme();
    final AboutPages pages;
    private final FakePluginHost host;

    PagesFixture() throws Exception {
        var made = new AtomicReference<FakePluginHost>();
        var pagesMade = new AtomicReference<AboutPages>();
        SwingUtilities.invokeAndWait(() -> {
            made.set(new FakePluginHost());
            var started = new AtomicReference<PluginContext>();
            made.get().start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), started::set);
            pagesMade.set(new AboutPages(started.get().appearance(), about));
        });
        host = made.get();
        pages = pagesMade.get();
    }

    /** The HTML {@code page} is served with now. */
    String html(String page) {
        AboutScheme.Answer answer = about.answer("jasper-about:" + page);
        if (answer.status() != 200) throw new AssertionError(page + " answered " + answer);
        return answer.html();
    }

    /** What the script of {@code page} gets for {@code request}, asked as that page's own document; empty when it is refused. */
    Optional<String> ask(String page, String request) throws Exception {
        String secret = about.secret(page).orElse("0".repeat(32));
        var answer = about.query("jasper-about:" + page, true, 1, secret + request);
        if (answer.isEmpty()) return Optional.empty();
        try {
            return Optional.of(answer.get().get(10, TimeUnit.SECONDS));
        } catch (ExecutionException failure) {
            throw new AssertionError(failure.getCause().getMessage(), failure.getCause());
        }
    }

    /** As {@link #ask}, the answer read as a flat JSON object three levels deep. */
    Map<String, Object> object(String page, String request) throws Exception {
        return StrictJson.object(ask(page, request).orElseThrow(), 4).orElseThrow();
    }

    @Override public void close() {
        try {
            SwingUtilities.invokeAndWait(host::close);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        }
    }
}
