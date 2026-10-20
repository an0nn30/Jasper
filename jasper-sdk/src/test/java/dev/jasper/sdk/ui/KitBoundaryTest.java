package dev.jasper.sdk.ui;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class KitBoundaryTest {
 @Test void advertisesTheKitCompatibilityBoundary(){assertThat(dev.jasper.sdk.JasperSdk.VERSION).isEqualTo("0.18.2");}
 @Test void onlyOwnedViewsCrossMountBoundaries() throws Exception {
  assertThat(PanelFactory.class.getMethod("create",PanelHost.class).getReturnType()).isEqualTo(UiView.class);
  assertThat(dev.jasper.sdk.settings.SettingsPanel.class.getMethod("create",dev.jasper.sdk.settings.SettingsPanelHost.class).getReturnType()).isEqualTo(UiView.class);
  assertThat(dev.jasper.sdk.settings.SettingsPanelHost.class.getMethod("row",dev.jasper.sdk.settings.Setting.class).getReturnType()).isEqualTo(Nodes.Node.class);
  assertThat(java.util.Arrays.stream(WindowSurface.class.getMethods()).filter(m -> m.getName().equals("setContent")).map(m -> m.getParameterTypes()[0])).containsExactly(UiView.class);
 }
 @Test void appearanceHasNoRawIconsOrImplicitPaletteReaders(){
  assertThat(java.util.Arrays.stream(Appearance.class.getMethods()).map(java.lang.reflect.Method::getName)).doesNotContain("icon");
  for(var type:java.util.List.of(WorkspacePalette.class,EditorPalette.class,SyntaxPalette.class))
   assertThat(java.util.Arrays.stream(type.getDeclaredMethods()).map(java.lang.reflect.Method::getName)).doesNotContain("current");
 }
}
