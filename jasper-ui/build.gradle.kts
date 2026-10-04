plugins { `java-library` }

dependencies {
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-extras:3.7")
    implementation("com.github.weisj:jsvg:2.0.0")
}

// Exercise the same logical geometry in a separately scaled Swing process.
tasks.withType<Test>().configureEach {
    System.getProperty("flatlaf.uiScale")?.let { systemProperty("flatlaf.uiScale", it) }
}
