#define _POSIX_C_SOURCE 200809L
#include "protocol.h"
#include <gio/gio.h>
#include <math.h>
#include <string.h>

#define PORTAL_NAME "org.freedesktop.portal.Desktop"
#define PORTAL_PATH "/org/freedesktop/portal/desktop"
#define SETTINGS "org.freedesktop.portal.Settings"
#define APPEARANCE "org.freedesktop.appearance"

/* All state and callbacks belong to the calling thread's GLib main context.
 * Pending completions retain their observer after stop and never touch its FILE. */
struct observer {
    unsigned references;
    gboolean alive;
    GDBusConnection *connection;
    FILE *output;
    guint watch, subscription;
    GCancellable *read;
    gchar *owner;
    guint64 serial, changed[3];
    struct dc_portal value;
};
struct pending_read { struct observer *observer; gchar *owner; guint64 serial; int64_t generation; };
static struct observer *active;

void dc_portal_start(GDBusConnection *connection, FILE *output);
void dc_portal_stop(void);

static void release(struct observer *observer) {
    if (--observer->references) return;
    g_clear_object(&observer->connection);
    g_free(observer->owner);
    g_free(observer);
}
static void publish(struct observer *observer) {
    if (observer->alive && dc_write_portal(observer->output, &observer->value) != 0)
        g_warning("desktop portal output failed");
}
static int key_index(const char *key) {
    if (strcmp(key, "color-scheme") == 0) return 0;
    if (strcmp(key, "accent-color") == 0) return 1;
    if (strcmp(key, "contrast") == 0) return 2;
    return -1;
}
static void preference(struct observer *observer, int key, GVariant *value) {
    if (key == 0) {
        observer->value.appearance = DC_UNKNOWN;
        if (value && g_variant_is_of_type(value, G_VARIANT_TYPE_UINT32)) {
            guint32 scheme = g_variant_get_uint32(value);
            if (scheme == 1) observer->value.appearance = DC_DARK;
            if (scheme == 2) observer->value.appearance = DC_LIGHT;
        }
    } else if (key == 1) {
        observer->value.accent_present = 0;
        if (value && g_variant_is_of_type(value, G_VARIANT_TYPE("(ddd)"))) {
            double r, g, b;
            g_variant_get(value, "(ddd)", &r, &g, &b);
            if (isfinite(r) && isfinite(g) && isfinite(b) && r >= 0 && r <= 1 && g >= 0 && g <= 1 && b >= 0 && b <= 1) {
                observer->value.accent_present = 1;
                observer->value.accent = (struct dc_rgba) {r, g, b, 1};
            }
        }
    } else if (key == 2) {
        observer->value.contrast_present = 0;
        if (value && g_variant_is_of_type(value, G_VARIANT_TYPE_UINT32)) {
            guint32 contrast = g_variant_get_uint32(value);
            if (contrast <= 1) {
                observer->value.contrast_present = 1;
                observer->value.high_contrast = contrast == 1;
            }
        }
    }
}
static void setting_changed(GDBusConnection *connection, const gchar *sender, const gchar *path,
        const gchar *interface, const gchar *signal, GVariant *parameters, gpointer data) {
    (void) connection; (void) path; (void) interface; (void) signal;
    struct observer *observer = data;
    if (!observer->alive || g_strcmp0(sender, observer->owner) != 0
        || !g_variant_is_of_type(parameters, G_VARIANT_TYPE("(ssv)"))) return;
    const char *namespace, *key;
    GVariant *value;
    g_variant_get(parameters, "(&s&sv)", &namespace, &key, &value);
    int index = strcmp(namespace, APPEARANCE) == 0 ? key_index(key) : -1;
    if (index >= 0) {
        observer->changed[index] = ++observer->serial;
        preference(observer, index, value);
        observer->value.reason = "";
        publish(observer);
    }
    g_variant_unref(value);
}
static void read_complete(GObject *object, GAsyncResult *result, gpointer data) {
    struct pending_read *pending = data;
    struct observer *observer = pending->observer;
    GError *error = NULL;
    GVariant *reply = g_dbus_connection_call_finish(G_DBUS_CONNECTION(object), result, &error);
    if (observer->alive && observer->value.owner == pending->generation
        && g_strcmp0(observer->owner, pending->owner) == 0) {
        if (reply && g_variant_is_of_type(reply, G_VARIANT_TYPE("(a{sa{sv}})"))) {
            GVariant *namespaces = g_variant_get_child_value(reply, 0);
            GVariant *keys = g_variant_lookup_value(namespaces, APPEARANCE, G_VARIANT_TYPE("a{sv}"));
            static const char *const names[] = {"color-scheme", "accent-color", "contrast"};
            for (int i = 0; i < 3; i++) {
                if (observer->changed[i] > pending->serial) continue;
                GVariant *value = keys ? g_variant_lookup_value(keys, names[i], NULL) : NULL;
                preference(observer, i, value);
                if (value) g_variant_unref(value);
            }
            if (keys) g_variant_unref(keys);
            g_variant_unref(namespaces);
            observer->value.reason = "";
        } else {
            observer->value.reason = "portal preferences unavailable";
        }
        publish(observer);
    }
    g_clear_error(&error);
    if (reply) g_variant_unref(reply);
    g_free(pending->owner);
    g_free(pending);
    release(observer);
}
static void retire(struct observer *observer) {
    if (observer->subscription) g_dbus_connection_signal_unsubscribe(observer->connection, observer->subscription);
    observer->subscription = 0;
    if (observer->read) g_cancellable_cancel(observer->read);
    g_clear_object(&observer->read);
    g_clear_pointer(&observer->owner, g_free);
}
static void appeared(GDBusConnection *connection, const gchar *name, const gchar *owner, gpointer data) {
    (void) name;
    struct observer *observer = data;
    if (!observer->alive || g_strcmp0(owner, observer->owner) == 0) return;
    retire(observer);
    observer->owner = g_strdup(owner);
    /* Exhausting a signed 64-bit owner generation cannot happen in a helper lifetime. */
    observer->value = (struct dc_portal) { .owner = observer->value.owner + 1,
        .appearance = DC_UNKNOWN, .reason = "portal preferences pending" };
    memset(observer->changed, 0, sizeof observer->changed);
    observer->serial = 0;
    observer->subscription = g_dbus_connection_signal_subscribe(connection, owner, SETTINGS,
        "SettingChanged", PORTAL_PATH, APPEARANCE, G_DBUS_SIGNAL_FLAGS_NONE, setting_changed, observer, NULL);
    publish(observer);
    struct pending_read *pending = g_new0(struct pending_read, 1);
    pending->observer = observer;
    observer->references++;
    pending->owner = g_strdup(owner);
    pending->generation = observer->value.owner;
    pending->serial = observer->serial;
    observer->read = g_cancellable_new();
    const gchar *namespaces[] = {APPEARANCE, NULL};
    g_dbus_connection_call(connection, owner, PORTAL_PATH, SETTINGS, "ReadAll",
        g_variant_new("(^as)", namespaces), G_VARIANT_TYPE("(a{sa{sv}})"),
        G_DBUS_CALL_FLAGS_NONE, 2000, observer->read, read_complete, pending);
}
static void vanished(GDBusConnection *connection, const gchar *name, gpointer data) {
    (void) connection; (void) name;
    struct observer *observer = data;
    if (!observer->alive) return;
    retire(observer);
    observer->value = (struct dc_portal) { .owner = observer->value.owner,
        .appearance = DC_UNKNOWN, .reason = "portal unavailable" };
    publish(observer);
}
void dc_portal_start(GDBusConnection *connection, FILE *output) {
    dc_portal_stop();
    g_return_if_fail(G_IS_DBUS_CONNECTION(connection) && output);
    struct observer *observer = g_new0(struct observer, 1);
    observer->references = 1;
    observer->alive = TRUE;
    observer->connection = g_object_ref(connection);
    observer->output = output;
    observer->value = (struct dc_portal) { .appearance = DC_UNKNOWN, .reason = "portal unavailable" };
    active = observer;
    observer->watch = g_bus_watch_name_on_connection(connection, PORTAL_NAME,
        G_BUS_NAME_WATCHER_FLAGS_NONE, appeared, vanished, observer, NULL);
}
void dc_portal_stop(void) {
    struct observer *observer = active;
    if (!observer) return;
    active = NULL;
    observer->alive = FALSE;
    g_bus_unwatch_name(observer->watch);
    retire(observer);
    release(observer);
}

