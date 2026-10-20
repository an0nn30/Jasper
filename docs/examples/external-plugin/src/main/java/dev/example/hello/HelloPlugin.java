package dev.example.hello;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.StandardMenu;
import java.util.List;

/** An example external plugin: one command, in the Tools menu and the palette, that copies a greeting. */
public final class HelloPlugin implements Plugin {
    static final String COPY = "dev.example.hello.copy";
    static final String GREETING = "Hello from an external Jasper plugin";

    @Override public void start(PluginContext context) {
        context.actions().register(ActionSpec.of(COPY, "Copy Greeting").withKeywords(List.of("hello", "greeting", "example")),
            invoked -> context.platform().copy(GREETING));
        context.menus().standard(StandardMenu.TOOLS).add(COPY);
    }
}
