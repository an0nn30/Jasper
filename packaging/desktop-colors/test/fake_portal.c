/* Included by portal_test.c: a controllable service on the private test bus. */
#include <gio/gio.h>

#define PORTAL_NAME "org.freedesktop.portal.Desktop"
#define PORTAL_PATH "/org/freedesktop/portal/desktop"
#define SETTINGS "org.freedesktop.portal.Settings"
#define APPEARANCE "org.freedesktop.appearance"

struct fake_portal {
    GDBusConnection *connection;
    guint registration;
    GQueue pending;
    unsigned reads;
};
static void fake_method(GDBusConnection *connection, const gchar *sender, const gchar *path,
        const gchar *interface, const gchar *method, GVariant *parameters,
        GDBusMethodInvocation *invocation, gpointer data) {
    (void) connection; (void) sender; (void) path; (void) interface;
    struct fake_portal *fake = data;
    g_assert_cmpstr(method, ==, "ReadAll");
    const gchar **names;
    g_variant_get(parameters, "(^a&s)", &names);
    g_assert_cmpstr(names[0], ==, APPEARANCE);
    g_assert_null(names[1]);
    g_free(names);
    fake->reads++;
    g_queue_push_tail(&fake->pending, g_object_ref(invocation));
}
static const GDBusInterfaceVTable fake_vtable = { .method_call = fake_method };
static GDBusConnection *new_connection(void) {
    GError *error = NULL;
    GDBusConnection *connection = g_dbus_connection_new_for_address_sync(
        g_getenv("DBUS_SESSION_BUS_ADDRESS"),
        G_DBUS_CONNECTION_FLAGS_AUTHENTICATION_CLIENT | G_DBUS_CONNECTION_FLAGS_MESSAGE_BUS_CONNECTION,
        NULL, NULL, &error);
    g_assert_no_error(error);
    return connection;
}
static void name_call(struct fake_portal *fake, const char *method, GVariant *args) {
    GError *error = NULL;
    GVariant *reply = g_dbus_connection_call_sync(fake->connection, "org.freedesktop.DBus",
        "/org/freedesktop/DBus", "org.freedesktop.DBus", method, args, NULL,
        G_DBUS_CALL_FLAGS_NONE, 2000, NULL, &error);
    g_assert_no_error(error);
    g_variant_unref(reply);
}
static struct fake_portal *fake_new(void) {
    const char *xml = "<node><interface name='org.freedesktop.portal.Settings'>"
        "<method name='ReadAll'><arg type='as' direction='in'/>"
        "<arg type='a{sa{sv}}' direction='out'/></method>"
        "<signal name='SettingChanged'><arg type='s'/><arg type='s'/><arg type='v'/></signal>"
        "</interface></node>";
    struct fake_portal *fake = g_new0(struct fake_portal, 1);
    fake->connection = new_connection();
    GDBusNodeInfo *info = g_dbus_node_info_new_for_xml(xml, NULL);
    fake->registration = g_dbus_connection_register_object(fake->connection, PORTAL_PATH,
        info->interfaces[0], &fake_vtable, fake, NULL, NULL);
    g_assert_cmpuint(fake->registration, >, 0);
    g_dbus_node_info_unref(info);
    name_call(fake, "RequestName", g_variant_new("(su)", PORTAL_NAME, 0u));
    return fake;
}
static void fake_release(struct fake_portal *fake) {
    name_call(fake, "ReleaseName", g_variant_new("(s)", PORTAL_NAME));
}
static void fake_reply(struct fake_portal *fake, GVariant *scheme, GVariant *accent, GVariant *contrast) {
    GVariantBuilder keys, namespaces;
    g_variant_builder_init(&keys, G_VARIANT_TYPE("a{sv}"));
    if (scheme) g_variant_builder_add(&keys, "{sv}", "color-scheme", scheme);
    if (accent) g_variant_builder_add(&keys, "{sv}", "accent-color", accent);
    if (contrast) g_variant_builder_add(&keys, "{sv}", "contrast", contrast);
    g_variant_builder_init(&namespaces, G_VARIANT_TYPE("a{sa{sv}}"));
    g_variant_builder_add(&namespaces, "{s@a{sv}}", APPEARANCE, g_variant_builder_end(&keys));
    GDBusMethodInvocation *invocation = g_queue_pop_head(&fake->pending);
    g_assert_nonnull(invocation);
    g_dbus_method_invocation_return_value(invocation,
        g_variant_new("(@a{sa{sv}})", g_variant_builder_end(&namespaces)));
    g_object_unref(invocation);
}
static void fake_signal(struct fake_portal *fake, const char *key, GVariant *value) {
    g_assert_true(g_dbus_connection_emit_signal(fake->connection, NULL, PORTAL_PATH, SETTINGS,
        "SettingChanged", g_variant_new("(ssv)", APPEARANCE, key, value), NULL));
}
static void fake_free(struct fake_portal *fake) {
    fake_release(fake);
    while (!g_queue_is_empty(&fake->pending)) {
        GDBusMethodInvocation *invocation = g_queue_pop_head(&fake->pending);
        g_dbus_method_invocation_return_dbus_error(invocation, "org.example.Stopped", "test complete");
        g_object_unref(invocation);
    }
    g_dbus_connection_unregister_object(fake->connection, fake->registration);
    g_dbus_connection_close_sync(fake->connection, NULL, NULL);
    g_object_unref(fake->connection);
    g_free(fake);
}
