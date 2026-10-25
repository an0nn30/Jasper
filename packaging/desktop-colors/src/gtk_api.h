#ifndef JASPER_DESKTOP_GTK_API_H
#define JASPER_DESKTOP_GTK_API_H
#include "protocol.h"
#include <stdbool.h>
#include <glib-object.h>

/* Public ABI only. GTK3 uses double RGBA; GTK4 uses float RGBA. */
typedef struct _GtkWidget GtkWidget;
typedef struct _GtkSettings GtkSettings;
typedef struct _GtkStyleContext GtkStyleContext;
typedef struct _GtkCssProvider GtkCssProvider;
typedef struct _GdkDisplay GdkDisplay;
struct dc_rgba3 { double red, green, blue, alpha; };
struct dc_rgba4 { float red, green, blue, alpha; };
struct dc_gtk_api {
    void *gtk, *adw;
    int major; unsigned minor, micro;
    bool variables;
    char version[64];
    unsigned (*get_major)(void), (*get_minor)(void), (*get_micro)(void);
    gboolean (*init3)(int *, char ***), (*init4)(void);
    GtkSettings *(*settings)(void);
    GtkWidget *(*window3)(int), *(*window4)(void), *(*label)(const char *), *(*box)(int,int);
    void (*child)(void *, GtkWidget *), (*append)(void *, GtkWidget *);
    void (*destroy)(void *), (*add_class)(GtkWidget *, const char *);
    gboolean (*mapped)(GtkWidget *);
    GtkStyleContext *(*context)(GtkWidget *);
    gboolean (*lookup)(GtkStyleContext *, const char *, void *);
    void (*foreground)(GtkWidget *, struct dc_rgba4 *);
    GdkDisplay *(*display)(void);
    GtkCssProvider *(*provider)(void);
    gboolean (*css3)(GtkCssProvider *, const char *, gssize, GError **);
    void (*css4)(GtkCssProvider *, const char *, gssize);
    void (*add_provider)(void *, void *, guint), (*remove_provider)(void *, void *);
    void (*adw_init)(void), *(*style_manager)(void);
    void (*color_scheme)(void *, int);
};
bool dc_gtk_api_open(int major, struct dc_gtk_api *out);
void dc_gtk_api_close(struct dc_gtk_api *api);
bool dc_sample_gtk3(bool dark, uint64_t generation, struct dc_palette *out);
bool dc_sample_gtk4(bool dark, uint64_t generation, struct dc_palette *out);
/* Shared implementation and process lifetime; GTK majors must use separate processes. */
bool dc_gtk_sample(struct dc_gtk_api *, bool, uint64_t, struct dc_palette *);
struct dc_gtk_api *dc_gtk4_api(void);
void dc_gtk_extract(struct dc_gtk_api *, GtkWidget *, struct dc_palette *);
void dc_gtk_settle(void);
bool dc_gtk_color_valid(struct dc_rgba);
bool dc_gtk_named_pair(struct dc_gtk_api *, GtkStyleContext *, const char *, const char *, struct dc_colors *);
int dc_gtk_main(int major, int argc, char **argv);
void dc_gtk_shutdown(void);
#endif
