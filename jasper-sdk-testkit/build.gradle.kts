// Headless fake of the plugin runtime plus the contract suite every runtime must pass.
plugins { `java-library`; `java-test-fixtures` }

dependencies {
    api(project(":jasper-sdk"))
    testFixturesApi(platform("org.junit:junit-bom:6.1.3"))
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesApi("org.assertj:assertj-core:3.27.7")
}
