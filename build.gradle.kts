plugins {
    application
    id("com.gradleup.shadow") version "8.3.6"
}

group = "me.soknight.sandbox"
version = "1.0-SNAPSHOT"

application {
    mainClass = "me.soknight.sandbox.downloader.DownloaderAppLauncher"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jacksonDatabind)
    implementation(libs.logback)
    implementation(libs.retrofit)
    implementation(libs.retrofitConverterJackson)
    implementation(libs.slf4jApi)
    implementation(libs.xz)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.withType(JavaCompile::class) {
    options.compilerArgs.add("--enable-preview")
}

tasks.shadowJar {
    archiveVersion = ""
}
