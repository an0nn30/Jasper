package dev.jasper.fileserver.workstation;

import dev.jasper.enterprise.api.Workstation;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Set;

/** A stand-in for the Enterprise plugin on the testkit host: it publishes Workstation and nothing else, as its start does. */
public final class FakeEnterprise {
    /** Enterprise's identity, as its descriptor gives it. */
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.enterprise", "Enterprise", "0.1.0", Set.of("presentation.provide"));

    private FakeEnterprise() { }

    /** Starts the stand-in; start a consumer after it with {@code dev.jasper.enterprise} among its optional requirements. */
    public static void start(FakePluginHost host) {
        host.start(INFO, Set.of(), Set.of(), context -> context.services().publish(Workstation.class, new Workstation() { }));
    }
}
