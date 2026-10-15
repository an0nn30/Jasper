/*
 * See jasper_watch_launch.h. Three things send the byte: a thread that asks the loaded libjvm's own
 * JNI_GetCreatedJavaVMs every 10 ms (it reports a VM only once creation is complete), JLI_Launch returning with a
 * libjvm loaded, and an exit handler that finds a VM (System.exit within one interval of the JVM's creation). HotSpot's
 * initialization failures end with _exit or exit() before any of them can, so the watcher reads end of file.
 */
#include "jasper_watch_launch.h"

#include <stdlib.h>
#include <string.h>

#include "jasper_launch_rules.h"
#include "jni.h"

#ifdef _WIN32
#include <wchar.h>
#include <windows.h>
#else
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <signal.h>
#include <spawn.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#endif

#ifdef __APPLE__
#include <crt_externs.h>
#include <mach-o/dyld.h>
#endif

typedef jint (JNICALL *CreatedJavaVmsFunction)(JavaVM**, jsize, jsize*);

static int beforeCalled;
static int polling = 1;
static volatile CreatedJavaVmsFunction createdJavaVmsFunction;

/* The loaded libjvm's own JNI_GetCreatedJavaVMs, or NULL when no libjvm is loaded. Asked of that image only: macOS's
   libjli exports a wrapper of the same name that would load the default libjvm on demand. */
static CreatedJavaVmsFunction loadedJvmFunction(void) {
    CreatedJavaVmsFunction found = createdJavaVmsFunction;
    if (found != NULL) {
        return found;
    }
#if defined(_WIN32)
    {
        HMODULE jvm = GetModuleHandleW(L"jvm.dll");
        found = jvm == NULL ? NULL : (CreatedJavaVmsFunction) GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
    }
#elif defined(__APPLE__)
    {
        static const char suffix[] = "/libjvm.dylib";
        const size_t suffixLength = sizeof(suffix) - 1;
        uint32_t images = _dyld_image_count();
        uint32_t i;
        for (i = 0; i < images && found == NULL; ++i) {
            const char* name = _dyld_get_image_name(i);
            size_t length = name == NULL ? 0 : strlen(name);
            if (length >= suffixLength && strcmp(name + length - suffixLength, suffix) == 0) {
                void* jvm = dlopen(name, RTLD_LAZY | RTLD_NOLOAD);
                found = jvm == NULL ? NULL : (CreatedJavaVmsFunction) dlsym(jvm, "JNI_GetCreatedJavaVMs");
            }
        }
    }
#else
    {
        void* jvm = dlopen("libjvm.so", RTLD_LAZY | RTLD_NOLOAD);
        found = jvm == NULL ? NULL : (CreatedJavaVmsFunction) dlsym(jvm, "JNI_GetCreatedJavaVMs");
    }
#endif
    createdJavaVmsFunction = found;
    return found;
}

/* The number of created Java VMs, or -1 when it cannot be told (no libjvm is loaded). */
static int createdJavaVms(void) {
    CreatedJavaVmsFunction function = loadedJvmFunction();
    JavaVM* vms[1];
    jsize count = 0;
    if (function == NULL || function(vms, 1, &count) != JNI_OK) {
        return -1;
    }
    return (int) count;
}

#ifdef _WIN32

static SRWLOCK signalLock = SRWLOCK_INIT;
static HANDLE volatile writeEnd;

static int isSet(const char* name) {
    return GetEnvironmentVariableA(name, NULL, 0) > 0;
}

static void removeVariable(const char* name) {
    SetEnvironmentVariableA(name, NULL);
}

static int watching(void) {
    return writeEnd != NULL;
}

void jasperWatchSignal(void) {
    static const char byte = 1;
    DWORD written;
    AcquireSRWLockExclusive(&signalLock);
    if (writeEnd != NULL) {
        WriteFile(writeEnd, &byte, 1, &written, NULL);
        CloseHandle(writeEnd);
        writeEnd = NULL;
    }
    ReleaseSRWLockExclusive(&signalLock);
}

/* Writes argument in double quotes, escaped for CommandLineToArgvW and the C runtime: backslashes are literal unless
   they precede a quote, where each one and the quote are escaped. Needs 2 * wcslen(argument) + 2 characters. */
static wchar_t* quoted(wchar_t* out, const wchar_t* argument) {
    *out++ = L'"';
    for (;;) {
        size_t backslashes = 0;
        size_t i;
        while (*argument == L'\\') {
            ++argument;
            ++backslashes;
        }
        if (*argument == L'\0') {
            for (i = 0; i < backslashes * 2; ++i) {
                *out++ = L'\\';
            }
            break;
        }
        for (i = 0; i < (*argument == L'"' ? backslashes * 2 + 1 : backslashes); ++i) {
            *out++ = L'\\';
        }
        *out++ = *argument++;
    }
    *out++ = L'"';
    return out;
}

