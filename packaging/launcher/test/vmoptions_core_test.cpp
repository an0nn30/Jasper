// Native tests of JasperVmOptionsCore and jasper_launch_rules (packaging/launcher/README.md).
// Usage: vmoptions-core-test <digest-vectors.txt> <aot-record-vectors.txt>
#include "JasperVmOptionsCore.h"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

namespace vm = jasper::vmoptions;
using vm::AotRecord;
using vm::CacheFile;
using vm::Environment;
using vm::Os;
using vm::Plan;
using vm::UserFile;

namespace {

int failures = 0;
int checks = 0;

void check(bool condition, const std::string& what) {
    ++checks;
    if (!condition) {
        ++failures;
        std::cerr << "FAIL: " << what << "\n";
    }
}

std::string show(const std::vector<std::string>& values) {
    std::string text = "[";
    for (std::vector<std::string>::size_type i = 0; i < values.size(); ++i) {
        text += (i == 0 ? "" : " | ") + values[i];
    }
    return text + "]";
}

void expectLines(const std::vector<std::string>& actual, const std::vector<std::string>& expected,
                 const std::string& what) {
    check(actual == expected, what + ": got " + show(actual) + ", expected " + show(expected));
}

void expectText(const std::string& actual, const std::string& expected, const std::string& what) {
    check(actual == expected, what + ": got \"" + actual + "\", expected \"" + expected + "\"");
}

std::function<bool(const std::string&)> existing(const std::vector<std::string>& files) {
    return [files](const std::string& path) { return std::find(files.begin(), files.end(), path) != files.end(); };
}

void location() {
    const auto none = existing({});
    expectText(vm::userFilePath(Os::Mac, {{"HOME", "/Users/a"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "macOS default");
    expectText(vm::userFilePath(Os::Mac, {{"HOME", "/Users/a"}, {"XDG_CONFIG_HOME", "/xdg"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "macOS ignores XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Linux, {{"HOME", "/home/a"}, {"XDG_CONFIG_HOME", "/xdg"}}, "/", none),
        "/xdg/jasper/jasper.vmoptions", "Linux absolute XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Linux, {{"HOME", "/home/a"}, {"XDG_CONFIG_HOME", "relative"}}, "/", none),
        "/home/a/.config/jasper/jasper.vmoptions", "Linux ignores a relative XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Windows, {{"APPDATA", "C:\\Users\\a\\AppData\\Roaming"}}, "C:\\", none),
        "C:\\Users\\a\\AppData\\Roaming\\jasper\\jasper.vmoptions", "Windows APPDATA");
    expectText(vm::userFilePath(Os::Windows, {{"APPDATA", "relative"}, {"USERPROFILE", "C:\\Users\\a"}}, "C:\\", none),
        "C:\\Users\\a\\AppData\\Roaming\\jasper\\jasper.vmoptions", "Windows falls back to USERPROFILE");
    expectText(vm::userFilePath(Os::Windows, {}, "C:\\", none), "", "Windows without APPDATA or USERPROFILE");
    expectText(vm::userFilePath(Os::Mac, {}, "/", none), "", "macOS without HOME");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_HOME", " /opt/jh "}, {"HOME", "/Users/a"}}, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_HOME, trimmed");
    expectText(vm::userFilePath(Os::Linux, {{"JASPER_HOME", "/opt/jh/"}}, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_HOME with a trailing separator");
    expectText(vm::userFilePath(Os::Linux, {{"JASPER_HOME", "jh"}}, "/work", none),
        "/work/jh/jasper.vmoptions", "a relative JASPER_HOME resolves from the working directory");
    expectText(vm::userFilePath(Os::Windows, {{"JASPER_HOME", "jh"}}, "C:\\work", none),
        "C:\\work\\jh\\jasper.vmoptions", "a relative JASPER_HOME on Windows");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_HOME", "   "}, {"HOME", "/Users/a"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "a blank JASPER_HOME is ignored");
    const Environment chosen = {{"JASPER_VM_OPTIONS", "/tmp/mine.vmoptions"}, {"JASPER_HOME", "/opt/jh"}};
    expectText(vm::userFilePath(Os::Mac, chosen, "/", existing({"/tmp/mine.vmoptions"})),
        "/tmp/mine.vmoptions", "JASPER_VM_OPTIONS naming a file");
    expectText(vm::userFilePath(Os::Mac, chosen, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_VM_OPTIONS naming no file falls through");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_VM_OPTIONS", "  "}, {"HOME", "/Users/a"}}, "/", existing({"  "})),
        "/Users/a/.config/jasper/jasper.vmoptions", "a blank JASPER_VM_OPTIONS is ignored");
}

void home() {
    expectText(vm::jasperHome(Os::Mac, {{"HOME", "/Users/a"}}, "/"), "/Users/a/.config/jasper", "macOS home");
    expectText(vm::jasperHome(Os::Linux, {{"HOME", "/home/a"}, {"XDG_CONFIG_HOME", "/xdg"}}, "/"), "/xdg/jasper",
        "Linux home under an absolute XDG_CONFIG_HOME");
    expectText(vm::jasperHome(Os::Windows, {{"APPDATA", "C:\\Users\\a\\AppData\\Roaming"}}, "C:\\"),
        "C:\\Users\\a\\AppData\\Roaming\\jasper", "Windows home");
    expectText(vm::jasperHome(Os::Linux, {{"JASPER_HOME", " jh "}}, "/work"), "/work/jh", "a relative JASPER_HOME");
    expectText(vm::jasperHome(Os::Mac, {}, "/"), "", "no home without HOME");
    expectText(vm::aotRecordPath(Os::Mac, {{"HOME", "/Users/a"}}, "/"), "/Users/a/.config/jasper/jasper-aot.failed",
        "the record in the macOS home");
    expectText(vm::aotRecordPath(Os::Windows, {{"APPDATA", "relative"}, {"USERPROFILE", "C:\\Users\\a"}}, "C:\\"),
        "C:\\Users\\a\\AppData\\Roaming\\jasper\\jasper-aot.failed", "the record under USERPROFILE");
    expectText(vm::aotRecordPath(Os::Linux, {{"JASPER_HOME", "/opt/jh/"}, {"JASPER_VM_OPTIONS", "/tmp/x.vmoptions"}}, "/"),
        "/opt/jh/jasper-aot.failed", "JASPER_VM_OPTIONS never moves the record");
    expectText(vm::aotRecordPath(Os::Windows, {}, "C:\\"), "", "no record without a home");
}

void parsing() {
    const UserFile mixed = vm::parse("# heap\r\n\r\n  -Xmx1g  \r\n\t-Dfoo=a b\t\n#-Xmx2g\n-XX:+UseZGC");
    expectLines(mixed.lines, {"-Xmx1g", "-Dfoo=a b", "-XX:+UseZGC"}, "comments, blank lines, trimming, CRLF");
    check(!mixed.corrupted, "a clean file is not corrupted");
    expectLines(vm::parse("-Dcolor=#fff\n").lines, {"-Dcolor=#fff"}, "only whole-line comments");
    expectLines(vm::parse("\xEF\xBB\xBF-Xmx1g\n").lines, {"-Xmx1g"}, "a UTF-8 byte order mark is ignored");
    expectLines(vm::parse("-Dname=J\xC3\xBCrgen\n").lines, {"-Dname=J\xC3\xBCrgen"}, "UTF-8 text");
    expectLines(vm::parse("").lines, {}, "an empty file");
    expectLines(vm::parse("# only\n\n   \n").lines, {}, "only comments and blank lines");
    const char nulBytes[] = "-Xmx1g\n\0-Xms1g\n";
    const UserFile nul = vm::parse(std::string(nulBytes, sizeof(nulBytes) - 1));
    check(nul.corrupted && nul.lines.empty(), "a NUL character corrupts the file");
    check(vm::parse("-Dname=\xC3\x28\n").corrupted, "invalid UTF-8 corrupts the file");
    check(vm::parse("-D=\xC0\xAF\n").corrupted, "overlong UTF-8 corrupts the file");
    check(vm::parse("-D=\xED\xA0\x80\n").corrupted, "a UTF-8 surrogate corrupts the file");
    check(vm::parse("-D=\xE2\x82").corrupted, "truncated UTF-8 corrupts the file");
    const UserFile filtered = vm::parse("Xmx300m\n\"-Dfoo=a b\"\n// heap\n-version\n--add-opens\n-Xmx1g\n"
        "--add-opens=java.base/java.lang=ALL-UNNAMED\n--class-path=/x\n-Xshare:auto\n");
    expectLines(filtered.lines, {"-Xmx1g", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-Xshare:auto"},
        "only option lines are applied");
    expectLines(filtered.ignored, {"Xmx300m", "\"-Dfoo=a b\"", "// heap", "-version", "--add-opens", "--class-path=/x"},
        "lines without a dash and the launcher's own options are ignored");
    check(!filtered.corrupted, "ignored lines do not corrupt the file");
}

void applicability() {
    check(vm::isApplicable("-Xmx1g"), "-Xmx1g is applicable");
    check(!vm::isApplicable("-X"), "-X is a launcher option");
    check(vm::isApplicable("-XshowSettings"), "-XshowSettings is applicable");
    check(!vm::isApplicable("-cp"), "-cp is a launcher option");
    check(!vm::isApplicable("-classpath"), "-classpath is a launcher option");
    check(!vm::isApplicable("--class-path=/x"), "--class-path=/x is a launcher option");
    check(vm::isApplicable("--module-path=/x"), "--module-path=/x is applicable");
    check(!vm::isApplicable("--module-path"), "--module-path alone takes the next argument");
    check(vm::isApplicable("--enable-native-access=ALL-UNNAMED"), "--enable-native-access=... is applicable");
    check(!vm::isApplicable("--enable-native-access"), "--enable-native-access alone takes the next argument");
    check(vm::isApplicable("-Dfoo=bar"), "-Dfoo=bar is applicable");
    check(!vm::isApplicable("Xmx1g"), "a line without a dash is not applicable");
    check(!vm::isApplicable(""), "an empty line is not applicable");
    check(vm::isApplicable("-"), "a lone dash is applicable");
}

void merging() {
    expectLines(vm::merge({"-a", "-b"}, {"-c"}), {"-a", "-b", "-c"}, "user lines follow the bundled options");
    expectLines(vm::merge({"-XX:+UseG1GC", "-Xmx2048m"}, {"-XX:+UseZGC"}), {"-Xmx2048m", "-XX:+UseZGC"},
        "a user collector removes the bundled collector");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-XX:+DisableExplicitGC"}), {"-XX:+DisableExplicitGC"},
        "IntelliJ's text match: any -XX:+...GC line counts as a collector");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-XX:-UseG1GC"}), {"-XX:+UseG1GC", "-XX:-UseG1GC"},
        "a disabled collector is not a collector choice");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-Xmx1g"}), {"-XX:+UseG1GC", "-Xmx1g"},
        "without a user collector the bundled one stays");
    expectLines(vm::merge({"-Xmx2048m", "-Xms128m"}, {"-XX:MaxRAMPercentage=50"}),
        {"-Xms128m", "-XX:MaxRAMPercentage=50"}, "MaxRAMPercentage removes the bundled -Xmx");
    expectLines(vm::merge({"-Xmx2048m"}, {"-XX:MinRAMPercentage=5"}), {"-XX:MinRAMPercentage=5"},
        "MinRAMPercentage removes the bundled -Xmx");
    expectLines(vm::merge({"-Xmx2048m", "-Xms128m"}, {"-XX:InitialRAMPercentage=5"}),
        {"-Xmx2048m", "-XX:InitialRAMPercentage=5"}, "InitialRAMPercentage removes the bundled -Xms");
    expectLines(vm::merge({"-Xmx2048m"}, {"-Xmx4g"}), {"-Xmx2048m", "-Xmx4g"}, "a user -Xmx wins by coming last");
}

void digests(const char* vectorsPath) {
    std::ifstream input(vectorsPath, std::ios::binary);
    check(static_cast<bool>(input), std::string("cannot read ") + vectorsPath);
    std::string lines;
    std::string line;
    int vectors = 0;
    while (std::getline(input, line)) {
        if (!line.empty() && line[line.size() - 1] == '\r') {
            line.erase(line.size() - 1);
        }
        if (line.empty() || line[0] == '#') {
            continue;
        }
        if (line.compare(0, 2, "= ") == 0) {
            const std::vector<std::string> applied = vm::parse(lines).lines;
            expectText(vm::digest(applied), line.substr(2), "digest of the applied lines " + show(applied));
            lines.clear();
            ++vectors;
        } else {
            lines += line + "\n";
        }
    }
    check(vectors >= 6, "the vectors file holds at least six vectors");
}

void planning() {
    const std::vector<std::string> bundled = {"-Xmx2048m", "-XX:+UseG1GC"};
    UserFile lines;
    lines.lines = {"-Xmx4g", "-XX:+UseZGC"};
    UserFile empty;
    UserFile corrupted;
    corrupted.corrupted = true;
    const std::string path = "/h/jasper.vmoptions";
    const std::string file = "-Djasper.vmOptionsFile=/h/jasper.vmoptions";

    Plan p = vm::plan(bundled, "", nullptr, {});
    expectLines(p.javaOptions, bundled, "no location: only the bundled options");
    expectLines(vm::plan(bundled, path, nullptr, {}).javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file},
        "a missing file is still reported");
    expectLines(vm::plan(bundled, path, &empty, {}).javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file},
        "a file without options adds no digest");
    expectLines(vm::plan(bundled, path, &lines, {}).javaOptions,
        {"-Xmx2048m", "-Xmx4g", "-XX:+UseZGC", file, "-Djasper.vmOptionsDigest=" + vm::digest(lines.lines)},
        "the applied lines, the file and their digest");
    expectLines(vm::plan(bundled, path, &corrupted, {}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsFile.corrupted=true"}, "a corrupted file is reported");
    p = vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", " launch-failed "}});
    expectLines(p.javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsSkipped=launch-failed"},
        "the skip variable skips the file");
    expectLines(vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", "  "}}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsSkipped=environment"},
        "a blank skip variable is set too, as the guard sees it");
    expectLines(vm::plan(bundled, "", &lines, {{"JASPER_VM_OPTIONS_SKIP", "manual"}}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", "-Djasper.vmOptionsSkipped=manual"}, "skipping without a location");
    expectLines(vm::plan(bundled, "", &lines, {}).javaOptions, bundled, "no location applies no lines");
}

bool inUse(const std::vector<const char*>& options) {
    return jasperAotCacheInUse(static_cast<int>(options.size()), options.data()) != 0;
}

std::string next(const std::vector<const char*>& options, bool cacheSkipped, bool optionsSkipped) {
    const char* variable = jasperNextStartVariable(static_cast<int>(options.size()), options.data(),
        cacheSkipped ? 1 : 0, optionsSkipped ? 1 : 0);
    return variable == nullptr ? std::string("report") : std::string(variable);
}

void rules() {
    check(inUse({"-Xmx2048m", "-XX:AOTCache=/a/jasper.aot"}), "a named cache is in use");
    check(!inUse({"-Xmx2048m"}), "no cache option, no cache");
    check(!inUse({"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off"}), "a later -XX:AOTMode=off turns it off");
    check(inUse({"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off", "-XX:AOTMode=auto"}), "the last -XX:AOTMode= wins");
    check(!inUse({"-XX:AOTMode=off", "-XX:AOTCache=/a/jasper.aot"}), "-XX:AOTMode=off anywhere, as the JVM reads it");
    check(inUse({"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=on"}), "-XX:AOTMode=on keeps it");
    check(!inUse({"-XX:AOTCacheOutput=/a/jasper.aot"}), "-XX:AOTCacheOutput= is not -XX:AOTCache=");
    const std::vector<const char*> both = {"/x/Jasper", "-XX:AOTCache=/a/jasper.aot",
        "-Djasper.vmOptionsDigest=0123456789abcdef", "dev.jasper.app.Main"};
    expectText(next(both, false, false), "JASPER_AOT_CACHE_SKIP", "the cache goes first");
    expectText(next(both, true, false), "JASPER_VM_OPTIONS_SKIP", "then the custom options");
    expectText(next(both, true, true), "report", "then the report");
    expectText(next({"/x/Jasper", "-Djasper.vmOptionsDigest=0123456789abcdef"}, false, false), "JASPER_VM_OPTIONS_SKIP",
        "custom options without a cache");
    expectText(next({"/x/Jasper", "-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off"}, false, false), "report",
        "a cache that is off is not skipped");
    expectText(next({"/x/Jasper", "-Xmx2048m"}, false, false), "report", "nothing left to skip");
    check(jasperHasVmOptionsDigest(2, both.data()) == 0, "only the counted options are read");
}

AotRecord record(const std::string& options) {
    return vm::parseAotRecord("cache=/a/jasper.aot\nsize=7\nmodified=100\noptions=" + options + "\n");
}

CacheFile present(std::uint64_t size, std::int64_t modified) {
    CacheFile file;
    file.regular = true;
    file.size = size;
    file.modified = modified;
    return file;
}

void records() {
    const AotRecord parsed = record("0123456789abcdef");
    check(parsed.valid && parsed.cache == "/a/jasper.aot" && parsed.size == 7 && parsed.modified == 100 &&
        parsed.options == "0123456789abcdef", "a record's fields");
    check(!vm::parseAotRecord("cache=/a\n \t \nsize=7\nmodified=100\noptions=\n").valid,
        "a line of spaces and tabs is not blank: it has no '='");
    check(vm::parseAotRecord("cache=/a\n\r\nsize=7\nmodified=100\noptions=\n").valid,
        "a line that is empty once its carriage return goes is blank");
    check(!vm::parseAotRecord("\xEF\xBB\xBF# c\ncache=/a\nsize=7\nmodified=100\noptions=\n").valid,
        "a byte order mark is not skipped");
    check(vm::parseAotRecord("cache=/a\nsize=7\nmodified=100\noptions=").valid, "no newline after the last line");
    const AotRecord twoReturns = vm::parseAotRecord("cache=/a\r\r\nsize=7\nmodified=100\noptions=\n");
    check(twoReturns.valid && twoReturns.cache == "/a\r", "only one carriage return is removed");
    check(vm::aotRecordMatches(record(""), present(7, 100), ""), "a matching record");
    check(vm::aotRecordMatches(record(""), present(7, 100), "0123456789abcdef"), "empty options match any digest");
    check(!vm::aotRecordMatches(record(""), present(7, -100), ""), "a file before 1970 never matches");
    check(!vm::aotRecordMatches(record(""), CacheFile(), ""), "no file, no match");
    check(!vm::aotRecordMatches(AotRecord(), present(7, 100), ""), "a malformed record matches nothing");
    check(!vm::aotRecordMatches(record("0123456789abcdef"), present(7, 100), ""), "options need this start's digest");
}

void startupCache() {
    const std::vector<std::string> options = {"-Xmx2048m", "-XX:AOTCache=/a/jasper.aot", "-Dx=1"};
    const std::string recordPath = "/h/jasper-aot.failed";
    const std::string property = "-Djasper.aotCacheRecord=/h/jasper-aot.failed";
    const std::string matching = "cache=/a/jasper.aot\nsize=7\nmodified=100\noptions=\n";
    expectLines(vm::applyAotCache({"-Xmx2048m"}, {}, recordPath, &matching, present(7, 100)), {"-Xmx2048m"},
        "no cache option, nothing to do");
    expectLines(vm::applyAotCache(options, {}, recordPath, nullptr, present(7, 100)),
        {"-Xmx2048m", "-XX:AOTCache=/a/jasper.aot", "-Dx=1", property}, "the cache is used and the record named");
    expectLines(vm::applyAotCache(options, {}, "", nullptr, present(7, 100)), options, "no home, no record property");
    expectLines(vm::applyAotCache(options, {}, recordPath, &matching, present(7, 100)),
        {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=remembered"}, "a matching record skips the cache");
    expectLines(vm::applyAotCache(options, {}, recordPath, &matching, present(8, 100)),
        {"-Xmx2048m", "-XX:AOTCache=/a/jasper.aot", "-Dx=1", property}, "a stale record is ignored");
    expectLines(vm::applyAotCache(options, {{"JASPER_AOT_CACHE_SKIP", " launch-failed "}}, recordPath, nullptr,
        present(7, 100)), {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=launch-failed"},
        "the skip variable skips the cache");
    expectLines(vm::applyAotCache(options, {{"JASPER_AOT_CACHE_SKIP", ""}}, recordPath, &matching, present(7, 100)),
        {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=environment"}, "a blank skip variable is set too");
    expectLines(vm::applyAotCache({"-XX:AOTCache=/old.aot", "-XX:AOTCache=/a/jasper.aot"}, {}, recordPath, &matching,
        present(7, 100)), {property, "-Djasper.aotCacheSkipped=remembered"}, "every cache option goes");
    const std::string elsewhere = "cache=/somewhere/else.aot\nsize=7\nmodified=100\noptions=\n";
    expectLines(vm::applyAotCache(options, {}, recordPath, &elsewhere, present(7, 100)),
        {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=remembered"},
        "the record's cache path is not compared");
    const std::vector<std::string> off = {"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off"};
    expectLines(vm::applyAotCache(off, {{"JASPER_AOT_CACHE_SKIP", "manual"}}, recordPath, &matching, present(7, 100)),
        {"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off", property}, "a cache that is off is never skipped");
    const std::string withDigest = "cache=/a/jasper.aot\nsize=7\nmodified=100\noptions=0123456789abcdef\n";
    const std::vector<std::string> custom = {"-XX:AOTCache=/a/jasper.aot", "-Djasper.vmOptionsDigest=0123456789abcdef"};
    expectLines(vm::applyAotCache(custom, {}, recordPath, &withDigest, present(7, 100)),
        {"-Djasper.vmOptionsDigest=0123456789abcdef", property, "-Djasper.aotCacheSkipped=remembered"},
        "a record for these custom options");
    expectLines(vm::applyAotCache({"-XX:AOTCache=/a/jasper.aot"}, {}, recordPath, &withDigest, present(7, 100)),
        {"-XX:AOTCache=/a/jasper.aot", property}, "a record for custom options this start does not have");
    expectLines(vm::applyAotCache({"-Xmx2048m"}, {{"JASPER_LAUNCH_DIAGNOSE", "1"}}, recordPath, nullptr, CacheFile()),
        {"-Xmx2048m", "-version"}, "a diagnostic launch ends its options with -version");
    expectLines(vm::applyAotCache(options, {{"JASPER_LAUNCH_DIAGNOSE", ""}, {"JASPER_AOT_CACHE_SKIP", "launch-failed"}},
        recordPath, nullptr, present(7, 100)),
        {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=launch-failed", "-version"},
        "-version comes after everything else");
}

// The vectors file: see its header. It is split on LF only; a record line ending in backslash and r ends in CR, and
// empty lines between vectors are skipped.
void recordVectors(const char* vectorsPath) {
    std::ifstream input(vectorsPath, std::ios::binary);
    check(static_cast<bool>(input), std::string("cannot read ") + vectorsPath);
    std::string line;
    std::string record;
    std::string title;
    bool inRecord = false;
    bool matched = false;
    int vectors = 0;
    while (std::getline(input, line)) {
        if (line.compare(0, 2, "##") == 0) {
            title = line.substr(2);
        } else if (line == "record") {
            record.clear();
            inRecord = true;
        } else if (line.compare(0, 7, "actual ") == 0) {
            inRecord = false;
            const std::string::size_type first = line.find(' ', 7);
            const std::string::size_type second = first == std::string::npos ? first : line.find(' ', first + 1);
            const std::string::size_type third = second == std::string::npos ? second : line.find(' ', second + 1);
            if (third == std::string::npos) {
                check(false, "malformed actual line:" + title);
                continue;
            }
            const std::string size = line.substr(7, first - 7);
            const std::string modified = line.substr(first + 1, second - first - 1);
            const std::string digestText = line.substr(second + 1, third - second - 1);
            CacheFile file;
            if (size != "absent") {
                file.regular = true;
                file.size = std::stoull(size);
                file.modified = std::stoll(modified);
            }
            matched = vm::aotRecordMatches(vm::parseAotRecord(record), file,
                digestText == "-" ? std::string() : digestText);
        } else if (line == "= match" || line == "= no match") {
            check(matched == (line == "= match"), "record vector" + title + ": expected " + line.substr(2));
            ++vectors;
        } else if (inRecord) {
            const bool returns = line.size() >= 2 && line.compare(line.size() - 2, 2, "\\r") == 0;
            record += (returns ? line.substr(0, line.size() - 2) + "\r" : line) + "\n";
        } else if (!line.empty()) {
            check(false, "unexpected line in the record vectors: " + line);
        }
    }
    check(vectors >= 25, "the record vectors file holds at least 25 vectors");
}

} // namespace

int main(int argc, char** argv) {
    if (argc != 3) {
        std::cerr << "usage: vmoptions-core-test <digest-vectors.txt> <aot-record-vectors.txt>\n";
        return 64;
    }
    location();
    home();
    parsing();
    applicability();
    merging();
    digests(argv[1]);
    planning();
    rules();
    records();
    startupCache();
    recordVectors(argv[2]);
    std::cout << checks << " checks, " << failures << " failures\n";
    return failures == 0 ? 0 : 1;
}
