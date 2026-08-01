plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.openapi.generator")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.postgresql:r2dbc-postgresql")

    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.6"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:r2dbc")
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generatePaymentClient") {
    description = "OpenAPI: генерация клиента для payment-service"
    generatorName.set("java")
    library.set("webclient")
    inputSpec.set("$rootDir/api-spec/payment-service-api.yaml")
    outputDir.set("${layout.buildDirectory.get().asFile}/generated/payment-client")
    apiPackage.set("org.market.app.payment.api")
    modelPackage.set("org.market.app.payment.model")
    configOptions.set(
        mapOf(
            "useJakartaEe" to "true",
            "reactive" to "true",
            "openApiNullable" to "false",
            "useTags" to "true"
        )
    )
}

sourceSets {
    main {
        java {
            srcDir("${layout.buildDirectory.get().asFile}/generated/payment-client/src/main/java")
        }
    }
}

tasks.compileJava {
    dependsOn(tasks.named("generatePaymentClient"))
}