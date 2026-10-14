/*
 * The jasper command (packaging/cli/README.md). It hands each call to the running Jasper through its command socket
 * and relays the reply, starting Jasper first when nothing answers; it never starts a JVM of its own.
 */
#include "jasper_cli_core.h"

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <winsock2.h>
#include <afunix.h>
#include <windows.h>
#include <direct.h>
#include <fcntl.h>
#include <io.h>
#include <wchar.h>
typedef SOCKET jc_socket;
typedef int jc_length;
#define JC_NO_SOCKET INVALID_SOCKET
#define close_socket closesocket
#else
#include <fcntl.h>
#include <pwd.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#ifdef __APPLE__
#include <mach-o/dyld.h>
#include <spawn.h>
#endif
extern char **environ;
typedef int jc_socket;
typedef socklen_t jc_length;
#define JC_NO_SOCKET (-1)
#define close_socket close
#endif

#if defined(_WIN32)
#define HOST JC_OS_WINDOWS
#define SEPARATOR '\\'
#elif defined(__APPLE__)
#define HOST JC_OS_MAC
#define SEPARATOR '/'
#else
#define HOST JC_OS_LINUX
#define SEPARATOR '/'
#endif

#define START_WAIT_MILLIS 20000
#define POLL_MILLIS 50
#define TOKEN_WAIT_MILLIS 1000
#define MAX_OPTIONS 64

static const char USAGE[] =
    "Usage: jasper [--config <path>] [--safe-mode] [--plugin-dir <path>] [--standalone]\n"
    "       jasper COMMAND [ARGUMENTS...]\n"
    "       jasper --help\n"
    "\n"
    "With no arguments, opens a Jasper window; with options, starts Jasper with them. With a COMMAND, runs it in\n"
    "the running Jasper, starting Jasper first when it is not running.\n";

static void pause_millis(int millis) {
#ifdef _WIN32
    Sleep((DWORD) millis);
#else
    struct timespec delay;
    delay.tv_sec = millis / 1000;
    delay.tv_nsec = (long) (millis % 1000) * 1000000L;
    while (nanosleep(&delay, &delay) != 0 && errno == EINTR) { }
#endif
}

#ifdef _WIN32
static char *utf8_of(const wchar_t *wide) {
    int size = WideCharToMultiByte(CP_UTF8, 0, wide, -1, NULL, 0, NULL, NULL);
    char *text;
    if (size <= 0) return NULL;
    text = malloc((size_t) size);
    if (text != NULL) WideCharToMultiByte(CP_UTF8, 0, wide, -1, text, size, NULL, NULL);
    return text;
}

static wchar_t *wide_of(const char *text) {
    int size = MultiByteToWideChar(CP_UTF8, 0, text, -1, NULL, 0);
    wchar_t *wide;
    if (size <= 0) return NULL;
    wide = malloc((size_t) size * sizeof(wchar_t));
    if (wide != NULL) MultiByteToWideChar(CP_UTF8, 0, text, -1, wide, size);
    return wide;
}
#endif

/* An environment variable as UTF-8, or NULL when unset. The strings live until the program exits. */
static const char *variable(const char *name, void *context) {
    (void) context;
#ifdef _WIN32
    {
        wchar_t *wide = wide_of(name);
        const wchar_t *value = wide == NULL ? NULL : _wgetenv(wide);
        free(wide);
        return value == NULL ? NULL : utf8_of(value);
    }
#else
    return getenv(name);
#endif
}

static FILE *open_file(const char *path, const char *mode) {
#ifdef _WIN32
    wchar_t *wide_path = wide_of(path), *wide_mode = wide_of(mode);
    FILE *file = wide_path == NULL || wide_mode == NULL ? NULL : _wfopen(wide_path, wide_mode);
    free(wide_path);
    free(wide_mode);
    return file;
#else
    return fopen(path, mode);
#endif
}

