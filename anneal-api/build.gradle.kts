description = "REST API layer — Quarkus resources, DTOs, health, container image"

plugins {
    id("io.quarkus")
}

dependencies {
    implementation(project(":anneal-core"))
    implementation(project(":anneal-store"))
    implementation(project(":anneal-llm"))

    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.rest.client.jackson)
    implementation(libs.quarkus.smallrye.health)
    implementation(libs.quarkus.smallrye.openapi)
    implementation(libs.quarkus.container.image.docker)
    implementation(libs.quarkus.hibernate.validator)
    implementation("io.quarkus:quarkus-config-yaml")
    testImplementation(libs.rest.assured)
}
