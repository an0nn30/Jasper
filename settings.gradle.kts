rootProject.name = "jasper"
include("jasper-terminal", "jasper-app", "jasper-buddy", "jasper-sdk", "jasper-sdk-testkit", "jasper-plugin-sample")
project(":jasper-plugin-sample").projectDir = file("plugins/sample")
