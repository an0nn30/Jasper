#define _POSIX_C_SOURCE 200809L
#include "protocol.h"
#include <gio/gio.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <glib/gstdio.h>
#include "fake_portal.c"

void dc_portal_start(GDBusConnection *, FILE *);
void dc_portal_stop(void);

static FILE *output;
static GDBusConnection *observer;
static unsigned checks;
static char *last_line(void) {
    fflush(output);
    long end = ftell(output);
    rewind(output);
    char *line = NULL, *last = g_strdup("");
    size_t capacity = 0;
    while (getline(&line, &capacity, output) >= 0) { g_free(last); last = g_strdup(line); }
    free(line);
    fseek(output, end, SEEK_SET);
    return last;
}
static void expect(const char *fragment) {
    char *line = last_line();
    checks++;
    if (!strstr(line, fragment)) g_error("Expected %s in %s", fragment, line);
    g_free(line);
}
static void absent(const char *fragment) {
    char *line = last_line();
    checks++;
    if (strstr(line, fragment)) g_error("Unexpected %s in %s", fragment, line);
    g_free(line);
}
static void wait_reads(struct fake_portal *fake, unsigned reads) {
    gint64 deadline = g_get_monotonic_time() + 3000000;
    while (fake->reads < reads && g_get_monotonic_time() < deadline) {
        while (g_main_context_iteration(NULL, FALSE)) { }
        g_usleep(1000);
    }
    g_assert_cmpuint(fake->reads, ==, reads);
}
/* A bus round-trip is a barrier after messages from that connection. The async callback
 * also runs in our main context, letting the observer process its previously queued events. */
