package dev.jasper.editor.ui;

import dev.jasper.sdk.services.Services;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Without the Browser plugin its API's classes are absent: the Editor's lookup of the Markdown previews answers empty, so
 * the preview is Basic, and nothing throws.
 */
class ChromiumPreviewsAbsentTest {
    /** Loads the Editor's and the SDK's classes itself, as a plugin's class loader does, and has no Browser class. */
    private static final class WithoutTheBrowser extends URLClassLoader {
        WithoutTheBrowser(URL... classes) { super(classes, ChromiumPreviewsAbsentTest.class.getClassLoader()); }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("dev.jasper.browser.")) throw new ClassNotFoundException(name);
            if (!name.startsWith("dev.jasper.editor.") && !name.startsWith("dev.jasper.sdk.")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    @Test void findAnswersEmptyWithoutTheBrowsersClassesAndThrowsNothing() throws Exception {
        URL editor = ChromiumPreviews.class.getProtectionDomain().getCodeSource().getLocation();
        URL sdk = Services.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new WithoutTheBrowser(editor, sdk)) {
            assertThatThrownBy(() -> loader.loadClass("dev.jasper.browser.api.MarkdownPreviews")).isInstanceOf(ClassNotFoundException.class);
            Class<?> services = loader.loadClass(Services.class.getName());
            Class<?> previews = loader.loadClass(ChromiumPreviews.class.getName());
            assertThat(services.getClassLoader()).isSameAs(loader);
            assertThat(previews.getClassLoader()).isSameAs(loader);
            List<String> asked = new ArrayList<>();
            Object lookups = Proxy.newProxyInstance(loader, new Class<?>[] {services}, (proxy, method, arguments) -> {
                asked.add(method.getName());
                return method.getName().equals("find") ? Optional.empty() : null;
            });
            assertThat(previews.getMethod("find", services).invoke(null, lookups)).isEqualTo(Optional.empty());
            assertThat(asked).as("the API's class literal fails before the lookup").isEmpty();
        }
    }
}
