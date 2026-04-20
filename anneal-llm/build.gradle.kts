description = "LLM integration — fix generation, ONNX embeddings, provider abstraction"

dependencies {
    implementation(project(":anneal-core"))

    implementation(libs.langchain4j)
    implementation(libs.langchain4j.ollama)
    implementation(libs.langchain4j.anthropic)
    implementation(libs.langchain4j.embeddings.minilm)
}
