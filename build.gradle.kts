plugins {
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    kotlin("plugin.jpa") version "2.3.20"
    jacoco
}

group = "com.mdwiki"
version = "0.1.5"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M4")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")

    // RAG Pipeline
    implementation("com.pgvector:pgvector:0.1.6")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.19.2")
    // HuggingFace tokenizers (BERT WordPiece) for cross-encoder reranker.
    // Поставляется как fat-jar с JNI под linux/mac; на первом запуске native-lib
    // извлекается в `~/.djl.ai/tokenizers/`. Используется в CrossEncoderReranker.
    implementation("ai.djl.huggingface:tokenizers:0.33.0")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    runtimeOnly("io.netty:netty-resolver-dns-native-macos") {
        artifact {
            classifier = "osx-aarch_64"
        }
    }
    runtimeOnly("io.netty:netty-resolver-dns-native-macos") {
        artifact {
            classifier = "osx-x86_64"
        }
    }

    // MCP Server
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

/**
 * Кладём содержимое `models/` (ONNX cross-encoder) в classpath jar, чтобы reranker не зависел
 * от внешнего volume/secret. Файл ~22 MB — приемлемая цена за готовый к работе образ.
 * Перекрыть путь всё ещё можно через `MDWIKI_RERANKER_MODEL_PATH` — тогда classpath-ресурс
 * игнорируется (см. CrossEncoderReranker.resolveModelPath).
 */
tasks.named<Copy>("processResources") {
    from(layout.projectDirectory.dir("models")) {
        into("models")
        exclude("**/.DS_Store", "**/Thumbs.db")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    environment("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "1")
    // mdwiki.jwt.secret обязателен (дефолта больше нет); тестовый, не для prod.
    environment("JWT_SECRET", "integration-test-jwt-secret-do-not-use-in-production")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

/** Spring не читает `.env` сам; подмешиваем в окружение процесса для локального bootRun. */
fun parseDotEnv(file: java.io.File): Map<String, String> {
    if (!file.isFile) return emptyMap()
    val out = LinkedHashMap<String, String>()
    file.readLines().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val eq = line.indexOf('=')
        if (eq <= 0) return@forEach
        val key = line.substring(0, eq).trim()
        if (key.isEmpty()) return@forEach
        var value = line.substring(eq + 1).trim()
        if (value.length >= 2) {
            val q = value.first()
            if ((q == '"' || q == '\'') && value.last() == q) {
                value = value.substring(1, value.length - 1)
            }
        }
        out[key] = value
    }
    return out
}

fun gitShortSha(): String {
    // In Docker builds .git is ignored — pass APP_GIT_SHA as build-arg/ENV.
    val fromEnv = System.getenv("APP_GIT_SHA")?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    return try {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim().ifEmpty { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }
}

fun gitVersionTag(): String {
    val fromEnv = System.getenv("APP_VERSION_TAG")?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    return try {
        providers.exec {
            commandLine("git", "describe", "--tags", "--always")
        }.standardOutput.asText.get().trim().ifEmpty { "v${version}" }
    } catch (_: Exception) {
        "v${version}"
    }
}

springBoot {
    buildInfo {
        properties {
            additional.put("gitSha", gitShortSha())
            additional.put("versionTag", gitVersionTag())
        }
    }
}

tasks.bootRun {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    environment(parseDotEnv(layout.projectDirectory.file(".env").asFile))
}

/**
 * Paketo по умолчанию собирает на Tiny-stack (noble-tiny), в котором отсутствуют locale-данные
 * glibc. Из-за этого JVM внутри контейнера игнорирует `-Dsun.jnu.encoding=UTF-8` для имён
 * файлов: `nl_langinfo(CODESET)` возвращает `ANSI_X3.4-1968` (ASCII), и кириллица в путях
 * читается как `?`/`\uFFFD`. Переключаемся на `builder-jammy-base`, где есть полноценный
 * Ubuntu с UTF-8 локалью.
 */
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    builder.set("paketobuildpacks/builder-jammy-base")
    runImage.set("paketobuildpacks/run-jammy-base")
    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "25",
            "BPL_JVM_THREAD_COUNT" to "250"
        )
    )
}
