// Applies the user's jasper.vmoptions to the launcher's cfg (packaging/launcher/README.md). Called from
// AppLauncher::createJvmLauncher; never throws.
#ifndef JasperVmOptions_h
#define JasperVmOptions_h

class CfgFile;

namespace jasper {

/** Replaces the cfg's java-options with the bundled options merged with the user's file (JasperVmOptionsCore). */
void applyUserVmOptions(CfgFile& cfgFile);

} // namespace jasper

#endif // JasperVmOptions_h