static const char *home_folder(void) {
#ifdef _WIN32
    const char *profile = variable("USERPROFILE", NULL);
    return profile != NULL && profile[0] != '\0' ? profile : "C:\\";
#else
    /* As Java's user.home: the password database first. */
    struct passwd *entry = getpwuid(getuid());
    const char *home;
    if (entry != NULL && entry->pw_dir != NULL && entry->pw_dir[0] != '\0') return entry->pw_dir;
    home = getenv("HOME");
    return home != NULL && home[0] != '\0' ? home : "/";
#endif
}

static const char *working_folder(void) {
    static char folder[JC_MAX_PATH];
#ifdef _WIN32
    wchar_t wide[JC_MAX_PATH];
    char *text;
    if (_wgetcwd(wide, JC_MAX_PATH) == NULL) return "C:\\";
    text = utf8_of(wide);
    if (text == NULL || strlen(text) >= sizeof folder) {
        free(text);
        return "C:\\";
    }
    memcpy(folder, text, strlen(text) + 1);
    free(text);
    return folder;
#else
    return getcwd(folder, sizeof folder) != NULL ? folder : "/";
#endif
}

/* Writes text to standard output or error; a Windows console gets it as UTF-16 so any letter shows. */
static void emit(FILE *stream, const void *data, size_t length) {
#ifdef _WIN32
    HANDLE handle = GetStdHandle(stream == stdout ? STD_OUTPUT_HANDLE : STD_ERROR_HANDLE);
    DWORD mode;
    if (length > 0 && length < 0x7fffffff && GetConsoleMode(handle, &mode)) {
        int size = MultiByteToWideChar(CP_UTF8, 0, data, (int) length, NULL, 0);
        wchar_t *wide = size > 0 ? malloc((size_t) size * sizeof(wchar_t)) : NULL;
        if (wide != NULL) {
            DWORD written;
            MultiByteToWideChar(CP_UTF8, 0, data, (int) length, wide, size);
            WriteConsoleW(handle, wide, (DWORD) size, &written, NULL);
            free(wide);
            return;
        }
    }
#endif
    fwrite(data, 1, length, stream);
    fflush(stream);
}

/* Removes the last path component in place; 0 when there was one. */
static int up(char *path) {
    char *last = strrchr(path, SEPARATOR);
    if (last == NULL || last == path) return -1;
    *last = '\0';
    return 0;
}

/*
 * The app image this program belongs to, from its own path with links resolved. On macOS bundle is Jasper.app
 * (this program is Contents/Resources/bin/jasper); elsewhere it is the image root (lib/bin/jasper on Linux,
 * bin\jasper.exe on Windows). launcher is the image's launcher. 0 when the launcher exists.
 */
static int find_app(char *bundle, char *launcher) {
#if defined(_WIN32)
    wchar_t wide[JC_MAX_PATH];
    char *self;
    wchar_t *target;
    DWORD attributes;
    DWORD length = GetModuleFileNameW(NULL, wide, JC_MAX_PATH);
    if (length == 0 || length >= JC_MAX_PATH) return -1;
    self = utf8_of(wide);
    if (self == NULL || strlen(self) >= JC_MAX_PATH) {
        free(self);
        return -1;
    }
    memcpy(bundle, self, strlen(self) + 1);
    free(self);
    if (up(bundle) != 0 || up(bundle) != 0) return -1;
    if (snprintf(launcher, JC_MAX_PATH, "%s\\Jasper.exe", bundle) >= JC_MAX_PATH) return -1;
    target = wide_of(launcher);
    attributes = target == NULL ? INVALID_FILE_ATTRIBUTES : GetFileAttributesW(target);
    free(target);
    return attributes == INVALID_FILE_ATTRIBUTES || (attributes & FILE_ATTRIBUTE_DIRECTORY) != 0 ? -1 : 0;
#else
    char self[JC_MAX_PATH];
#if defined(__APPLE__)
    char invoked[JC_MAX_PATH];
    uint32_t size = sizeof invoked;
    if (_NSGetExecutablePath(invoked, &size) != 0 || realpath(invoked, self) == NULL) return -1;
    memcpy(bundle, self, strlen(self) + 1);
    if (up(bundle) != 0 || up(bundle) != 0 || up(bundle) != 0 || up(bundle) != 0) return -1;
    if (snprintf(launcher, JC_MAX_PATH, "%s/Contents/MacOS/Jasper", bundle) >= JC_MAX_PATH) return -1;
#else
    ssize_t length = readlink("/proc/self/exe", self, sizeof self - 1);
    if (length <= 0) return -1;
    self[length] = '\0';
    memcpy(bundle, self, (size_t) length + 1);
    if (up(bundle) != 0 || up(bundle) != 0 || up(bundle) != 0) return -1;
    if (snprintf(launcher, JC_MAX_PATH, "%s/bin/Jasper", bundle) >= JC_MAX_PATH) return -1;
#endif
    return access(launcher, X_OK) == 0 ? 0 : -1;
#endif
}

