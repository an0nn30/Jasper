package dev.jasper.app.cygwin;

import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Checks executables and loaded modules for this user's processes before changing Cygwin files. */
public final class CygwinProcesses {
    private CygwinProcesses() { }
    public static void assertIdle(CygwinLayout layout) throws IOException {
        if (!CygwinLayout.windows()) return; // Portable fixtures have no Windows executables.
        String user = CygwinLayout.identity();
        var pointer = new PointerByReference(); var count = new IntByReference(); var level = new IntByReference(1);
        if (!Wtsapi32.INSTANCE.WTSEnumerateProcessesEx(null, level, -2, pointer, count)) throw new IOException("Cannot enumerate Windows processes");
        var unknown = new ArrayList<String>(); var active = new ArrayList<String>();
        try {
            if (count.getValue() == 0) return;
            var first = new Wtsapi32.WTS_PROCESS_INFO_EX(pointer.getValue());
            for (var value : first.toArray(count.getValue())) {
                var process = (Wtsapi32.WTS_PROCESS_INFO_EX)value;
                if (process.ProcessId == ProcessHandle.current().pid() || process.pUserSid == null || process.pUserSid.sid == null) continue;
                // JNA embeds PSID as a one-pointer structure here; sid is the native SID address.
                if (!user.equals(Advapi32Util.convertSidToStringSid(new WinNT.PSID(process.pUserSid.sid)))) continue;
                String label = process.pProcessName + " (PID " + Integer.toUnsignedLong(process.ProcessId) + ")";
                try {
                    String image = Kernel32Util.QueryFullProcessImageName(process.ProcessId, 0);
                    if (inside(image, layout.root()) || inside(image, layout.manager())) { active.add(label); continue; }
                    WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                        new WinDef.DWORD(Tlhelp32.TH32CS_SNAPMODULE.longValue() | Tlhelp32.TH32CS_SNAPMODULE32.longValue()), new WinDef.DWORD(process.ProcessId));
                    if (WinBase.INVALID_HANDLE_VALUE.equals(snapshot)) throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
                    try {
                        var module = new Tlhelp32.MODULEENTRY32W(); boolean found = Kernel32.INSTANCE.Module32FirstW(snapshot, module);
                        if (!found && Kernel32.INSTANCE.GetLastError() != WinError.ERROR_NO_MORE_FILES) throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
                        while (found) {
                            if (inside(module.szExePath(), layout.root())) { active.add(label); break; }
                            found = Kernel32.INSTANCE.Module32NextW(snapshot, module);
                        }
                        if (!found && Kernel32.INSTANCE.GetLastError() != WinError.ERROR_NO_MORE_FILES) throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
                    } finally { Kernel32.INSTANCE.CloseHandle(snapshot); }
                } catch (RuntimeException denied) {
                    // Exited processes cannot hold files. A live inaccessible process is not an all-clear.
                    if (ProcessHandle.of(Integer.toUnsignedLong(process.ProcessId)).map(ProcessHandle::isAlive).orElse(false)) unknown.add(label);
                }
            }
        } finally { Wtsapi32.INSTANCE.WTSFreeMemoryEx(Wtsapi32.WTS_PROCESS_INFO_LEVEL_1, pointer.getValue(), count.getValue()); }
        if (!active.isEmpty()) throw new IOException("Close processes using this Cygwin environment: " + String.join(", ", active));
        if (!unknown.isEmpty()) throw new IOException("Cannot verify whether these processes use Cygwin; close them before package changes: " + String.join(", ", unknown));
    }
    private static boolean inside(String file, Path root) { return Path.of(file).toAbsolutePath().normalize().startsWith(root); }
}
