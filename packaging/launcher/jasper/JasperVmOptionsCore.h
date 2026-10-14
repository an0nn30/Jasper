// The launcher's handling of the user's jasper.vmoptions and of the start-up cache, as pure functions over UTF-8
// strings (packaging/launcher/README.md; custom VM options spec sections 3 and 4.2, startup recovery spec sections 4
// and 6.2). No upstream or platform code.
#ifndef JasperVmOptionsCore_h
#define JasperVmOptionsCore_h

#include <cstdint>
#include <functional>
#include <map>
#include <string>
#include <vector>

#include "jasper_launch_rules.h"

namespace jasper {
namespace vmoptions {

enum class Os { Mac, Linux, Windows };

/** Environment variables by name, UTF-8; a missing key is an unset variable. */
typedef std::map<std::string, std::string> Environment;

const char* const FILE_NAME = "jasper.vmoptions";
const char* const VM_OPTIONS_VARIABLE = "JASPER_VM_OPTIONS";
const char* const SKIP_VARIABLE = JASPER_VM_OPTIONS_SKIP_VARIABLE;
const char* const HOME_VARIABLE = "JASPER_HOME";
const char* const AOT_RECORD_FILE_NAME = "jasper-aot.failed";

/** The user's options file, parsed. */
struct UserFile {
    std::vector<std::string> lines;
    std::vector<std::string> ignored;
    bool corrupted = false;
};

/**
 * The Jasper home: JASPER_HOME (trimmed; a relative value resolves from workingDirectory), else the OS default. Empty
 * when no location can be determined.
 */
std::string jasperHome(Os os, const Environment& env, const std::string& workingDirectory);

/**
 * The file the launcher reads: JASPER_VM_OPTIONS when it names a regular file, else jasper.vmoptions in the Jasper
 * home. Empty when no location can be determined.
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

/** <Jasper home>/jasper-aot.failed, the remembered start-up cache failure, whatever JASPER_VM_OPTIONS says. Empty
    without a home. */
std::string aotRecordPath(Os os, const Environment& env, const std::string& workingDirectory);

/** What is at the cache's path: a regular file, its size in bytes and its modification time in whole seconds since
    the epoch (rounded down). */
struct CacheFile {
    bool regular = false;
    std::uint64_t size = 0;
    std::int64_t modified = 0;
};

/** The remembered failure. valid is false for a malformed record, which matches nothing. */
struct AotRecord {
    bool valid = false;
    std::string cache;
    std::uint64_t size = 0;
    std::uint64_t modified = 0;
    std::string options;
};

/**
 * Reads a record (spec 6.2): lines split on '\n', one trailing '\r' removed; empty lines and lines starting with '#'
 * skipped; every other line is key=value, split at the first '=', with exactly the keys cache (non-empty), size and
 * modified (1 to 19 ASCII digits) and options (empty or 16 lowercase hex digits), each once.
 */
AotRecord parseAotRecord(const std::string& bytes);

/**
 * Whether the record matches the file at the cache's path in a start whose digest is digest (empty: no digest): the
 * record is valid, the file is there with the record's size and modification time, and options is empty or the digest.
 * The record's cache path is information for people and is not compared.
 */
bool aotRecordMatches(const AotRecord& record, const CacheFile& cache, const std::string& digest);

/** Whether an option starts with -XX:AOTCache=. */
bool namesAotCache(const std::vector<std::string>& options);

/** The value of the last -XX:AOTCache= option, the one the JVM uses; empty when there is none. */
std::string aotCachePath(const std::vector<std::string>& options);

/**
 * The start-up cache's part of the decision, on the merged options: -Djasper.aotCacheRecord=<recordPath> whenever they
 * name a cache, decided before anything is removed; while it is in use, every -XX:AOTCache= removed and
 * -Djasper.aotCacheSkipped=<reason> added when
 * JASPER_AOT_CACHE_SKIP is set (its trimmed value, "environment" when blank) or the record matches ("remembered"); and
 * -version last when JASPER_LAUNCH_DIAGNOSE is set. record is null when the file is missing or unreadable; cache
 * describes aotCachePath(options).
 */
std::vector<std::string> applyAotCache(const std::vector<std::string>& options, const Environment& env,
                                       const std::string& recordPath, const std::string* record,
                                       const CacheFile& cache);

} // namespace vmoptions
} // namespace jasper

#endif // JasperVmOptionsCore_h