#if defined(_WIN32)
static int put(wchar_t *line, size_t size, size_t *used, wchar_t c) {
    if (*used + 2 > size) return -1;
    line[(*used)++] = c;
    line[*used] = L'\0';
    return 0;
}

/* Appends one argument to a command line, quoted the way CommandLineToArgvW reads it back. */
static int append_argument(wchar_t *line, size_t size, const wchar_t *argument) {
    size_t used = wcslen(line);
    if (used > 0 && put(line, size, &used, L' ') != 0) return -1;
    if (put(line, size, &used, L'"') != 0) return -1;
    for (const wchar_t *c = argument;; c++) {
        size_t backslashes = 0;
        while (*c == L'\\') {
            backslashes++;
            c++;
        }
        if (*c == L'\0') {
            for (size_t i = 0; i < backslashes * 2; i++) if (put(line, size, &used, L'\\') != 0) return -1;
            break;
        }
        if (*c == L'"') {
            for (size_t i = 0; i < backslashes * 2 + 1; i++) if (put(line, size, &used, L'\\') != 0) return -1;
        } else {
            for (size_t i = 0; i < backslashes; i++) if (put(line, size, &used, L'\\') != 0) return -1;
        }
        if (put(line, size, &used, *c) != 0) return -1;
    }
    return put(line, size, &used, L'"');
}

/* Starts a program detached, passing it the right to come to the front. 0 when it started. */
static int start_process(const char *program, int count, const char *const *arguments) {
    size_t size = 32768;
    wchar_t *line = calloc(size, sizeof(wchar_t));
    wchar_t *wide_program = wide_of(program);
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    int ok = line != NULL && wide_program != NULL && append_argument(line, size, wide_program) == 0;
    for (int i = 0; ok && i < count; i++) {
        wchar_t *wide = wide_of(arguments[i]);
        ok = wide != NULL && append_argument(line, size, wide) == 0;
        free(wide);
    }
    memset(&startup, 0, sizeof startup);
    startup.cb = sizeof startup;
    if (ok) {
        ok = CreateProcessW(wide_program, line, NULL, NULL, FALSE, DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP, NULL, NULL,
            &startup, &process) != 0;
    }
    if (ok) {
        /* Only this process may pass on the right to come to the front; Jasper shows its window later. */
        AllowSetForegroundWindow(process.dwProcessId);
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
    }
    free(line);
    free(wide_program);
    return ok ? 0 : -1;
}
#elif defined(__APPLE__)
/* Runs a program and waits for it; 0 when it exited with 0. */
static int run_and_wait(char *const arguments[]) {
    pid_t child;
    int status;
    if (posix_spawn(&child, arguments[0], NULL, NULL, arguments, environ) != 0) return -1;
    while (waitpid(child, &status, 0) < 0) if (errno != EINTR) return -1;
    return WIFEXITED(status) && WEXITSTATUS(status) == 0 ? 0 : -1;
}
#else
/* Starts a program away from this terminal: its own session, standard streams on /dev/null. 0 when it started. */
static int spawn_detached(char *const arguments[]) {
    pid_t child = fork();
    if (child < 0) return -1;
    if (child == 0) {
        int nothing;
        setsid();
        nothing = open("/dev/null", O_RDWR);
        if (nothing >= 0) {
            dup2(nothing, 0);
            dup2(nothing, 1);
            dup2(nothing, 2);
            if (nothing > 2) close(nothing);
        }
        execv(arguments[0], arguments);
        _exit(127);
    }
    return 0;
}
#endif

