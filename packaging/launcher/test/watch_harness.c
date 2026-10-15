/*
 * Test harness for jasper_watch_launch.c (packaging/launcher/README.md, testLauncherWatch). Runs JLI_Launch from the
 * libjli named by WATCH_HARNESS_JLI with this program's arguments as java's, between the watch module's two hooks, as
 * jvmLauncherStartJvm does; the real jasper-watch is beside this program. A start that finds WATCH_HARNESS_STARTED set
 * is the watcher's retry: it appends its arguments and skip variables to $WATCH_HARNESS_RECORD and exits 0. The
 * watcher's diagnostic launch appends a line too, then runs the JVM. On macOS libjli calls main again on a new thread
 * with the same arguments, so only the first call does this. WATCH_HARNESS_NO_POLLING turns the watch module's polling
 * thread off, leaving the byte to JLI_Launch's return and the exit handler.
 */
#include "jasper_launch_rules.h"
#include "jasper_watch_launch.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

typedef int (JNICALL *LaunchFunction)(int, char**, int, const char**, int, const char**, const char*, const char*,
                                      const char*, const char*, jboolean, jboolean, jboolean, jint);

static int called;

static const char* shown(const char* value) {
    return value == NULL ? "-" : value;
}

static void record(const char* kind, int argc, char** argv) {
    const char* path = getenv("WATCH_HARNESS_RECORD");
    FILE* file = path == NULL ? NULL : fopen(path, "ab");
    int i;
    if (file == NULL) {
        return;
    }
    fprintf(file, "%s aot=%s vm=%s diagnose=%s args=", kind, shown(getenv(JASPER_AOT_CACHE_SKIP_VARIABLE)),
        shown(getenv(JASPER_VM_OPTIONS_SKIP_VARIABLE)), shown(getenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE)));
    for (i = 1; i < argc; ++i) {
        fprintf(file, "%s%s", i == 1 ? "" : "|", argv[i]);
    }
    fputc('\n', file);
    fclose(file);
}

static LaunchFunction loadLaunch(const char* library) {
#ifdef _WIN32
    char directory[MAX_PATH];
    char* separator;
    HMODULE jli;
    strncpy(directory, library, sizeof(directory) - 1);
    directory[sizeof(directory) - 1] = '\0';
    separator = strrchr(directory, '\\');
    if (separator == NULL) {
        separator = strrchr(directory, '/');
    }
    if (separator != NULL) {
        *separator = '\0';
        SetDllDirectoryA(directory);
    }
    jli = LoadLibraryA(library);
    return jli == NULL ? NULL : (LaunchFunction) GetProcAddress(jli, "JLI_Launch");
#else
    void* jli = dlopen(library, RTLD_NOW | RTLD_GLOBAL);
    return jli == NULL ? NULL : (LaunchFunction) dlsym(jli, "JLI_Launch");
#endif
}

int main(int argc, char** argv) {
    const char* library = getenv("WATCH_HARNESS_JLI");
    LaunchFunction launch;
    int status;
    if (!called) {
        called = 1;
        if (getenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE) != NULL) {
            record("diagnose", argc, argv);
        } else if (getenv("WATCH_HARNESS_STARTED") != NULL) {
            record("retried", argc, argv);
            return 0;
        }
#ifdef _WIN32
        _putenv_s("WATCH_HARNESS_STARTED", "1");
#else
        setenv("WATCH_HARNESS_STARTED", "1", 1);
#endif
    }
    launch = library == NULL ? NULL : loadLaunch(library);
    if (launch == NULL) {
        fprintf(stderr, "watch-harness: cannot load JLI_Launch from WATCH_HARNESS_JLI\n");
        return 65;
    }
    if (getenv("WATCH_HARNESS_NO_POLLING") != NULL) {
        jasperWatchSetPolling(0);
    }
    jasperWatchBeforeLaunch(argc, argv);
    status = launch(argc, argv, 0, NULL, 0, NULL, "", "", "java", "java", JNI_FALSE, JNI_FALSE, JNI_FALSE, 0);
    jasperWatchAfterLaunch();
    return status;
}
