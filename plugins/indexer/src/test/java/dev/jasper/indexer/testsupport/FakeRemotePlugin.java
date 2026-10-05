package dev.jasper.indexer.testsupport;

import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.Set;

/** Publishes a {@link FakeRemote} as Remote would. */
public final class FakeRemotePlugin implements Plugin {
    /** Remote, as the indexer requires it. */
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.12.0", Set.of());
    private final FakeRemote remote;

    /** @param remote what to publish */
    public FakeRemotePlugin(FakeRemote remote) { this.remote = remote; }

    @Override public void start(PluginContext context) { context.services().publish(RemoteProjects.class, remote); }
}
