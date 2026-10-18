#include "gtk_api.h"
static const char *variables[DC_PAIR_COUNT][2] = {
    {"window-bg-color", "window-fg-color"}, {"view-bg-color", "view-fg-color"},
    {"headerbar-bg-color", "headerbar-fg-color"}, {"sidebar-bg-color", "sidebar-fg-color"},
    {"dialog-bg-color", "dialog-fg-color"}, {"popover-bg-color", "popover-fg-color"},
    {"accent-bg-color", "accent-fg-color"}
};

void dc_gtk_extract(struct dc_gtk_api *api, GtkWidget *root, struct dc_palette *out) {
    if(api->variables) {
        GtkWidget *box=api->box(1,0);
        api->child(root,box);
        GtkWidget *labels[DC_PAIR_COUNT][2][2];
        GString *css=g_string_new(NULL);
        for(int p=0;p<DC_PAIR_COUNT;p++) for(int half=0;half<2;half++) for(int s=0;s<2;s++) {
            char cls[64]; snprintf(cls,sizeof cls,"jasper-probe-%d-%d-%d",p,half,s);
            labels[p][half][s]=api->label(""); api->add_class(labels[p][half][s],cls); api->append(box,labels[p][half][s]);
            g_string_append_printf(css,".%s { color: var(--%s, %s); }\n",cls,variables[p][half],s?"cyan":"magenta");
        }
        GtkCssProvider *provider=api->provider(); api->css4(provider,css->str,-1); g_string_free(css,TRUE);
        /* Instrumentation outranks user label colors; inherited variables retain their user cascade. */
        api->add_provider(api->display(),provider,900);
        dc_gtk_settle();
        for(int p=0;p<DC_PAIR_COUNT;p++) {
            struct dc_rgba halves[2]; bool available=true;
            for(int half=0;half<2;half++) {
                struct dc_rgba4 a,b; api->foreground(labels[p][half][0],&a); api->foreground(labels[p][half][1],&b);
                if(a.red!=b.red || a.green!=b.green || a.blue!=b.blue || a.alpha!=b.alpha) available=false;
                halves[half]=(struct dc_rgba){a.red,a.green,a.blue,a.alpha};
                if(!dc_gtk_color_valid(halves[half])) available=false;
            }
            out->colors[p]=(struct dc_colors){available,halves[0],halves[1]};
        }
        api->remove_provider(api->display(),provider); g_object_unref(provider);
    } else {
        dc_gtk_settle(); GtkStyleContext *context=api->context(root);
        for(int p=0;p<DC_PAIR_COUNT;p++) {
            bool found=false;
            if(api->major==4) {
                char bg[64],fg[64]; snprintf(bg,sizeof bg,"%s",variables[p][0]); snprintf(fg,sizeof fg,"%s",variables[p][1]);
                for(char *s=bg;*s;s++) if(*s=='-') *s='_';
                for(char *s=fg;*s;s++) if(*s=='-') *s='_';
                found=dc_gtk_named_pair(api,context,bg,fg,&out->colors[p]);
            }
            if(!found && (p==DC_WINDOW || p==DC_VIEW || p==DC_SELECTION)) {
                const char *bg=p==DC_VIEW?"theme_base_color":p==DC_SELECTION?"theme_selected_bg_color":"theme_bg_color";
                const char *fg=p==DC_VIEW?"theme_text_color":p==DC_SELECTION?"theme_selected_fg_color":"theme_fg_color";
                dc_gtk_named_pair(api,context,bg,fg,&out->colors[p]);
            }
        }
    }
}

bool dc_sample_gtk4(bool dark, uint64_t generation, struct dc_palette *out) {
    return dc_gtk_sample(dc_gtk4_api(), dark, generation, out);
}
#ifndef DC_GTK_NO_MAIN
int main(int argc, char **argv) { return dc_gtk_main(4, argc, argv); }
#endif
