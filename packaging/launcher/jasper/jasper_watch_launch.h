/*
 * Jasper's launch watch (packaging/launcher/README.md; startup recovery spec section 5.1). Before JLI_Launch the
 * launcher starts jasper-watch, from beside its own executable, with the read end of a pipe whose write end only this
 * process holds, and writes one byte once its JVM exists. When this process ends first, the watcher starts Jasper
 * once more without the next thing to skip, or explains why it cannot start.
 */
#ifndef jasper_watch_launch_h
#define jasper_watch_launch_h

#ifdef __cplusplus
extern "C" {
#endif

/*
 * The first call in this process: unless JASPER_LAUNCH_DIAGNOSE is set, starts the watcher for the next start argv
 * selects (jasperNextStartVariable) and a thread that sends the byte once the loaded libjvm reports a VM. Then it
 * removes JASPER_VM_OPTIONS_SKIP, JASPER_AOT_CACHE_SKIP and JASPER_LAUNCH_DIAGNOSE from the environment, so the JVM and
 * its children never inherit them. Later calls (macOS runs main again on a new thread) do nothing.
 */
void jasperWatchBeforeLaunch(int argc, char** argv);

/* JLI_Launch returned. With a libjvm loaded the JVM existed (or libjli refused its arguments), so the byte is sent. */
void jasperWatchAfterLaunch(void);

/*
 * Starts jasper-watch from beside this executable: "retry" with nextVariable=launch-failed in its environment, or
 * "report" when nextVariable is NULL. Returns 0 when it runs; otherwise the launch goes on unwatched. This process's
 * environment is left as it was. Called by jasperWatchBeforeLaunch and by the watcher's test launcher.
 */
int jasperWatchStart(const char* nextVariable);

/* Sends the byte and closes the pipe: the JVM exists. Idempotent; does nothing without a watcher. */
void jasperWatchSignal(void);

/* Tests only: 0 keeps jasperWatchBeforeLaunch from starting the polling thread, so that jasperWatchAfterLaunch and the
   exit handler alone must send the byte. */
void jasperWatchSetPolling(int enabled);

#ifdef __cplusplus
}
#endif

#endif /* jasper_watch_launch_h */