/* Starts Jasper with these options, detached; new_instance asks macOS for a new process. 0 when it started. */
static int start_jasper(int count, const char *const *options, int new_instance) {
    char bundle[JC_MAX_PATH], launcher[JC_MAX_PATH];
    int started = -1;
    if (count > MAX_OPTIONS) return -1;
    if (find_app(bundle, launcher) != 0) {
        fputs("jasper: can't find Jasper; reinstall the command from Settings\n", stderr);
        return -1;
    }
#if defined(_WIN32)
    (void) new_instance;
    started = start_process(launcher, count, options);
#elif defined(__APPLE__)
    {
        /* LaunchServices, so Jasper is not this shell's child. It passes none of this environment, so JASPER_HOME goes along. */
        static char home[JC_MAX_PATH + 16];
        char *arguments[MAX_OPTIONS + 10];
        const char *override = getenv("JASPER_HOME");
        int n = 0;
        arguments[n++] = "/usr/bin/open";
        arguments[n++] = new_instance ? "-n" : "-g";
        arguments[n++] = "-a";
        arguments[n++] = bundle;
        if (override != NULL && override[0] != '\0' && snprintf(home, sizeof home, "JASPER_HOME=%s", override) < (int) sizeof home) {
            arguments[n++] = "--env";
            arguments[n++] = home;
        }
        arguments[n++] = "--args";
        for (int i = 0; i < count; i++) arguments[n++] = (char *) options[i];
        arguments[n] = NULL;
        started = run_and_wait(arguments);
    }
#else
    {
        char *arguments[MAX_OPTIONS + 2];
        int n = 0;
        (void) new_instance;
        arguments[n++] = launcher;
        for (int i = 0; i < count; i++) arguments[n++] = (char *) options[i];
        arguments[n] = NULL;
        started = spawn_detached(arguments);
    }
#endif
    if (started != 0) fputs("jasper: could not start Jasper\n", stderr);
    return started;
}

/* Connects to the socket. On failure *nothing_listens says whether starting Jasper could help. */
static jc_socket connect_to(const char *path, int *nothing_listens) {
    struct sockaddr_un address;
    size_t length = strlen(path);
    jc_socket client;
    *nothing_listens = 0;
    memset(&address, 0, sizeof address);
    address.sun_family = AF_UNIX;
    if (length >= sizeof address.sun_path) return JC_NO_SOCKET;
    memcpy(address.sun_path, path, length + 1);
    client = socket(AF_UNIX, SOCK_STREAM, 0);
    if (client == JC_NO_SOCKET) return JC_NO_SOCKET;
    if (connect(client, (struct sockaddr *) &address, (jc_length) sizeof address) == 0) return client;
#ifdef _WIN32
    *nothing_listens = 1;
#else
    *nothing_listens = errno == ENOENT || errno == ECONNREFUSED;
#endif
    close_socket(client);
    return JC_NO_SOCKET;
}

static int send_all(jc_socket client, const char *data, size_t length) {
    while (length > 0) {
#ifdef _WIN32
        int sent = send(client, data, length > 65536 ? 65536 : (int) length, 0);
#else
        ssize_t sent = send(client, data, length, 0);
#endif
        if (sent <= 0) return -1;
        data += sent;
        length -= (size_t) sent;
    }
    return 0;
}

/* Reads the token beside the socket, waiting briefly for a Jasper that is still writing it. 0 when read. */
static int read_token(const char *socket_path, char *token, size_t size) {
    char path[JC_MAX_PATH];
    if (snprintf(path, sizeof path, "%s.token", socket_path) >= (int) sizeof path) return -1;
    for (int waited = 0;; waited += POLL_MILLIS) {
        FILE *file = open_file(path, "rb");
        if (file != NULL) {
            size_t length = fread(token, 1, size - 1, file);
            fclose(file);
            while (length > 0 && (token[length - 1] == '\n' || token[length - 1] == '\r' || token[length - 1] == ' ')) length--;
            token[length] = '\0';
            if (length > 0) return 0;
        }
        if (waited >= TOKEN_WAIT_MILLIS) return -1;
        pause_millis(POLL_MILLIS);
    }
}

