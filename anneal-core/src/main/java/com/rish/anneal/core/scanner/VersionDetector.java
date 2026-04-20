package com.rish.anneal.core.scanner;

import com.rish.anneal.core.model.JavaVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the Java version of a project from its build file.
 *
 * Detection strategy (in priority order):
 *   1. maven.compiler.release in pom.xml
 *   2. maven.compiler.source in pom.xml
 *   3. maven.compiler.target in pom.xml
 *   4. sourceCompatibility in build.gradle / build.gradle.kts
 *   5. java.toolchain.languageVersion in build.gradle
 *
 * Returns empty if version cannot be determined — caller decides the fallback.
 * Stateless — safe to reuse.
 */
public class VersionDetector {

    private static final Logger log = Logger.getLogger(VersionDetector.class.getName());

    // Maven patterns
    private static final Pattern MAVEN_RELEASE =
            Pattern.compile("<maven\\.compiler\\.release>(\\d+)</maven\\.compiler\\.release>");
    private static final Pattern MAVEN_SOURCE =
            Pattern.compile("<maven\\.compiler\\.source>(\\d+(?:\\.\\d+)?)</maven\\.compiler\\.source>");
    private static final Pattern MAVEN_TARGET =
            Pattern.compile("<maven\\.compiler\\.target>(\\d+(?:\\.\\d+)?)</maven\\.compiler\\.target>");
    private static final Pattern COMPILER_PLUGIN_SOURCE =
            Pattern.compile("<source>(\\d+(?:\\.\\d+)?)</source>");
    private static final Pattern COMPILER_PLUGIN_RELEASE =
            Pattern.compile("<release>(\\d+)</release>");

    // Gradle patterns
    private static final Pattern GRADLE_SOURCE_COMPAT =
            Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?(?:JavaVersion\\.VERSION_)?(\\d+(?:\\.\\d+)?)['\"]?");
    private static final Pattern GRADLE_TOOLCHAIN =
            Pattern.compile("languageVersion\\s*=\\s*JavaLanguageVersion\\.of\\((\\d+)\\)");
    private static final Pattern GRADLE_KOTLIN_SOURCE =
            Pattern.compile("sourceCompatibility\\s*=\\s*JavaVersion\\.VERSION_(\\d+)");

    /**
     * Detects the Java version from build files in the given repository root.
     *
     * @param repoPath absolute path to the repository root
     * @return detected JavaVersion, or empty if not determinable
     */
    public Optional<JavaVersion> detect(Path repoPath) {
        // Try pom.xml first
        Path pomXml = repoPath.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            Optional<JavaVersion> version = detectFromPom(pomXml);
            if (version.isPresent()) {
                log.info("Detected Java version %s from %s"
                        .formatted(version.get(), pomXml));
                return version;
            }
        }

        // Try build.gradle
        Path buildGradle = repoPath.resolve("build.gradle");
        if (Files.exists(buildGradle)) {
            Optional<JavaVersion> version = detectFromGradle(buildGradle);
            if (version.isPresent()) {
                log.info("Detected Java version %s from %s"
                        .formatted(version.get(), buildGradle));
                return version;
            }
        }

        // Try build.gradle.kts
        Path buildGradleKts = repoPath.resolve("build.gradle.kts");
        if (Files.exists(buildGradleKts)) {
            Optional<JavaVersion> version = detectFromGradle(buildGradleKts);
            if (version.isPresent()) {
                log.info("Detected Java version %s from %s"
                        .formatted(version.get(), buildGradleKts));
                return version;
            }
        }

        log.warning("Could not detect Java version from build files in: " + repoPath);
        return Optional.empty();
    }

    private Optional<JavaVersion> detectFromPom(Path pomXml) {
        String content;
        try {
            content = Files.readString(pomXml);
        } catch (IOException e) {
            log.warning("Could not read pom.xml: " + e.getMessage());
            return Optional.empty();
        }

        // Priority: release > source > target > compiler plugin source > compiler plugin release
        return tryMatch(content, MAVEN_RELEASE)
                .or(() -> tryMatch(content, MAVEN_SOURCE))
                .or(() -> tryMatch(content, MAVEN_TARGET))
                .or(() -> tryMatch(content, COMPILER_PLUGIN_SOURCE))
                .or(() -> tryMatch(content, COMPILER_PLUGIN_RELEASE))
                .flatMap(this::parseVersion);
    }

    private Optional<JavaVersion> detectFromGradle(Path gradleFile) {
        String content;
        try {
            content = Files.readString(gradleFile);
        } catch (IOException e) {
            log.warning("Could not read gradle file: " + e.getMessage());
            return Optional.empty();
        }

        return tryMatch(content, GRADLE_TOOLCHAIN)
                .or(() -> tryMatch(content, GRADLE_SOURCE_COMPAT))
                .or(() -> tryMatch(content, GRADLE_KOTLIN_SOURCE))
                .flatMap(this::parseVersion);
    }

    private Optional<String> tryMatch(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<JavaVersion> parseVersion(String raw) {
        try {
            // Handle "1.8" → 8, "1.11" → 11, "17" → 17
            String normalized = raw.startsWith("1.") ? raw.substring(2) : raw;
            int version = Integer.parseInt(normalized);
            return Optional.of(JavaVersion.fromInt(version));
        } catch (IllegalArgumentException e) {
            log.warning("Unrecognized Java version string: " + raw);
            return Optional.empty();
        }
    }
}