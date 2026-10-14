/*
 * jasper-watch (packaging/launcher/README.md; startup recovery spec sections 5.2 and 5.3). Jasper's launcher starts it
 * with the read end of a pipe whose write end only the launcher holds, and writes one byte once its JVM exists:
 *
 *   jasper-watch <pipe> retry|report <launcher> [<argument>...]
 *
 * <pipe> is 3 on POSIX. On Windows it is the pipe's read end and a handle to the launcher's process, two decimal handle
 * values separated by a comma, and the one argument is the launcher's whole command line. End of file without the
 * byte means the launcher ended before its JVM existed. Then retry starts <launcher> once more with the arguments and
 * this environment, and report runs it once more with JASPER_LAUNCH_DIAGNOSE=1 and shows what Java reported, or writes
 * it to the file JASPER_WATCH_REPORT names.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jasper_launch_rules.h"

#ifdef _WIN32
#include <wchar.h>
#include <windows.h>
#else
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <spawn.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#endif

#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#include <crt_externs.h>
#include <libproc.h>
#endif

#define JASPER_WATCH_TITLE "Jasper could not start"
#define JASPER_WATCH_INTRO \
    "Jasper could not start, even without its start-up cache and your custom VM options. Reinstalling Jasper may help."
#define JASPER_WATCH_REPORTED "Java reported:"
#define JASPER_WATCH_NO_ERROR "Java reported no error when checked again. Try opening Jasper again."
#define JASPER_WATCH_NO_OUTPUT "Java exited with an error and printed nothing."
#define JASPER_WATCH_TAIL 1500
#define JASPER_WATCH_LIMIT_MS 10000L

/* The diagnostic launch's standard output and error, interleaved; only the last 64 KiB are kept. */
static char captured[65536];
static size_t capturedLength;

static void keep(const char* data, size_t count) {
    if (count >= sizeof(captured)) {
        memcpy(captured, data + count - sizeof(captured), sizeof(captured));
        capturedLength = sizeof(captured);
        return;
    }
    if (capturedLength + count > sizeof(captured)) {
        const size_t drop = capturedLength + count - sizeof(captured);
        memmove(captured, captured + drop, capturedLength - drop);
        capturedLength -= drop;
    }
    memcpy(captured + capturedLength, data, count);
    capturedLength += count;
}