/* On Windows, lets the owner of the socket bring its window to the front. */
static void allow_foreground(const char *socket_path) {
#ifdef _WIN32
    char path[JC_MAX_PATH], text[32];
    FILE *file;
    size_t length;
    if (snprintf(path, sizeof path, "%s.token.pid", socket_path) >= (int) sizeof path) return;
    file = open_file(path, "rb");
    if (file == NULL) return;
    length = fread(text, 1, sizeof text - 1, file);
    fclose(file);
    text[length] = '\0';
    {
        unsigned long pid = strtoul(text, NULL, 10);
        if (pid > 0 && pid < 0xFFFFFFFFUL) AllowSetForegroundWindow((DWORD) pid);
    }
#else
    (void) socket_path;
#endif
}

/* Relays reply frames until the exit frame; returns the exit code. */
static int relay(jc_socket client) {
    size_t capacity = 64 * 1024, used = 0;
    char *buffer = malloc(capacity);
    if (buffer == NULL) return 1;
    for (;;) {
        char *start = buffer, *newline;
        size_t rest;
        if (used == capacity) {
            char *grown = realloc(buffer, capacity * 2);
            if (grown == NULL) break;
            buffer = grown;
            capacity *= 2;
            start = buffer;
        }
#ifdef _WIN32
        {
            int got = recv(client, buffer + used, (int) (capacity - used > 0x7fffffff ? 0x7fffffff : capacity - used), 0);
            if (got <= 0) break;
            used += (size_t) got;
        }
#else
        {
            ssize_t got = recv(client, buffer + used, capacity - used, 0);
            if (got <= 0) break;
            used += (size_t) got;
        }
#endif
        while ((newline = memchr(start, '\n', used - (size_t) (start - buffer))) != NULL) {
            jc_frame frame;
            *newline = '\0';
            frame = jc_parse_frame(start);
            start = newline + 1;
            switch (frame.kind) {
                case JC_FRAME_OUT:
                    emit(stdout, frame.data, frame.length);
                    free(frame.data);
                    break;
                case JC_FRAME_ERR:
                    emit(stderr, frame.data, frame.length);
                    free(frame.data);
                    break;
                case JC_FRAME_EXIT:
                    free(buffer);
                    return frame.code;
                case JC_FRAME_STALE:
                    free(buffer);
                    fputs("jasper: Jasper was updated; quit it completely and try again\n", stderr);
                    return 1;
                case JC_FRAME_REFUSED:
                    free(buffer);
                    fputs("jasper: Jasper refused the connection\n", stderr);
                    return 1;
                default:
                    /* A frame from a newer Jasper this client does not know: skipped. */
                    break;
            }
        }
        rest = used - (size_t) (start - buffer);
        memmove(buffer, start, rest);
        used = rest;
    }
    free(buffer);
    fputs("jasper: Jasper quit before the command finished\n", stderr);
    return 1;
}

static void report_no_start(const char *socket_path) {
    char daemon[JC_MAX_PATH], root[JC_MAX_PATH];
    if (jc_parent(HOST, socket_path, daemon, sizeof daemon) == 0 && jc_parent(HOST, daemon, root, sizeof root) == 0)
        fprintf(stderr, "jasper: Jasper didn't start; see %s%clogs\n", root, SEPARATOR);
    else
        fputs("jasper: Jasper didn't start\n", stderr);
}

static int help(const char *socket_path) {
    static const char none[] = "\nStart Jasper once to list the plugin commands.\n";
    static const char heading[] = "\nCommands:\n";
    char folder[JC_MAX_PATH], list[JC_MAX_PATH], line[2048];
    FILE *file = NULL;
    emit(stdout, USAGE, strlen(USAGE));
    if (jc_parent(HOST, socket_path, folder, sizeof folder) == 0
            && snprintf(list, sizeof list, "%s%csubcommands", folder, SEPARATOR) < (int) sizeof list)
        file = open_file(list, "rb");
    if (file == NULL) {
        emit(stdout, none, strlen(none));
        return 0;
    }
    emit(stdout, heading, strlen(heading));
    while (fgets(line, sizeof line, file) != NULL) {
        char text[2200];
        char *summary, *plugin;
        int length;
        line[strcspn(line, "\r\n")] = '\0';
        summary = strchr(line, '\t');
        if (summary == NULL) continue;
        *summary++ = '\0';
        plugin = strchr(summary, '\t');
        if (plugin != NULL) *plugin = '\0';
        length = snprintf(text, sizeof text, "  %-12s%s\n", line, summary);
        if (length > 0) emit(stdout, text, (size_t) length < sizeof text ? (size_t) length : sizeof text - 1);
    }
    fclose(file);
    return 0;
}