#ifndef DC_PORTAL_NO_MAIN
#include <errno.h>
#include <fcntl.h>
#include <glib-unix.h>
#include <unistd.h>

struct input { GMainLoop *loop; char line[DC_MAX_MESSAGE]; size_t length; gboolean oversized; };
static gboolean stdin_ready(gint fd, GIOCondition condition, gpointer data) {
    (void) condition;
    struct input *input = data;
    char bytes[8192];
    ssize_t length = read(fd, bytes, sizeof bytes);
    if (length < 0 && (errno == EAGAIN || errno == EINTR)) return G_SOURCE_CONTINUE;
    if (length <= 0) { g_main_loop_quit(input->loop); return G_SOURCE_REMOVE; }
    for (ssize_t i = 0; i < length; i++) {
        if (bytes[i] == '\n') {
            struct dc_command command;
            if (!input->oversized && dc_read_command(input->line, input->length, &command) == 0
                && command.kind == DC_PING && dc_write_pong(stdout, command.generation) != 0) {
                g_main_loop_quit(input->loop);
                return G_SOURCE_REMOVE;
            }
            input->length = 0; input->oversized = FALSE;
        } else if (input->length < sizeof input->line) input->line[input->length++] = bytes[i];
        else input->oversized = TRUE;
    }
    return G_SOURCE_CONTINUE;
}
int main(int argc, char **argv) {
    if (argc != 2 || strcmp(argv[1], "--protocol=1") != 0) {
        fprintf(stderr, "usage: jasper-desktop-portal --protocol=1\n");
        return 2;
    }
    GError *error = NULL;
    GDBusConnection *connection = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, &error);
    if (connection) dc_portal_start(connection, stdout);
    else {
        struct dc_portal unavailable = { .appearance = DC_UNKNOWN, .reason = "session bus unavailable" };
        if (dc_write_portal(stdout, &unavailable) != 0) { g_clear_error(&error); return 1; }
    }
    g_clear_error(&error);
    GMainLoop *loop = g_main_loop_new(NULL, FALSE);
    int flags = fcntl(STDIN_FILENO, F_GETFL);
    if (flags < 0 || fcntl(STDIN_FILENO, F_SETFL, flags | O_NONBLOCK) < 0) {
        dc_portal_stop(); g_clear_object(&connection); g_main_loop_unref(loop); return 1;
    }
    struct input input = { .loop = loop };
    guint source = g_unix_fd_add(STDIN_FILENO, G_IO_IN | G_IO_HUP | G_IO_ERR, stdin_ready, &input);
    g_main_loop_run(loop);
    /* The stdin callback removes itself on EOF; a live source is removed here if needed. */
    GSource *remaining = g_main_context_find_source_by_id(NULL, source);
    if (remaining) g_source_destroy(remaining);
    dc_portal_stop();
    while (g_main_context_iteration(NULL, FALSE)) { }
    g_clear_object(&connection);
    g_main_loop_unref(loop);
    fcntl(STDIN_FILENO, F_SETFL, flags);
    return 0;
}
#endif
