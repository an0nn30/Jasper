// See JasperVmOptionsCore.h.
#include "JasperVmOptionsCore.h"

#include <algorithm>
#include <cstdint>
#include <cstdio>

namespace jasper {
namespace vmoptions {

namespace {

const char* const WHITESPACE = " \t\r\n\f\v";

std::string trim(const std::string& value) {
    const std::string::size_type first = value.find_first_not_of(WHITESPACE);
    if (first == std::string::npos) {
        return std::string();
    }
    const std::string::size_type last = value.find_last_not_of(WHITESPACE);
    return value.substr(first, last - first + 1);
}

bool startsWith(const std::string& value, const std::string& prefix) {
    return value.size() >= prefix.size() && value.compare(0, prefix.size(), prefix) == 0;
}

bool endsWith(const std::string& value, const std::string& suffix) {
    return value.size() >= suffix.size()
        && value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

const std::string* variable(const Environment& env, const char* name) {
    const Environment::const_iterator found = env.find(name);
    return found == env.end() ? nullptr : &found->second;
}

std::string value(const Environment& env, const char* name) {
    const std::string* found = variable(env, name);
    return found == nullptr ? std::string() : *found;
}

bool isAbsolute(Os os, const std::string& path) {
    if (os != Os::Windows) {
        return startsWith(path, "/");
    }
    if (startsWith(path, "\\\\") || startsWith(path, "//")) {
        return true;
    }
    const bool drive = path.size() >= 3
        && ((path[0] >= 'A' && path[0] <= 'Z') || (path[0] >= 'a' && path[0] <= 'z')) && path[1] == ':';
    return drive && (path[2] == '\\' || path[2] == '/');
}

std::string join(Os os, const std::string& directory, const std::string& child) {
    if (directory.empty()) {
        return child;
    }
    const char last = directory[directory.size() - 1];
    if (last == '/' || (os == Os::Windows && last == '\\')) {
        return directory + child;
    }
    return directory + (os == Os::Windows ? "\\" : "/") + child;
}

// libjli's own launcher options. As JVM options they end option parsing, select a mode or take the next argument, so
// the JVM would start without running the application (spec 3.2).
const char* const LAUNCHER_OPTIONS[] = {
    "-version", "--version", "-help", "--help", "-h", "-?", "-X", "--help-extra", "--dry-run", "--list-modules",
    "-d", "--describe-module", "--validate-modules", "-jar", "-m", "--module", "-cp", "-classpath", "--class-path",
    "--source", "--add-opens", "--add-exports", "--add-reads", "--add-modules", "--limit-modules", "--module-path", "-p",
    "--upgrade-module-path", "--patch-module", "--enable-native-access" };
const char* const LAUNCHER_OPTION_PREFIXES[] = { "--describe-module=", "--module=", "--class-path=", "--source=" };

bool isValidUtf8(const std::string& bytes) {
    std::string::size_type i = 0;
    while (i < bytes.size()) {
        const unsigned char lead = static_cast<unsigned char>(bytes[i]);
        if (lead < 0x80u) {
            ++i;
            continue;
        }
        std::string::size_type extra = 0;
        std::uint32_t codePoint = 0;
        if (lead >= 0xC2u && lead <= 0xDFu) {
            extra = 1;
            codePoint = lead & 0x1Fu;
        } else if (lead >= 0xE0u && lead <= 0xEFu) {
            extra = 2;
            codePoint = lead & 0x0Fu;
        } else if (lead >= 0xF0u && lead <= 0xF4u) {
            extra = 3;
            codePoint = lead & 0x07u;
        } else {
            return false;
        }
        if (bytes.size() - i <= extra) {
            return false;
        }
        for (std::string::size_type k = 1; k <= extra; ++k) {
            const unsigned char next = static_cast<unsigned char>(bytes[i + k]);
            if ((next & 0xC0u) != 0x80u) {
                return false;
            }
            codePoint = (codePoint << 6) | (next & 0x3Fu);
        }
        const bool overlong = (extra == 2 && codePoint < 0x800u) || (extra == 3 && codePoint < 0x10000u);
        const bool surrogate = codePoint >= 0xD800u && codePoint <= 0xDFFFu;
        if (overlong || surrogate || codePoint > 0x10FFFFu) {
            return false;
        }
        i += extra + 1;
    }
    return true;
}

// 1 to 19 ASCII digits, which always fit in 64 unsigned bits.
bool isDecimal(const std::string& text) {
    if (text.empty() || text.size() > 19) {
        return false;
    }
    for (const char c : text) {
        if (c < '0' || c > '9') {
            return false;
        }
    }
    return true;
}

std::uint64_t decimal(const std::string& digits) {
    std::uint64_t result = 0;
    for (const char c : digits) {
        result = result * 10u + static_cast<std::uint64_t>(c - '0');
    }
    return result;
}

bool isDigest(const std::string& text) {
    if (text.size() != 16) {
        return false;
    }
    for (const char c : text) {
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            return false;
        }
    }
    return true;
}

// The value of the last option starting with prefix; empty when there is none.
std::string lastValue(const std::vector<std::string>& options, const char* prefix) {
    std::string result;
    for (const std::string& option : options) {
        if (startsWith(option, prefix)) {
            result = option.substr(std::string(prefix).size());
        }
    }
    return result;
}

bool aotCacheInUse(const std::vector<std::string>& options) {
    std::vector<const char*> pointers;
    for (const std::string& option : options) {
        pointers.push_back(option.c_str());
    }
    return jasperAotCacheInUse(static_cast<int>(pointers.size()), pointers.data()) != 0;
}

} // namespace

std::string jasperHome(Os os, const Environment& env, const std::string& workingDirectory) {
    const std::string home = trim(value(env, HOME_VARIABLE));
    if (!home.empty()) {
        return isAbsolute(os, home) ? home : join(os, workingDirectory, home);
    }
    if (os == Os::Windows) {
        const std::string appData = value(env, "APPDATA");
        if (isAbsolute(os, appData)) {
            return join(os, appData, "jasper");
        }
        const std::string profile = value(env, "USERPROFILE");
        return profile.empty() ? std::string() : join(os, join(os, join(os, profile, "AppData"), "Roaming"), "jasper");
    }
    if (os == Os::Linux) {
        const std::string xdg = value(env, "XDG_CONFIG_HOME");
        if (isAbsolute(os, xdg)) {
            return join(os, xdg, "jasper");
        }
    }
    const std::string userHome = value(env, "HOME");
    return userHome.empty() ? std::string() : join(os, join(os, userHome, ".config"), "jasper");
}

std::string userFilePath(Os os, const Environment& env, const std::string& workingDirectory,
                         const std::function<bool(const std::string&)>& isRegularFile) {
    const std::string chosen = value(env, VM_OPTIONS_VARIABLE);
    if (!trim(chosen).empty() && isRegularFile(chosen)) {
        return chosen;
    }
    const std::string home = jasperHome(os, env, workingDirectory);
    return home.empty() ? std::string() : join(os, home, FILE_NAME);
}

bool isApplicable(const std::string& line) {
    if (line.empty() || line[0] != '-') {
        return false;
    }
    for (const char* option : LAUNCHER_OPTIONS) {
        if (line == option) {
            return false;
        }
    }
    for (const char* prefix : LAUNCHER_OPTION_PREFIXES) {
        if (startsWith(line, prefix)) {
            return false;
        }
    }
    return true;
}

UserFile parse(const std::string& bytes) {
    UserFile result;
    std::string text = bytes;
    if (startsWith(text, "\xEF\xBB\xBF")) {
        text.erase(0, 3);
    }
    if (text.find('\0') != std::string::npos || !isValidUtf8(text)) {
        result.corrupted = true;
        return result;
    }
    std::string::size_type start = 0;
    while (start <= text.size()) {
        std::string::size_type end = text.find('\n', start);
        if (end == std::string::npos) {
            end = text.size();
        }
        const std::string line = trim(text.substr(start, end - start));
        if (!line.empty() && line[0] != '#') {
            (isApplicable(line) ? result.lines : result.ignored).push_back(line);
        }
        start = end + 1;
    }
    return result;
}

std::vector<std::string> merge(const std::vector<std::string>& bundled, const std::vector<std::string>& user) {
    bool dropCollector = false;
    bool dropXms = false;
    bool dropXmx = false;
    for (const std::string& line : user) {
        if (startsWith(line, "-XX:+") && endsWith(line, "GC")) {
            dropCollector = true;
        }
        if (startsWith(line, "-XX:InitialRAMPercentage=")) {
            dropXms = true;
        }
        if (startsWith(line, "-XX:MinRAMPercentage=") || startsWith(line, "-XX:MaxRAMPercentage=")) {
            dropXmx = true;
        }
    }
    std::vector<std::string> result;
    for (const std::string& option : bundled) {
        const bool collector = startsWith(option, "-XX:+") && endsWith(option, "GC");
        if ((dropCollector && collector) || (dropXms && startsWith(option, "-Xms"))
                || (dropXmx && startsWith(option, "-Xmx"))) {
            continue;
        }
        result.push_back(option);
    }
    result.insert(result.end(), user.begin(), user.end());
    return result;
}

std::string digest(const std::vector<std::string>& lines) {
    std::uint64_t hash = 0xcbf29ce484222325ULL;
    const auto add = [&hash](unsigned char byte) {
        hash ^= byte;
        hash *= 0x100000001b3ULL;
    };
    for (const std::string& line : lines) {
        for (const char c : line) {
            add(static_cast<unsigned char>(c));
        }
        add('\n');
    }
    char text[17];
    std::snprintf(text, sizeof(text), "%016llx", static_cast<unsigned long long>(hash));
    return std::string(text);
}

Plan plan(const std::vector<std::string>& bundled, const std::string& path, const UserFile* file,
          const Environment& env) {
    Plan result;
    result.javaOptions = bundled;
    const std::string* skip = variable(env, SKIP_VARIABLE);
    const bool skipped = skip != nullptr;
    const bool applied = !skipped && !path.empty() && file != nullptr && !file->corrupted && !file->lines.empty();
    if (applied) {
        result.javaOptions = merge(bundled, file->lines);
    }
    if (!path.empty()) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile=" + path);
    }
    if (skipped) {
        // A blank value is set too: the watch module tests presence, so both treat this launch as a fallback start.
        const std::string reason = trim(*skip);
        result.javaOptions.push_back("-Djasper.vmOptionsSkipped=" + (reason.empty() ? std::string("environment") : reason));
    } else if (!path.empty() && file != nullptr && file->corrupted) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile.corrupted=true");
    } else if (applied) {
        result.javaOptions.push_back("-Djasper.vmOptionsDigest=" + digest(file->lines));
    }
    return result;
}

