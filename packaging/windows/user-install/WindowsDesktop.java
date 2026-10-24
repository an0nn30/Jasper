import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Direct Windows APIs via the same JNA version Jasper uses; no PowerShell or script host. */
final class WindowsDesktop implements UserInstall.Desktop, AutoCloseable {
    private final String runKey;
    WindowsDesktop() { this("Software\\Microsoft\\Windows\\CurrentVersion\\Run"); }
    WindowsDesktop(String runKey) {
        this.runKey = runKey;
        COMUtils.checkRC(Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED));
    }
    public void close() { Ole32.INSTANCE.CoUninitialize(); }
    static Path appData() { return Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_RoamingAppData)); }
    static Path programs() { return Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Programs)); }
    static boolean reparsePoint(Path path) throws IOException {
        String nativePath = path.toAbsolutePath().normalize().toString();
        if (!nativePath.startsWith("\\\\?\\"))
            nativePath = nativePath.startsWith("\\\\") ? "\\\\?\\UNC\\" + nativePath.substring(2) : "\\\\?\\" + nativePath;
        int attributes = Kernel32.INSTANCE.GetFileAttributes(nativePath);
        if (attributes == WinBase.INVALID_FILE_ATTRIBUTES)
            throw new IOException("Cannot inspect " + path + ": Windows error " + Kernel32.INSTANCE.GetLastError());
        return (attributes & WinNT.FILE_ATTRIBUTE_REPARSE_POINT) != 0;
    }
    public void assertStopped(Path directory) throws IOException {
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        if (WinBase.INVALID_HANDLE_VALUE.equals(snapshot)) throw new IOException("Cannot inspect running processes");
        try {
            var process = new Tlhelp32.PROCESSENTRY32();
            boolean found = Kernel32.INSTANCE.Process32First(snapshot, process);
            while (found) {
                if ("Jasper.exe".equalsIgnoreCase(Native.toString(process.szExeFile))) {
                    String executable;
                    try { executable = Kernel32Util.QueryFullProcessImageName(process.th32ProcessID.intValue(), 0); }
                    catch (Win32Exception denied) {
                        throw new IOException("Cannot inspect Jasper. Exit Jasper before installing or uninstalling.", denied);
                    }
                    if (directory.resolve("Jasper.exe").toString().equalsIgnoreCase(executable))
                        throw new IOException("Exit Jasper, including its background process, before installing or uninstalling.");
                }
                found = Kernel32.INSTANCE.Process32Next(snapshot, process);
            }
            int error = Kernel32.INSTANCE.GetLastError();
            if (error != WinError.ERROR_NO_MORE_FILES) throw new IOException("Cannot enumerate processes: " + error);
        } finally { Kernel32.INSTANCE.CloseHandle(snapshot); }
    }
    public String target(Path shortcut) {
        if (!Files.exists(shortcut)) return null;
        try (var link = new ShellLink()) {
            link.file.call(5, new WString(shortcut.toString()), 0); // IPersistFile.Load, STGM_READ
            char[] path = new char[32768];
            link.call(3, path, path.length, null, 4); // IShellLinkW.GetPath, SLGP_RAWPATH
            return Native.toString(path);
        }
    }
    public void save(Path shortcut, Path executable) {
        try (var link = new ShellLink()) {
            link.call(20, new WString(executable.toString())); // SetPath
            link.call(9, new WString(executable.getParent().toString())); // SetWorkingDirectory
            link.call(7, new WString("Jasper terminal")); // SetDescription
            link.call(17, new WString(executable.toString()), 0); // SetIconLocation
            link.file.call(6, new WString(shortcut.toString()), 1); // IPersistFile.Save, TRUE
        }
    }
    public void removeLogin(Path executable) {
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, runKey)
                || !Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, runKey, "Jasper")) return;
        Object value = Advapi32Util.registryGetValue(WinReg.HKEY_CURRENT_USER, runKey, "Jasper");
        if (value instanceof String text && ("\"" + executable + "\" --background").equalsIgnoreCase(text))
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, runKey, "Jasper");
    }

    // COM ABI slots from Windows SDK shobjidl_core.h (IShellLinkW) and objidl.h (IPersistFile).
    // https://learn.microsoft.com/windows/win32/api/shobjidl_core/nn-shobjidl_core-ishelllinkw
    private static class ComPointer extends Unknown implements AutoCloseable {
        ComPointer(Pointer pointer) { super(pointer); }
        void call(int slot, Object... arguments) {
            Object[] nativeArguments = new Object[arguments.length + 1];
            nativeArguments[0] = getPointer();
            System.arraycopy(arguments, 0, nativeArguments, 1, arguments.length);
            COMUtils.checkRC((WinNT.HRESULT) _invokeNativeObject(slot, nativeArguments, WinNT.HRESULT.class));
        }
        public void close() { Release(); }
    }
    private static final class ShellLink extends ComPointer {
        final ComPointer file;
        ShellLink() {
            super(create());
            var result = new PointerByReference();
            try {
                COMUtils.checkRC(QueryInterface(new Guid.REFIID(
                        new Guid.IID("{0000010b-0000-0000-C000-000000000046}")), result));
                file = new ComPointer(result.getValue());
            } catch (RuntimeException failure) {
                Release();
                throw failure;
            }
        }
        static Pointer create() {
            var result = new PointerByReference();
            COMUtils.checkRC(Ole32.INSTANCE.CoCreateInstance(
                    new Guid.CLSID("{00021401-0000-0000-C000-000000000046}"), null, WTypes.CLSCTX_INPROC_SERVER,
                    new Guid.IID("{000214F9-0000-0000-C000-000000000046}"), result));
            return result.getValue();
        }
        public void close() {
            try { file.close(); } finally { super.close(); }
        }
    }
}
