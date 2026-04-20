description = "Persistence — Panache entities, repositories, Flyway migrations, pgvector"

dependencies {
    implementation(project(":anneal-core"))

    implementation(libs.quarkus.hibernate.orm.panache)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.flyway)
    implementation(libs.langchain4j.pgvector)

    testImplementation(libs.quarkus.test.h2)
}
