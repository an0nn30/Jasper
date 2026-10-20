package dev.jasper.sdk.testing;
final class KitFixtures {
 static dev.jasper.sdk.ui.UiView view(dev.jasper.sdk.plugin.PluginContext context){return context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body",dev.jasper.sdk.ui.ControlState.READY,"Content",dev.jasper.sdk.ui.Nodes.TextRole.BODY)),e -> {});}
}
