description = "Domain model, rule engine, AST scanner — pure Java, zero framework dependencies"

dependencies {
    implementation(libs.javaparser.symbol.solver)

    testImplementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0")
    testImplementation("jakarta.xml.ws:jakarta.xml.ws-api:4.0.0")

}
