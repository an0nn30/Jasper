/*
 * Jasper's launch guard (packaging/launcher/README.md, spec section 4): when the JVM cannot be created with the
 * user's options, start this launcher again with JASPER_VM_OPTIONS_SKIP=launch-failed.
 */
#ifndef jasper_launch_guard_h
#define jasper_launch_guard_h

#ifdef __cplusplus
extern "C" {
#endif

/* Removes JASPER_VM_OPTIONS_SKIP from the environment, then arms the guard when argv carries
   -Djasper.vmOptionsDigest= (the user's options were applied). Idempotent. */
void jasperGuardBeforeLaunch(int argc, char** argv);

/* Records that JLI_Launch returned: the JVM was created and has finished. */
void jasperGuardAfterLaunch(void);

/* Tests only: replaces the re-spawn of this executable. */
void jasperGuardSetRespawn(void (*replacement)(void));

#ifdef __cplusplus
}
#endif

#endif /* jasper_launch_guard_h */