static int call(const char *socket_path, int count, const char *const *arguments) {
    char token[256];
    const char *pane;
    char *request;
    int nothing_listens, code;
    jc_socket client;
    if (strlen(socket_path) >= sizeof(((struct sockaddr_un *) 0)->sun_path)) {
        fprintf(stderr, "jasper: Jasper's command socket path is too long (%s); set JASPER_HOME to a shorter folder\n",
            socket_path);
        return 1;
    }
    client = connect_to(socket_path, &nothing_listens);
    if (client == JC_NO_SOCKET && !nothing_listens) {
        fprintf(stderr, "jasper: can't reach Jasper at %s\n", socket_path);
        return 1;
    }
    if (client == JC_NO_SOCKET) {
        static const char *const cold[] = { "--no-window" };
        if (start_jasper(1, cold, 0) != 0) return 1;
        for (int waited = 0; client == JC_NO_SOCKET && waited < START_WAIT_MILLIS; waited += POLL_MILLIS) {
            pause_millis(POLL_MILLIS);
            client = connect_to(socket_path, &nothing_listens);
        }
        if (client == JC_NO_SOCKET) {
            report_no_start(socket_path);
            return 1;
        }
    }
    if (read_token(socket_path, token, sizeof token) != 0) {
        close_socket(client);
        fputs("jasper: Jasper refused the connection\n", stderr);
        return 1;
    }
    allow_foreground(socket_path);
    pane = variable("JASPER_PANE", NULL);
    request = jc_request(token, working_folder(), pane == NULL ? "" : pane, count, arguments);
    if (request == NULL || send_all(client, request, strlen(request)) != 0) {
        fputs("jasper: Jasper quit before the command finished\n", stderr);
        code = 1;
    } else {
        code = relay(client);
    }
    free(request);
    close_socket(client);
    return code;
}

static int run(int count, const char *const *arguments) {
    char socket_path[JC_MAX_PATH];
    if (jc_socket_path(HOST, variable, NULL, home_folder(), working_folder(), socket_path, sizeof socket_path) != 0) {
        fputs("jasper: Jasper's home folder path is too long\n", stderr);
        return 1;
    }
    if (count > 0 && strcmp(arguments[0], "--help") == 0) return help(socket_path);
    if (count > 0 && strncmp(arguments[0], "--", 2) == 0) return start_jasper(count, arguments, 1) == 0 ? 0 : 1;
    return call(socket_path, count, arguments);
}

#ifdef _WIN32
int wmain(int argc, wchar_t **argv) {
    WSADATA winsock;
    const char **arguments;
    int code;
    /* Frames carry exact bytes; text mode would turn each newline into CR LF. */
    _setmode(_fileno(stdout), _O_BINARY);
    _setmode(_fileno(stderr), _O_BINARY);
    if (WSAStartup(MAKEWORD(2, 2), &winsock) != 0) {
        fputs("jasper: Windows sockets are unavailable\n", stderr);
        return 1;
    }
    arguments = calloc((size_t) (argc > 0 ? argc : 1), sizeof *arguments);
    if (arguments == NULL) return 1;
    for (int i = 1; i < argc; i++) {
        arguments[i - 1] = utf8_of(argv[i]);
        if (arguments[i - 1] == NULL) return 1;
    }
    code = run(argc - 1, arguments);
    WSACleanup();
    return code;
}
#else
int main(int argc, char **argv) {
    /* A Jasper that goes away mid-write must not kill the client before it can say so. */
    signal(SIGPIPE, SIG_IGN);
    return run(argc - 1, (const char *const *) (argv + 1));
}
#endif
