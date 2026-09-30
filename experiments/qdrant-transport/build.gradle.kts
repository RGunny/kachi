plugins {
    java
    application
}

group = "me.rgunny.kachi.experiments"
version = "0.0.1"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("io.qdrant:client:1.19.0")
    implementation("tools.jackson.core:jackson-databind:3.2.2")
}

application {
    mainClass.set("me.rgunny.kachi.experiments.qdranttransport.Main")
    // 20,000점 × 1024차원 float 벡터를 메모리에 두고 배치로 보낸다. 질의 2,000개도 같이 든다.
    applicationDefaultJvmArgs = listOf("-Xmx2g")
}

