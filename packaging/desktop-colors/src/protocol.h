#ifndef JASPER_DESKTOP_PROTOCOL_H
#define JASPER_DESKTOP_PROTOCOL_H

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#define DC_MAX_MESSAGE (64u * 1024u)
#define DC_MAX_REASON 1024u

enum dc_command_kind { DC_SAMPLE, DC_PING };
struct dc_command { enum dc_command_kind kind; int64_t generation; int dark; };
enum dc_source { DC_GTK3, DC_GTK4, DC_QT5, DC_QT6, DC_KDE5, DC_KDE6 };
enum dc_polarity { DC_DARK, DC_LIGHT, DC_UNKNOWN };
enum dc_pair { DC_WINDOW, DC_VIEW, DC_HEADER, DC_SIDEBAR, DC_DIALOG, DC_POPOVER, DC_SELECTION, DC_PAIR_COUNT };
struct dc_rgba { double red, green, blue, alpha; };
struct dc_colors { int present; struct dc_rgba background, foreground; };
struct dc_palette {
    int64_t generation;
    enum dc_source source;
    enum dc_polarity appearance;
    struct dc_colors colors[DC_PAIR_COUNT];
    const char *implementation;
    const char *version;
    const char *reason;
};

struct dc_portal {
    int64_t owner;
    enum dc_polarity appearance;
    int accent_present;
    struct dc_rgba accent;
    int contrast_present;
    int high_contrast;
    const char *reason;
};

/* Return 0 on success, -1 on invalid input or I/O failure. No partial parse is published.
 * Commands accept only the fixed v1 schema; length excludes the framing newline.
 * Messages are buffered and validated before writing one newline-terminated envelope.
 * Invalid optional colors are omitted. Strings are borrowed for the duration of the call. */
#ifdef __cplusplus
extern "C" {
#endif

int dc_read_command(const char *line, size_t length, struct dc_command *out);
int dc_write_palette(FILE *stream, const struct dc_palette *palette);
int dc_write_portal(FILE *stream, const struct dc_portal *portal);
int dc_write_pong(FILE *stream, int64_t generation);

#ifdef __cplusplus
}
#endif

#endif
