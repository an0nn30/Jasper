/*
 * Native tests of jasper_cli_core (packaging/cli/README.md).
 * Usage: cli-core-test <home-vectors.txt> <protocol-vectors.txt>
 */
#include "jasper_cli_core.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int checks;
static int failures;

static void check(int passed, const char *what) {
    checks++;
    if (!passed) {
        failures++;
        printf("FAILED: %s\n", what);
    }
}

static void check_text(const char *actual, const char *expected, const char *what) {
    checks++;
    if (actual == NULL || strcmp(actual, expected) != 0) {
        failures++;
        printf("FAILED: %s: expected \"%s\", got \"%s\"\n", what, expected, actual == NULL ? "(null)" : actual);
    }
}

/* Copies value into out, cut to fit. */
static void set(char *out, size_t size, const char *value) {
    size_t length = strlen(value);
    if (length >= size) length = size - 1;
    memcpy(out, value, length);
    out[length] = '\0';
}

static void base64(void) {
    static const char *const plain[] = { "", "f", "fo", "foo", "foob", "fooba", "foobar", "hello \xc3\xbc\n" };
    static const char *const encoded[] = { "", "Zg==", "Zm8=", "Zm9v", "Zm9vYg==", "Zm9vYmE=", "Zm9vYmFy", "aGVsbG8gw7wK" };
    static const char *const malformed[] = { "abc", "ab=c", "a===", "@@@@", "Zg==Zg==", "Z===" };
    for (size_t i = 0; i < sizeof plain / sizeof plain[0]; i++) {
        size_t length = 0;
        char *text = jc_base64_encode((const unsigned char *) plain[i], strlen(plain[i]));
        unsigned char *data = jc_base64_decode(encoded[i], &length);
        check_text(text, encoded[i], "base64 encoding");
        check(data != NULL && length == strlen(plain[i]) && memcmp(data, plain[i], length) == 0, encoded[i]);
        free(text);
        free(data);
    }
    for (size_t i = 0; i < sizeof malformed / sizeof malformed[0]; i++) {
        size_t length = 0;
        unsigned char *data = jc_base64_decode(malformed[i], &length);
        check(data == NULL, malformed[i]);
        free(data);
    }
}

static void normalize(void) {
    static const char *const posix[][2] = {
        { "/", "/" }, { "/a/b", "/a/b" }, { "/a//b/", "/a/b" }, { "/a/./b/../c", "/a/c" }, { "/..", "/" },
        { "/a/../..", "/" },
    };
    static const char *const windows[][2] = {
        { "C:\\", "C:\\" }, { "C:/a/b", "C:\\a\\b" }, { "c:\\a\\..\\b", "c:\\b" },
        { "\\\\server\\share\\x", "\\\\server\\share\\x" },
    };
    char out[JC_MAX_PATH];
    for (size_t i = 0; i < sizeof posix / sizeof posix[0]; i++) {
        check(jc_normalize(JC_OS_LINUX, posix[i][0], out, sizeof out) == 0, posix[i][0]);
        check_text(out, posix[i][1], posix[i][0]);
    }
    for (size_t i = 0; i < sizeof windows / sizeof windows[0]; i++) {
        check(jc_normalize(JC_OS_WINDOWS, windows[i][0], out, sizeof out) == 0, windows[i][0]);
        check_text(out, windows[i][1], windows[i][0]);
    }
    check(jc_normalize(JC_OS_LINUX, "relative", out, sizeof out) == -1, "a relative path");
    check(jc_normalize(JC_OS_WINDOWS, "C:relative", out, sizeof out) == -1, "a drive-relative path");
    check(jc_normalize(JC_OS_LINUX, "/abcdef", out, 4) == -1, "a result that does not fit");
}

static void parent(void) {
    char out[JC_MAX_PATH];
    check(jc_parent(JC_OS_LINUX, "/a/b/commands", out, sizeof out) == 0, "a parent");
    check_text(out, "/a/b", "a parent");
    check(jc_parent(JC_OS_LINUX, "/commands", out, sizeof out) == 0, "the root");
    check_text(out, "/", "the root");
    check(jc_parent(JC_OS_WINDOWS, "C:\\x\\commands", out, sizeof out) == 0, "a Windows parent");
    check_text(out, "C:\\x", "a Windows parent");
    check(jc_parent(JC_OS_WINDOWS, "C:\\commands", out, sizeof out) == 0, "a drive's root");
    check_text(out, "C:\\", "a drive's root");
    check(jc_parent(JC_OS_LINUX, "commands", out, sizeof out) == -1, "no folder");
}

