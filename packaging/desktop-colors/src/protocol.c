#define _POSIX_C_SOURCE 200809L
#include "protocol.h"

#include <inttypes.h>
#include <locale.h>
#include <math.h>
#include <stdarg.h>
#include <string.h>

struct reader { const char *text; size_t length, at; };
static void space(struct reader *r) {
    while (r->at < r->length && (r->text[r->at] == ' ' || r->text[r->at] == '\t' || r->text[r->at] == '\r' || r->text[r->at] == '\n')) r->at++;
}
static int take(struct reader *r, char c) {
    if (r->at < r->length && r->text[r->at] == c) { r->at++; return 1; }
    return 0;
}
static int hex(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}
/* Every accepted command key/value is ASCII. Other Unicode strings cannot match
 * the fixed command schema; escaped ASCII is decoded before key comparison. */
static int string(struct reader *r, char *out, size_t capacity) {
    if (!take(r, '"')) return -1;
    size_t used = 0;
    while (r->at < r->length) {
        unsigned char c = (unsigned char) r->text[r->at++];
        if (c == '"') { out[used] = '\0'; return 0; }
        if (c < 0x20 || c >= 0x80) return -1;
        if (c == '\\') {
            if (r->at == r->length) return -1;
            c = (unsigned char) r->text[r->at++];
            switch (c) {
                case '"': case '\\': case '/': break;
                case 'b': c = '\b'; break; case 'f': c = '\f'; break;
                case 'n': c = '\n'; break; case 'r': c = '\r'; break; case 't': c = '\t'; break;
                case 'u': {
                    unsigned value = 0;
                    if (r->length - r->at < 4) return -1;
                    for (int i = 0; i < 4; i++) {
                        int digit = hex(r->text[r->at++]);
                        if (digit < 0) return -1;
                        value = value * 16 + (unsigned) digit;
                    }
                    if (value == 0 || value >= 0x80) return -1;
                    c = (unsigned char) value;
                    break;
                }
                default: return -1;
            }
        }
        if (used + 1 >= capacity) return -1;
        out[used++] = (char) c;
    }
    return -1;
}
static int integer(struct reader *r, int64_t *out) {
    size_t start = r->at;
    int64_t value = 0;
    if (take(r, '0')) { *out = 0; return 0; }
    while (r->at < r->length && r->text[r->at] >= '0' && r->text[r->at] <= '9') {
        int digit = r->text[r->at++] - '0';
        if (value > (INT64_MAX - digit) / 10) return -1;
        value = value * 10 + digit;
    }
    if (r->at == start) return -1;
    *out = value;
    return 0;
}
int dc_read_command(const char *line, size_t length, struct dc_command *out) {
    if (!line || !out || length > DC_MAX_MESSAGE) return -1;
    struct reader r = {line, length, 0};
    struct dc_command command = {0};
    unsigned seen = 0;
    char kind[16] = "", appearance[16] = "";
    space(&r);
    if (!take(&r, '{')) return -1;
    for (;;) {
        char key[24];
        space(&r);
        if (string(&r, key, sizeof key) != 0) return -1;
        unsigned bit;
        if (strcmp(key, "v") == 0) bit = 1;
        else if (strcmp(key, "kind") == 0) bit = 2;
        else if (strcmp(key, "generation") == 0) bit = 4;
        else if (strcmp(key, "appearance") == 0) bit = 8;
        else return -1;
        if (seen & bit) return -1;
        seen |= bit;
        space(&r);
        if (!take(&r, ':')) return -1;
        space(&r);
        if (bit == 1) {
            int64_t version;
            if (integer(&r, &version) != 0 || version != 1) return -1;
        } else if (bit == 2) {
            if (string(&r, kind, sizeof kind) != 0) return -1;
        } else if (bit == 4) {
            if (integer(&r, &command.generation) != 0) return -1;
        } else if (string(&r, appearance, sizeof appearance) != 0) return -1;
        space(&r);
        if (take(&r, '}')) break;
        if (!take(&r, ',')) return -1;
    }
    space(&r);
    if (r.at != r.length) return -1;
    if (strcmp(kind, "ping") == 0 && seen == 7) command.kind = DC_PING;
    else if (strcmp(kind, "sample") == 0 && seen == 15) {
        command.kind = DC_SAMPLE;
        if (strcmp(appearance, "dark") == 0) command.dark = 1;
        else if (strcmp(appearance, "light") != 0) return -1;
    } else return -1;
    *out = command;
    return 0;
}