static int isTrailingSpace(int c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

#ifdef _WIN32

#define JASPER_WIDEN2(text) L##text
#define JASPER_WIDEN(text) JASPER_WIDEN2(text)

/* text (UTF-8, or the ANSI code page) as a new wide string, or NULL. */
static wchar_t* wide(const char* text, size_t length, UINT codePage) {
    const int count = length == 0 ? 0 : MultiByteToWideChar(codePage, 0, text, (int) length, NULL, 0);
    wchar_t* result = (wchar_t*) malloc(((size_t) count + 1) * sizeof(wchar_t));
    if (result == NULL) {
        return NULL;
    }
    if (count > 0) {
        MultiByteToWideChar(codePage, 0, text, (int) length, result, count);
    }
    result[count] = L'\0';
    return result;
}

static int receivedByte(HANDLE readEnd) {
    char byte;
    DWORD count = 0;
    return ReadFile(readEnd, &byte, 1, &count, NULL) && count == 1;
}

/* Ctrl-C ended the launcher (it ignores nothing while its JVM starts): the user stopped Jasper on purpose. */
static int stoppedByControlC(HANDLE launcher) {
    DWORD code = 0;
    WaitForSingleObject(launcher, 5000);
    return GetExitCodeProcess(launcher, &code) && code == (DWORD) STATUS_CONTROL_C_EXIT;
}

static void startLauncher(const wchar_t* launcher, const wchar_t* commandLine) {
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    wchar_t* line = _wcsdup(commandLine);
    ZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    if (line != NULL && CreateProcessW(launcher, line, NULL, NULL, FALSE, 0, NULL, NULL, &startup, &process)) {
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
    }
    free(line);
}

/* Runs the launcher with JASPER_LAUNCH_DIAGNOSE=1 and its output captured, for up to 10 seconds. Returns 1 when it
   exited 0 or had to be killed (Java reported no error), 0 otherwise. */
static int runDiagnostic(const wchar_t* launcher, const wchar_t* commandLine) {
    SECURITY_ATTRIBUTES inheritable = { sizeof(SECURITY_ATTRIBUTES), NULL, TRUE };
    HANDLE readEnd = NULL;
    HANDLE writeEnd = NULL;
    HANDLE nul;
    HANDLE inherited[2];
    SIZE_T listSize = 0;
    LPPROC_THREAD_ATTRIBUTE_LIST list;
    STARTUPINFOEXW startup;
    PROCESS_INFORMATION process;
    wchar_t* line = _wcsdup(commandLine);
    const ULONGLONG deadline = GetTickCount64() + (ULONGLONG) JASPER_WATCH_LIMIT_MS;
    DWORD code = 1;
    int started;
    ZeroMemory(&process, sizeof(process));
    if (line == NULL || !SetEnvironmentVariableA(JASPER_LAUNCH_DIAGNOSE_VARIABLE, "1")
            || !CreatePipe(&readEnd, &writeEnd, &inheritable, 0)) {
        free(line);
        return 0;
    }
    SetHandleInformation(readEnd, HANDLE_FLAG_INHERIT, 0);
    nul = CreateFileW(L"NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, &inheritable, OPEN_EXISTING, 0, NULL);
    InitializeProcThreadAttributeList(NULL, 1, 0, &listSize);
    list = (LPPROC_THREAD_ATTRIBUTE_LIST) malloc(listSize);
    inherited[0] = writeEnd;
    inherited[1] = nul;
    ZeroMemory(&startup, sizeof(startup));
    startup.StartupInfo.cb = sizeof(startup);
    startup.StartupInfo.dwFlags = STARTF_USESTDHANDLES;
    startup.StartupInfo.hStdInput = nul;
    startup.StartupInfo.hStdOutput = writeEnd;
    startup.StartupInfo.hStdError = writeEnd;
    startup.lpAttributeList = list;
    started = nul != INVALID_HANDLE_VALUE && list != NULL && InitializeProcThreadAttributeList(list, 1, 0, &listSize)
        && UpdateProcThreadAttribute(list, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST, inherited, sizeof(inherited), NULL,
               NULL)
        && CreateProcessW(launcher, line, NULL, NULL, TRUE, CREATE_NO_WINDOW | EXTENDED_STARTUPINFO_PRESENT, NULL, NULL,
               &startup.StartupInfo, &process);
    if (list != NULL) {
        DeleteProcThreadAttributeList(list);
        free(list);
    }
    free(line);
    CloseHandle(writeEnd);
    if (nul != INVALID_HANDLE_VALUE) {
        CloseHandle(nul);
    }
    if (!started) {
        CloseHandle(readEnd);
        return 0;
    }
    for (;;) {
        char buffer[4096];
        DWORD available = 0;
        DWORD count = 0;
        if (!PeekNamedPipe(readEnd, NULL, 0, NULL, &available, NULL)) {
            break; /* every writer is gone */
        }
        if (available > 0) {
            const DWORD wanted = available < sizeof(buffer) ? available : (DWORD) sizeof(buffer);
            if (!ReadFile(readEnd, buffer, wanted, &count, NULL)) {
                break;
            }
            keep(buffer, count);
            continue;
        }
        if (GetTickCount64() >= deadline || WaitForSingleObject(process.hProcess, 50) == WAIT_OBJECT_0) {
            if (WaitForSingleObject(process.hProcess, 0) == WAIT_OBJECT_0
                    && PeekNamedPipe(readEnd, NULL, 0, NULL, &available, NULL) && available > 0) {
                continue;
            }
            break;
        }
    }
    CloseHandle(readEnd);
    if (WaitForSingleObject(process.hProcess,
            GetTickCount64() >= deadline ? 0 : (DWORD) (deadline - GetTickCount64())) != WAIT_OBJECT_0) {
        TerminateProcess(process.hProcess, 1);
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
        return 1;
    }
    GetExitCodeProcess(process.hProcess, &code);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return code == 0;
}

/* The dialog's text: the introduction, then Java's last 1,500 characters, or the note that it reported no error, or
   (it failed and printed nothing) the note that says so. */
static wchar_t* reportText(int clean) {
    wchar_t* output = clean ? wide("", 0, CP_ACP) : wide(captured, capturedLength, CP_ACP);
    size_t length = output == NULL ? 0 : wcslen(output);
    size_t start;
    const char* note;
    wchar_t* intro;
    wchar_t* wideNote;
    wchar_t* text = NULL;
    while (length > 0 && isTrailingSpace(output[length - 1])) {
        --length;
    }
    note = clean ? JASPER_WATCH_NO_ERROR : (length == 0 ? JASPER_WATCH_NO_OUTPUT : JASPER_WATCH_REPORTED);
    intro = wide(JASPER_WATCH_INTRO, strlen(JASPER_WATCH_INTRO), CP_UTF8);
    wideNote = wide(note, strlen(note), CP_UTF8);
    if (output != NULL && intro != NULL && wideNote != NULL) {
        start = length > JASPER_WATCH_TAIL ? length - JASPER_WATCH_TAIL : 0;
        if (start > 0 && output[start] >= 0xDC00 && output[start] <= 0xDFFF) {
            ++start;
        }
        text = (wchar_t*) malloc((wcslen(intro) + wcslen(wideNote) + (length - start) + 4) * sizeof(wchar_t));
        if (text != NULL) {
            wcscpy(text, intro);
            wcscat(text, L"\n\n");
            wcscat(text, wideNote);
            if (!clean && length > 0) {
                wcscat(text, L"\n");
                wcsncat(text, output + start, length - start);
            }
        }
    }
    free(intro);
    free(wideNote);
    free(output);
    return text;
}

/* Writes text as UTF-8 to a temporary file beside path, then moves it there. Returns 0 on success. */
static int writeReport(const wchar_t* path, const wchar_t* text) {
    static wchar_t temporary[32800];
    const int size = WideCharToMultiByte(CP_UTF8, 0, text, -1, NULL, 0, NULL, NULL);
    char* bytes = size > 0 ? (char*) malloc((size_t) size) : NULL;
    FILE* file;
    int failed;
    if (bytes == NULL || wcslen(path) + 5 > sizeof(temporary) / sizeof(temporary[0])) {
        free(bytes);
        return -1;
    }
    WideCharToMultiByte(CP_UTF8, 0, text, -1, bytes, size, NULL, NULL);
    wcscpy(temporary, path);
    wcscat(temporary, L".tmp");
    file = _wfopen(temporary, L"wb");
    if (file == NULL) {
        free(bytes);
        return -1;
    }
    failed = fwrite(bytes, 1, (size_t) size - 1, file) != (size_t) size - 1;
    failed = fclose(file) != 0 || failed;
    free(bytes);
    return failed || !MoveFileExW(temporary, path, MOVEFILE_REPLACE_EXISTING) ? -1 : 0;
}

static int report(const wchar_t* launcher, const wchar_t* commandLine) {
    static wchar_t file[32768];
    const DWORD capacity = (DWORD) (sizeof(file) / sizeof(file[0]));
    const int clean = runDiagnostic(launcher, commandLine);
    wchar_t* text = reportText(clean);
    wchar_t* title = wide(JASPER_WATCH_TITLE, strlen(JASPER_WATCH_TITLE), CP_UTF8);
    const DWORD length = GetEnvironmentVariableW(JASPER_WIDEN(JASPER_WATCH_REPORT_VARIABLE), file, capacity);
    if (text != NULL && title != NULL) {
        if (length > 0 && length < capacity) {
            writeReport(file, text);
        } else {
            MessageBoxW(NULL, text, title, MB_ICONERROR | MB_OK);
        }
    }
    free(text);
    free(title);
    return 0;
}

int wmain(int argc, wchar_t** argv) {
    const wchar_t* comma = argc == 5 ? wcschr(argv[1], L',') : NULL;
    HANDLE readEnd;
    HANDLE launcher;
    if (comma == NULL || (wcscmp(argv[2], L"retry") != 0 && wcscmp(argv[2], L"report") != 0)) {
        fputws(L"usage: jasper-watch <pipe>,<process> retry|report <launcher> <command line>\n", stderr);
        return 64;
    }
    readEnd = (HANDLE) (ULONG_PTR) _wcstoui64(argv[1], NULL, 10);
    launcher = (HANDLE) (ULONG_PTR) _wcstoui64(comma + 1, NULL, 10);
    if (receivedByte(readEnd)) {
        return 0;
    }
    CloseHandle(readEnd);
    if (stoppedByControlC(launcher)) {
        return 0;
    }
    CloseHandle(launcher);
    if (wcscmp(argv[2], L"retry") == 0) {
        startLauncher(argv[3], argv[4]);
        return 0;
    }
    return report(argv[3], argv[4]);
}

#else

#ifdef __APPLE__
static char** processEnvironment(void) {
    return *_NSGetEnviron();
}
#else
extern char** environ;
static char** processEnvironment(void) {
    return environ;
}
#endif

static long nowMs(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (long) now.tv_sec * 1000L + now.tv_nsec / 1000000L;
}

static void pause10Ms(void) {
    struct timespec interval;
    interval.tv_sec = 0;
    interval.tv_nsec = 10L * 1000L * 1000L;
    nanosleep(&interval, NULL);
}

/* Closes every descriptor above 2 except keep: nothing the launcher had open stays open here or in what this starts. */
static void closeOtherDescriptors(int keep) {
#ifdef __APPLE__
    const char* listing = "/dev/fd";
#else
    const char* listing = "/proc/self/fd";
#endif
    int found[1024];
    size_t count = 0;
    size_t i;
    long maximum;
    long fd;
    DIR* directory = opendir(listing);
    if (directory != NULL) {
        const int own = dirfd(directory);
        struct dirent* entry;
        while ((entry = readdir(directory)) != NULL && count < sizeof(found) / sizeof(found[0])) {
            char* end;
            fd = strtol(entry->d_name, &end, 10);
            if (entry->d_name[0] != '\0' && *end == '\0' && fd > 2 && fd != keep && fd != own) {
                found[count++] = (int) fd;
            }
        }
        closedir(directory);
        for (i = 0; i < count; ++i) {
            close(found[i]);
        }
        if (count < sizeof(found) / sizeof(found[0])) {
            return;
        }
    }
    maximum = sysconf(_SC_OPEN_MAX);
    if (maximum < 0 || maximum > 65536) {
        maximum = 65536;
    }
    for (fd = 3; fd < maximum; ++fd) {
        if (fd != keep) {
            close((int) fd);
        }
    }
}

static int receivedByte(int descriptor) {
    char byte;
    ssize_t count;
    do {
        count = read(descriptor, &byte, 1);
    } while (count < 0 && errno == EINTR);
    return count == 1;
}

/* This process's parent while that is still the launcher (its executable is the file launcher names), else 0: the
   launcher ended before this process started and another process adopted this one, or it cannot be told. */
static pid_t launcherParent(const char* launcher) {
    const pid_t parent = getppid();
    struct stat expected;
    struct stat actual;
#ifdef __APPLE__
    char path[PROC_PIDPATHINFO_MAXSIZE];
    if (proc_pidpath(parent, path, sizeof(path)) <= 0) {
        return 0;
    }
#else
    char path[64];
    snprintf(path, sizeof(path), "/proc/%ld/exe", (long) parent);
#endif
    if (stat(launcher, &expected) != 0 || stat(path, &actual) != 0) {
        return 0;
    }
    return expected.st_dev == actual.st_dev && expected.st_ino == actual.st_ino ? parent : 0;
}

/* End of file without the byte: whether the launcher ended (launcher 0: before this process started). When it dies,
   the system gives this process another parent. When libjli re-executes it (Linux, for LD_LIBRARY_PATH), the write
   end closes as well, but the same process lives on as this one's parent and starts a watcher of its own; after a
   second this one leaves the launch to that. */
static int launcherEnded(pid_t launcher) {
    const long deadline = nowMs() + 1000L;
    if (launcher == 0) {
        return 1;
    }
    while (getppid() == launcher) {
        if (nowMs() >= deadline) {
            return 0;
        }
        pause10Ms();
    }
    return 1;
}

static int closeOnExec(int fd) {
    int flags = fcntl(fd, F_GETFD);
    return flags < 0 ? -1 : fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
}

/* Runs launch (the launcher and its arguments) with JASPER_LAUNCH_DIAGNOSE=1 and its output captured, for up to 10
   seconds. Returns 1 when it exited 0 or had to be killed (Java reported no error), 0 otherwise. */
static int runDiagnostic(char** launch) {
    const long deadline = nowMs() + JASPER_WATCH_LIMIT_MS;
    posix_spawn_file_actions_t actions;
    pid_t child;
    int fds[2];
    int status = 0;
    int exited = 0;
    int failed;
    if (setenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE, "1", 1) != 0 || pipe(fds) != 0) {
        return 0;
    }
    closeOnExec(fds[0]);
    closeOnExec(fds[1]);
    posix_spawn_file_actions_init(&actions);
    posix_spawn_file_actions_addopen(&actions, 0, "/dev/null", O_RDONLY, 0);
    posix_spawn_file_actions_adddup2(&actions, fds[1], 1);
    posix_spawn_file_actions_adddup2(&actions, fds[1], 2);
    failed = posix_spawn(&child, launch[0], &actions, NULL, launch, processEnvironment());
    posix_spawn_file_actions_destroy(&actions);
    close(fds[1]);
    if (failed) {
        close(fds[0]);
        return 0;
    }
    for (;;) {
        const long remaining = deadline - nowMs();
        struct pollfd reader;
        int ready;
        if (remaining <= 0) {
            break;
        }
        reader.fd = fds[0];
        reader.events = POLLIN;
        reader.revents = 0;
        ready = poll(&reader, 1, remaining > 100 ? 100 : (int) remaining);
        if (ready > 0) {
            char buffer[4096];
            const ssize_t count = read(fds[0], buffer, sizeof(buffer));
            if (count > 0) {
                keep(buffer, (size_t) count);
                continue;
            }
            if (count == 0 || errno != EINTR) {
                break; /* end of file */
            }
        }
        if (!exited && waitpid(child, &status, WNOHANG) == child) {
            exited = 1;
        }
        if (exited && ready == 0) {
            break; /* it exited, and whatever still holds the pipe writes nothing */
        }
    }
    close(fds[0]);
    while (!exited && nowMs() < deadline) {
        if (waitpid(child, &status, WNOHANG) == child) {
            exited = 1;
        } else {
            pause10Ms();
        }
    }
    if (!exited) {
        kill(child, SIGKILL);
        while (waitpid(child, &status, 0) < 0 && errno == EINTR) {
        }
        return 1;
    }
    return WIFEXITED(status) && WEXITSTATUS(status) == 0;
}

/* The dialog's text: the introduction, then Java's last 1,500 characters (UTF-8), or the note that it reported no
   error, or (it failed and printed nothing) the note that says so. */
static char* reportText(int clean) {
    size_t length = clean ? 0 : capturedLength;
    size_t start;
    size_t characters = 0;
    char* text;
    while (length > 0 && isTrailingSpace((unsigned char) captured[length - 1])) {
        --length;
    }
    start = length;
    while (start > 0 && characters < JASPER_WATCH_TAIL) {
        --start;
        if (((unsigned char) captured[start] & 0xC0u) != 0x80u) {
            ++characters;
        }
    }
    text = (char*) malloc(strlen(JASPER_WATCH_INTRO) + strlen(JASPER_WATCH_NO_ERROR) + strlen(JASPER_WATCH_NO_OUTPUT)
        + strlen(JASPER_WATCH_REPORTED) + (length - start) + 4);
    if (text == NULL) {
        return NULL;
    }
    strcpy(text, JASPER_WATCH_INTRO "\n\n");
    if (clean) {
        strcat(text, JASPER_WATCH_NO_ERROR);
    } else if (length == 0) {
        strcat(text, JASPER_WATCH_NO_OUTPUT);
    } else {
        strcat(text, JASPER_WATCH_REPORTED "\n");
        strncat(text, captured + start, length - start);
    }
    return text;
}

/* Writes text to a temporary file beside path, then moves it there. Returns 0 on success. */
static int writeReport(const char* path, const char* text) {
    const size_t length = strlen(text);
    char* temporary = (char*) malloc(strlen(path) + 5);
    FILE* file;
    int failed;
    if (temporary == NULL) {
        return -1;
    }
    strcpy(temporary, path);
    strcat(temporary, ".tmp");
    file = fopen(temporary, "wb");
    if (file == NULL) {
        free(temporary);
        return -1;
    }
    failed = fwrite(text, 1, length, file) != length;
    failed = fclose(file) != 0 || failed;
    failed = failed || rename(temporary, path) != 0;
    free(temporary);
    return failed ? -1 : 0;
}

static void show(const char* text) {
#ifdef __APPLE__
    CFStringRef title = CFStringCreateWithCString(kCFAllocatorDefault, JASPER_WATCH_TITLE, kCFStringEncodingUTF8);
    CFStringRef message = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*) text, (CFIndex) strlen(text),
        kCFStringEncodingUTF8, false);
    CFOptionFlags response = 0;
    if (message == NULL) {
        /* Java's output was not UTF-8: every byte is a Latin-1 character. */
        message = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*) text, (CFIndex) strlen(text),
            kCFStringEncodingISOLatin1, false);
    }
    if (title != NULL && message != NULL) {
        CFUserNotificationDisplayAlert(0, kCFUserNotificationStopAlertLevel, NULL, NULL, NULL, title, message,
            CFSTR("Quit"), NULL, NULL, &response);
    }
    if (title != NULL) {
        CFRelease(title);
    }
    if (message != NULL) {
        CFRelease(message);
    }