static void panes(void) {
    check(jc_valid_pane("123e4567-e89b-12d3-a456-426614174000"), "a UUID");
    check(!jc_valid_pane(""), "empty");
    check(!jc_valid_pane(NULL), "unset");
    check(!jc_valid_pane("123e4567e89b12d3a456426614174000"), "no dashes");
    check(!jc_valid_pane("123e4567-e89b-12d3-a456-42661417400g"), "not hexadecimal");
}

static void frames(void) {
    jc_frame frame = jc_parse_frame("out\taGVsbG8gw7wK");
    check(frame.kind == JC_FRAME_OUT && frame.length == 9 && memcmp(frame.data, "hello \xc3\xbc\n", 9) == 0, "an out frame");
    free(frame.data);
    frame = jc_parse_frame("err\t");
    check(frame.kind == JC_FRAME_ERR && frame.length == 0, "an empty err frame");
    free(frame.data);
    frame = jc_parse_frame("exit\t3");
    check(frame.kind == JC_FRAME_EXIT && frame.code == 3, "an exit frame");
    check(jc_parse_frame("exit\t256").kind == JC_FRAME_UNKNOWN, "an exit code out of range");
    check(jc_parse_frame("exit\t-1").kind == JC_FRAME_UNKNOWN, "a negative exit code");
    check(jc_parse_frame("exit\t").kind == JC_FRAME_UNKNOWN, "an exit frame without a code");
    check(jc_parse_frame("stale").kind == JC_FRAME_STALE, "stale");
    check(jc_parse_frame("refused").kind == JC_FRAME_REFUSED, "refused");
    check(jc_parse_frame("out\t@@").kind == JC_FRAME_UNKNOWN, "bad base64");
    check(jc_parse_frame("bogus").kind == JC_FRAME_UNKNOWN, "an unknown frame");
}

static const char *pairs(const char *name, void *context) {
    const char *const *pair = context;
    for (; pair[0] != NULL; pair += 2) if (strcmp(pair[0], name) == 0) return pair[1];
    return NULL;
}

/* Rules the shared vectors cannot express: the application never sees a relative or padded JASPER_HOME. */
static void home_rules(void) {
    char out[JC_MAX_PATH];
    const char *relative[] = { "JASPER_HOME", "rel/dir", NULL };
    const char *padded[] = { "JASPER_HOME", "  /srv/j  ", NULL };
    const char *blank_socket[] = { "JASPER_COMMAND_SOCKET", " ", NULL };
    const char *windows_relative[] = { "JASPER_HOME", "rel", NULL };
    const char *none[] = { NULL };
    check(jc_socket_path(JC_OS_LINUX, pairs, relative, "/home/ann", "/tmp/w", out, sizeof out) == 0, "a relative home");
    check_text(out, "/tmp/w/rel/dir/daemon/commands", "a relative home resolves from the working directory");
    check(jc_socket_path(JC_OS_LINUX, pairs, padded, "/home/ann", "/tmp", out, sizeof out) == 0, "a padded home");
    check_text(out, "/srv/j/daemon/commands", "surrounding spaces are not part of the home");
    check(jc_socket_path(JC_OS_LINUX, pairs, blank_socket, "/home/ann", "/tmp", out, sizeof out) == 0, "a blank socket");
    check_text(out, "/home/ann/.config/jasper/daemon/commands", "a blank JASPER_COMMAND_SOCKET is ignored");
    check(jc_socket_path(JC_OS_WINDOWS, pairs, windows_relative, "C:\\Users\\ann", "C:\\w", out, sizeof out) == 0, "a relative Windows home");
    check_text(out, "C:\\w\\rel\\daemon\\commands", "a relative Windows home");
    check(jc_socket_path(JC_OS_LINUX, pairs, none, "/home/ann", "/tmp", out, 10) == -1, "a socket path that does not fit");
}

static char *trim_line(char *line) {
    line[strcspn(line, "\r\n")] = '\0';
    return line;
}