/* jasper-watch <read>,<process> retry|report "<launcher>" "<command line>", inheriting exactly the pipe's read end and
   a handle to this process. The write end is not inheritable, so no other child of this process can hold it open. */
static int spawnWatcher(const wchar_t* mode) {
    static wchar_t self[32768];
    static wchar_t watcher[32800];
    const DWORD capacity = (DWORD) (sizeof(self) / sizeof(self[0]));
    const DWORD length = GetModuleFileNameW(NULL, self, capacity);
    const wchar_t* original = GetCommandLineW();
    wchar_t* separator;
    SECURITY_ATTRIBUTES notInherited = { sizeof(SECURITY_ATTRIBUTES), NULL, FALSE };
    HANDLE readEnd = NULL;
    HANDLE pipeWrite = NULL;
    HANDLE process = NULL;
    HANDLE inherited[2];
    wchar_t* commandLine = NULL;
    wchar_t* end;
    SIZE_T listSize = 0;
    LPPROC_THREAD_ATTRIBUTE_LIST list = NULL;
    STARTUPINFOEXW startup;
    PROCESS_INFORMATION child;
    int started = 0;
    ZeroMemory(&child, sizeof(child));
    if (length == 0 || length >= capacity) {
        return -1;
    }
    wcscpy(watcher, self);
    separator = wcsrchr(watcher, L'\\');
    if (separator == NULL) {
        return -1;
    }
    wcscpy(separator + 1, L"jasper-watch.exe");
    if (GetFileAttributesW(watcher) == INVALID_FILE_ATTRIBUTES) {
        return -1;
    }
    if (!CreatePipe(&readEnd, &pipeWrite, &notInherited, 0)) {
        return -1;
    }
    commandLine = (wchar_t*) malloc((2 * wcslen(watcher) + 2 * length + 2 * wcslen(original) + 80) * sizeof(wchar_t));
    InitializeProcThreadAttributeList(NULL, 1, 0, &listSize);
    list = (LPPROC_THREAD_ATTRIBUTE_LIST) malloc(listSize);
    if (commandLine != NULL && list != NULL
            && SetHandleInformation(readEnd, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT)
            && DuplicateHandle(GetCurrentProcess(), GetCurrentProcess(), GetCurrentProcess(), &process,
                   SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION, TRUE, 0)
            && InitializeProcThreadAttributeList(list, 1, 0, &listSize)) {
        inherited[0] = readEnd;
        inherited[1] = process;
        end = quoted(commandLine, watcher);
        end += swprintf(end, 64, L" %llu,%llu %ls ", (unsigned long long) (ULONG_PTR) readEnd,
            (unsigned long long) (ULONG_PTR) process, mode);
        end = quoted(end, self);
        *end++ = L' ';
        end = quoted(end, original);
        *end = L'\0';
        ZeroMemory(&startup, sizeof(startup));
        startup.StartupInfo.cb = sizeof(startup);
        startup.lpAttributeList = list;
        if (wcslen(commandLine) < 32767
                && UpdateProcThreadAttribute(list, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST, inherited, sizeof(inherited),
                       NULL, NULL)
                && CreateProcessW(watcher, commandLine, NULL, NULL, TRUE,
                       CREATE_NO_WINDOW | EXTENDED_STARTUPINFO_PRESENT, NULL, NULL, &startup.StartupInfo, &child)) {
            CloseHandle(child.hThread);
            CloseHandle(child.hProcess);
            started = 1;
        }
        DeleteProcThreadAttributeList(list);
    }
    free(list);
    free(commandLine);
    CloseHandle(readEnd);
    if (process != NULL) {
        CloseHandle(process);
    }
    if (!started) {
        CloseHandle(pipeWrite);
        return -1;
    }
    writeEnd = pipeWrite;
    return 0;
}

int jasperWatchStart(const char* nextVariable) {
    int result;
    if (nextVariable != NULL && !SetEnvironmentVariableA(nextVariable, JASPER_LAUNCH_FAILED)) {
        return -1;
    }
    result = spawnWatcher(nextVariable != NULL ? L"retry" : L"report");
    if (nextVariable != NULL) {
        removeVariable(nextVariable);
    }
    return result;
}

static DWORD WINAPI pollForJvm(LPVOID unused) {
    (void) unused;
    while (watching()) {
        if (createdJavaVms() > 0) {
            jasperWatchSignal();
            break;
        }
        Sleep(10);
    }
    return 0;
}