#else
    fprintf(stderr, "%s\n\n%s\n", JASPER_WATCH_TITLE, text);
#endif
}

static int report(char** launch) {
    const int clean = runDiagnostic(launch);
    const char* file = getenv(JASPER_WATCH_REPORT_VARIABLE);
    char* text = reportText(clean);
    if (text == NULL) {
        return 1;
    }
    if (file != NULL && file[0] != '\0') {
        if (writeReport(file, text) != 0) {
            fprintf(stderr, "%s\n", text);
        }
    } else {
        show(text);
    }
    free(text);
    return 0;
}

int main(int argc, char** argv) {
    char* end;
    long descriptor;
    pid_t launcher;
    if (argc < 4 || (strcmp(argv[2], "retry") != 0 && strcmp(argv[2], "report") != 0)) {
        fputs("usage: jasper-watch <pipe> retry|report <launcher> [<argument>...]\n", stderr);
        return 64;
    }
    descriptor = strtol(argv[1], &end, 10);
    if (argv[1][0] == '\0' || *end != '\0' || descriptor < 0 || descriptor > 65535) {
        fputs("jasper-watch: <pipe> must be a descriptor number\n", stderr);
        return 64;
    }
    launcher = launcherParent(argv[3]);
    /* jpackage's Linux launcher sets _JPACKAGE_LAUNCHER to a hash of LD_LIBRARY_PATH and then adds its app directory
       there. A launch that inherited both would take itself for libjli's re-execution and ignore its cfg file; without
       the variable it starts afresh and finds the app directory already in LD_LIBRARY_PATH. */
    unsetenv("_JPACKAGE_LAUNCHER");
    /* The diagnostic launch is waited for: with SIGCHLD ignored, as a parent may leave it, waitpid finds no child. */
    signal(SIGCHLD, SIG_DFL);
    closeOtherDescriptors((int) descriptor);
    if (receivedByte((int) descriptor)) {
        return 0;
    }
    close((int) descriptor);
    if (!launcherEnded(launcher)) {
        return 0;
    }
    if (strcmp(argv[2], "retry") == 0) {
        pid_t child;
        /* argv + 3 is <launcher> followed by its arguments and NULL. Nothing else happens when it cannot start. */
        posix_spawn(&child, argv[3], NULL, NULL, argv + 3, processEnvironment());
        return 0;
    }
    return report(argv + 3);
}

#endif