std::string aotRecordPath(Os os, const Environment& env, const std::string& workingDirectory) {
    const std::string home = jasperHome(os, env, workingDirectory);
    return home.empty() ? std::string() : join(os, home, AOT_RECORD_FILE_NAME);
}

AotRecord parseAotRecord(const std::string& bytes) {
    const AotRecord malformed;
    std::map<std::string, std::string> values;
    std::string::size_type start = 0;
    while (start <= bytes.size()) {
        std::string::size_type end = bytes.find('\n', start);
        if (end == std::string::npos) {
            end = bytes.size();
        }
        std::string line = bytes.substr(start, end - start);
        start = end + 1;
        if (!line.empty() && line[line.size() - 1] == '\r') {
            line.erase(line.size() - 1);
        }
        if (line.empty() || line[0] == '#') {
            continue;
        }
        const std::string::size_type equals = line.find('=');
        if (equals == std::string::npos) {
            return malformed;
        }
        const std::string key = line.substr(0, equals);
        if (key != "cache" && key != "size" && key != "modified" && key != "options") {
            return malformed;
        }
        if (!values.insert(std::make_pair(key, line.substr(equals + 1))).second) {
            return malformed;
        }
    }
    if (values.size() != 4 || values["cache"].empty() || !isDecimal(values["size"]) || !isDecimal(values["modified"])
            || !(values["options"].empty() || isDigest(values["options"]))) {
        return malformed;
    }
    AotRecord record;
    record.valid = true;
    record.cache = values["cache"];
    record.size = decimal(values["size"]);
    record.modified = decimal(values["modified"]);
    record.options = values["options"];
    return record;
}

