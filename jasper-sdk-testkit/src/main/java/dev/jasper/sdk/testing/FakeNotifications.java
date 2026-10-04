package dev.jasper.sdk.testing;

import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Notifications;
import dev.jasper.sdk.notifications.Severity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * What fake plugins posted and the groups declared for them, as the application's notification center holds them.
 * The host's is {@link FakePluginHost#notifications()}; a component under test that takes a {@link NotificationGroup}
 * gets a recording one from a new instance's {@link #group(String)}. Safe from any thread.
 *
 * @since 0.14.0
 */
public final class FakeNotifications {
    /** The host's group for a plugin's activity outcomes: {@code <plugin-id>.activities}. */
    static final String ACTIVITIES = ".activities";

    /** One post, and whether it has expired since. */
    public static final class Post {
        private final String pluginId;
        private final String group;
        private final NotificationSpec spec;
        private volatile boolean expired;

        Post(String pluginId, String group, NotificationSpec spec) { this.pluginId = pluginId; this.group = group; this.spec = spec; }

        /**
         * The plugin that posted it; a standalone group's own id.
         *
         * @return the plugin id
         */
        public String pluginId() { return pluginId; }

        /**
         * The group it was posted in.
         *
         * @return the group's id
         */
        public String group() { return group; }

        /**
         * What was posted.
         *
         * @return the spec
         */
        public NotificationSpec spec() { return spec; }

        /**
         * Whether it expired: through its handle, an expiring action, or its plugin stopping.
         *
         * @return true once expired
         */
        public boolean expired() { return expired; }

        /**
         * The post for assertions: {@code group|SEVERITY|title|content}, then {@code |} and the cause's simple class name
         * when it carried a cause.
         *
         * @return the line
         */
        public String line() {
            String line = group + "|" + spec.severity() + "|" + spec.title() + "|" + spec.content();
            return spec.cause().map(cause -> line + "|" + cause.getClass().getSimpleName()).orElse(line);
        }
    }

    private final FakePluginHost host;
    private final List<Post> posts = new CopyOnWriteArrayList<>();
    private final Map<String, String> groups = new LinkedHashMap<>();

    /** A recorder with no host, for components that take a {@link NotificationGroup}. */
    public FakeNotifications() { this(null); }

    FakeNotifications(FakePluginHost host) { this.host = host; }

    /**
     * A group whose posts are recorded here, as if a plugin with that id posted them. Its actions run on the caller's
     * thread when {@link #runAction} clicks them, and what they throw reaches the caller.
     *
     * @param id the group's id
     * @return the group
     */
    public NotificationGroup group(String id) {
        declare(Objects.requireNonNull(id, "id"), id, DisplayMode.CARD);
        return new Group(id, id, () -> true);
    }

    /**
     * Every post, oldest first.
     *
     * @return the posts
     */
    public List<Post> posts() { return List.copyOf(posts); }

    /**
     * Every post's {@link Post#line()}, oldest first.
     *
     * @return the lines
     */
    public List<String> lines() { return posts.stream().map(Post::line).toList(); }

    /**
     * Declared groups, in declaration order, as {@code id|name|MODE}.
     *
     * @return the groups
     */
    public List<String> groups() { synchronized (groups) { return List.copyOf(groups.values()); } }

    /**
     * Clicks the labelled link of the newest post with this title, as the user would. A plugin's action that throws is
     * recorded among {@link FakePluginHost#failures()} instead of escaping.
     *
     * @param title the post's title
     * @param label the link's label
     * @return false when the newest such post has expired, or there is no such post or link
     */
    public boolean runAction(String title, String label) {
        Optional<Post> post = newestLive(title);
        if (post.isEmpty()) return false;
        Optional<NotificationAction> action = post.get().spec.actions().stream().filter(candidate -> candidate.label().equals(label)).findFirst();
        if (action.isEmpty()) return false;
        contained(post.get(), "notification action " + label, action.get().run());
        if (action.get().expires()) post.get().expired = true;
        return true;
    }