static gboolean barrier_done;
static void barrier_reply(GObject *object, GAsyncResult *result, gpointer data) {
    (void) data;
    GError *error = NULL;
    GVariant *reply = g_dbus_connection_call_finish(G_DBUS_CONNECTION(object), result, &error);
    g_assert_no_error(error);
    g_variant_unref(reply);
    barrier_done = TRUE;
}
static void drain(GDBusConnection *connection) {
    barrier_done = FALSE;
    g_dbus_connection_call(connection, "org.freedesktop.DBus", "/org/freedesktop/DBus",
        "org.freedesktop.DBus", "GetId", NULL, NULL, G_DBUS_CALL_FLAGS_NONE, 2000,
        NULL, barrier_reply, NULL);
    gint64 deadline = g_get_monotonic_time() + 3000000;
    while (!barrier_done && g_get_monotonic_time() < deadline) g_main_context_iteration(NULL, TRUE);
    g_assert_true(barrier_done);
    while (g_main_context_iteration(NULL, FALSE)) { }
}
static void setup(void) {
    observer = new_connection();
    output = tmpfile();
    g_assert_nonnull(output);
    dc_portal_start(observer, output);
}
static void teardown(void) {
    dc_portal_stop();
    drain(observer);
    g_dbus_connection_close_sync(observer, NULL, NULL);
    g_object_unref(observer);
    fclose(output);
}
static void delayed_read_after_signal(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    fake_signal(fake, "color-scheme", g_variant_new_uint32(1));
    drain(fake->connection); drain(observer);
    expect("\"appearance\":\"dark\"");
    fake_reply(fake, g_variant_new_uint32(2), g_variant_new("(ddd)", .25, .5, .75), g_variant_new_uint32(0));
    drain(fake->connection); drain(observer);
    expect("\"appearance\":\"dark\""); expect("\"accent\":\"0.25,0.5,0.75,1\"");
    expect("\"highContrast\":false");
    teardown(); fake_free(fake);
}
static void out_of_order_keys(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    fake_signal(fake, "contrast", g_variant_new_uint32(1));
    fake_signal(fake, "color-scheme", g_variant_new_uint32(1));
    fake_signal(fake, "contrast", g_variant_new_uint32(0));
    fake_reply(fake, g_variant_new_uint32(2), NULL, g_variant_new_uint32(1));
    drain(fake->connection); drain(observer);
    expect("\"appearance\":\"dark\""); expect("\"highContrast\":false"); absent("\"accent\"");
    teardown(); fake_free(fake);
}
static void retired_owner_reply(void) {
    struct fake_portal *a = fake_new(); setup(); wait_reads(a, 1);
    fake_signal(a, "accent-color", g_variant_new("(ddd)", .25, .5, .75));
    drain(a->connection); drain(observer);
    expect("\"accent\""); expect("\"owner\":1");
    fake_release(a);
    struct fake_portal *b = fake_new(); wait_reads(b, 1);
    fake_reply(b, g_variant_new_uint32(2), NULL, NULL);
    drain(b->connection); drain(observer);
    expect("\"appearance\":\"light\""); expect("\"owner\":2"); absent("\"accent\"");
    fake_reply(a, g_variant_new_uint32(1), g_variant_new("(ddd)", 1., 0., 0.), g_variant_new_uint32(1));
    drain(a->connection); drain(observer);
    expect("\"appearance\":\"light\""); expect("\"owner\":2"); absent("\"accent\"");
    teardown(); fake_free(a); fake_free(b);
}
static void late_registration(void) {
    setup(); drain(observer); expect("\"appearance\":\"unknown\""); expect("unavailable");
    struct fake_portal *fake = fake_new(); wait_reads(fake, 1);
    fake_reply(fake, g_variant_new_uint32(1), NULL, NULL);
    drain(fake->connection); drain(observer);
    expect("\"appearance\":\"dark\""); expect("\"owner\":1");
    fake_free(fake); drain(observer);
    expect("unavailable"); absent("\"accent\""); teardown();
}
static void partial_keys(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    fake_reply(fake, NULL, g_variant_new("(ddd)", .25, .5, .75), NULL);
    drain(fake->connection); drain(observer);
    expect("\"appearance\":\"unknown\""); expect("\"accent\":\"0.25,0.5,0.75,1\""); absent("\"highContrast\"");
    teardown(); fake_free(fake);
}
static void wrong_signatures(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    fake_reply(fake, g_variant_new_string("dark"), g_variant_new("(uuu)", 0u, 1u, 0u), g_variant_new_boolean(TRUE));
    drain(fake->connection); drain(observer);
    expect("\"appearance\":\"unknown\""); absent("\"accent\""); absent("\"highContrast\"");
    /* A malformed signal body must not clear the valid snapshot. */
    fake_signal(fake, "color-scheme", g_variant_new_uint32(2));
    g_dbus_connection_emit_signal(fake->connection, NULL, PORTAL_PATH, SETTINGS, "SettingChanged", g_variant_new("(s)", "bad"), NULL);
    drain(fake->connection); drain(observer); expect("\"appearance\":\"light\"");
    teardown(); fake_free(fake);
}
static void unknown_enums(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    fake_reply(fake, g_variant_new_uint32(1), NULL, g_variant_new_uint32(1));
    drain(fake->connection); drain(observer);
    fake_signal(fake, "color-scheme", g_variant_new_uint32(999));
    fake_signal(fake, "contrast", g_variant_new_uint32(2));
    drain(fake->connection); drain(observer);
    expect("\"appearance\":\"unknown\""); absent("\"highContrast\"");
    fake_signal(fake, "color-scheme", g_variant_new_uint32(0));
    drain(fake->connection); drain(observer); expect("\"appearance\":\"unknown\"");
    teardown(); fake_free(fake);
}
static void invalid_accent_independent(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    fake_reply(fake, g_variant_new_uint32(1), g_variant_new("(ddd)", .25, .5, .75), g_variant_new_uint32(1));
    drain(fake->connection); drain(observer);
    double invalid[] = {NAN, INFINITY, -.1, 1.1};
    for (unsigned i = 0; i < G_N_ELEMENTS(invalid); i++) {
        fake_signal(fake, "accent-color", g_variant_new("(ddd)", invalid[i], 0., 0.));
        drain(fake->connection); drain(observer);
        expect("\"appearance\":\"dark\""); expect("\"highContrast\":true"); absent("\"accent\"");
    }
    teardown(); fake_free(fake);
}
static void stop_pending(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    dc_portal_stop(); long before = ftell(output);
    fake_reply(fake, g_variant_new_uint32(1), NULL, NULL);
    drain(fake->connection); drain(observer);
    g_assert_cmpint(ftell(output), ==, before);
    /* Starting another observer while the old completion is pending must stay independent. */
    dc_portal_start(observer, output); wait_reads(fake, 2);
    fake_reply(fake, g_variant_new_uint32(2), NULL, NULL);
    drain(fake->connection); drain(observer); expect("\"appearance\":\"light\"");
    teardown(); fake_free(fake);
}
static void read_timeout(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    gint64 began = g_get_monotonic_time();
    gboolean timed_out = FALSE;
    while (g_get_monotonic_time() - began < 3000000) {
        while (g_main_context_iteration(NULL, FALSE)) { }
        char *line = last_line();
        timed_out = strstr(line, "preferences unavailable") != NULL;
        g_free(line);
        if (timed_out) break;
        g_usleep(1000);
    }
    g_assert_true(timed_out);
    /* Prevent a mistakenly short deadline from passing the upper-bound assertion. */
    g_assert_cmpint(g_get_monotonic_time() - began, >=, 1500000);
    expect("\"appearance\":\"unknown\"");
    fake_signal(fake, "color-scheme", g_variant_new_uint32(1));
    drain(fake->connection); drain(observer); expect("\"appearance\":\"dark\"");
    teardown(); fake_free(fake);
}
static void iterate_for(gint64 microseconds) {
    gint64 deadline = g_get_monotonic_time() + microseconds;
    while (g_get_monotonic_time() < deadline) {
        while (g_main_context_iteration(NULL, FALSE)) { }
        g_usleep(1000);
    }
}
static void stop_acquisition(void) {
    struct fake_portal *fake = fake_new(); observer = new_connection();
    output = tmpfile(); g_assert_nonnull(output);
    dc_portal_start(NULL, output); dc_portal_stop();
    fclose(output); iterate_for(100000);
    g_assert_cmpuint(fake->reads, ==, 0);
    output = tmpfile(); g_assert_nonnull(output);
    dc_portal_start(observer, output); wait_reads(fake, 1);
    fake_reply(fake, g_variant_new_uint32(2), NULL, NULL);
    drain(fake->connection); drain(observer); expect("\"appearance\":\"light\"");
    teardown(); fake_free(fake);
}
static void fail_read(struct fake_portal *fake) {
    GDBusMethodInvocation *call = g_queue_pop_head(&fake->pending);
    g_dbus_method_invocation_return_dbus_error(call, "org.example.Temporary", "temporarily unavailable");
    g_object_unref(call); drain(fake->connection); drain(observer);
}
static void stop_retry(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1); fail_read(fake);
    expect("preferences unavailable");
    dc_portal_stop(); long before = ftell(output);
    iterate_for(1200000);
    g_assert_cmpuint(fake->reads, ==, 1); g_assert_cmpint(ftell(output), ==, before);
    teardown(); fake_free(fake);
}
static void retried_owner_reply(void) {
    struct fake_portal *a = fake_new(); setup(); wait_reads(a, 1); fail_read(a);
    wait_reads(a, 2); fake_release(a);
    struct fake_portal *b = fake_new(); wait_reads(b, 1);
    fake_reply(b, g_variant_new_uint32(2), NULL, NULL); drain(b->connection); drain(observer);
    fake_reply(a, g_variant_new_uint32(1), NULL, NULL); drain(a->connection); drain(observer);
    expect("\"appearance\":\"light\""); expect("\"owner\":2");
    teardown(); fake_free(a); fake_free(b);
}
static void read_failure_recovers(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    GDBusMethodInvocation *call = g_queue_pop_head(&fake->pending);
    g_dbus_method_invocation_return_dbus_error(call, "org.example.Temporary", "temporarily unavailable");
    g_object_unref(call); drain(fake->connection); drain(observer);
    expect("preferences unavailable");
    wait_reads(fake, 2);
    fake_reply(fake, g_variant_new_uint32(2), NULL, g_variant_new_uint32(1));
    drain(fake->connection); drain(observer);
    expect("\"appearance\":\"light\""); expect("\"owner\":1"); expect("\"highContrast\":true");
    teardown(); fake_free(fake);
}
static void read_timeout_recovers(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    /* Retain the timed-out invocation until the successful retry has completed. */
    GDBusMethodInvocation *late = g_queue_pop_head(&fake->pending);
    gint64 deadline = g_get_monotonic_time() + 5000000;
    while (fake->reads < 2 && g_get_monotonic_time() < deadline) {
        while (g_main_context_iteration(NULL, FALSE)) { }
        g_usleep(1000);
    }
    g_assert_cmpuint(fake->reads, ==, 2);
    fake_reply(fake, g_variant_new_uint32(2), NULL, NULL);
    drain(fake->connection); drain(observer); expect("\"appearance\":\"light\"");
    g_queue_push_tail(&fake->pending, late);
    fake_reply(fake, g_variant_new_uint32(1), NULL, NULL);
    drain(fake->connection); drain(observer); expect("\"appearance\":\"light\"");
    teardown(); fake_free(fake);
}
static void closed_bus_recovers(void) {
    struct fake_portal *fake = fake_new(); setup(); wait_reads(fake, 1);
    GDBusMethodInvocation *late = g_queue_pop_head(&fake->pending);
    g_dbus_connection_close_sync(observer, NULL, NULL);
    wait_reads(fake, 2);
    fake_reply(fake, g_variant_new_uint32(2), NULL, NULL);
    drain(fake->connection);
    expect("\"appearance\":\"light\""); expect("\"owner\":2");
    g_queue_push_tail(&fake->pending, late);
    fake_reply(fake, g_variant_new_uint32(1), NULL, NULL); drain(fake->connection);
    expect("\"appearance\":\"light\""); expect("\"owner\":2");
    dc_portal_stop(); g_object_unref(observer); fclose(output); fake_free(fake);
}
struct communication { gboolean done; GError *error; gchar *out, *err; };
static void communicated(GObject *object, GAsyncResult *result, gpointer data) {
    struct communication *communication = data;
    g_subprocess_communicate_utf8_finish(G_SUBPROCESS(object), result,
        &communication->out, &communication->err, &communication->error);
    communication->done = TRUE;
}
static gboolean force_exit(gpointer data) {
    g_subprocess_force_exit(G_SUBPROCESS(data));
    return G_SOURCE_REMOVE;
}
static void helper_commands(gboolean missing_bus) {
    GSubprocessLauncher *launcher = g_subprocess_launcher_new(
        G_SUBPROCESS_FLAGS_STDIN_PIPE | G_SUBPROCESS_FLAGS_STDOUT_PIPE | G_SUBPROCESS_FLAGS_STDERR_PIPE);
    if (missing_bus) g_subprocess_launcher_setenv(launcher, "DBUS_SESSION_BUS_ADDRESS", "unix:path=/does/not/exist", TRUE);
    const char *helper = g_getenv("DC_PORTAL_TEST_HELPER");
    g_assert_nonnull(helper);
    GError *error = NULL;
    GSubprocess *child = g_subprocess_launcher_spawn(launcher, &error, helper, "--protocol=1", NULL);
    g_assert_no_error(error);
    g_object_unref(launcher);
    GString *input = g_string_sized_new(DC_MAX_MESSAGE + 2048);
    /* Overlong and malformed frames cannot poison the following ping. */
    for (unsigned i = 0; i < DC_MAX_MESSAGE + 1; i++) g_string_append_c(input, 'x');
    g_string_append(input, "\n{bad}\n{\"v\":1,\"kind\":\"ping\",\"generation\":9223372036854775807}\n");
    struct communication communication = {0};
    guint deadline = g_timeout_add(5000, force_exit, child);
    g_subprocess_communicate_utf8_async(child, input->str, NULL, communicated, &communication);
    while (!communication.done) g_main_context_iteration(NULL, TRUE);
    GSource *timer = g_main_context_find_source_by_id(NULL, deadline);
    if (timer) g_source_destroy(timer);
    g_assert_no_error(communication.error);
    g_assert_true(g_subprocess_get_successful(child));
    g_assert_nonnull(strstr(communication.out, "{\"v\":1,\"kind\":\"pong\",\"generation\":9223372036854775807}\n"));
    g_assert_cmpstr(communication.err, ==, "");
    if (missing_bus) g_assert_nonnull(strstr(communication.out, "session bus unavailable"));
    g_free(communication.out); g_free(communication.err);
    g_string_free(input, TRUE); g_object_unref(child);
}
struct line_read { gboolean done; char *line; GError *error; };
static void line_ready(GObject *object, GAsyncResult *result, gpointer data) {
    struct line_read *read = data;
    read->line = g_data_input_stream_read_line_finish(G_DATA_INPUT_STREAM(object), result, NULL, &read->error);
    read->done = TRUE;
}
static char *helper_line(GDataInputStream *stream) {
    struct line_read read = {0};
    GCancellable *cancel = g_cancellable_new();
    g_data_input_stream_read_line_async(stream, G_PRIORITY_DEFAULT, cancel, line_ready, &read);
    gint64 deadline = g_get_monotonic_time() + 3000000;
    while (!read.done && g_get_monotonic_time() < deadline) {
        while (g_main_context_iteration(NULL, FALSE)) { }
        g_usleep(1000);
    }
    if (!read.done) {
        g_cancellable_cancel(cancel);
        while (!read.done) g_main_context_iteration(NULL, TRUE);
    }
    g_object_unref(cancel);
    g_assert_no_error(read.error); g_assert_nonnull(read.line);
    return read.line;
}
static void acquisition_recovers(void) {
    struct fake_portal *fake = fake_new();
    GError *error = NULL;
    char *directory = g_dir_make_tmp("jasper-portal-XXXXXX", &error); g_assert_no_error(error);
    char *socket = g_build_filename(directory, "bus", NULL);
    char *address = g_strconcat("unix:path=", socket, NULL);
    GSubprocessLauncher *launcher = g_subprocess_launcher_new(
        G_SUBPROCESS_FLAGS_STDIN_PIPE | G_SUBPROCESS_FLAGS_STDOUT_PIPE | G_SUBPROCESS_FLAGS_STDERR_PIPE);
    g_subprocess_launcher_setenv(launcher, "DBUS_SESSION_BUS_ADDRESS", address, TRUE);
    GSubprocess *child = g_subprocess_launcher_spawn(launcher, &error, g_getenv("DC_PORTAL_TEST_HELPER"), "--protocol=1", NULL);
    g_assert_no_error(error); g_object_unref(launcher);
    GDataInputStream *stream = g_data_input_stream_new(g_subprocess_get_stdout_pipe(child));
    g_filter_input_stream_set_close_base_stream(G_FILTER_INPUT_STREAM(stream), FALSE);
    /* The first envelope is the pending initial state; the next proves acquisition failed. */
    char *line = helper_line(stream); g_assert_nonnull(strstr(line, "session bus unavailable")); g_free(line);
    line = helper_line(stream); g_assert_nonnull(strstr(line, "session bus unavailable")); g_free(line);
    g_assert_cmpuint(fake->reads, ==, 0);
    const char *private_address = g_getenv("DBUS_SESSION_BUS_ADDRESS");
    g_assert_true(g_str_has_prefix(private_address, "unix:path="));
    char *target = g_strdup(private_address + strlen("unix:path="));
    char *comma = strchr(target, ','); if (comma) *comma = '\0';
    g_assert_cmpint(symlink(target, socket), ==, 0);
    wait_reads(fake, 1); fake_reply(fake, g_variant_new_uint32(2), NULL, NULL); drain(fake->connection);
    gboolean recovered = FALSE;
    for (int i = 0; i < 4 && !recovered; i++) {
        line = helper_line(stream); recovered = strstr(line, "\"appearance\":\"light\"") != NULL; g_free(line);
    }
    g_assert_true(recovered);
    g_object_unref(stream);
    struct communication communication = {0};
    guint deadline = g_timeout_add(5000, force_exit, child);
    g_subprocess_communicate_utf8_async(child, NULL, NULL, communicated, &communication);
    while (!communication.done) g_main_context_iteration(NULL, TRUE);
    GSource *timer = g_main_context_find_source_by_id(NULL, deadline); if (timer) g_source_destroy(timer);
    g_assert_no_error(communication.error); g_assert_true(g_subprocess_get_successful(child));
    g_assert_cmpstr(communication.err, ==, "");
    g_free(communication.out); g_free(communication.err); g_object_unref(child);
    g_assert_cmpint(g_unlink(socket), ==, 0); g_assert_cmpint(g_rmdir(directory), ==, 0);
    g_free(target); g_free(address); g_free(socket); g_free(directory); fake_free(fake);
}
static void helper_ping_eof(void) { helper_commands(FALSE); }
static void helper_missing_bus(void) { helper_commands(TRUE); }
int main(int argc, char **argv) {
    g_test_init(&argc, &argv, NULL);
    g_assert_nonnull(g_getenv("DBUS_SESSION_BUS_ADDRESS"));
    g_test_add_func("/portal/bus-acquisition-recovers", acquisition_recovers);
    g_test_add_func("/portal/stop-acquisition", stop_acquisition);
    g_test_add_func("/portal/stop-retry", stop_retry);
    g_test_add_func("/portal/retried-owner-reply", retried_owner_reply);
    g_test_add_func("/portal/delayed-read-after-newer-signal", delayed_read_after_signal);
    g_test_add_func("/portal/out-of-order-key-changes", out_of_order_keys);
    g_test_add_func("/portal/retired-owner-reply", retired_owner_reply);
    g_test_add_func("/portal/late-registration", late_registration);
    g_test_add_func("/portal/partial-keys", partial_keys);
    g_test_add_func("/portal/wrong-signatures", wrong_signatures);
    g_test_add_func("/portal/unknown-enums", unknown_enums);
    g_test_add_func("/portal/invalid-accent-independent", invalid_accent_independent);
    g_test_add_func("/portal/stop-pending-read", stop_pending);
    g_test_add_func("/portal/read-timeout", read_timeout);
    g_test_add_func("/portal/read-failure-recovers", read_failure_recovers);
    g_test_add_func("/portal/read-timeout-recovers", read_timeout_recovers);
    g_test_add_func("/portal/closed-bus-recovers", closed_bus_recovers);
    g_test_add_func("/helper/ping-and-eof", helper_ping_eof);
    g_test_add_func("/helper/no-session-bus", helper_missing_bus);
    int result = g_test_run();
    g_print("desktop portal: %u snapshot assertions, %d failures\n", checks, result);
    return result;
}