bool aotRecordMatches(const AotRecord& record, const CacheFile& cache, const std::string& digest) {
    return record.valid && cache.regular && cache.size == record.size
        && cache.modified >= 0 && static_cast<std::uint64_t>(cache.modified) == record.modified
        && (record.options.empty() || record.options == digest);
}

bool namesAotCache(const std::vector<std::string>& options) {
    for (const std::string& option : options) {
        if (startsWith(option, JASPER_AOT_CACHE_OPTION)) {
            return true;
        }
    }
    return false;
}

std::string aotCachePath(const std::vector<std::string>& options) {
    return lastValue(options, JASPER_AOT_CACHE_OPTION);
}

std::vector<std::string> applyAotCache(const std::vector<std::string>& options, const Environment& env,
                                       const std::string& recordPath, const std::string* record,
                                       const CacheFile& cache) {
    std::vector<std::string> result = options;
    if (namesAotCache(options)) {
        if (!recordPath.empty()) {
            result.push_back("-Djasper.aotCacheRecord=" + recordPath);
        }
        if (aotCacheInUse(options)) {
            std::string reason;
            const std::string* skip = variable(env, JASPER_AOT_CACHE_SKIP_VARIABLE);
            if (skip != nullptr) {
                reason = trim(*skip).empty() ? std::string("environment") : trim(*skip);
            } else if (record != nullptr
                    && aotRecordMatches(parseAotRecord(*record), cache, lastValue(options, JASPER_DIGEST_OPTION))) {
                reason = "remembered";
            }
            if (!reason.empty()) {
                result.erase(std::remove_if(result.begin(), result.end(), [](const std::string& option) {
                    return startsWith(option, JASPER_AOT_CACHE_OPTION);
                }), result.end());
                result.push_back("-Djasper.aotCacheSkipped=" + reason);
            }
        }
    }
    if (variable(env, JASPER_LAUNCH_DIAGNOSE_VARIABLE) != nullptr) {
        result.push_back("-version");
    }
    return result;
}

} // namespace vmoptions
} // namespace jasper