    /**
     * Clicks the body of the newest post with this title, running its default action if it has one.
     *
     * @param title the post's title
     * @return false when the newest such post has expired, or there is none
     */
    public boolean click(String title) {
        Optional<Post> post = newestLive(title);
        if (post.isEmpty()) return false;
        post.get().spec.activate().ifPresent(body -> contained(post.get(), "notification body", body));
        return true;
    }

    /** A plugin's notifications; {@code open} turns false once its context closes. */
    Notifications of(String pluginId, BooleanSupplier open) { return new PluginView(pluginId, open); }

    /** A plugin starts: its default group and the host's activities group exist. */
    void declareDefaults(String pluginId, String name) {
        declare(pluginId, name, DisplayMode.CARD);
        declare(pluginId + ACTIVITIES, name + " activities", DisplayMode.CARD);
    }

    /** A plugin stopped: everything it posted expires. */
    void stopped(String pluginId) {
        for (Post post : posts) if (post.pluginId.equals(pluginId)) post.expired = true;
    }

    /** As the host does: an activity that succeeded or failed is posted in its plugin's activities group. */
    void outcome(ActivityEvent event, Optional<Runnable> activate) {
        if (event.state() != ActivityEvent.State.SUCCEEDED && event.state() != ActivityEvent.State.FAILED) return;
        String detail = event.detail();
        var spec = NotificationSpec.of(event.state() == ActivityEvent.State.SUCCEEDED ? Severity.INFO : Severity.ERROR, event.title())
            .withContent(detail.length() <= 4_000 ? detail : detail.substring(0, 3_999) + "…");
        posts.add(new Post(event.sourcePluginId(), event.sourcePluginId() + ACTIVITIES, activate.map(spec::withActivate).orElse(spec)));
    }

    private void declare(String id, String name, DisplayMode mode) {
        synchronized (groups) { groups.putIfAbsent(id, id + "|" + name + "|" + mode); }
    }

    private Optional<Post> newestLive(String title) {
        List<Post> all = posts();
        for (int i = all.size() - 1; i >= 0; i--)
            if (all.get(i).spec.title().equals(title)) return all.get(i).expired ? Optional.empty() : Optional.of(all.get(i));
        return Optional.empty();
    }

    /** Runs a plugin's callback as the host would; a standalone recorder lets what it throws reach the caller. */
    private void contained(Post post, String what, Runnable callback) {
        if (host == null) { callback.run(); return; }
        try { callback.run(); }
        catch (RuntimeException | LinkageError failure) { host.recordFailure(post.pluginId + " " + what + ": " + failure); }
    }

    private final class PluginView implements Notifications {
        private final String pluginId;
        private final BooleanSupplier open;
        private final Group defaultGroup;
        private final Map<String, Group> declared = new ConcurrentHashMap<>();

        PluginView(String pluginId, BooleanSupplier open) {
            this.pluginId = pluginId; this.open = open;
            defaultGroup = new Group(pluginId, pluginId, open);
        }

        @Override public NotificationGroup group() { return defaultGroup; }

        @Override public NotificationGroup group(GroupSpec spec) {
            Objects.requireNonNull(spec, "spec");
            if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
            if (spec.id().equals(pluginId)) return defaultGroup;
            if (!spec.id().startsWith(pluginId + "."))
                throw new IllegalArgumentException("A notification group of " + pluginId + " starts with its id and a dot: " + spec.id());
            if (spec.id().equals(pluginId + ACTIVITIES))
                throw new IllegalArgumentException("The host declares " + spec.id() + " for activity outcomes");
            return declared.computeIfAbsent(spec.id(), id -> {
                declare(id, spec.name(), spec.defaultMode());
                return new Group(pluginId, id, open);
            });
        }
    }

    private final class Group implements NotificationGroup {
        private final String pluginId;
        private final String id;
        private final BooleanSupplier open;

        Group(String pluginId, String id, BooleanSupplier open) { this.pluginId = pluginId; this.id = id; this.open = open; }

        @Override public String id() { return id; }

        @Override public NotificationHandle post(NotificationSpec spec) {
            Objects.requireNonNull(spec, "spec");
            if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
            var post = new Post(pluginId, id, spec);
            posts.add(post);
            return new NotificationHandle() {
                @Override public void expire() { post.expired = true; }
                @Override public boolean expired() { return post.expired; }
            };
        }
    }
}
