// See JasperVmOptions.h. Environment and file access around JasperVmOptionsCore, with jpackage's own helpers.
#include "JasperVmOptions.h"

#include <cstdint>
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
        JASPER_AOT_CACHE_SKIP_VARIABLE, JASPER_LAUNCH_DIAGNOSE_VARIABLE, "HOME", "XDG_CONFIG_HOME", "APPDATA",
        "USERPROFILE" };
    for (const char* name : names) {
        const tstring key = native(name);
        if (SysInfo::isEnvVariableSet(key)) {
            env[name] = utf8(SysInfo::getEnvVariable(std::nothrow, key));
        }
    }
    return env;
}

// The working directory, needed only for a relative JASPER_HOME; empty when it cannot be determined (a failing getcwd
// must not cost the file when JASPER_HOME is absolute or unset).
std::string workingDirectory() {
    try {
        return utf8(FileUtils::toAbsolutePath(tstring()));
    } catch (...) {
        return std::string();
    }
}

// A regular file: not a directory, FIFO or device (stat follows symbolic links, as upstream's isFileExists does).
// Upstream defines FileUtils::isDirectory on Windows only.
bool isRegularFile(const std::string& path) {
#ifdef _WIN32
    const tstring value = native(path);
    return FileUtils::isFileExists(value) && !FileUtils::isDirectory(value);
#else
    struct stat info;
    return stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode);
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

// What is at the cache's path, as the record compares it: size and modification time in whole seconds since the epoch.
vmoptions::CacheFile cacheFile(const std::string& path) {
    vmoptions::CacheFile result;
#ifdef _WIN32
    WIN32_FILE_ATTRIBUTE_DATA data;
    if (GetFileAttributesExW(native(path).c_str(), GetFileExInfoStandard, &data)
            && (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0) {
        // FILETIME counts 100 ns intervals since 1601; 11644473600 s lie between 1601 and 1970.
        const std::uint64_t ticks = (static_cast<std::uint64_t>(data.ftLastWriteTime.dwHighDateTime) << 32)
            | data.ftLastWriteTime.dwLowDateTime;
        result.regular = true;
        result.size = (static_cast<std::uint64_t>(data.nFileSizeHigh) << 32) | data.nFileSizeLow;
        result.modified = static_cast<std::int64_t>(ticks / 10000000u) - 11644473600LL;
    }
#else
    struct stat info;
    if (stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode)) {
        result.regular = true;
        result.size = static_cast<std::uint64_t>(info.st_size);
        result.modified = static_cast<std::int64_t>(info.st_mtime);
    }
#endif
    return result;
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
        const std::string directory = workingDirectory();
        const std::string path = vmoptions::userFilePath(hostOs(), env, directory, isRegularFile);
        std::string bytes;
        vmoptions::UserFile file;
        const bool exists = !path.empty() && readBytes(path, bytes);
        if (exists) {
            file = vmoptions::parse(bytes);
        }
        const vmoptions::Plan plan = vmoptions::plan(bundled, path, exists ? &file : nullptr, env);
        const std::string recordPath = vmoptions::aotRecordPath(hostOs(), env, directory);
        std::string record;
        const bool remembered = !recordPath.empty() && vmoptions::namesAotCache(plan.javaOptions)
            && readBytes(recordPath, record);
        const vmoptions::CacheFile cache = vmoptions::namesAotCache(plan.javaOptions)
            ? cacheFile(vmoptions::aotCachePath(plan.javaOptions)) : vmoptions::CacheFile();
        tstring_array merged;
        for (const std::string& option : vmoptions::applyAotCache(plan.javaOptions, env, recordPath,
                remembered ? &record : nullptr, cache)) {
            merged.push_back(native(option));
        }
        cfgFile.setPropertyValue(SectionName::JavaOptions, PropertyName::javaOptions, merged);
        LOG_TRACE(tstrings::any() << "Jasper VM options file: " << native(path));
    } catch (...) {
        try {
            LOG_ERROR(tstrings::any() << "Jasper: the user's VM options were ignored after an error");
        } catch (...) {
        }
    }
}

} // namespace jasper
