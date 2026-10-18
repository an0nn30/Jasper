#define _GNU_SOURCE
#ifdef DC_GTK_ABI
#include <gtk/gtk.h>
#include "gtk_api.h"
#include <stddef.h>
#define ABI(function, type) _Static_assert(__builtin_types_compatible_p(__typeof__(&(function)), type), #function " ABI")
#if DC_GTK_ABI == 4
#include <adwaita.h>
ABI(adw_init, void (*)(void));
static guint (*check_adw_major)(void) G_GNUC_UNUSED = adw_get_major_version;
static guint (*check_adw_minor)(void) G_GNUC_UNUSED = adw_get_minor_version;
static guint (*check_adw_micro)(void) G_GNUC_UNUSED = adw_get_micro_version;
ABI(adw_style_manager_get_default, AdwStyleManager *(*)(void));
ABI(adw_style_manager_set_color_scheme, void (*)(AdwStyleManager *,AdwColorScheme));
_Static_assert(sizeof(GdkRGBA)==sizeof(struct dc_rgba4), "GTK4 RGBA layout");
_Static_assert(offsetof(GdkRGBA,alpha)==offsetof(struct dc_rgba4,alpha), "GTK4 RGBA alpha");
ABI(gtk_init_check, gboolean (*)(void));
ABI(gtk_window_new, GtkWidget *(*)(void));
ABI(gtk_widget_get_color, void (*)(GtkWidget *,GdkRGBA *));
ABI(gtk_widget_add_css_class, void (*)(GtkWidget *,const char *));
ABI(gtk_window_set_child, void (*)(GtkWindow *,GtkWidget *));
ABI(gtk_box_append, void (*)(GtkBox *,GtkWidget *));
ABI(gtk_window_destroy, void (*)(GtkWindow *));
ABI(gtk_css_provider_load_from_data, void (*)(GtkCssProvider *,const char *,gssize));
ABI(gtk_style_context_add_provider_for_display, void (*)(GdkDisplay *,GtkStyleProvider *,guint));
ABI(gtk_style_context_remove_provider_for_display, void (*)(GdkDisplay *,GtkStyleProvider *));
#else
_Static_assert(sizeof(GdkRGBA)==sizeof(struct dc_rgba3), "GTK3 RGBA layout");
_Static_assert(offsetof(GdkRGBA,alpha)==offsetof(struct dc_rgba3,alpha), "GTK3 RGBA alpha");
ABI(gtk_init_check, gboolean (*)(int *,char ***));
ABI(gtk_window_new, GtkWidget *(*)(GtkWindowType));
ABI(gtk_container_add, void (*)(GtkContainer *,GtkWidget *));
ABI(gtk_widget_destroy, void (*)(GtkWidget *));
ABI(gtk_css_provider_load_from_data, gboolean (*)(GtkCssProvider *,const char *,gssize,GError **));
ABI(gtk_style_context_add_provider_for_screen, void (*)(GdkScreen *,GtkStyleProvider *,guint));
ABI(gtk_style_context_remove_provider_for_screen, void (*)(GdkScreen *,GtkStyleProvider *));
#endif
static guint (*check_major)(void) G_GNUC_UNUSED = gtk_get_major_version;
static guint (*check_minor)(void) G_GNUC_UNUSED = gtk_get_minor_version;
static guint (*check_micro)(void) G_GNUC_UNUSED = gtk_get_micro_version;
ABI(gtk_settings_get_default, GtkSettings *(*)(void));ABI(gtk_label_new, GtkWidget *(*)(const char *));
ABI(gtk_box_new, GtkWidget *(*)(GtkOrientation,int));ABI(gtk_widget_get_mapped, gboolean (*)(GtkWidget *));
ABI(gtk_widget_get_style_context, GtkStyleContext *(*)(GtkWidget *));
ABI(gtk_style_context_lookup_color, gboolean (*)(GtkStyleContext *,const char *,GdkRGBA *));
ABI(gdk_display_get_default, GdkDisplay *(*)(void));ABI(gtk_css_provider_new, GtkCssProvider *(*)(void));
#elif defined(DC_TEST_SHIM)
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
/* Fault injection is confined to helper subprocesses. Never loaded into a probe harness. */
void *dlopen(const char *name, int flags) {
    void *(*real_open)(const char *,int);
    *(void **)(&real_open)=dlsym(RTLD_NEXT,"dlopen");
    const char *mode=getenv("DC_TEST_RUNTIME");
    if(mode && name) {
        if((strcmp(mode,"neither")==0 && (strstr(name,"libgtk-")||strstr(name,"libadwaita-")))
            || (strcmp(mode,"plain")==0 && strstr(name,"libadwaita-"))
            || (strcmp(mode,"gtk3-only")==0 && (strstr(name,"libgtk-4")||strstr(name,"libadwaita-")))) return NULL;
        if(((strcmp(mode,"old-gtk4")==0 && strstr(name,"libgtk-4")) || (strcmp(mode,"old-gtk3")==0 && strstr(name,"libgtk-3")))) return real_open(getenv("LD_PRELOAD"),flags);
    }
    return real_open(name,flags);
}
unsigned gtk_get_major_version(void) {return strcmp(getenv("DC_TEST_RUNTIME"),"old-gtk3")==0?3:4;}
unsigned gtk_get_minor_version(void) {return strcmp(getenv("DC_TEST_RUNTIME"),"old-gtk3")==0?22:8;}
unsigned gtk_get_micro_version(void) {return 0;}
#else
#include "../src/gtk_api.c"
#include <gio/gio.h>
#include <dlfcn.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
void assert_rgba(struct dc_rgba c,double r,double g,double b,double a) {
    g_assert_cmpfloat(fabs(c.red-r),<,.0001);g_assert_cmpfloat(fabs(c.green-g),<,.0001);
    g_assert_cmpfloat(fabs(c.blue-b),<,.0001);g_assert_cmpfloat(fabs(c.alpha-a),<,.0001);
}
void assert_unmapped(struct dc_gtk_api *api) {
    if(api->major==4) {
        GListModel *(*windows)(void); *(void **)(&windows)=dlsym(api->gtk,"gtk_window_get_toplevels");
        GListModel *list=windows();
        g_assert_cmpuint(g_list_model_get_n_items(list),==,1);
        GtkWidget *root=g_list_model_get_item(list,0);g_assert_false(api->mapped(root));g_object_unref(root);
    } else {
        GList *(*windows)(void); *(void **)(&windows)=dlsym(api->gtk,"gtk_window_list_toplevels");
        GList *list=windows();g_assert_cmpuint(g_list_length(list),==,1);
        g_assert_false(api->mapped(list->data));g_list_free(list);
    }
}
void *fixture_target(struct dc_gtk_api *api) {
    if(api->major==4) return api->display();
    void *(*screen)(void); *(void **)(&screen)=dlsym(api->gtk,"gdk_screen_get_default");return screen();
}
void fixture_load(struct dc_gtk_api *api,GtkCssProvider *provider,const char *css) {
    if(api->major==4) api->css4(provider,css,-1);
    else {GError *error=NULL;api->css3(provider,css,-1,&error);g_assert_no_error(error);}
}
char *fixture_read(const char *name) {
    char *path=g_build_filename(g_getenv("DC_GTK_FIXTURES"),name,NULL),*css=NULL;
    GError *error=NULL;g_assert_true(g_file_get_contents(path,&css,NULL,&error));g_assert_no_error(error);g_free(path);return css;
}
void helper_contract(const char *helper,const char *mode) {
    GError *error=NULL;
    GSubprocessLauncher *launcher=g_subprocess_launcher_new(G_SUBPROCESS_FLAGS_STDIN_PIPE|G_SUBPROCESS_FLAGS_STDOUT_PIPE|G_SUBPROCESS_FLAGS_STDERR_PIPE);
    if(mode) {g_subprocess_launcher_setenv(launcher,"LD_PRELOAD",g_getenv("DC_GTK_SHIM"),TRUE);g_subprocess_launcher_setenv(launcher,"DC_TEST_RUNTIME",mode,TRUE);}
    if(mode && strcmp(mode,"no-display")==0) {
        g_subprocess_launcher_unsetenv(launcher,"DISPLAY");g_subprocess_launcher_unsetenv(launcher,"WAYLAND_DISPLAY");
        g_subprocess_launcher_setenv(launcher,"GDK_BACKEND","x11",TRUE);
    }
    GSubprocess *process=g_subprocess_launcher_spawn(launcher,&error,helper,"--protocol=1",NULL);g_assert_no_error(error);
    const char *commands="{\"v\":1,\"kind\":\"sample\",\"generation\":17,\"appearance\":\"dark\"}\n"
        "{\"v\":1,\"kind\":\"sample\",\"generation\":18,\"appearance\":\"light\"}\n"
        "{\"v\":1,\"kind\":\"ping\",\"generation\":19}\n";
    char *out=NULL,*err=NULL;g_assert_true(g_subprocess_communicate_utf8(process,commands,NULL,&out,&err,&error));g_assert_no_error(error);
    if(err && *err) g_printerr("%s",err);
    if(!g_subprocess_get_successful(process)) g_test_message("helper output: %s",out);
    g_assert_true(g_subprocess_get_successful(process));
    g_assert_nonnull(strstr(out,"\"generation\":17"));g_assert_nonnull(strstr(out,"\"generation\":18"));
    g_assert_nonnull(strstr(out,"\"kind\":\"pong\",\"generation\":19"));
    if(mode && (strcmp(mode,"neither")==0 || strcmp(mode,"old-gtk4")==0 || strcmp(mode,"old-gtk3")==0 || (strcmp(mode,"gtk3-only")==0 && strstr(helper,"gtk4")))) {
        g_assert_nonnull(strstr(out,"toolkit unavailable or unsupported"));g_assert_null(strstr(out,"window.bg"));
    }
    if(mode && strcmp(mode,"no-display")==0) {g_assert_nonnull(strstr(out,"display unavailable"));g_assert_null(strstr(out,"window.bg"));}
    if(mode && strcmp(mode,"gtk3-only")==0 && strstr(helper,"gtk3")) {g_assert_nonnull(strstr(out,"window.bg"));g_assert_nonnull(strstr(out,"\"implementation\":\"gtk3\""));}
    if(mode && strcmp(mode,"plain")==0) {g_assert_nonnull(strstr(out,"\"implementation\":\"gtk4\""));g_assert_null(strstr(out,"libadwaita"));}
    g_free(out);g_free(err);g_object_unref(process);g_object_unref(launcher);
}
#endif

