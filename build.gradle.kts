plugins {
    java
    id("io.quarkus") version "3.33.1" apply false
}

// Apply common config to all subprojects
subprojects {
    apply(plugin = "java")

    group = "com.rish.anneal"
    version = "1.0.0-SNAPSHOT"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    repositories {
        mavenCentral()
    }

    // Import Quarkus BOM — manages versions for all Quarkus artifacts
    dependencies {
        implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.33.1"))
    }

    // Common dependencies across all modules
    dependencies {
        compileOnly("org.projectlombok:lombok:1.18.42")
        annotationProcessor("org.projectlombok:lombok:1.18.42")

        testImplementation("io.quarkus:quarkus-junit")
        testImplementation("org.mockito:mockito-core")
        testImplementation("org.assertj:assertj-core:3.26.3")
        testCompileOnly("org.projectlombok:lombok:1.18.42")
        testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    }
}
