package dev.jasper.app.application;

import dev.jasper.app.plugins.PluginShutdownTestSupport;

/** Waits for plugin resources after an application's bounded shutdown, before its fixture home is deleted. */
final class ApplicationPluginCleanup {
    private ApplicationPluginCleanup() { }

    static void await(JasperApplication application) throws Exception {
        if (application == null || application.pluginRuntime() == null) return;
        PluginShutdownTestSupport.capture(application.pluginRuntime()).await();
    }
}
