#define _POSIX_C_SOURCE 200809L
#include "protocol.h"
#include <locale.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int checks, failures;
static void check(int ok, const char *name) {
    checks++;
    if (!ok) { failures++; fprintf(stderr, "FAIL: %s\n", name); }
}

static void commands(void) {
    struct dc_command command;
    const char *ping = "{\"v\":1,\"kind\":\"ping\",\"generation\":9223372036854775807}";
    check(dc_read_command(ping, strlen(ping), &command) == 0 && command.kind == DC_PING && command.generation == INT64_MAX, "signed 64-bit max");
    const char *rejected[] = {
        "{\"v\":1,\"kind\":\"ping\",\"generation\":9223372036854775808}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":-1}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":1.0}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":1,\"extra\":false}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":1,\"appearance\":\"dark\"}",
        "{\"v\":1,\"kind\":\"sample\",\"generation\":1}",
        "{\"v\":1,\"kind\":\"sample\",\"generation\":1,\"appearance\":\"unknown\"}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":1,\"\\u0076\":1}",
        "{\"v\":1,\"kind\":\"pi\\u0000ng\",\"generation\":1}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":01}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":+1}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":1}{}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":1,}",
        "{\"v\":1,\"kind\":\"ping\",\"generation\":1}\001"
    };
    for (size_t i = 0; i < sizeof rejected / sizeof rejected[0]; i++)
        check(dc_read_command(rejected[i], strlen(rejected[i]), &command) == -1, rejected[i]);
    char *boundary = malloc(DC_MAX_MESSAGE + 1);
    memset(boundary, ' ', DC_MAX_MESSAGE + 1);
    memcpy(boundary, ping, strlen(ping));
    check(dc_read_command(boundary, DC_MAX_MESSAGE, &command) == 0, "64 KiB command accepted");
    check(dc_read_command(boundary, DC_MAX_MESSAGE + 1, &command) == -1, "64 KiB + 1 command rejected");
    free(boundary);
}

static char *write_palette(struct dc_palette *palette) {
    FILE *stream = tmpfile();
    check(stream != NULL, "temporary output stream");
    if (!stream) return NULL;
    int result = dc_write_palette(stream, palette);
    check(result == 0, "palette writes successfully");
    long length = ftell(stream);
    char *text = calloc((size_t) length + 1, 1);
    rewind(stream);
    check(fread(text, 1, (size_t) length, stream) == (size_t) length, "palette output read");
    fclose(stream);
    return text;
}

static void output(void) {
    struct dc_palette palette = {
        .generation = INT64_MAX, .source = DC_GTK4, .appearance = DC_DARK,
        .implementation = "GTK \"four\"\\", .version = "4.10", .reason = "\001\b\f\n\r\t"
    };
    palette.colors[DC_WINDOW] = (struct dc_colors) { .present = 1,
        .background = {0.12, 0.25, 0.5, 1}, .foreground = {1, 1, 1, 1} };
    char *text = write_palette(&palette);
    if (!text) return;
    check(strstr(text, "\"generation\":9223372036854775807") != NULL, "output retains integer generation");
    check(strstr(text, "GTK \\\"four\\\"\\\\") != NULL, "quotes and backslashes escaped");
    check(strstr(text, "\\u0001\\u0008\\u000c\\u000a\\u000d\\u0009") != NULL, "all controls escaped");
    check(strstr(text, "0.12,0.25,0.5,1") != NULL, "dot color decimals");
    check(strstr(text, "view.bg") == NULL, "missing optional pair omitted");
    check(text[strlen(text) - 1] == '\n', "newline terminates output");
    for (size_t i = 0; i + 1 < strlen(text); i++) check((unsigned char) text[i] >= 0x20, "no raw JSON controls");
    free(text);
    const char *locales[] = {"de_DE.UTF-8", "de_DE.utf8", "fr_FR.UTF-8", "fr_FR.utf8", "en_DK.UTF-8", "en_DK.utf8"};
    int locale_found = 0;
    for (size_t i = 0; i < sizeof locales / sizeof locales[0]; i++) {
        if (setlocale(LC_NUMERIC, locales[i])) { locale_found = 1; break; }
    }
    check(locale_found, "comma-decimal locale is available");
    if (locale_found) {
        check(strcmp(localeconv()->decimal_point, ",") == 0, "test process uses comma decimal locale");
        text = write_palette(&palette);
        check(text && strstr(text, "0.12,0.25,0.5,1"), "writer ignores comma decimal locale");
        check(strcmp(localeconv()->decimal_point, ",") == 0, "writer preserves caller locale");
        free(text);
    }
    setlocale(LC_NUMERIC, "C");
    palette.reason = "bad\xff";
    FILE *stream = tmpfile();
    check(dc_write_palette(stream, &palette) == -1 && ftell(stream) == 0, "malformed UTF-8 rejected before envelope write");
    fclose(stream);
}

static void vectors(const char *json_path, const char *manifest_path) {
    FILE *json = fopen(json_path, "r"), *manifest = fopen(manifest_path, "r");
    check(json && manifest, "shared vector files open");
    if (!json || !manifest) { if (json) fclose(json); if (manifest) fclose(manifest); return; }
    char *line = NULL, *entry = NULL;
    size_t line_size = 0, entry_size = 0;
    ssize_t length;
    while ((length = getline(&line, &line_size, json)) >= 0) {
        check(getline(&entry, &entry_size, manifest) >= 0, "matching manifest row");
        char id[80], java[40], native[40], appearance[20];
        long long generation;
        check(sscanf(entry, "%79s %39s %39s %lld %19s", id, java, native, &generation, appearance) == 5, "manifest fields");
        struct dc_command command;
        int result = dc_read_command(line, (size_t) length - 1, &command);
        check(result == (strcmp(native, "reject") == 0 ? -1 : 0), id);
        if (result == 0) {
            check(command.generation == generation, "vector command generation");
            check(command.kind == (strcmp(native, "ping") == 0 ? DC_PING : DC_SAMPLE), "vector command kind");
            if (command.kind == DC_SAMPLE) check(command.dark == (strcmp(appearance, "dark") == 0), "vector command appearance");
        }
    }
    check(getline(&entry, &entry_size, manifest) == -1, "no extra manifest entries");
    free(line); free(entry); fclose(json); fclose(manifest);
}

int main(int argc, char **argv) {
    if (argc != 3) { fprintf(stderr, "usage: protocol-test vectors.jsonl vectors.tsv\n"); return 2; }
    commands(); output(); vectors(argv[1], argv[2]);
    printf("desktop protocol: %d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
