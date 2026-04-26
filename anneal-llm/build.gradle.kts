dependencies {
    implementation(project(":anneal-core"))
    implementation(libs.quarkus.arc)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.ollama)
    implementation(libs.langchain4j.anthropic) {
        exclude(group = "dev.langchain4j", module = "langchain4j-http-client-jdk")
    }
    implementation(libs.langchain4j.embeddings.minilm)
}

tasks.named("test") {
    enabled = false
}