#if !defined(DC_GTK_ABI) && !defined(DC_TEST_SHIM)
void assert_notifications(struct dc_gtk_api *api,GtkCssProvider *provider,bool modern) {
    FILE *output=tmpfile();g_assert_nonnull(output);
    struct worker worker={.loop=g_main_loop_new(NULL,FALSE),.output=output,.api=api->major==3?&api3:&api4,
        .last={DC_SAMPLE,33,1},.have_last=true};
    gulong signal=g_signal_connect(api->settings(),"notify::gtk-theme-name",G_CALLBACK(changed),&worker);
    if(modern) fixture_load(api,provider,"window { --window-bg-color: rgb(10%,20%,30%); --window-fg-color: white; }");
    else {char *css=fixture_read("named.css");fixture_load(api,provider,css);g_free(css);}
    g_object_notify(G_OBJECT(api->settings()),"gtk-theme-name");
    g_assert_cmpuint(worker.idle,>,0);
    while(worker.idle) g_main_context_iteration(NULL,TRUE);
    rewind(output);char line[8192];g_assert_nonnull(fgets(line,sizeof line,output));
    g_assert_nonnull(strstr(line,"\"generation\":33"));g_assert_nonnull(strstr(line,"window.bg"));
    g_signal_handler_disconnect(api->settings(),signal);
    if(api->adw) {
        signal=g_signal_connect(api->style_manager(),"notify::color-scheme",G_CALLBACK(changed),&worker);
        g_object_notify(G_OBJECT(api->style_manager()),"color-scheme");g_assert_cmpuint(worker.idle,>,0);
        while(worker.idle) g_main_context_iteration(NULL,TRUE);
        rewind(output);g_assert_nonnull(fgets(line,sizeof line,output));
        g_assert_nonnull(fgets(line,sizeof line,output));g_assert_nonnull(strstr(line,"\"generation\":33"));
        g_signal_handler_disconnect(api->style_manager(),signal);
    }
    g_main_loop_unref(worker.loop);fclose(output);
}
#endif

