// See JasperVmOptionsCore.h.
#include "JasperVmOptionsCore.h"

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

std::string inJasperDirectory(Os os, const std::string& configurationRoot) {
    return join(os, join(os, configurationRoot, "jasper"), FILE_NAME);
}

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

} // namespace

std::string userFilePath(Os os, const Environment& env, const std::string& workingDirectory,
                         const std::function<bool(const std::string&)>& isRegularFile) {
    const std::string chosen = value(env, VM_OPTIONS_VARIABLE);
    if (!trim(chosen).empty() && isRegularFile(chosen)) {
        return chosen;
    }
    const std::string home = trim(value(env, HOME_VARIABLE));
    if (!home.empty()) {
        return join(os, isAbsolute(os, home) ? home : join(os, workingDirectory, home), FILE_NAME);
    }
    if (os == Os::Windows) {
        const std::string appData = value(env, "APPDATA");
        if (isAbsolute(os, appData)) {
            return inJasperDirectory(os, appData);
        }
        const std::string profile = value(env, "USERPROFILE");
        return profile.empty() ? std::string()
                               : inJasperDirectory(os, join(os, join(os, profile, "AppData"), "Roaming"));
    }
    if (os == Os::Linux) {
        const std::string xdg = value(env, "XDG_CONFIG_HOME");
        if (isAbsolute(os, xdg)) {
            return inJasperDirectory(os, xdg);
        }
    }
    const std::string userHome = value(env, "HOME");
    return userHome.empty() ? std::string() : inJasperDirectory(os, join(os, userHome, ".config"));
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
            result.lines.push_back(line);
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
    const std::string reason = skip == nullptr ? std::string() : trim(*skip);
    const bool applied = reason.empty() && !path.empty() && file != nullptr && !file->corrupted && !file->lines.empty();
    if (applied) {
        result.javaOptions = merge(bundled, file->lines);
    }
    if (!path.empty()) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile=" + path);
    }
    if (!reason.empty()) {
        result.javaOptions.push_back("-Djasper.vmOptionsSkipped=" + reason);
    } else if (!path.empty() && file != nullptr && file->corrupted) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile.corrupted=true");
    } else if (applied) {
        result.javaOptions.push_back("-Djasper.vmOptionsDigest=" + digest(file->lines));
    }
    return result;
}

} // namespace vmoptions
} // namespace jasper
