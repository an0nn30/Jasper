/*
 * Test harness for jasper_launch_guard.c (packaging/launcher/README.md). Arms the guard as jvmLauncherStartJvm does,
 * with the re-spawn replaced by writing the file named by GUARD_HARNESS_MARKER, then runs JLI_Launch from the libjli
 * named by GUARD_HARNESS_JLI with this program's arguments as java's. On macOS libjli calls main again on a new thread
 * with the same arguments, so the settings come from the environment.
 */
#include "jasper_launch_guard.h"

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

static void writeMarker(void) {
    const char* marker = getenv("GUARD_HARNESS_MARKER");
    FILE* file = marker == NULL ? NULL : fopen(marker, "w");
    if (file != NULL) {
        fputs("respawned\n", file);
        fclose(file);
    }
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
    const char* library = getenv("GUARD_HARNESS_JLI");
    LaunchFunction launch = library == NULL ? NULL : loadLaunch(library);
    int status;
    if (launch == NULL) {
        fprintf(stderr, "guard-harness: cannot load JLI_Launch from GUARD_HARNESS_JLI\n");
        return 65;
    }
    jasperGuardSetRespawn(writeMarker);
    jasperGuardBeforeLaunch(argc, argv);
    status = launch(argc, argv, 0, NULL, 0, NULL, "", "", "java", "java", JNI_FALSE, JNI_FALSE, JNI_FALSE, 0);
    jasperGuardAfterLaunch();
    return status;
}
