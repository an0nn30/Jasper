#include "gtk_api.h"
void dc_gtk_extract(struct dc_gtk_api *api, GtkWidget *root, struct dc_palette *out) {
    dc_gtk_settle();
    GtkStyleContext *context=api->context(root);
    for(int p=0;p<DC_PAIR_COUNT;p++) {
        const char *bg=p==DC_VIEW?"theme_base_color":p==DC_SELECTION?"theme_selected_bg_color":"theme_bg_color";
        const char *fg=p==DC_VIEW?"theme_text_color":p==DC_SELECTION?"theme_selected_fg_color":"theme_fg_color";
        dc_gtk_named_pair(api,context,bg,fg,&out->colors[p]);
    }
}
bool dc_sample_gtk3(bool dark, uint64_t generation, struct dc_palette *out) {
    return dc_gtk_sample(NULL, dark, generation, out);
}
#ifndef DC_GTK_NO_MAIN
int main(int argc, char **argv) { return dc_gtk_main(3, argc, argv); }
#endif
