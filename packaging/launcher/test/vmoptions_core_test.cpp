// Native tests of JasperVmOptionsCore (packaging/launcher/README.md). Usage: vmoptions-core-test <digest-vectors.txt>
#include "JasperVmOptionsCore.h"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

namespace vm = jasper::vmoptions;
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
    std::vector<std::string> lines;
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
            expectText(vm::digest(lines), line.substr(2), "digest of " + show(lines));
            lines.clear();
            ++vectors;
        } else {
            lines.push_back(line);
        }
    }
    check(vectors >= 5, "the vectors file holds at least five vectors");
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
    p = vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", "  "}});
    check(p.javaOptions.back() == "-Djasper.vmOptionsDigest=" + vm::digest(lines.lines),
        "a blank skip variable skips nothing");
    expectLines(vm::plan(bundled, "", &lines, {{"JASPER_VM_OPTIONS_SKIP", "manual"}}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", "-Djasper.vmOptionsSkipped=manual"}, "skipping without a location");
    expectLines(vm::plan(bundled, "", &lines, {}).javaOptions, bundled, "no location applies no lines");
}

} // namespace

int main(int argc, char** argv) {
    if (argc != 2) {
        std::cerr << "usage: vmoptions-core-test <digest-vectors.txt>\n";
        return 64;
    }
    location();
    parsing();
    merging();
    digests(argv[1]);
    planning();
    std::cout << checks << " checks, " << failures << " failures\n";
    return failures == 0 ? 0 : 1;
}
