// See JasperVmOptions.h. Environment and file access around JasperVmOptionsCore, with jpackage's own helpers.
#include "JasperVmOptions.h"

#include <cstdio>
#include <new>
#include <string>
#include <vector>

#include "CfgFile.h"
#include "FileUtils.h"
#include "JasperVmOptionsCore.h"
#include "Log.h"
#include "SysInfo.h"
#include "tstrings.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <stdlib.h>
#include <sys/stat.h>
#endif

namespace jasper {

namespace {

vmoptions::Os hostOs() {
#if defined(_WIN32)
    return vmoptions::Os::Windows;
#elif defined(__APPLE__)
    return vmoptions::Os::Mac;
#else
    return vmoptions::Os::Linux;
#endif
}

std::string utf8(const tstring& value) {
    return tstrings::toUtf8(value);
}

tstring native(const std::string& value) {
    return tstrings::fromUtf8(value);
}

vmoptions::Environment environment() {
    vmoptions::Environment env;
    const char* const names[] = { vmoptions::VM_OPTIONS_VARIABLE, vmoptions::SKIP_VARIABLE, vmoptions::HOME_VARIABLE,
        "HOME", "XDG_CONFIG_HOME", "APPDATA", "USERPROFILE" };
    for (const char* name : names) {
        const tstring key = native(name);
        if (SysInfo::isEnvVariableSet(key)) {
            env[name] = utf8(SysInfo::getEnvVariable(std::nothrow, key));
        }
    }
    return env;
}

// Exists and is not a directory (stat follows symbolic links, as upstream's isFileExists does). Upstream defines
// FileUtils::isDirectory on Windows only.
bool isRegularFile(const std::string& path) {
#ifdef _WIN32
    const tstring value = native(path);
    return FileUtils::isFileExists(value) && !FileUtils::isDirectory(value);
#else
    struct stat info;
    return stat(path.c_str(), &info) == 0 && !S_ISDIR(info.st_mode);
#endif
}

// The file's bytes; false when it is not a regular file or cannot be read.
bool readBytes(const std::string& path, std::string& bytes) {
    if (!isRegularFile(path)) {
        return false;
    }
#ifdef _WIN32
    FILE* file = _wfopen(native(path).c_str(), L"rb");
#else
    FILE* file = std::fopen(path.c_str(), "rb");
#endif
    if (file == nullptr) {
        return false;
    }
    char buffer[4096];
    size_t count;
    while ((count = std::fread(buffer, 1, sizeof(buffer), file)) > 0) {
        bytes.append(buffer, count);
    }
    const bool failed = std::ferror(file) != 0;
    std::fclose(file);
    return !failed;
}

void removeVariable(const char* name) {
#ifdef _WIN32
    SetEnvironmentVariableW(native(name).c_str(), nullptr);
#else
    unsetenv(name);
#endif
}

} // namespace

void applyUserVmOptions(CfgFile& cfgFile) {
    try {
        std::vector<std::string> bundled;
        const CfgFile::Properties& section = cfgFile.getProperties(SectionName::JavaOptions);
        const CfgFile::Properties::const_iterator options = section.find(PropertyName::javaOptions);
        if (options != section.end()) {
            for (const tstring& option : options->second) {
                bundled.push_back(utf8(option));
            }
        }
        const vmoptions::Environment env = environment();
        const std::string path = vmoptions::userFilePath(hostOs(), env, utf8(FileUtils::toAbsolutePath(tstring())),
            isRegularFile);
        std::string bytes;
        vmoptions::UserFile file;
        const bool exists = !path.empty() && readBytes(path, bytes);
        if (exists) {
            file = vmoptions::parse(bytes);
        }
        const vmoptions::Plan plan = vmoptions::plan(bundled, path, exists ? &file : nullptr, env);
        if (plan.removeSkipVariable) {
            removeVariable(vmoptions::SKIP_VARIABLE);
        }
        tstring_array merged;
        for (const std::string& option : plan.javaOptions) {
            merged.push_back(native(option));
        }
        cfgFile.setPropertyValue(SectionName::JavaOptions, PropertyName::javaOptions, merged);
        LOG_TRACE(tstrings::any() << "Jasper VM options file: " << native(path));
    } catch (...) {
        LOG_ERROR(tstrings::any() << "Jasper: the user's VM options were ignored after an error");
    }
}

} // namespace jasper
