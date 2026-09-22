rootProject.name = "jasper"
include("jasper-terminal", "jasper-app", "jasper-buddy", "jasper-sdk", "jasper-sdk-testkit", "jasper-plugin-sample", "jasper-plugin-snippets", "jasper-plugin-history")
project(":jasper-plugin-sample").projectDir = file("plugins/sample")
project(":jasper-plugin-snippets").projectDir = file("plugins/snippets")
project(":jasper-plugin-history").projectDir = file("plugins/history")