static void startPolling(void) {
    HANDLE thread = CreateThread(NULL, 0, pollForJvm, NULL, 0, NULL);
    if (thread != NULL) {
        CloseHandle(thread);
    }
}

#else

static pthread_mutex_t signalLock = PTHREAD_MUTEX_INITIALIZER;
static volatile int writeEnd = -1;
static pid_t watcherProcess = -1;

static int isSet(const char* name) {
    return getenv(name) != NULL;
}

static void removeVariable(const char* name) {
    unsetenv(name);
}

static int watching(void) {
    return writeEnd >= 0;
}

/* One byte, without dying of SIGPIPE when the watcher is gone: the signal is blocked in this thread and a pending one
   is taken back. */
static void writeByte(int fd) {
    static const char byte = 1;
    sigset_t pipeSignal;
    sigset_t previous;
    sigset_t pending;
    ssize_t written;
    int received;
    sigemptyset(&pipeSignal);
    sigaddset(&pipeSignal, SIGPIPE);
    pthread_sigmask(SIG_BLOCK, &pipeSignal, &previous);
    do {
        written = write(fd, &byte, 1);
    } while (written < 0 && errno == EINTR);
    if (written < 0 && errno == EPIPE && !sigismember(&previous, SIGPIPE) && sigpending(&pending) == 0
            && sigismember(&pending, SIGPIPE)) {
        sigwait(&pipeSignal, &received);
    }
    pthread_sigmask(SIG_SETMASK, &previous, NULL);
}

void jasperWatchSignal(void) {
    pthread_mutex_lock(&signalLock);
    if (writeEnd >= 0) {
        writeByte(writeEnd);
        close(writeEnd);
        writeEnd = -1;
    }
    pthread_mutex_unlock(&signalLock);
}

#ifdef __APPLE__

static char** processArguments(void) {
    return *_NSGetArgv();
}

static char** processEnvironment(void) {
    return *_NSGetEnviron();
}

static int executablePath(char* path, size_t size) {
    char found[PATH_MAX];
    uint32_t length = (uint32_t) sizeof(found);
    if (_NSGetExecutablePath(found, &length) != 0) {
        return -1;
    }
    if (realpath(found, path) == NULL) {
        if (strlen(found) >= size) {
            return -1;
        }
        strcpy(path, found);
    }
    return 0;
}

#else

extern char** environ;

/* The original command line, read from /proc: up to 64 KiB and 1023 arguments. Arguments beyond that are dropped, and a
   last argument cut by the 64 KiB limit is passed on truncated. */
static char** processArguments(void) {
    static char buffer[65536];
    static char* arguments[1024];
    size_t used;
    size_t i = 0;
    int count = 0;
    FILE* file = fopen("/proc/self/cmdline", "rb");
    if (file == NULL) {
        return NULL;
    }
    used = fread(buffer, 1, sizeof(buffer) - 1, file);
    fclose(file);
    buffer[used] = '\0';
    while (i < used && count < 1023) {
        arguments[count++] = buffer + i;
        i += strlen(buffer + i) + 1;
    }
    arguments[count] = NULL;
    return count == 0 ? NULL : arguments;
}

static char** processEnvironment(void) {
    return environ;
}

static int executablePath(char* path, size_t size) {
    ssize_t length = readlink("/proc/self/exe", path, size - 1);
    if (length <= 0) {
        return -1;
    }
    path[length] = '\0';
    return 0;
}

#endif

static int closeOnExec(int fd) {
    int flags = fcntl(fd, F_GETFD);
    return flags < 0 ? -1 : fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
}

/* jasper-watch 3 retry|report <launcher> <argument>..., with the pipe's read end at descriptor 3. Both ends are
   close-on-exec here, so no other child of this process inherits them; dup2 clears the flag on the watcher's copy. */