struct writer { char text[DC_MAX_MESSAGE + 1]; size_t length; int failed; };
static void append(struct writer *w, const char *format, ...) {
    if (w->failed) return;
    va_list args;
    va_start(args, format);
    int added = vsnprintf(w->text + w->length, sizeof w->text - w->length, format, args);
    va_end(args);
    if (added < 0 || (size_t) added >= sizeof w->text - w->length) { w->failed = 1; return; }
    w->length += (size_t) added;
}
/* Validate UTF-8, reject overlong/surrogate/out-of-range encodings and count code points. */
static int valid_text(const char *text, size_t max_characters) {
    if (!text) return 0;
    size_t characters = 0, bytes = 0;
    const unsigned char *p = (const unsigned char *) text;
    while (*p) {
        uint32_t value = *p++;
        unsigned more = 0;
        uint32_t minimum = 0;
        bytes++;
        if (value >= 0x80) {
            if (value >= 0xc2 && value <= 0xdf) { more = 1; minimum = 0x80; value &= 0x1f; }
            else if (value >= 0xe0 && value <= 0xef) { more = 2; minimum = 0x800; value &= 0x0f; }
            else if (value >= 0xf0 && value <= 0xf4) { more = 3; minimum = 0x10000; value &= 7; }
            else return 0;
            for (unsigned i = 0; i < more; i++) {
                if (*p < 0x80 || *p > 0xbf) return 0;
                value = (value << 6) | (*p++ & 0x3f);
                bytes++;
            }
            if (value < minimum || value > 0x10ffff || (value >= 0xd800 && value <= 0xdfff)) return 0;
        }
        if (++characters > max_characters || bytes > DC_MAX_MESSAGE) return 0;
    }
    return 1;
}
static void quoted(struct writer *w, const char *text) {
    append(w, "\"");
    for (const unsigned char *p = (const unsigned char *) text; *p && !w->failed; p++) {
        if (*p == '"' || *p == '\\') append(w, "\\%c", *p);
        else if (*p < 0x20) append(w, "\\u%04x", (unsigned) *p);
        else append(w, "%c", *p);
    }
    append(w, "\"");
}
static int valid_color(struct dc_rgba color) {
    const double values[] = {color.red, color.green, color.blue, color.alpha};
    for (size_t i = 0; i < 4; i++) if (!isfinite(values[i]) || values[i] < 0 || values[i] > 1) return 0;
    return 1;
}
static void color(struct writer *w, const char *pair, const char *half, struct dc_rgba value) {
    append(w, ",\"%s.%s\":\"%.17g,%.17g,%.17g,%.17g\"", pair, half, value.red, value.green, value.blue, value.alpha);
}
int dc_write_palette(FILE *stream, const struct dc_palette *palette) {
    static const char *const pairs[] = {"window", "view", "header", "sidebar", "dialog", "popover", "selection"};
    static const char *const appearances[] = {"dark", "light", "unknown"};
    if (!stream || !palette || palette->generation < 0 || (palette->source != DC_GTK3 && palette->source != DC_GTK4)
        || palette->appearance < DC_DARK || palette->appearance > DC_UNKNOWN
        || !valid_text(palette->implementation, DC_MAX_MESSAGE) || !valid_text(palette->version, DC_MAX_MESSAGE)
        || !valid_text(palette->reason, DC_MAX_REASON)) return -1;
    locale_t decimal = newlocale(LC_NUMERIC_MASK, "C", (locale_t) 0);
    if (!decimal) return -1;
    locale_t previous = uselocale(decimal);
    if (!previous) { freelocale(decimal); return -1; }
    struct writer writer = { .length = 0, .failed = 0 };
    append(&writer, "{\"v\":1,\"kind\":\"palette\",\"generation\":%" PRId64 ",\"source\":\"%s\",\"appearance\":\"%s\",\"implementation\":",
        palette->generation, palette->source == DC_GTK3 ? "gtk3" : "gtk4", appearances[palette->appearance]);
    quoted(&writer, palette->implementation);
    append(&writer, ",\"libraryVersion\":"); quoted(&writer, palette->version);
    append(&writer, ",\"reason\":"); quoted(&writer, palette->reason);
    for (int i = 0; i < DC_PAIR_COUNT; i++) {
        const struct dc_colors *pair = &palette->colors[i];
        if (pair->present && valid_color(pair->background) && valid_color(pair->foreground)) {
            color(&writer, pairs[i], "bg", pair->background);
            color(&writer, pairs[i], "fg", pair->foreground);
        }
    }
    append(&writer, "}\n");
    uselocale(previous);
    freelocale(decimal);
    if (writer.failed) return -1;
    if (fwrite(writer.text, 1, writer.length, stream) != writer.length || fflush(stream) != 0) return -1;
    return 0;
}
