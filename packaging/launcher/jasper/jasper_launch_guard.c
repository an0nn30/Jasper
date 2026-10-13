/*
 * See jasper_launch_guard.h. libjli calls exit() when the JVM cannot be created, so the launcher never regains
 * control; an exit handler tells that case apart: JLI_Launch has not returned and no JVM was created. System.exit
 * leaves its JVM counted, and a normal return passes through jasperGuardAfterLaunch first.
 */
#include "jasper_launch_guard.h"

#include <stdlib.h>
#include <string.h>

#include "jni.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#include <spawn.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>
#endif

#ifdef __APPLE__
#include <crt_externs.h>
#include <mach-o/dyld.h>
#endif

#define JASPER_DIGEST_PREFIX "-Djasper.vmOptionsDigest="
#define JASPER_SKIP_VARIABLE "JASPER_VM_OPTIONS_SKIP"
#define JASPER_SKIP_REASON "launch-failed"

typedef jint (JNICALL *CreatedJavaVmsFunction)(JavaVM**, jsize, jsize*);

static volatile int armed;
static volatile int registered;
static volatile int returned;
static volatile int skippedLaunch;
static void (*respawnReplacement)(void);

/* The loaded libjvm's own JNI_GetCreatedJavaVMs, or NULL when no libjvm is loaded. Asked of that image only: macOS's
   libjli exports a wrapper of the same name that would load the default libjvm on demand. */
static CreatedJavaVmsFunction loadedJvmFunction(void) {
#if defined(_WIN32)
    HMODULE jvm = GetModuleHandleW(L"jvm.dll");
    return jvm == NULL ? NULL : (CreatedJavaVmsFunction) GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
#elif defined(__APPLE__)
    static const char suffix[] = "/libjvm.dylib";
    const size_t suffixLength = sizeof(suffix) - 1;
    uint32_t images = _dyld_image_count();
    uint32_t i;
    for (i = 0; i < images; ++i) {
        const char* name = _dyld_get_image_name(i);
        size_t length = name == NULL ? 0 : strlen(name);
        if (length >= suffixLength && strcmp(name + length - suffixLength, suffix) == 0) {
            void* jvm = dlopen(name, RTLD_LAZY | RTLD_NOLOAD);
            return jvm == NULL ? NULL : (CreatedJavaVmsFunction) dlsym(jvm, "JNI_GetCreatedJavaVMs");
        }
    }
    return NULL;
#else
    void* jvm = dlopen("libjvm.so", RTLD_LAZY | RTLD_NOLOAD);
    return jvm == NULL ? NULL : (CreatedJavaVmsFunction) dlsym(jvm, "JNI_GetCreatedJavaVMs");
#endif
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

static void respawnSelf(void) {
    static wchar_t path[32768];
    DWORD capacity = (DWORD) (sizeof(path) / sizeof(path[0]));
    DWORD length = GetModuleFileNameW(NULL, path, capacity);
    wchar_t* commandLine;
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    if (length == 0 || length >= capacity) {
        return;
    }
    if (!SetEnvironmentVariableA(JASPER_SKIP_VARIABLE, JASPER_SKIP_REASON)) {
        return;
    }
    commandLine = _wcsdup(GetCommandLineW());
    if (commandLine == NULL) {
        return;
    }
    ZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    if (CreateProcessW(path, commandLine, NULL, NULL, FALSE, 0, NULL, NULL, &startup, &process)) {
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
    }
    free(commandLine);
}

#else

#ifdef __APPLE__

static char** processArguments(void) {
    return *_NSGetArgv();
}

static char** processEnvironment(void) {
    return *_NSGetEnviron();
}

static int executablePath(char* path, size_t size) {
    uint32_t length = (uint32_t) size;
    return _NSGetExecutablePath(path, &length) == 0 ? 0 : -1;
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

static void respawnSelf(void) {
    static char path[4096];
    char** arguments;
    pid_t child;
    if (executablePath(path, sizeof(path)) != 0) {
        return;
    }
    arguments = processArguments();
    if (arguments == NULL || setenv(JASPER_SKIP_VARIABLE, JASPER_SKIP_REASON, 1) != 0) {
        return;
    }
    if (posix_spawn(&child, path, NULL, NULL, arguments, processEnvironment()) != 0) {
        return;
    }
}

#endif

static void onExit(void) {
    if (!armed || returned || createdJavaVms() != 0) {
        return;
    }
    armed = 0;
    if (respawnReplacement != NULL) {
        respawnReplacement();
    } else {
        respawnSelf();
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

void jasperGuardBeforeLaunch(int argc, char** argv) {
    int i;
    /* The process that starts the JVM removes the skip variable, so the JVM and its children never inherit it. On Linux
       the cfg is read in a forked child, and on Windows possibly in a restart wrapper, so the wrapper cannot do it. A
       launch that found it set is a fallback start and never arms, whatever argv holds. */
#ifdef _WIN32
    if (GetEnvironmentVariableA(JASPER_SKIP_VARIABLE, NULL, 0) > 0) {
        skippedLaunch = 1;
    }
    SetEnvironmentVariableA(JASPER_SKIP_VARIABLE, NULL);
#else
    if (getenv(JASPER_SKIP_VARIABLE) != NULL) {
        skippedLaunch = 1;
    }
    unsetenv(JASPER_SKIP_VARIABLE);
#endif
    if (skippedLaunch) {
        return;
    }
    for (i = 0; i < argc; ++i) {
        if (argv[i] != NULL && strncmp(argv[i], JASPER_DIGEST_PREFIX, sizeof(JASPER_DIGEST_PREFIX) - 1) == 0) {
            armed = 1;
            break;
        }
    }
    if (armed && !registered) {
        registered = 1;
        registerExitHandler();
    }
}

void jasperGuardAfterLaunch(void) {
    returned = 1;
}

void jasperGuardSetRespawn(void (*replacement)(void)) {
    respawnReplacement = replacement;
}
