/* Pure logic of the jasper command (packaging/cli/README.md); see jasper_cli_core.h. */
#include "jasper_cli_core.h"

#include <ctype.h>
#include <stdlib.h>
#include <string.h>

static const char ALPHABET[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

char *jc_base64_encode(const unsigned char *data, size_t length) {
    char *text = malloc(4 * ((length + 2) / 3) + 1);
    size_t out = 0;
    if (text == NULL) return NULL;
    for (size_t i = 0; i < length; i += 3) {
        unsigned long chunk = (unsigned long) data[i] << 16;
        if (i + 1 < length) chunk |= (unsigned long) data[i + 1] << 8;
        if (i + 2 < length) chunk |= (unsigned long) data[i + 2];
        text[out++] = ALPHABET[(chunk >> 18) & 63];
        text[out++] = ALPHABET[(chunk >> 12) & 63];
        text[out++] = i + 1 < length ? ALPHABET[(chunk >> 6) & 63] : '=';
        text[out++] = i + 2 < length ? ALPHABET[chunk & 63] : '=';
    }
    text[out] = '\0';
    return text;
}

static int sextet(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

unsigned char *jc_base64_decode(const char *text, size_t *length) {
    size_t size = strlen(text), out = 0;
    unsigned char *data;
    if (size % 4 != 0) return NULL;
    data = malloc(size / 4 * 3 + 1);
    if (data == NULL) return NULL;
    for (size_t i = 0; i < size; i += 4) {
        int last = i + 4 == size;
        int a = sextet(text[i]), b = sextet(text[i + 1]);
        /* Padding only ends the last group: "x=" or "==". -2 marks a padded position. */
        int c = last && text[i + 2] == '=' && text[i + 3] == '=' ? -2 : sextet(text[i + 2]);
        int d = last && text[i + 3] == '=' ? -2 : sextet(text[i + 3]);
        if (a < 0 || b < 0 || c == -1 || d == -1) {
            free(data);
            return NULL;
        }
        data[out++] = (unsigned char) ((a << 2) | (b >> 4));
        if (c >= 0) data[out++] = (unsigned char) (((b & 15) << 4) | (c >> 2));
        if (c >= 0 && d >= 0) data[out++] = (unsigned char) (((c & 3) << 6) | d);
    }
    data[out] = '\0';
    *length = out;
    return data;
}

static int is_space(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\v' || c == '\f'; }

static int blank(const char *text) {
    if (text == NULL) return 1;
    for (; *text != '\0'; text++) if (!is_space(*text)) return 0;
    return 1;
}

static int is_separator(jc_os os, char c) { return c == '/' || (os == JC_OS_WINDOWS && c == '\\'); }

static int absolute(jc_os os, const char *path) {
    if (os == JC_OS_WINDOWS)
        return (isalpha((unsigned char) path[0]) && path[1] == ':' && is_separator(os, path[2]))
            || (is_separator(os, path[0]) && is_separator(os, path[1]));
    return path[0] == '/';
}

/* Copies length bytes of text into out; -1 when they do not fit. */
static int copy(char *out, size_t size, const char *text, size_t length) {
    if (length + 1 > size) return -1;
    memcpy(out, text, length);
    out[length] = '\0';
    return 0;
}

/* out = first, the OS's separator, second. */
static int join(jc_os os, char *out, size_t size, const char *first, const char *second) {
    size_t a = strlen(first), b = strlen(second);
    if (a + 1 + b + 1 > size) return -1;
    memcpy(out, first, a);
    out[a] = os == JC_OS_WINDOWS ? '\\' : '/';
    memcpy(out + a + 1, second, b + 1);
    return 0;
}

int jc_normalize(jc_os os, const char *path, char *out, size_t size) {
    char separator = os == JC_OS_WINDOWS ? '\\' : '/';
    size_t i, used, root;
    if (!absolute(os, path)) return -1;
    if (os == JC_OS_WINDOWS && path[1] == ':') {
        if (size < 4) return -1;
        out[0] = path[0];
        out[1] = ':';
        out[2] = '\\';
        used = 3;
    } else if (os == JC_OS_WINDOWS) {
        if (size < 3) return -1;
        out[0] = '\\';
        out[1] = '\\';
        used = 2;
    } else {
        if (size < 2) return -1;
        out[0] = '/';
        used = 1;
    }
    root = used;
    i = used;
    while (path[i] != '\0') {
        size_t start, length;
        while (is_separator(os, path[i])) i++;
        start = i;
        while (path[i] != '\0' && !is_separator(os, path[i])) i++;
        length = i - start;
        if (length == 0 || (length == 1 && path[start] == '.')) continue;
        if (length == 2 && path[start] == '.' && path[start + 1] == '.') {
            /* Back to just before the separator that starts the last kept segment; never above the root. */
            size_t cut = used;
            while (cut > root && out[cut - 1] != separator) cut--;
            used = cut > root ? cut - 1 : root;
            continue;
        }
        if (used + (used > root ? 1 : 0) + length + 1 > size) return -1;
        if (used > root) out[used++] = separator;
        memcpy(out + used, path + start, length);
        used += length;
    }
    out[used] = '\0';
    return 0;
}

int jc_socket_path(jc_os os, jc_lookup lookup, void *context, const char *home, const char *cwd, char *out, size_t size) {
    char root[JC_MAX_PATH], joined[JC_MAX_PATH], base[JC_MAX_PATH], daemon[JC_MAX_PATH];
    const char *socket = lookup("JASPER_COMMAND_SOCKET", context);
    const char *override = lookup("JASPER_HOME", context);
    if (!blank(socket)) return copy(out, size, socket, strlen(socket));
    if (!blank(override)) {
        /* As Java's strip(): surrounding whitespace is not part of the home. */
        const char *start = override, *end = override + strlen(override);
        char stripped[JC_MAX_PATH];
        while (is_space(*start)) start++;
        while (end > start && is_space(end[-1])) end--;
        if (copy(stripped, sizeof stripped, start, (size_t) (end - start)) != 0) return -1;
        if (absolute(os, stripped)) {
            if (copy(joined, sizeof joined, stripped, strlen(stripped)) != 0) return -1;
        } else if (join(os, joined, sizeof joined, cwd, stripped) != 0) {
            return -1;
        }
        if (jc_normalize(os, joined, root, sizeof root) != 0) return -1;
    } else {
        const char *configured = os == JC_OS_WINDOWS ? lookup("APPDATA", context)
            : os == JC_OS_LINUX ? lookup("XDG_CONFIG_HOME", context) : NULL;
        /* As AppDirs.absoluteRoot: a blank or relative value means the default under the home. */
        if (!blank(configured) && absolute(os, configured)) {
            if (copy(base, sizeof base, configured, strlen(configured)) != 0) return -1;
        } else if (join(os, base, sizeof base, home, os == JC_OS_WINDOWS ? "AppData\\Roaming" : ".config") != 0) {
            return -1;
        }
        if (join(os, joined, sizeof joined, base, "jasper") != 0 || jc_normalize(os, joined, root, sizeof root) != 0) return -1;
    }
    if (join(os, daemon, sizeof daemon, root, "daemon") != 0) return -1;
    return join(os, out, size, daemon, "commands");
}

int jc_parent(jc_os os, const char *path, char *out, size_t size) {
    size_t length = strlen(path), keep;
    while (length > 0 && !is_separator(os, path[length - 1])) length--;
    if (length == 0) return -1;
    keep = length - 1;
    /* A root keeps its separator: "/" and "C:\". */
    if (keep == 0 || (os == JC_OS_WINDOWS && keep == 2 && path[1] == ':')) keep++;
    return copy(out, size, path, keep);
}

int jc_valid_pane(const char *pane) {
    if (pane == NULL || strlen(pane) != 36) return 0;
    for (int i = 0; i < 36; i++) {
        if (i == 8 || i == 13 || i == 18 || i == 23) {
            if (pane[i] != '-') return 0;
        } else if (!isxdigit((unsigned char) pane[i])) {
            return 0;
        }
    }
    return 1;
}

/* Whether entry is NAME=... for exactly this name. */
static int named(const char *entry, const char *name) {
    size_t length = strlen(name);
    return strncmp(entry, name, length) == 0 && entry[length] == '=';
}

int jc_cold_start_keeps(const char *entry) {
    if (entry == NULL) return 0;
    if (strchr(entry, '=') == NULL) return 1;
    return strncmp(entry, "GIT_", 4) != 0 && !named(entry, "JASPER_PANE") && !named(entry, "JASPER_COMMAND_SOCKET");
}

typedef struct {
    char *data;
    size_t used;
    size_t capacity;
    int failed;
} text_buffer;

static void append(text_buffer *buffer, const char *text) {
    size_t length = strlen(text);
    if (buffer->failed) return;
    if (buffer->used + length + 1 > buffer->capacity) {
        size_t capacity = buffer->capacity == 0 ? 256 : buffer->capacity;
        char *grown;
        while (buffer->used + length + 1 > capacity) capacity *= 2;
        grown = realloc(buffer->data, capacity);
        if (grown == NULL) {
            buffer->failed = 1;
            return;
        }
        buffer->data = grown;
        buffer->capacity = capacity;
    }
    memcpy(buffer->data + buffer->used, text, length + 1);
    buffer->used += length;
}

static void append_text(text_buffer *buffer, const char *text) {
    char *encoded = jc_base64_encode((const unsigned char *) text, strlen(text));
    if (encoded == NULL) {
        buffer->failed = 1;
        return;
    }
    append(buffer, encoded);
    free(encoded);
}

char *jc_request(const char *token, const char *cwd, const char *pane, int count, const char *const *arguments) {
    text_buffer buffer = { NULL, 0, 0, 0 };
    append(&buffer, "jasper-command\t" JC_PROTOCOL_VERSION "\t");
    append(&buffer, token);
    append(&buffer, "\t");
    append_text(&buffer, cwd);
    append(&buffer, "\t");
    append(&buffer, jc_valid_pane(pane) ? pane : "");
    for (int i = 0; i < count; i++) {
        append(&buffer, "\t");
        append_text(&buffer, arguments[i]);
    }
    append(&buffer, "\n");
    if (buffer.failed) {
        free(buffer.data);
        return NULL;
    }
    return buffer.data;
}

static size_t encoded_length(const char *text) { return 4 * ((strlen(text) + 2) / 3); }

size_t jc_request_length(size_t token_length, const char *cwd, const char *pane, int count, const char *const *arguments) {
    /* As jc_request: the magic and version, the token, cwd and pane fields, each argument, the newline. */
    size_t length = strlen("jasper-command\t" JC_PROTOCOL_VERSION "\t") + token_length + 1 + encoded_length(cwd) + 1
        + (jc_valid_pane(pane) ? strlen(pane) : 0) + 1;
    for (int i = 0; i < count; i++) length += 1 + encoded_length(arguments[i]);
    return length;
}

int jc_request_fits(const char *cwd, const char *pane, int count, const char *const *arguments) {
    return jc_request_length(JC_MAX_TOKEN, cwd, pane, count, arguments) <= JC_MAX_REQUEST;
}

jc_frame jc_parse_frame(const char *line) {
    jc_frame frame = { JC_FRAME_UNKNOWN, NULL, 0, 0 };
    if (strcmp(line, "stale") == 0) {
        frame.kind = JC_FRAME_STALE;
    } else if (strcmp(line, "refused") == 0) {
        frame.kind = JC_FRAME_REFUSED;
    } else if (strncmp(line, "exit\t", 5) == 0) {
        char *end;
        long code = strtol(line + 5, &end, 10);
        if (end != line + 5 && *end == '\0' && code >= 0 && code <= 255) {
            frame.kind = JC_FRAME_EXIT;
            frame.code = (int) code;
        }
    } else if (strncmp(line, "out\t", 4) == 0 || strncmp(line, "err\t", 4) == 0) {
        frame.data = jc_base64_decode(line + 4, &frame.length);
        if (frame.data != NULL) frame.kind = line[0] == 'o' ? JC_FRAME_OUT : JC_FRAME_ERR;
    }
    return frame;
}
