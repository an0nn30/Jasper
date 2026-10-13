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
static void (*respawnReplacement)(void);

/* The number of created Java VMs, or -1 when it cannot be told (libjvm is not loaded). */
static int createdJavaVms(void) {
    CreatedJavaVmsFunction function;
    JavaVM* vms[1];
    jsize count = 0;
#ifdef _WIN32
    HMODULE jvm = GetModuleHandleW(L"jvm.dll");
    if (jvm == NULL) {
        return -1;
    }
    function = (CreatedJavaVmsFunction) GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
#else
    function = (CreatedJavaVmsFunction) dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs");
#endif
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

/* The original command line, read from /proc; arguments beyond 64 KiB or 1023 entries are dropped. */
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