static int spawnWatcher(const char* mode) {
    static char self[PATH_MAX];
    static char watcher[PATH_MAX + 16];
    char** original = processArguments();
    char** arguments;
    char* separator;
    int fds[2];
    int count = 0;
    int i;
    int failed;
    pid_t child;
    posix_spawn_file_actions_t actions;
    if (original == NULL || executablePath(self, sizeof(self)) != 0) {
        return -1;
    }
    strcpy(watcher, self);
    separator = strrchr(watcher, '/');
    if (separator == NULL) {
        return -1;
    }
    strcpy(separator + 1, "jasper-watch");
    if (access(watcher, X_OK) != 0 || pipe(fds) != 0) {
        return -1;
    }
    if (closeOnExec(fds[0]) != 0 || closeOnExec(fds[1]) != 0) {
        close(fds[0]);
        close(fds[1]);
        return -1;
    }
    if (fds[0] == 3) {
        /* dup2(3, 3) would leave the flag set, so the read end moves first. */
        int moved = fcntl(fds[0], F_DUPFD_CLOEXEC, 4);
        close(fds[0]);
        if (moved < 0) {
            close(fds[1]);
            return -1;
        }
        fds[0] = moved;
    }
    while (original[count] != NULL) {
        ++count;
    }
    arguments = (char**) calloc((size_t) count + 4, sizeof(char*));
    if (arguments == NULL) {
        close(fds[0]);
        close(fds[1]);
        return -1;
    }
    arguments[0] = watcher;
    arguments[1] = (char*) "3";
    arguments[2] = (char*) mode;
    arguments[3] = self;
    for (i = 1; i < count; ++i) {
        arguments[3 + i] = original[i];
    }
    posix_spawn_file_actions_init(&actions);
    posix_spawn_file_actions_adddup2(&actions, fds[0], 3);
    failed = posix_spawn(&child, watcher, &actions, NULL, arguments, processEnvironment());
    posix_spawn_file_actions_destroy(&actions);
    free(arguments);
    close(fds[0]);
    if (failed) {
        close(fds[1]);
        return -1;
    }
    watcherProcess = child;
    writeEnd = fds[1];
    return 0;
}

int jasperWatchStart(const char* nextVariable) {
    int result;
    if (nextVariable != NULL && setenv(nextVariable, JASPER_LAUNCH_FAILED, 1) != 0) {
        return -1;
    }
    result = spawnWatcher(nextVariable != NULL ? "retry" : "report");
    if (nextVariable != NULL) {
        removeVariable(nextVariable);
    }
    return result;
}

/* Polls until the byte is sent, then reaps the watcher, which exits once it has read it. */
static void* pollForJvm(void* unused) {
    struct timespec interval;
    (void) unused;
    interval.tv_sec = 0;
    interval.tv_nsec = 10L * 1000L * 1000L;
    while (watching()) {
        if (createdJavaVms() > 0) {
            jasperWatchSignal();
            break;
        }
        nanosleep(&interval, NULL);
    }
    if (watcherProcess > 0) {
        while (waitpid(watcherProcess, NULL, 0) < 0 && errno == EINTR) {
        }
    }
    return NULL;
}

/* The thread starts with every signal blocked, so HotSpot never delivers one to it. */
static void startPolling(void) {
    pthread_t thread;
    pthread_attr_t attributes;
    sigset_t all;
    sigset_t previous;
    sigfillset(&all);
    pthread_sigmask(SIG_SETMASK, &all, &previous);
    if (pthread_attr_init(&attributes) == 0) {
        pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);
        pthread_create(&thread, &attributes, pollForJvm, NULL);
        pthread_attr_destroy(&attributes);
    }
    pthread_sigmask(SIG_SETMASK, &previous, NULL);
}

#endif

static void onExit(void) {
    if (watching() && createdJavaVms() > 0) {
        jasperWatchSignal();
    }
}

static void registerExitHandler(void) {
#ifdef _WIN32
    /* jli.dll and jvm.dll call the shared UCRT's exit(), which runs the UCRT's process-wide table but not this
       statically linked CRT's own atexit table, so register there too. */
    typedef int (__cdecl *CrtAtExitFunction)(void (__cdecl *)(void));
    HMODULE ucrt = GetModuleHandleW(L"ucrtbase.dll");
    CrtAtExitFunction crtAtExit = ucrt == NULL ? NULL : (CrtAtExitFunction) GetProcAddress(ucrt, "_crt_atexit");
    if (crtAtExit != NULL) {
        crtAtExit(onExit);
    }
#endif
    atexit(onExit);
}

void jasperWatchBeforeLaunch(int argc, char** argv) {
    if (beforeCalled) {
        return;
    }
    beforeCalled = 1;
    if (!isSet(JASPER_LAUNCH_DIAGNOSE_VARIABLE)) {
        const char* next = jasperNextStartVariable(argc, (const char* const*) argv,
            isSet(JASPER_AOT_CACHE_SKIP_VARIABLE), isSet(JASPER_VM_OPTIONS_SKIP_VARIABLE));
        if (jasperWatchStart(next) == 0) {
            registerExitHandler();
            if (polling) {
                startPolling();
            }
        }
    }
    removeVariable(JASPER_VM_OPTIONS_SKIP_VARIABLE);
    removeVariable(JASPER_AOT_CACHE_SKIP_VARIABLE);
    removeVariable(JASPER_LAUNCH_DIAGNOSE_VARIABLE);
}

void jasperWatchAfterLaunch(void) {
    if (watching() && loadedJvmFunction() != NULL) {
        jasperWatchSignal();
    }
}

void jasperWatchSetPolling(int enabled) {
    polling = enabled;
}
