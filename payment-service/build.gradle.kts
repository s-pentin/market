plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.openapi.generator")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.22")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generatePaymentServer") {
    description = "OpenAPI: генерация серверных интерфейсов"
    generatorName.set("spring")
    library.set("spring-boot")
    inputSpec.set("$rootDir/api-spec/payment-service-api.yaml")
    outputDir.set("${layout.buildDirectory.get().asFile}/generated/payment-server")
    apiPackage.set("org.market.payment.controller")
    modelPackage.set("org.market.payment.model")
    configOptions.set(
        mapOf(
            "useJakartaEe" to "true",
            "reactive" to "true",
            "interfaceOnly" to "true",
            "openApiNullable" to "false",
            "useTags" to "true"
        )
    )
}

sourceSets {
    main {
        java {
            srcDir("${layout.buildDirectory.get().asFile}/generated/payment-server/src/main/java")
        }
    }
}

tasks.compileJava {
    dependsOn(tasks.named("generatePaymentServer"))
}