/* Splits a line in place at each separator; returns the field count, or max + 1 when there are more. */
static int split(char *line, char separator, char **fields, int max) {
    int count = 0;
    fields[count++] = line;
    for (char *c = line; *c != '\0'; c++) {
        if (*c != separator) continue;
        *c = '\0';
        if (count == max) return max + 1;
        fields[count++] = c + 1;
    }
    return count;
}

typedef struct {
    const char *names[4];
    const char *values[4];
} vector_environment;

static const char *vector_lookup(const char *name, void *context) {
    vector_environment *environment = context;
    for (int i = 0; i < 4; i++) if (strcmp(environment->names[i], name) == 0) return environment->values[i];
    return NULL;
}

static const char *vector_value(const char *field) {
    if (strcmp(field, "-") == 0) return NULL;
    if (strcmp(field, "_") == 0) return " ";
    return field;
}

static void home_vectors(const char *path) {
    FILE *file = fopen(path, "rb");
    char line[4096];
    int seen = 0;
    if (file == NULL) {
        check(0, path);
        return;
    }
    while (fgets(line, sizeof line, file) != NULL) {
        char *fields[9];
        char out[JC_MAX_PATH];
        trim_line(line);
        if (line[0] == '\0' || line[0] == '#') continue;
        if (split(line, '|', fields, 8) != 8) {
            check(0, "a home vector has eight fields");
            continue;
        }
        jc_os os = strcmp(fields[0], "mac") == 0 ? JC_OS_MAC : strcmp(fields[0], "linux") == 0 ? JC_OS_LINUX : JC_OS_WINDOWS;
        vector_environment environment = {
            { "JASPER_COMMAND_SOCKET", "JASPER_HOME", "APPDATA", "XDG_CONFIG_HOME" },
            { vector_value(fields[1]), vector_value(fields[2]), vector_value(fields[3]), vector_value(fields[4]) } };
        int status = jc_socket_path(os, vector_lookup, &environment, fields[5], fields[6], out, sizeof out);
        for (char *c = out; status == 0 && *c != '\0'; c++) if (*c == '\\') *c = '/';
        check(status == 0, fields[7]);
        if (status == 0) check_text(out, fields[7], "a home vector");
        seen++;
    }
    fclose(file);
    check(seen > 0, "home vectors were read");
}

static void protocol_vectors(const char *path) {
    FILE *file = fopen(path, "rb");
    char line[4096], token[256] = "", cwd[1024] = "", pane[64] = "";
    char arguments[16][256];
    const char *pointers[16];
    int count = 0, seen = 0;
    if (file == NULL) {
        check(0, path);
        return;
    }
    while (fgets(line, sizeof line, file) != NULL) {
        char *equals;
        trim_line(line);
        if (line[0] == '\0' || line[0] == '#') continue;
        equals = strchr(line, '=');
        if (equals == NULL) {
            check(0, "a protocol vector line has a key");
            continue;
        }
        *equals = '\0';
        const char *value = equals + 1;
        if (strcmp(line, "token") == 0) set(token, sizeof token, value);
        else if (strcmp(line, "cwd") == 0) set(cwd, sizeof cwd, value);
        else if (strcmp(line, "pane") == 0) set(pane, sizeof pane, value);
        else if (strcmp(line, "arg") == 0 && count < 16) {
            set(arguments[count], sizeof arguments[count], value);
            pointers[count] = arguments[count];
            count++;
        } else if (strcmp(line, "expect") == 0) {
            char expected[4096], copy[4096];
            char *fields[64];
            set(copy, sizeof copy, value);
            int found = split(copy, ' ', fields, 63);
            expected[0] = '\0';
            for (int i = 0; i < found && i < 63; i++) {
                if (i > 0) strcat(expected, "\t");
                if (strcmp(fields[i], "-") != 0) strcat(expected, fields[i]);
            }
            strcat(expected, "\n");
            char *request = jc_request(token, cwd, pane, count, pointers);
            check_text(request, expected, "a protocol vector");
            free(request);
            count = 0;
            seen++;
        } else {
            check(0, "an unknown protocol vector key");
        }
    }
    fclose(file);
    check(seen > 0, "protocol vectors were read");
}

int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "usage: cli-core-test <home-vectors.txt> <protocol-vectors.txt>\n");
        return 64;
    }
    base64();
    normalize();
    parent();
    panes();
    frames();
    home_rules();
    home_vectors(argv[1]);
    protocol_vectors(argv[2]);
    printf("%d checks, %d failures\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
