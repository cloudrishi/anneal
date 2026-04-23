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

    configurations.all {
        exclude(group = "dev.langchain4j", module = "langchain4j-http-client-jdk")
    }

    dependencies {
        // Quarkus BOM — manages all Quarkus artifact versions
        implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.33.1"))

        // LangChain4j BOM 1.13.0 — declared directly, no quarkus-langchain4j-bom
        // quarkus-langchain4j-bom locked LangChain4j to 1.11.0 via strictly constraints
        // which cannot be overridden. Dropping it here; quarkus-langchain4j-pgvector
        // in anneal-store declares its own Quarkiverse version (1.8.4) independently.
        implementation(enforcedPlatform("dev.langchain4j:langchain4j-bom:1.13.0"))
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