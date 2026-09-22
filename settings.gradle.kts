rootProject.name = "jasper"
include("jasper-terminal", "jasper-app", "jasper-buddy", "jasper-sdk", "jasper-sdk-testkit", "jasper-plugin-sample", "jasper-plugin-snippets")
project(":jasper-plugin-sample").projectDir = file("plugins/sample")
project(":jasper-plugin-snippets").projectDir = file("plugins/snippets")
