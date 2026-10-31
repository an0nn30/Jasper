import java.nio.file.*;
import java.util.zip.ZipFile;

/** The JBR 25 WiX v3 extension point validated by the native installer probe. */
public final class CygwinInstallerTemplate {
    // The per-user registration as a web browser. Windows lists Jasper among the http and https choices; the uninstall,
    // and so an upgrade (RemoveExistingProducts runs first), removes the values and deletes the two keys Jasper owns.
    // DefaultBrowser compares the user's choice with the JasperURL ProgId. Keep the GUID: it names this component in
    // every release.
    private static final String BROWSER_COMPONENT = """
        <DirectoryRef Id="INSTALLDIR">
          <Component Id="JasperWebBrowser" Guid="{6F685105-E30D-4CAF-9091-ED60DF8D5D62}">
            <RegistryValue Root="HKCU" Key="Software\\RegisteredApplications" Name="Jasper" Type="string" Value="Software\\Jasper\\Capabilities"/>
            <RegistryKey Root="HKCU" Key="Software\\Jasper\\Capabilities" ForceDeleteOnUninstall="yes">
              <RegistryValue Name="ApplicationName" Type="string" Value="Jasper" KeyPath="yes"/>
              <RegistryValue Name="ApplicationDescription" Type="string" Value="Jasper's built-in web browser"/>
              <RegistryKey Key="URLAssociations">
                <RegistryValue Name="http" Type="string" Value="JasperURL"/>
                <RegistryValue Name="https" Type="string" Value="JasperURL"/>
              </RegistryKey>
            </RegistryKey>
            <RegistryKey Root="HKCU" Key="Software\\Classes\\JasperURL" ForceDeleteOnUninstall="yes">
              <RegistryValue Type="string" Value="Jasper URL"/>
              <RegistryValue Key="DefaultIcon" Type="string" Value="&quot;[INSTALLDIR]Jasper.exe&quot;,0"/>
              <RegistryValue Key="shell\\open\\command" Type="string" Value="&quot;[INSTALLDIR]Jasper.exe&quot; &quot;%1&quot;"/>
            </RegistryKey>
          </Component>
        </DirectoryRef>
        """;

    static String withHook(String template) {
        if (template.indexOf("</Product>") < 0 || template.indexOf("<InstallExecuteSequence>") < 0
                || template.indexOf("</Product>") != template.lastIndexOf("</Product>")
                || template.indexOf("<InstallExecuteSequence>") != template.lastIndexOf("<InstallExecuteSequence>"))
            throw new IllegalArgumentException("Unsupported JBR main.wxs; review the selected JBR template");
        String command = "\"[INSTALLDIR]runtime\\bin\\java.exe\" --enable-native-access=ALL-UNNAMED -cp \"[INSTALLDIR]app\\cygwin-tool.jar\" CygwinTool ensure --seed \"[INSTALLDIR]app\\cygwin-seed\"";
        String escaped = command.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
        return template.replace("<InstallExecuteSequence>", "<CustomAction Id=\"JasperProvisionCygwin\" Directory=\"INSTALLDIR\" ExeCommand=\""
            + escaped + "\" Execute=\"deferred\" Impersonate=\"yes\" Return=\"check\"/>\n<InstallExecuteSequence>\n"
            + "<Custom Action=\"JasperProvisionCygwin\" After=\"InstallFiles\">NOT REMOVE</Custom>");
    }
    /** Adds the browser registration to the template's one feature, which installs and removes it with the program files. */
    static String withBrowserRegistration(String template) {
        if (template.indexOf("</Feature>") < 0 || template.indexOf("</Feature>") != template.lastIndexOf("</Feature>"))
            throw new IllegalArgumentException("Unsupported JBR main.wxs; review the selected JBR template");
        return template.replace("</Feature>", "  <ComponentRef Id=\"JasperWebBrowser\"/>\n    </Feature>\n" + BROWSER_COMPONENT);
    }
    static void stageImage(Path source, Path destination) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) throw new java.io.IOException("Linked app-image file: " + path);
                Path target = destination.resolve(source.relativize(path));
                if (target.toAbsolutePath().toString().length() >= 260)
                    throw new java.io.IOException("WiX 3 requires a shorter TEMP directory for: " + target);
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("stage-image")) { stageImage(Path.of(args[1]), Path.of(args[2])); return; }
        if (args.length != 2) throw new IllegalArgumentException("CygwinInstallerTemplate jdk.jpackage.jmod resource-directory");
        try (var archive = new ZipFile(args[0])) {
            var entry = archive.getEntry("classes/jdk/jpackage/internal/resources/main.wxs");
            if (entry == null) throw new IllegalArgumentException("Selected JBR has no Windows main.wxs");
            String template;
            try (var input = archive.getInputStream(entry)) { template = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            Files.createDirectories(Path.of(args[1])); Files.writeString(Path.of(args[1], "main.wxs"), withBrowserRegistration(withHook(template)));
        }
    }
}
