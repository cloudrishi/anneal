package com.rish.anneal.api.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ScanResourceTest {

    @Test
    @DisplayName("GET /api/health returns ok")
    void healthReturnsOk() {
        given()
                .when().get("/api/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
                .body("service", equalTo("anneal"));
    }

    @Test
    @DisplayName("POST /api/scan with missing repoPath returns 400")
    void scanMissingRepoPathReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/scan")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("POST /api/scan with non-existent path returns 400")
    void scanNonExistentPathReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"repoPath\": \"/tmp/does-not-exist-anneal-test\", \"sourceVersion\": \"8\"}")
                .when().post("/api/scan")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    @Test
    @DisplayName("POST /api/scan with valid path returns scan result")
    void scanValidPathReturnsScanResult() throws IOException {
        Path tempDir = createTestRepo();

        given()
                .contentType(ContentType.JSON)
                .body("{\"repoPath\": \"" + tempDir.toString() + "\", \"sourceVersion\": \"8\"}")
                .when().post("/api/scan")
                .then()
                .statusCode(200)
                .body("scanId", notNullValue())
                .body("detectedVersion", equalTo("Java 8"))
                .body("targetVersion", equalTo("Java 25"))
                .body("riskScore", greaterThanOrEqualTo(0))
                .body("riskBand", notNullValue())
                .body("findings", notNullValue())
                .body("boundaryScores.size()", equalTo(4));
    }

    @Test
    @DisplayName("POST /api/scan with legacy Java file detects findings")
    void scanLegacyFileDetectsFindings() throws IOException {
        Path tempDir = createLegacyTestRepo();

        given()
                .contentType(ContentType.JSON)
                .body("{\"repoPath\": \"" + tempDir.toString() + "\", \"sourceVersion\": \"8\"}")
                .when().post("/api/scan")
                .then()
                .statusCode(200)
                .body("findings.size()", greaterThanOrEqualTo(1))
                .body("riskScore", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("GET /api/scans returns list")
    void listScansReturnsList() {
        given()
                .when().get("/api/scans")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("GET /api/scans/{scanId} with unknown id returns 404")
    void getScanUnknownIdReturns404() {
        given()
                .when().get("/api/scans/unknown-scan-id-12345")
                .then()
                .statusCode(404);
    }

    // --- Helpers ---

    private Path createTestRepo() throws IOException {
        Path dir = Files.createTempDirectory("anneal-test-clean-");
        Path src = Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(src.resolve("Clean.java"), """
                import java.util.List;
                import java.util.ArrayList;
                public class Clean {
                    public List<String> getItems() {
                        return new ArrayList<>();
                    }
                }
                """);
        return dir;
    }

    private Path createLegacyTestRepo() throws IOException {
        Path dir = Files.createTempDirectory("anneal-test-legacy-");
        Path src = Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(src.resolve("Legacy.java"), """
                import sun.misc.Unsafe;
                import javax.xml.bind.JAXBContext;
                public class Legacy {
                    protected void finalize() throws Throwable {
                        super.finalize();
                    }
                }
                """);
        return dir;
    }
}
