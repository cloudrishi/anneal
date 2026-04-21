package com.rish.anneal.core.scanner;

import com.rish.anneal.core.model.JavaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VersionDetectorTest {

    private VersionDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new VersionDetector();
    }

    @Nested
    @DisplayName("Maven pom.xml detection")
    class MavenTests {

        @Test
        @DisplayName("detects maven.compiler.release")
        void detectsMavenCompilerRelease() throws IOException {
            writePom("""
                    <properties>
                        <maven.compiler.release>17</maven.compiler.release>
                    </properties>
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V17);
        }

        @Test
        @DisplayName("detects maven.compiler.source")
        void detectsMavenCompilerSource() throws IOException {
            writePom("""
                    <properties>
                        <maven.compiler.source>11</maven.compiler.source>
                    </properties>
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V11);
        }

        @Test
        @DisplayName("detects legacy 1.8 format")
        void detectsLegacyJava8Format() throws IOException {
            writePom("""
                    <properties>
                        <maven.compiler.source>1.8</maven.compiler.source>
                    </properties>
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V8);
        }

        @Test
        @DisplayName("prefers release over source")
        void prefersReleaseOverSource() throws IOException {
            writePom("""
                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                        <maven.compiler.source>17</maven.compiler.source>
                    </properties>
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V21);
        }

        @Test
        @DisplayName("detects Java 25")
        void detectsJava25() throws IOException {
            writePom("""
                    <properties>
                        <maven.compiler.release>25</maven.compiler.release>
                    </properties>
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V25);
        }
    }

    @Nested
    @DisplayName("Gradle build file detection")
    class GradleTests {

        @Test
        @DisplayName("detects sourceCompatibility in build.gradle")
        void detectsSourceCompatibility() throws IOException {
            writeGradle("""
                    sourceCompatibility = '17'
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V17);
        }

        @Test
        @DisplayName("detects toolchain languageVersion")
        void detectsToolchain() throws IOException {
            writeGradle("""
                    java {
                        toolchain {
                            languageVersion = JavaLanguageVersion.of(21)
                        }
                    }
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V21);
        }

        @Test
        @DisplayName("detects Kotlin DSL JavaVersion")
        void detectsKotlinDsl() throws IOException {
            writeGradleKts("""
                    sourceCompatibility = JavaVersion.VERSION_17
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V17);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("returns empty when no build file found")
        void returnsEmptyWhenNoBuildFile() {
            assertThat(detector.detect(tempDir))
                    .isEmpty();
        }

        @Test
        @DisplayName("returns empty when version not in build file")
        void returnsEmptyWhenVersionNotFound() throws IOException {
            writePom("""
                    <properties>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                    """);
            assertThat(detector.detect(tempDir))
                    .isEmpty();
        }

        @Test
        @DisplayName("prefers pom.xml over build.gradle")
        void prefersPomOverGradle() throws IOException {
            writePom("""
                    <properties>
                        <maven.compiler.release>17</maven.compiler.release>
                    </properties>
                    """);
            writeGradle("""
                    sourceCompatibility = '11'
                    """);
            assertThat(detector.detect(tempDir))
                    .hasValue(JavaVersion.V17);
        }
    }

    // --- Helpers ---

    private void writePom(String content) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), content);
    }

    private void writeGradle(String content) throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), content);
    }

    private void writeGradleKts(String content) throws IOException {
        Files.writeString(tempDir.resolve("build.gradle.kts"), content);
    }
}
