#include "gtk_api.h"
#include <gio/gio.h>
#include <dlfcn.h>
#include <string.h>
void assert_rgba(struct dc_rgba,double,double,double,double);
void assert_unmapped(struct dc_gtk_api *);
void *fixture_target(struct dc_gtk_api *);
void fixture_load(struct dc_gtk_api *,GtkCssProvider *,const char *);
char *fixture_read(const char *);
void helper_contract(const char *,const char *);
void assert_notifications(struct dc_gtk_api *,GtkCssProvider *,bool);
void assert_nested_requests(struct dc_gtk_api *,GtkCssProvider *,bool);
void assert_plain_native(const char *);
static const char *program;
void assert_bounded_dispatch(void);
void assert_burst_or_eof(struct dc_gtk_api *,GtkCssProvider *,bool);
void assert_window_alpha(struct dc_gtk_api *,GtkCssProvider *);
static struct dc_gtk_api api;
static GtkCssProvider *provider;
static struct dc_palette modern_palette(void) {
    char *css=fixture_read("modern.css");fixture_load(&api,provider,css);g_free(css);
    struct dc_palette p;g_assert_true(dc_sample_gtk4(true,7,&p));return p;
}
static void modern_override(void) {
    struct dc_palette p=modern_palette();assert_rgba(p.colors[DC_WINDOW].background,.1,.2,.3,1);
    g_assert_cmpint(p.generation,==,7);g_assert_cmpint(p.appearance,==,DC_DARK);assert_unmapped(&api);
}
static void magenta(void) {struct dc_palette p=modern_palette();g_assert_true(p.colors[DC_VIEW].present);assert_rgba(p.colors[DC_VIEW].background,1,0,1,1);}
static void cyan(void) {struct dc_palette p=modern_palette();g_assert_true(p.colors[DC_HEADER].present);assert_rgba(p.colors[DC_HEADER].background,0,1,1,1);}
static void missing_variable(void) {struct dc_palette p=modern_palette();g_assert_false(p.colors[DC_SIDEBAR].present);}
static void missing_half(void) {struct dc_palette p=modern_palette();g_assert_false(p.colors[DC_DIALOG].present);}
static void unsupported(void) {
    modern_palette();struct dc_palette p;g_assert_false(dc_sample_gtk4(false,8,&p));g_assert_false(p.colors[DC_WINDOW].present);
    g_assert_cmpstr(p.reason,==,"requested appearance unsupported");
}
static void sequential(void) {
    modern_palette();struct dc_palette p;g_assert_true(dc_sample_gtk4(true,9,&p));g_assert_cmpint(p.generation,==,9);assert_unmapped(&api);
    fixture_load(&api,provider,"window { --window-bg-color: white; --window-fg-color: black; }");
    g_assert_true(dc_sample_gtk4(false,10,&p));g_assert_cmpint(p.generation,==,10);assert_rgba(p.colors[DC_WINDOW].background,1,1,1,1);assert_unmapped(&api);
}
static void optional_alpha(void) {
    fixture_load(&api,provider,"window { --window-bg-color: rgb(10%,20%,30%); --window-fg-color: white; --popover-bg-color: rgba(10%,20%,30%,0.5); --popover-fg-color: white; }");
    struct dc_palette p;g_assert_true(dc_sample_gtk4(true,11,&p));
    g_assert_true(p.colors[DC_POPOVER].present);assert_rgba(p.colors[DC_POPOVER].background,.1,.2,.3,.5);
}
static void translucent_window(void) {
    fixture_load(&api,provider,"window { --window-bg-color: rgba(10%,20%,30%,0.5); --window-fg-color: white; }");
    struct dc_palette p;g_assert_false(dc_sample_gtk4(true,12,&p));g_assert_false(p.colors[DC_WINDOW].present);
}
static void old_named(void) {
    char *css=fixture_read("named.css");fixture_load(&api,provider,css);g_free(css);
    struct dc_palette p;g_assert_true(dc_sample_gtk4(true,7,&p));
    assert_rgba(p.colors[DC_WINDOW].background,.1,.2,.3,1);assert_unmapped(&api);
}
static void runtime_limits(void) {helper_contract(g_getenv("DC_GTK4_HELPER"),"old-gtk4");}
static void neither(void) {helper_contract(g_getenv("DC_GTK4_HELPER"),"neither");helper_contract(g_getenv("DC_GTK3_HELPER"),"neither");}
static void no_display(void) {helper_contract(g_getenv("DC_GTK4_HELPER"),"no-display");}
static void plain_named_native(void) {
    g_assert_null(api.adw);
    char *css=fixture_read("named.css");
    char *combined=g_strconcat(css,api.variables?"window { --window-bg-color: initial; --window-fg-color: initial; }":"",NULL);
    fixture_load(&api,provider,combined);g_free(css);g_free(combined);
    struct dc_palette p;g_assert_true(dc_sample_gtk4(true,51,&p));
    assert_rgba(p.colors[DC_WINDOW].background,.1,.2,.3,1);assert_rgba(p.colors[DC_WINDOW].foreground,1,1,1,1);
    g_assert_cmpstr(p.implementation,==,"gtk4");assert_unmapped(&api);
    if(api.variables) {
        fixture_load(&api,provider,"@define-color window_bg_color red; @define-color window_fg_color white; window { --window-bg-color: rgb(10%,20%,30%); --window-fg-color: white; }");
        g_assert_true(dc_sample_gtk4(true,52,&p));assert_rgba(p.colors[DC_WINDOW].background,.1,.2,.3,1);
        fixture_load(&api,provider,"@define-color window_bg_color rgb(10%,20%,30%); @define-color window_fg_color white; window { --window-bg-color: red; --window-fg-color: initial; }");
        g_assert_true(dc_sample_gtk4(true,53,&p));assert_rgba(p.colors[DC_WINDOW].background,.1,.2,.3,1);
        assert_rgba(p.colors[DC_WINDOW].foreground,1,1,1,1);
    }
}
static void plain(void) {helper_contract(g_getenv("DC_GTK4_HELPER"),"plain");assert_plain_native(program);}
static void during_notification(void) {assert_nested_requests(&api,provider,true);}
static void during_timer(void) {assert_nested_requests(&api,provider,false);}
static void gtk3_only(void) {helper_contract(g_getenv("DC_GTK4_HELPER"),"gtk3-only");helper_contract(g_getenv("DC_GTK3_HELPER"),"gtk3-only");}
static void ping_and_preview(void) {helper_contract(g_getenv("DC_GTK4_HELPER"),NULL);}
static void close_idempotent(void) {struct dc_gtk_api absent={0};dc_gtk_api_close(&absent);dc_gtk_api_close(&absent);g_assert_false(dc_gtk_api_open(2,&absent));}
static void notification(void) {assert_notifications(&api,provider,api.variables);}
static void burst(void) {assert_burst_or_eof(&api,provider,false);}
static void eof_during_sample(void) {assert_burst_or_eof(&api,provider,true);}
static void window_alpha(void) {assert_window_alpha(&api,provider);}
int main(int argc,char **argv) {
    program=argv[0];g_test_init(&argc,&argv,NULL);g_assert_true(dc_gtk_api_open(4,&api));g_assert_true(api.init4());
    if(api.adw) api.adw_init();
    unsigned (*adw_major)(void)=NULL,(*adw_minor)(void)=NULL,(*adw_micro)(void)=NULL;
    if(api.adw) {*(void **)(&adw_major)=dlsym(api.adw,"adw_get_major_version");*(void **)(&adw_minor)=dlsym(api.adw,"adw_get_minor_version");*(void **)(&adw_micro)=dlsym(api.adw,"adw_get_micro_version");}
    g_print("GTK %u.%u.%u; libadwaita %u.%u.%u; variables %s\n",api.get_major(),api.get_minor(),api.get_micro(),adw_major?adw_major():0,adw_minor?adw_minor():0,adw_micro?adw_micro():0,api.variables?"yes":"no");
    provider=api.provider();api.add_provider(fixture_target(&api),provider,800);
    if(api.variables) {
        g_test_add_func("/gtk4/modern-variable-override-precedence",modern_override);
        g_test_add_func("/gtk4/real-magenta-sentinel",magenta);g_test_add_func("/gtk4/real-cyan-sentinel",cyan);
        g_test_add_func("/gtk4/missing-variable",missing_variable);g_test_add_func("/gtk4/missing-pair-half",missing_half);
        g_test_add_func("/gtk4/forced-variant-unsupported",unsupported);g_test_add_func("/gtk4/sequential-preview-variants",sequential);
        g_test_add_func("/gtk4/optional-rgba-alpha",optional_alpha);g_test_add_func("/gtk4/translucent-window-unavailable",translucent_window);
    }
    else g_test_add_func("/gtk4/old-named-colors",old_named);
    g_test_add_func("/gtk4/runtime-below-4-10",runtime_limits);g_test_add_func("/gtk4/neither-toolkit",neither);
    g_test_add_func("/gtk4/plain-without-adwaita",plain);g_test_add_func("/gtk4/gtk3-only",gtk3_only);
    g_test_add_func("/gtk4/helper-ping-preview-eof",ping_and_preview);g_test_add_func("/gtk4/loader-close-idempotent",close_idempotent);
    g_test_add_func("/gtk4/stylesheet-setting-notification",notification);g_test_add_func("/gtk4/no-display-ping",no_display);
    g_test_add_func("/gtk4/request-during-notification-sample",during_notification);
    g_test_add_func("/gtk4/request-during-timer-sample",during_timer);
    if(!api.adw) g_test_add_func("/gtk4/plain-named-native",plain_named_native);
    g_test_add_func("/gtk4/bounded-input-dispatch",assert_bounded_dispatch);
    g_test_add_func("/gtk4/burst-backpressure",burst);g_test_add_func("/gtk4/eof-during-sample",eof_during_sample);
    g_test_add_func("/gtk4/window-foreground-alpha",window_alpha);
    int status=g_test_run();api.remove_provider(fixture_target(&api),provider);g_object_unref(provider);dc_gtk_shutdown();return status;
}
