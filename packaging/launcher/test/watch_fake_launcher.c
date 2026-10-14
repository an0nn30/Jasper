/*
 * The test launcher of testWatcher (packaging/launcher/README.md). It starts jasper-watch from beside itself through
 * the launcher's own watch module (jasper_watch_launch.c), then ends the way its first argument says, without a JVM:
 *
 *   byte     sends the byte, then exits 0
 *   exit     exit(1) before the byte
 *   _exit    _exit(1) before the byte
 *   kill     (POSIX) SIGKILL before the byte
 *   ctrl-c   (Windows) ends with STATUS_CONTROL_C_EXIT before the byte
 *   report*  starts the watcher in report mode and exits 1
 *
 * A start with JASPER_LAUNCH_DIAGNOSE set is the watcher's diagnostic launch, and one with JASPER_VM_OPTIONS_SKIP set
 * its retry: each appends a line about itself to $WATCH_FAKE_RECORD. The diagnostic launch of report prints a JVM
 * error and exits 1, of report-pass exits 0, of report-hang sleeps for a minute, of report-silent exits 1 and prints
 * nothing, of report-long prints 2,000 x and TAIL-END, and of report-utf8 prints 1,600 e-acute; the last two exit 1.
 */
#include "jasper_launch_rules.h"
#include "jasper_watch_launch.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <signal.h>
#include <unistd.h>
#endif

static const char* shown(const char* value) {
    return value == NULL ? "-" : value;
}

static void record(const char* kind, int argc, char** argv) {
    const char* path = getenv("WATCH_FAKE_RECORD");
    FILE* file = path == NULL ? NULL : fopen(path, "ab");
    int i;
    if (file == NULL) {
        return;
    }
    fprintf(file, "%s skip=%s kept=%s diagnose=%s args=", kind, shown(getenv(JASPER_VM_OPTIONS_SKIP_VARIABLE)),
        shown(getenv("WATCH_FAKE_KEPT")), shown(getenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE)));
    for (i = 1; i < argc; ++i) {
        fprintf(file, "%s%s", i == 1 ? "" : "|", argv[i]);
    }
    fputc('\n', file);
    fclose(file);
}

static void sleepSeconds(int seconds) {
#ifdef _WIN32
    Sleep((DWORD) seconds * 1000u);
#else
    sleep((unsigned) seconds);
#endif
}

static int diagnose(const char* ending) {
    int i;
    if (strcmp(ending, "report-pass") == 0) {
        puts("fake java: version 25");
        return 0;
    }
    if (strcmp(ending, "report-hang") == 0) {
        sleepSeconds(60);
        return 0;
    }
    if (strcmp(ending, "report-silent") == 0) {
        return 1;
    }
    if (strcmp(ending, "report-long") == 0) {
        for (i = 0; i < 2000; ++i) {
            putchar('x');
        }
        puts("TAIL-END");
        return 1;
    }
    if (strcmp(ending, "report-utf8") == 0) {
        for (i = 0; i < 1600; ++i) {
            fputs("\xC3\xA9", stderr);
        }
        fputc('\n', stderr);
        return 1;
    }
    fputs("Error: fake JVM failure: Invalid maximum heap size: -Xmx3q\n", stderr);
    return 1;
}

int main(int argc, char** argv) {
    const char* ending = argc > 1 ? argv[1] : "";
    if (getenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE) != NULL) {
        record("diagnose", argc, argv);
        return diagnose(ending);
    }
    if (getenv(JASPER_VM_OPTIONS_SKIP_VARIABLE) != NULL) {
        record("retried", argc, argv);
        return 0;
    }
    if (jasperWatchStart(strncmp(ending, "report", 6) == 0 ? NULL : JASPER_VM_OPTIONS_SKIP_VARIABLE) != 0) {
        fputs("watch-fake-launcher: cannot start jasper-watch\n", stderr);
        return 70;
    }
    if (strcmp(ending, "byte") == 0) {
        jasperWatchSignal();
        return 0;
    }
    if (strcmp(ending, "_exit") == 0) {
        _exit(1);
    }
#ifdef _WIN32
    if (strcmp(ending, "ctrl-c") == 0) {
        ExitProcess((UINT) STATUS_CONTROL_C_EXIT);
    }
#else
    if (strcmp(ending, "kill") == 0) {
        kill(getpid(), SIGKILL);
    }
#endif
    exit(1);
}
