package dev.jasper.app.testsupport;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Keeps real serialized I/O, while tests explicitly advance the configuration poll. */
public final class ConfigPollWorker extends ScheduledThreadPoolExecutor {
    private Runnable poll;
    public ConfigPollWorker() { super(1); }
    @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initial, long delay, TimeUnit unit) {
        poll = task;
        return super.scheduleWithFixedDelay(task, 1, 1, TimeUnit.DAYS);
    }
    public void poll() throws Exception { submit(poll).get(5, TimeUnit.SECONDS); }
}
