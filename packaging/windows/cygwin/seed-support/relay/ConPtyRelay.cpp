// Experimental native ConPTY frontend. The Cygwin backend is detached and owns a POSIX PTY.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

static HANDLE console_in, console_out, pipe_in, pipe_out, process_handle, job;
static std::mutex frames;
static SHORT last_rows, last_columns;
[[noreturn]] static void fail(const char* operation) {
    std::fprintf(stderr, "Jasper PTY relay: %s (Windows error %lu)\n", operation, GetLastError());
    ExitProcess(1); // Closing our sole job handle terminates the entire owned process tree.
}
static void check(BOOL value, const char* operation) { if (!value) fail(operation); }
static void write_all(HANDLE handle, const unsigned char* bytes, size_t size) {
    while (size) {
        DWORD count = 0, chunk = static_cast<DWORD>(std::min<size_t>(size, 65536));
        check(WriteFile(handle, bytes, chunk, &count, nullptr), "WriteFile");
        if (!count) fail("zero-byte write");
        bytes += count; size -= count;
    }
}
static void frame(unsigned char type, const unsigned char* bytes, uint32_t size) {
    unsigned char header[] = {type, static_cast<unsigned char>(size >> 24), static_cast<unsigned char>(size >> 16),
        static_cast<unsigned char>(size >> 8), static_cast<unsigned char>(size)};
    write_all(pipe_in, header, sizeof header); write_all(pipe_in, bytes, size);
}
static COORD dimensions() {
    CONSOLE_SCREEN_BUFFER_INFO info{};
    check(GetConsoleScreenBufferInfo(console_out, &info), "console dimensions");
    return {static_cast<SHORT>(info.srWindow.Right - info.srWindow.Left + 1),
        static_cast<SHORT>(info.srWindow.Bottom - info.srWindow.Top + 1)};
}
// Caller owns frames, ordering a resize before the next input packet.
static void resize() {
    COORD size = dimensions();
    if (size.Y == last_rows && size.X == last_columns) return;
    unsigned char payload[] = {static_cast<unsigned char>(size.Y >> 8), static_cast<unsigned char>(size.Y),
        static_cast<unsigned char>(size.X >> 8), static_cast<unsigned char>(size.X)};
    frame(2, payload, sizeof payload); last_rows = size.Y; last_columns = size.X;
}
static std::wstring quote(const std::wstring& value) {
    std::wstring result = L"\""; size_t slashes = 0;
    for (wchar_t ch : value) {
        if (ch == L'\\') { ++slashes; continue; }
        result.append(ch == L'"' ? slashes * 2 + 1 : slashes, L'\\'); slashes = 0; result += ch;
    }
    result.append(slashes * 2, L'\\'); return result + L'"';
}
struct Utf16Input {
    wchar_t pending = 0;
    std::string encode(const wchar_t* buffer, size_t count) {
        std::wstring value; if (pending) value += pending; value.append(buffer, count); pending = 0;
        if (!value.empty() && value.back() >= 0xd800 && value.back() <= 0xdbff) {
            pending = value.back(); value.pop_back();
        }
        if (value.empty()) return {};
        int needed = WideCharToMultiByte(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), nullptr, 0, nullptr, nullptr);
        if (!needed) fail("UTF-16 input conversion");
        std::string bytes(static_cast<size_t>(needed), 0);
        if (!WideCharToMultiByte(CP_UTF8, 0, value.data(), static_cast<int>(value.size()),
            bytes.data(), needed, nullptr, nullptr)) fail("UTF-8 input conversion");
        return bytes;
    }
};
static int self_test() {
    Utf16Input encoder;
    wchar_t first[] = {0x00e9, 0x6f22, 0xd83d}, second[] = {0xde00, L'\r'};
    std::string encoded = encoder.encode(first, 3); encoded += encoder.encode(second, 2);
    if (encoded != "\xc3\xa9\xe6\xbc\xa2\xf0\x9f\x98\x80\r") return 1;
    Utf16Input incomplete; wchar_t high[] = {0xd83d};
    if (!incomplete.encode(high, 1).empty()) return 2;
    if (quote(L"") != L"\"\"" || quote(L"a b") != L"\"a b\"") return 3;
    if (quote(L"a\\") != L"\"a\\\\\"" || quote(L"a\"b") != L"\"a\\\"b\"") return 4;
    std::puts("Native relay UTF-16 and argument checks passed"); return 0;
}
int wmain(int argc, wchar_t** argv) {
    if (argc == 2 && std::wstring(argv[1]) == L"--self-test") return self_test();
    if (argc < 3) { std::fputs("ConPtyRelay backend.exe program [args...]\n", stderr); return 64; }
    console_in = GetStdHandle(STD_INPUT_HANDLE); console_out = GetStdHandle(STD_OUTPUT_HANDLE);
    DWORD old_input = 0, old_output = 0;
    check(GetConsoleMode(console_in, &old_input), "input console required");
    check(GetConsoleMode(console_out, &old_output), "output console required");
    check(SetConsoleMode(console_in, ENABLE_VIRTUAL_TERMINAL_INPUT | ENABLE_WINDOW_INPUT), "raw VT input");
    check(SetConsoleMode(console_out, ENABLE_PROCESSED_OUTPUT | ENABLE_WRAP_AT_EOL_OUTPUT | ENABLE_VIRTUAL_TERMINAL_PROCESSING | DISABLE_NEWLINE_AUTO_RETURN), "VT output");
    UINT input_cp = GetConsoleCP(), output_cp = GetConsoleOutputCP();
    check(SetConsoleCP(CP_UTF8), "UTF-8 input"); check(SetConsoleOutputCP(CP_UTF8), "UTF-8 output");
    COORD size = dimensions(); last_rows = size.Y; last_columns = size.X;
    SECURITY_ATTRIBUTES security{sizeof security, nullptr, TRUE};
    HANDLE child_input, child_output;
    check(CreatePipe(&child_input, &pipe_in, &security, 0), "input pipe");
    check(CreatePipe(&pipe_out, &child_output, &security, 0), "output pipe");
    check(SetHandleInformation(pipe_in, HANDLE_FLAG_INHERIT, 0), "parent input ownership");
    check(SetHandleInformation(pipe_out, HANDLE_FLAG_INHERIT, 0), "parent output ownership");
    job = CreateJobObjectW(nullptr, nullptr); if (!job) fail("job creation");
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits{};
    limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    check(SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof limits), "job lifetime");
    SIZE_T attribute_size = 0;
    InitializeProcThreadAttributeList(nullptr, 1, 0, &attribute_size);
    std::vector<unsigned char> attributes(attribute_size);
    STARTUPINFOEXW startup{}; startup.StartupInfo.cb = sizeof startup;
    startup.StartupInfo.dwFlags = STARTF_USESTDHANDLES;
    startup.StartupInfo.hStdInput = child_input;
    startup.StartupInfo.hStdOutput = startup.StartupInfo.hStdError = child_output;
    startup.lpAttributeList = reinterpret_cast<LPPROC_THREAD_ATTRIBUTE_LIST>(attributes.data());
    check(InitializeProcThreadAttributeList(startup.lpAttributeList, 1, 0, &attribute_size), "handle attribute list");
    HANDLE inherited[] = {child_input, child_output};
    check(UpdateProcThreadAttribute(startup.lpAttributeList, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST,
        inherited, sizeof inherited, nullptr, nullptr), "explicit inherited pipes");
    std::wstring command = quote(argv[1]) + L" " + std::to_wstring(size.Y) + L" " + std::to_wstring(size.X);
    for (int i = 2; i < argc; ++i) command += L" " + quote(argv[i]);
    PROCESS_INFORMATION child{};
    check(CreateProcessW(argv[1], command.data(), nullptr, nullptr, TRUE,
        DETACHED_PROCESS | CREATE_SUSPENDED | EXTENDED_STARTUPINFO_PRESENT, nullptr, nullptr,
        &startup.StartupInfo, &child), "detached backend");
    DeleteProcThreadAttributeList(startup.lpAttributeList);
    CloseHandle(child_input); CloseHandle(child_output);
    process_handle = child.hProcess;
    if (!AssignProcessToJobObject(job, process_handle)) {
        DWORD error = GetLastError(); TerminateProcess(process_handle, 1); SetLastError(error); fail("own backend process tree");
    }
    if (ResumeThread(child.hThread) == static_cast<DWORD>(-1)) fail("resume backend");
    CloseHandle(child.hThread);
    std::thread([] {
        wchar_t buffer[1024]; Utf16Input encoder;
        for (;;) {
            DWORD count = 0;
            if (!ReadConsoleW(console_in, buffer, 1024, &count, nullptr) || !count) break;
            std::string bytes = encoder.encode(buffer, count);
            if (bytes.empty()) continue;
            std::lock_guard<std::mutex> lock(frames); resize();
            frame(1, reinterpret_cast<const unsigned char*>(bytes.data()), static_cast<uint32_t>(bytes.size()));
        }
        fail("console input closed");
    }).detach();
    std::thread([] {
        while (WaitForSingleObject(process_handle, 20) == WAIT_TIMEOUT) {
            std::lock_guard<std::mutex> lock(frames); resize();
        }
    }).detach();
    unsigned char buffer[8192]; DWORD count = 0;
    while (ReadFile(pipe_out, buffer, sizeof buffer, &count, nullptr) && count)
        write_all(console_out, buffer, count);
    if (WaitForSingleObject(process_handle, 2000) != WAIT_OBJECT_0) fail("backend output ended before exit");
    DWORD status = 1; check(GetExitCodeProcess(process_handle, &status), "backend exit status");
    SetConsoleMode(console_in, old_input); SetConsoleMode(console_out, old_output);
    SetConsoleCP(input_cp); SetConsoleOutputCP(output_cp);
    CloseHandle(job); // Also closes any child that outlived Bash.
    ExitProcess(status); // Cancels the blocking console reader without escaping the owned process lifetime.
}
