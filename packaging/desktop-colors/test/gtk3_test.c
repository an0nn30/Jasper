#include "gtk_api.h"
#include <gio/gio.h>
void assert_rgba(struct dc_rgba,double,double,double,double);
void assert_unmapped(struct dc_gtk_api *);
void *fixture_target(struct dc_gtk_api *);
void fixture_load(struct dc_gtk_api *,GtkCssProvider *,const char *);
char *fixture_read(const char *);
void helper_contract(const char *,const char *);
void assert_notifications(struct dc_gtk_api *,GtkCssProvider *,bool);
void assert_nested_requests(struct dc_gtk_api *,GtkCssProvider *,bool);
static struct dc_gtk_api api;
static GtkCssProvider *provider;
static void named_colors(void) {
    char *css=fixture_read("named.css");fixture_load(&api,provider,css);g_free(css);
    struct dc_palette p;g_assert_true(dc_sample_gtk3(true,4,&p));
    assert_rgba(p.colors[DC_WINDOW].background,.1,.2,.3,1);g_assert_true(p.colors[DC_HEADER].present);
    g_assert_cmpint(p.generation,==,4);assert_unmapped(&api);
}
static void variant(void) {char *css=fixture_read("named.css");fixture_load(&api,provider,css);g_free(css);struct dc_palette p;g_assert_false(dc_sample_gtk3(false,5,&p));g_assert_cmpstr(p.reason,==,"requested appearance unsupported");g_assert_true(dc_sample_gtk3(true,6,&p));assert_unmapped(&api);}
static void runtime_minimum(void) {helper_contract(g_getenv("DC_GTK3_HELPER"),"old-gtk3");}
static void no_display(void) {helper_contract(g_getenv("DC_GTK3_HELPER"),"no-display");}
static void helper(void) {helper_contract(g_getenv("DC_GTK3_HELPER"),NULL);}
static void notification(void) {assert_notifications(&api,provider,false);}
static void during_notification(void) {assert_nested_requests(&api,provider,true);}
static void during_timer(void) {assert_nested_requests(&api,provider,false);}
int main(int argc,char **argv) {
    g_test_init(&argc,&argv,NULL);g_assert_true(dc_gtk_api_open(3,&api));g_assert_true(api.init3(NULL,NULL));
    g_print("GTK %s\n",api.version);provider=api.provider();api.add_provider(fixture_target(&api),provider,800);
    g_test_add_func("/gtk3/named-colors-unmapped",named_colors);g_test_add_func("/gtk3/forced-variant-sequential",variant);g_test_add_func("/gtk3/helper-ping-preview-eof",helper);
    g_test_add_func("/gtk3/stylesheet-setting-notification",notification);
    g_test_add_func("/gtk3/runtime-minimum",runtime_minimum);g_test_add_func("/gtk3/no-display-ping",no_display);
    g_test_add_func("/gtk3/request-during-notification-sample",during_notification);
    g_test_add_func("/gtk3/request-during-timer-sample",during_timer);
    int status=g_test_run();api.remove_provider(fixture_target(&api),provider);g_object_unref(provider);dc_gtk_shutdown();return status;
}
