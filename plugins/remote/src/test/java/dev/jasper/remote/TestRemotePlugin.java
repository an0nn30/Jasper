package dev.jasper.remote;

import dev.jasper.remote.agent.AgentClient;
import dev.jasper.sdk.plugin.PluginContext;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.*;

/** FakePluginHost drains finite work manually; long-lived loops need actual threads and a bounded test join. */
final class TestRemotePlugin extends RemotePlugin {
    TestRemotePlugin(Executor ui,Function<PluginContext,Optional<AgentClient>> agents,BiFunction<Duration,Runnable,Runnable> schedule,Path ssh) {
        super(ui,agents,schedule,ssh,context->command->Thread.ofVirtual().name("remote-test-loop").start(command));
    }
    @Override public void stop() {
        super.stop();
        try { transfersStopped().get(5,TimeUnit.SECONDS); }
        catch(Exception failure) { throw new AssertionError("Transfer shutdown did not drain",failure); }
    }
}
