/*
 * Rules over the JVM's arguments that the cfg hook (C++, JasperVmOptionsCore) and the watch module (C,
 * jasper_watch_launch) must agree on (packaging/launcher/README.md; startup recovery spec sections 4 and 5.1). Pure
 * C11: no platform or upstream code.
 */
#ifndef jasper_launch_rules_h
#define jasper_launch_rules_h

#ifdef __cplusplus
extern "C" {
#endif

#define JASPER_VM_OPTIONS_SKIP_VARIABLE "JASPER_VM_OPTIONS_SKIP"
#define JASPER_AOT_CACHE_SKIP_VARIABLE "JASPER_AOT_CACHE_SKIP"
#define JASPER_LAUNCH_DIAGNOSE_VARIABLE "JASPER_LAUNCH_DIAGNOSE"
#define JASPER_WATCH_REPORT_VARIABLE "JASPER_WATCH_REPORT"
#define JASPER_LAUNCH_FAILED "launch-failed"
#define JASPER_AOT_CACHE_OPTION "-XX:AOTCache="
#define JASPER_AOT_MODE_OPTION "-XX:AOTMode="
#define JASPER_DIGEST_OPTION "-Djasper.vmOptionsDigest="

/* Whether the options use an AOT cache: one starts with -XX:AOTCache= and the last -XX:AOTMode= option, if any, is not
   -XX:AOTMode=off (HotSpot's last-wins rule). */
int jasperAotCacheInUse(int count, const char* const* options);

/* Whether an option starts with -Djasper.vmOptionsDigest= (the user's options were applied). */
int jasperHasVmOptionsDigest(int count, const char* const* options);

/* The variable the next start adds as JASPER_LAUNCH_FAILED when this one fails before its JVM exists:
   JASPER_AOT_CACHE_SKIP_VARIABLE while the cache is in use and this start did not skip it, else
   JASPER_VM_OPTIONS_SKIP_VARIABLE while the options carry a digest and this start did not skip them, else NULL (the
   watcher reports instead). Each start skips at least one more thing than the last, so the sequence ends. */
const char* jasperNextStartVariable(int count, const char* const* options, int cacheSkipped, int optionsSkipped);

#ifdef __cplusplus
}
#endif

#endif /* jasper_launch_rules_h */
