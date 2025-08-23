
plugins {
    id("java")
}

group = "com.than"
version = "1.0"

repositories {
    mavenCentral()
}

tasks.register<Jar>("myJar"){
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE  // 排除重复文件（保留第一个）
    manifest{
        attributes["Main-Class"] = "com.than.Main"
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

}

dependencies {
    implementation("com.github.oshi:oshi-core:6.8.3")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}