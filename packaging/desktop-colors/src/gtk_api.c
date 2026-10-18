#define _POSIX_C_SOURCE 200809L
#include "gtk_api.h"
#include <glib-unix.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <math.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>

#define LOAD(field, name) do { *(void **)(&out->field) = dlsym(out->gtk, name); if (!out->field) goto missing; } while (0)
bool dc_gtk_api_open(int major, struct dc_gtk_api *out) {
    memset(out, 0, sizeof *out);
    if (major != 3 && major != 4) return false;
    out->major = major;
    out->gtk = dlopen(major == 3 ? "libgtk-3.so.0" : "libgtk-4.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!out->gtk) return false;
    LOAD(get_major, "gtk_get_major_version"); LOAD(get_minor, "gtk_get_minor_version"); LOAD(get_micro, "gtk_get_micro_version");
    out->minor = out->get_minor(); out->micro = out->get_micro();
    if (out->get_major() != (unsigned) major || out->minor < (major == 3 ? 24u : 10u)) goto missing;
    LOAD(settings, "gtk_settings_get_default"); LOAD(label, "gtk_label_new");
    LOAD(mapped, "gtk_widget_get_mapped"); LOAD(context, "gtk_widget_get_style_context");
    LOAD(lookup, "gtk_style_context_lookup_color"); LOAD(display, "gdk_display_get_default");
    LOAD(provider, "gtk_css_provider_new"); LOAD(box, "gtk_box_new");
    if (major == 3) {
        LOAD(init3, "gtk_init_check"); LOAD(window3, "gtk_window_new"); LOAD(child, "gtk_container_add");
        LOAD(destroy, "gtk_widget_destroy"); LOAD(css3, "gtk_css_provider_load_from_data");
        LOAD(add_provider, "gtk_style_context_add_provider_for_screen");
        LOAD(remove_provider, "gtk_style_context_remove_provider_for_screen");
    } else {
        LOAD(init4, "gtk_init_check"); LOAD(window4, "gtk_window_new"); LOAD(child, "gtk_window_set_child");
        LOAD(append, "gtk_box_append"); LOAD(destroy, "gtk_window_destroy");
        LOAD(add_class, "gtk_widget_add_css_class"); LOAD(foreground, "gtk_widget_get_color");
        LOAD(css4, "gtk_css_provider_load_from_data");
        LOAD(add_provider, "gtk_style_context_add_provider_for_display");
        LOAD(remove_provider, "gtk_style_context_remove_provider_for_display");
        out->variables = out->minor >= 16;
        out->adw = dlopen("libadwaita-1.so.0", RTLD_NOW | RTLD_LOCAL);
        if (out->adw) {
            *(void **)(&out->adw_init) = dlsym(out->adw, "adw_init");
            *(void **)(&out->style_manager) = dlsym(out->adw, "adw_style_manager_get_default");
            *(void **)(&out->color_scheme) = dlsym(out->adw, "adw_style_manager_set_color_scheme");
            if (!out->adw_init || !out->style_manager || !out->color_scheme) {
                dlclose(out->adw); out->adw = NULL;
            }
        }
    }
    snprintf(out->version, sizeof out->version, "%d.%u.%u", major, out->minor, out->micro);
    if(out->adw) {
        unsigned (*adw_major)(void), (*adw_minor)(void), (*adw_micro)(void);
        *(void **)(&adw_major)=dlsym(out->adw,"adw_get_major_version");
        *(void **)(&adw_minor)=dlsym(out->adw,"adw_get_minor_version");
        *(void **)(&adw_micro)=dlsym(out->adw,"adw_get_micro_version");
        if(adw_major && adw_minor && adw_micro) snprintf(out->version,sizeof out->version,
            "%u.%u.%u (GTK %d.%u.%u)",adw_major(),adw_minor(),adw_micro(),major,out->minor,out->micro);
    }
    return true;
missing:
    dc_gtk_api_close(out); return false;
}
void dc_gtk_api_close(struct dc_gtk_api *api) {
    if (api->adw) dlclose(api->adw);
    if (api->gtk) dlclose(api->gtk);
    memset(api, 0, sizeof *api);
}
static struct dc_gtk_api api3, api4;
static bool tried3, tried4, initialized3, initialized4, init_attempted3, init_attempted4, sampling;
static GtkWidget *root;
static struct dc_gtk_api *root_api;
static gboolean settled(gpointer flag) { *(bool *)flag = true; return G_SOURCE_REMOVE; }
void dc_gtk_settle(void) {
    bool done = false;
    g_idle_add_full(G_PRIORITY_LOW, settled, &done, NULL);
    while (!done) g_main_context_iteration(NULL, TRUE);
}
static double luminance(struct dc_rgba c) {
    double rgb[3] = {c.red,c.green,c.blue};
    for (int i=0;i<3;i++) rgb[i] = rgb[i] <= .04045 ? rgb[i]/12.92 : pow((rgb[i]+.055)/1.055,2.4);
    return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];
}
bool dc_gtk_color_valid(struct dc_rgba c) {
    return isfinite(c.red) && isfinite(c.green) && isfinite(c.blue) && isfinite(c.alpha)
        && c.red>=0 && c.red<=1 && c.green>=0 && c.green<=1 && c.blue>=0 && c.blue<=1 && c.alpha>=0 && c.alpha<=1;
}
static bool named(struct dc_gtk_api *api, GtkStyleContext *context, const char *name, struct dc_rgba *out) {
    if (api->major==3) {
        struct dc_rgba3 c;
        if (!api->lookup(context,name,&c)) return false;
        *out=(struct dc_rgba){c.red,c.green,c.blue,c.alpha};
    } else {
        struct dc_rgba4 c;
        if (!api->lookup(context,name,&c)) return false;
        *out=(struct dc_rgba){c.red,c.green,c.blue,c.alpha};
    }
    return dc_gtk_color_valid(*out);
}
bool dc_gtk_named_pair(struct dc_gtk_api *api, GtkStyleContext *context, const char *bg, const char *fg, struct dc_colors *out) {
    struct dc_colors pair={0};
    pair.present=named(api,context,bg,&pair.background) && named(api,context,fg,&pair.foreground);
    *out=pair; return pair.present;
}
struct dc_gtk_api *dc_gtk4_api(void) { return &api4; }
static void replace_root(struct dc_gtk_api *api) {
    if (root) { root_api->destroy(root); g_object_unref(root); }
    root=api->major==3 ? api->window3(0) : api->window4();
    g_object_ref_sink(root); root_api=api;
}
bool dc_gtk_sample(struct dc_gtk_api *requested, bool dark, uint64_t generation, struct dc_palette *out) {
    struct dc_gtk_api *api = requested ? requested : &api3;
    bool *tried=api==&api3?&tried3:&tried4, *initialized=api==&api3?&initialized3:&initialized4;
    *out=(struct dc_palette){.generation=(int64_t)generation,.source=api==&api3?DC_GTK3:DC_GTK4,
        .appearance=DC_UNKNOWN,.implementation=api==&api3?"gtk3":"gtk4",.version="",.reason="toolkit unavailable or unsupported"};
    if (generation>INT64_MAX) return false;
    if (sampling) {out->reason="sample already active";return false;}
    if (!*tried) { *tried=true; if (!dc_gtk_api_open(api==&api3?3:4,api)) return false; }
    if (!api->gtk) return false;
    out->version=api->version;
    bool *init_attempted=api==&api3?&init_attempted3:&init_attempted4;
    if (!*init_attempted) {
        *init_attempted=true;
        *initialized=api->major==3?api->init3(NULL,NULL):api->init4();
        if (*initialized && api->adw) api->adw_init();
    }
    if (!*initialized) { out->reason="display unavailable"; return false; }
    out->implementation=api->adw?"libadwaita":api->major==3?"gtk3":"gtk4";
    sampling=true;
    if (api->adw) api->color_scheme(api->style_manager(),dark?4:1); /* FORCE_DARK / FORCE_LIGHT */
    else g_object_set(api->settings(),"gtk-application-prefer-dark-theme",dark,NULL);
    replace_root(api);
    dc_gtk_extract(api,root,out);
    sampling=false;
    struct dc_colors window=out->colors[DC_WINDOW];
    if (!window.present || window.background.alpha!=1 || window.foreground.alpha!=1) {
        out->colors[DC_WINDOW].present=0; out->reason="window pair unavailable"; return false;
    }
    double bg=luminance(window.background), fg=luminance(window.foreground);
    out->appearance=bg<fg?DC_DARK:DC_LIGHT;
    if ((fmax(bg,fg)+.05)/(fmin(bg,fg)+.05)<4.5 || (out->appearance==DC_DARK)!=dark) {
        memset(out->colors,0,sizeof out->colors); out->reason="requested appearance unsupported"; return false;
    }
    out->reason=""; return true;
}
void dc_gtk_shutdown(void) {
    if(root) {root_api->destroy(root);g_object_unref(root);root=NULL;}
    /* GTK registers process-global GTypes: keep its library loaded until process exit. */
}

