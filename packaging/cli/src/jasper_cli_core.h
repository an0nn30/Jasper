/*
 * Pure logic of the jasper command (packaging/cli/README.md): base64, where the command socket is, the request line
 * and the reply frames. No I/O and no state; jasper_cli.c does the rest.
 */
#ifndef JASPER_CLI_CORE_H
#define JASPER_CLI_CORE_H

#include <stddef.h>

/* The protocol version of the request line; dev.jasper.app.commandline.CommandProtocol.VERSION. */
#define JC_PROTOCOL_VERSION "1"
/* The longest path the client handles, terminator included. */
#define JC_MAX_PATH 4096

typedef enum { JC_OS_MAC, JC_OS_LINUX, JC_OS_WINDOWS } jc_os;

/* An environment variable as UTF-8, or NULL when it is unset. */
typedef const char *(*jc_lookup)(const char *name, void *context);

/* Standard base64 with padding, in a malloc'd string; NULL when memory runs out. */
char *jc_base64_encode(const unsigned char *data, size_t length);

/* Decodes standard base64 with padding into a malloc'd buffer that is also NUL-terminated; NULL when malformed. */
unsigned char *jc_base64_decode(const char *text, size_t *length);

/*
 * An absolute path without "." and ".." segments or repeated separators. Windows paths take either separator and are
 * written with "\". Returns 0, or -1 when the path is relative or the result does not fit.
 */
int jc_normalize(jc_os os, const char *path, char *out, size_t size);

/*
 * The command socket, placed as Jasper's AppDirs places it: JASPER_COMMAND_SOCKET when set; otherwise
 * <home>/daemon/commands with the home from JASPER_HOME (resolved from cwd when relative), else the OS default
 * (%APPDATA%\jasper, ~/.config/jasper on macOS, $XDG_CONFIG_HOME/jasper or ~/.config/jasper on Linux). Returns 0,
 * or -1 when it does not fit.
 */
int jc_socket_path(jc_os os, jc_lookup lookup, void *context, const char *home, const char *cwd, char *out, size_t size);

/* The folder a path is in; the root for a file in the root. Returns 0, or -1 when there is none or it does not fit. */
int jc_parent(jc_os os, const char *path, char *out, size_t size);

/* Whether a pane id has a UUID's shape; anything else is sent as no pane. */
int jc_valid_pane(const char *pane);

/*
 * Whether a NAME=value environment entry is passed to a Jasper started because nothing listens: every entry but those
 * whose name starts with GIT_ (git sets them for the editor it runs), JASPER_PANE and JASPER_COMMAND_SOCKET. An entry
 * without "=" is kept.
 */
int jc_cold_start_keeps(const char *entry);

/* The request line, newline included, in a malloc'd string; NULL when memory runs out. An invalid pane is sent empty. */
char *jc_request(const char *token, const char *cwd, const char *pane, int count, const char *const *arguments);

typedef enum { JC_FRAME_OUT, JC_FRAME_ERR, JC_FRAME_EXIT, JC_FRAME_STALE, JC_FRAME_REFUSED, JC_FRAME_UNKNOWN } jc_frame_kind;

/* A reply frame. OUT and ERR carry malloc'd data the caller frees; EXIT carries its code. */
typedef struct {
    jc_frame_kind kind;
    unsigned char *data;
    size_t length;
    int code;
} jc_frame;

/* Parses one reply line without its newline; anything unrecognised is JC_FRAME_UNKNOWN. */
jc_frame jc_parse_frame(const char *line);

#endif
