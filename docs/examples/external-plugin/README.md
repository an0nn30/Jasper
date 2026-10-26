# Example external plugin

A minimal Jasper plugin built outside the Jasper repository, from the jars a Jasper GitHub release carries. The
plugin, Hello (`dev.example.hello`), adds one command, **Copy Greeting**, to the Tools menu and the command palette;
it copies "Hello from an external Jasper plugin" to the clipboard. Its test runs it in the testkit's `FakePluginHost`.
Copy this folder to start a plugin of your own.

| File | What it holds |
| --- | --- |
| [`build.gradle.kts`](build.gradle.kts) | The build: the SDK `compileOnly`, the SDK and testkit on the test classpath with JUnit and AssertJ from Maven Central, a Java 25 toolchain, and the `stagePlugin` and `pluginZip` tasks |
| [`settings.gradle.kts`](settings.gradle.kts) | The root project, `hello-plugin` |
| [`plugin.toml`](src/main/resources/plugin.toml) | The manifest; the build reads the plugin's id and version from it |
| [`HelloPlugin.java`](src/main/java/dev/example/hello/HelloPlugin.java) | The entry point |
| [`HelloPluginTest.java`](src/test/java/dev/example/hello/HelloPluginTest.java) | The test |

## Building it

1. Download `jasper-sdk-<version>.jar` and `jasper-sdk-testkit-<version>.jar` from a Jasper release whose SDK version
   the manifest's `sdk` range admits (`>=0.22.0, <0.23`), and put them in `libs/`. `-PjasperSdkDir=<folder>` reads
   them from another folder, and `-PjasperSdkVersion=<version>` picks other jars than the default, `0.22.0`. A missing
   jar stops the build with a message naming it.
2. Run the build with Gradle and a Java 25 JDK of any vendor installed. The folder has no Gradle wrapper; `gradle
   wrapper` adds one.

| Task | Result |
| --- | --- |
| `check` | Compiles the plugin and runs its test |
| `stagePlugin` | `build/plugin/dev.example.hello/`: the plugin jar and the libraries it bundles, a folder for `jasper --plugin-dir` |
| `pluginZip` | `build/distributions/dev.example.hello-1.0.0.zip`, for Install from Zip… |

From a Jasper checkout, `./gradlew sdkRelease` builds the jars into `build/sdk-release/`, and the repository's wrapper
builds the example from them; CI runs this on Linux:

```sh
./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip
```

## Running it in Jasper

- `jasper --plugin-dir build/plugin/dev.example.hello` starts a Jasper that loads the folder without asking for
  consent. The option applies only to a Jasper that starts: quit a Jasper running in the same Jasper home first, or set
  `JASPER_HOME` to another folder ([one Jasper per home](../../command-line.md#one-jasper-per-home)). See
  [launching Jasper with options](../../command-line.md#launching-jasper-with-options).
- To install it, open File › Manage Plugins…, choose Install from Zip… from the Plugin options menu (the three-dot
  button) on the Plugins page, pick the zip, review the plugin and choose Install. It loads after Jasper restarts; see
  [installing a plugin](../../plugins.md#installing-a-plugin).

## Making it yours

- Change `id`, `name`, `version`, `entry`, `description` and `vendor` in `plugin.toml`, and move the sources into your
  own package. The jar is named after the id and the zip after the id and version.
- List in `capabilities` what the plugin uses, and give the test's `PluginInfo` the same set: the testkit refuses a
  gated call without its capability, as Jasper does.
- Keep the SDK `compileOnly`: Jasper supplies it at run time. A library the plugin bundles goes in `implementation`,
  and `stagePlugin` and `pluginZip` put it beside the plugin jar.

[Writing a Jasper plugin](../../plugin-authoring.md) covers what a plugin can do, and
[the testkit](../../../jasper-sdk-testkit/README.md) how to test it.