struct worker { GMainLoop *loop; FILE *output; struct dc_gtk_api *api; struct dc_command last; bool have_last, publishing; GQueue deferred; guint idle; char line[DC_MAX_MESSAGE+1]; size_t used; bool overflow; };
static void publish(struct worker *w) {
    if(w->publishing || sampling) return;
    w->publishing=true;
    for(;;) {
        struct dc_palette p; dc_gtk_sample(w->api,w->last.dark,w->last.generation,&p);
        if(dc_write_palette(w->output,&p)!=0) {g_main_loop_quit(w->loop);break;}
        struct dc_command *next=g_queue_pop_head(&w->deferred);
        if(!next) break;
        w->last=*next;g_free(next);
    }
    w->publishing=false;
}
static gboolean refresh(gpointer data) { struct worker *w=data; w->idle=0; if(w->have_last && !sampling) publish(w); return G_SOURCE_REMOVE; }
static void changed(GObject *object,GParamSpec *property,gpointer data) {
    (void)object;(void)property; struct worker *w=data;
    if(!sampling && w->have_last && !w->idle) w->idle=g_idle_add(refresh,w);
}
static gboolean periodic(gpointer data) { struct worker *w=data; if(w->have_last && !sampling) publish(w); return G_SOURCE_CONTINUE; }
static gboolean input(gint fd,GIOCondition condition,gpointer data) {
    struct worker *w=data; char bytes[4096]; ssize_t n;
    (void)condition;
    while((n=read(fd,bytes,sizeof bytes))>0) for(ssize_t i=0;i<n;i++) {
        if(bytes[i]=='\n') {
            struct dc_command c;
            if(!w->overflow && dc_read_command(w->line,w->used,&c)==0) {
                if(c.kind==DC_PING) {if(dc_write_pong(stdout,c.generation)!=0) g_main_loop_quit(w->loop);}
                else if(w->publishing || sampling) {
                    /* The GTK settle idle pumps this context. Preserve the active root and
                     * request metadata until extraction and its publication have finished. */
                    struct dc_command *pending=g_new(struct dc_command,1);*pending=c;
                    g_queue_push_tail(&w->deferred,pending);
                } else {w->last=c;w->have_last=true;publish(w);}
            }
            w->used=0;w->overflow=false;
        } else if(w->used<DC_MAX_MESSAGE) w->line[w->used++]=bytes[i];
        else w->overflow=true;
    }
    if(n==0 || (n<0 && errno!=EAGAIN && errno!=EWOULDBLOCK)) {g_main_loop_quit(w->loop);return G_SOURCE_REMOVE;}
    return G_SOURCE_CONTINUE;
}
int dc_gtk_main(int major,int argc,char **argv) {
    if(argc!=2 || strcmp(argv[1],"--protocol=1")!=0) {fprintf(stderr,"usage: jasper-desktop-gtk%d --protocol=1\n",major);return 2;}
    struct worker w={.loop=g_main_loop_new(NULL,FALSE),.output=stdout,.api=major==3?&api3:&api4};
    struct dc_palette p; dc_gtk_sample(w.api,false,0,&p);
    /* Capabilities use a palette envelope with absent pairs and a reason in protocol v1. */
    if(dc_write_palette(stdout,&p)!=0) return 1;
    if(w.api->gtk && (major==3?initialized3:initialized4)) {
        g_signal_connect(w.api->settings(),"notify::gtk-theme-name",G_CALLBACK(changed),&w);
        g_signal_connect(w.api->settings(),"notify::gtk-application-prefer-dark-theme",G_CALLBACK(changed),&w);
        if(w.api->adw) g_signal_connect(w.api->style_manager(),"notify",G_CALLBACK(changed),&w);
    }
    int flags=fcntl(STDIN_FILENO,F_GETFL);
    if(flags<0 || fcntl(STDIN_FILENO,F_SETFL,flags|O_NONBLOCK)<0) return 1;
    guint source=g_unix_fd_add(STDIN_FILENO,G_IO_IN|G_IO_HUP|G_IO_ERR,input,&w);
    guint timer=g_timeout_add_seconds(30,periodic,&w);
    g_main_loop_run(w.loop);
    GSource *remaining=g_main_context_find_source_by_id(NULL,source); if(remaining) g_source_destroy(remaining);
    if(w.idle) g_source_remove(w.idle);
    g_source_remove(timer);
    if(w.api->gtk && (major==3?initialized3:initialized4)) {
        g_signal_handlers_disconnect_by_data(w.api->settings(),&w);
        if(w.api->adw) g_signal_handlers_disconnect_by_data(w.api->style_manager(),&w);
    }
    g_queue_clear_full(&w.deferred,g_free);
    dc_gtk_shutdown();g_main_loop_unref(w.loop);fcntl(STDIN_FILENO,F_SETFL,flags);return 0;
}
