plugins {
    id("nookfall.java-conventions")
    id("nookfall.publishing")
}

description = "Nookfall Framework storage: pooled JDBC access, async queries, transactions and migrations."

dependencies {
    api(project(":framework-api"))
    implementation(libs.hikari) {
        // Paper provides SLF4J.
        exclude(group = "org.slf4j")
    }
    compileOnly(libs.paper.api)
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    testImplementation(libs.sqlite.jdbc)
    // Paper ships Connector/J; tests use the same driver for MySQL connections.
    testRuntimeOnly("com.mysql:mysql-connector-j:9.7.0")
    testImplementation(libs.mariadb.jdbc)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.paper.api)
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
