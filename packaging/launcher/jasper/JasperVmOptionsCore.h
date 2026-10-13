// The launcher's handling of the user's jasper.vmoptions, as pure functions over UTF-8 strings
// (packaging/launcher/README.md; spec sections 3 and 4.2). No upstream or platform code.
#ifndef JasperVmOptionsCore_h
#define JasperVmOptionsCore_h

#include <functional>
#include <map>
#include <string>
#include <vector>

namespace jasper {
namespace vmoptions {

enum class Os { Mac, Linux, Windows };

/** Environment variables by name, UTF-8; a missing key is an unset variable. */
typedef std::map<std::string, std::string> Environment;

const char* const FILE_NAME = "jasper.vmoptions";
const char* const VM_OPTIONS_VARIABLE = "JASPER_VM_OPTIONS";
const char* const SKIP_VARIABLE = "JASPER_VM_OPTIONS_SKIP";
const char* const HOME_VARIABLE = "JASPER_HOME";

/** The user's options file, parsed. */
struct UserFile {
    std::vector<std::string> lines;
    std::vector<std::string> ignored;
    bool corrupted = false;
};

/**
 * The file the launcher reads: JASPER_VM_OPTIONS when it names a regular file, else <JASPER_HOME>/jasper.vmoptions,
 * else the OS default in the Jasper home. Empty when no location can be determined.
 */
std::string userFilePath(Os os, const Environment& env, const std::string& workingDirectory,
                         const std::function<bool(const std::string&)>& isRegularFile);

/** Whether a line is passed to the JVM: it starts with '-' and is not one of libjli's own launcher options. */
bool isApplicable(const std::string& line);

/**
 * One option per line, trimmed; blank and '#' lines skipped; a leading BOM skipped; NUL or bad UTF-8 corrupts. A kept
 * line goes to lines when isApplicable, otherwise to ignored.
 */
UserFile parse(const std::string& bytes);

/** The bundled options without those the user's lines replace, then the user's lines. */
std::vector<std::string> merge(const std::vector<std::string>& bundled, const std::vector<std::string>& user);

/** FNV-1a 64 over each line's bytes followed by '\n', as 16 lowercase hex digits. */
std::string digest(const std::vector<std::string>& lines);

/** The JVM options to launch with. */
struct Plan {
    std::vector<std::string> javaOptions;
};

/** The whole decision. file is null when the file does not exist or cannot be read. */
Plan plan(const std::vector<std::string>& bundled, const std::string& path, const UserFile* file,
          const Environment& env);

} // namespace vmoptions
} // namespace jasper

#endif // JasperVmOptionsCore_h
