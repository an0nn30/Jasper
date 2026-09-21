package dev.jasper.app.plugins;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Delegation order: the SDK from the application's loader; the exported packages of declared
 * dependencies from their loaders; then the platform loader and this plugin's own jars. Application,
 * terminal and Buddy packages are refused outright. The parent is the platform loader, so nothing on
 * the application classpath (its libraries, its resources) is visible. This is a hygiene boundary,
 * the runtime twin of the bytecode allowlists; it is not a sandbox.
 */
final class PluginClassLoader extends URLClassLoader {
    static { registerAsParallelCapable(); }

    private static final List<String> HIDDEN = List.of("dev.jasper.app.", "dev.jasper.terminal.", "dev.jasper.buddy.");
    private final ClassLoader sdk;
    private final Map<String, ClassLoader> imports;

    PluginClassLoader(String pluginId, List<Path> jars, ClassLoader sdk, Map<String, ClassLoader> imports) {
        super("plugin:" + pluginId, urls(jars), ClassLoader.getPlatformClassLoader());
        this.sdk = sdk;
        this.imports = Map.copyOf(imports);
    }

    private static URL[] urls(List<Path> jars) {
        URL[] urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            try { urls[i] = jars.get(i).toUri().toURL(); }
            catch (MalformedURLException impossible) { throw new IllegalArgumentException(impossible); }
        }
        return urls;
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) loaded = route(name);
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private Class<?> route(String name) throws ClassNotFoundException {
        if (name.startsWith("dev.jasper.sdk.")) return sdk.loadClass(name);
        for (String hidden : HIDDEN)
            if (name.startsWith(hidden)) throw new ClassNotFoundException(name + " is not visible to plugins");
        int dot = name.lastIndexOf('.');
        ClassLoader exporter = imports.get(dot < 0 ? "" : name.substring(0, dot));
        return exporter != null ? exporter.loadClass(name) : super.loadClass(name, false);
    }
}