#if !defined(DC_GTK_ABI) && !defined(DC_TEST_SHIM)
struct injected_commands { int fd; bool delivered; };
static gboolean inject_samples(gpointer data) {
    struct injected_commands *injected=data;
    g_assert_true(sampling);
    const char *commands="{\"v\":1,\"kind\":\"sample\",\"generation\":41,\"appearance\":\"dark\"}\n"
        "{\"v\":1,\"kind\":\"sample\",\"generation\":42,\"appearance\":\"dark\"}\n";
    g_assert_cmpint(write(injected->fd,commands,strlen(commands)),==,(ssize_t)strlen(commands));
    injected->delivered=true;return G_SOURCE_REMOVE;
}
void assert_nested_requests(struct dc_gtk_api *api,GtkCssProvider *provider,bool notification) {
    if(api->variables) fixture_load(api,provider,"window { --window-bg-color: rgb(10%,20%,30%); --window-fg-color: white; }");
    else {char *css=fixture_read("named.css");fixture_load(api,provider,css);g_free(css);}
    FILE *output=tmpfile();g_assert_nonnull(output);
    struct worker worker={.loop=g_main_loop_new(NULL,FALSE),.output=output,.api=api->major==3?&api3:&api4,
        .last={DC_SAMPLE,40,1},.have_last=true};
    int descriptors[2];g_assert_cmpint(pipe(descriptors),==,0);
    g_assert_cmpint(fcntl(descriptors[0],F_SETFL,O_NONBLOCK),==,0);
    guint source=g_unix_fd_add(descriptors[0],G_IO_IN,input,&worker);
    struct injected_commands injected={.fd=descriptors[1]};
    g_idle_add_full(G_PRIORITY_DEFAULT_IDLE+50,inject_samples,&injected,NULL);
    if(notification) {
        gulong signal=g_signal_connect(api->settings(),"notify::gtk-theme-name",G_CALLBACK(changed),&worker);
        g_object_notify(G_OBJECT(api->settings()),"gtk-theme-name");
        while(worker.idle) g_main_context_iteration(NULL,TRUE);
        g_signal_handler_disconnect(api->settings(),signal);
    } else g_assert_true(periodic(&worker));
    g_assert_true(injected.delivered);
    rewind(output);char line[8192];
    for(int generation=40;generation<=42;generation++) {
        char token[64];snprintf(token,sizeof token,"\"generation\":%d",generation);
        g_assert_nonnull(fgets(line,sizeof line,output));g_assert_nonnull(strstr(line,token));
        g_assert_nonnull(strstr(line,"window.bg"));
    }
    g_assert_null(fgets(line,sizeof line,output));assert_unmapped(api);
    g_source_remove(source);close(descriptors[0]);close(descriptors[1]);fclose(output);g_main_loop_unref(worker.loop);
}
void assert_plain_native(const char *program) {
    GError *error=NULL;
    GSubprocessLauncher *launcher=g_subprocess_launcher_new(G_SUBPROCESS_FLAGS_STDOUT_PIPE|G_SUBPROCESS_FLAGS_STDERR_PIPE);
    g_subprocess_launcher_setenv(launcher,"LD_PRELOAD",g_getenv("DC_GTK_SHIM"),TRUE);
    g_subprocess_launcher_setenv(launcher,"DC_TEST_RUNTIME","plain",TRUE);
    GSubprocess *process=g_subprocess_launcher_spawn(launcher,&error,program,"-p","/gtk4/plain-named-native",NULL);g_assert_no_error(error);
    char *out=NULL,*err=NULL;g_assert_true(g_subprocess_communicate_utf8(process,NULL,NULL,&out,&err,&error));g_assert_no_error(error);
    g_print("%s",out);if(err && *err) g_printerr("%s",err);
    g_assert_true(g_subprocess_get_successful(process));g_assert_nonnull(strstr(out,"ok 1 /gtk4/plain-named-native"));
    g_free(out);g_free(err);g_object_unref(process);g_object_unref(launcher);
}
#endif
