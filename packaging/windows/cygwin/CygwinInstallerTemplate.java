import java.nio.file.*;
import java.util.zip.ZipFile;

/** The JBR 25 WiX v3 extension point validated by the native installer probe. */
public final class CygwinInstallerTemplate {
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
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("CygwinInstallerTemplate jdk.jpackage.jmod resource-directory");
        try (var archive = new ZipFile(args[0])) {
            var entry = archive.getEntry("classes/jdk/jpackage/internal/resources/main.wxs");
            if (entry == null) throw new IllegalArgumentException("Selected JBR has no Windows main.wxs");
            String template;
            try (var input = archive.getInputStream(entry)) { template = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            Files.createDirectories(Path.of(args[1])); Files.writeString(Path.of(args[1], "main.wxs"), withHook(template));
        }
    }
}
