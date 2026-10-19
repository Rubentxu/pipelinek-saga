plugins {
    kotlin("jvm") version "2.4.10"
}

group = "dev.rubentxu.pipeline.v2.saga"
version = "0.1.0"

// INDEPENDENT BUILD. This repository never depends on PipelineK's source tree.
//
//   -PsdkRepo=<dir>      maven repository holding the published SDK artifacts
//   -PsdkVersion=<ver>   the SDK version to resolve
//
// The repository is the one the release publishes as an asset
// (`pipelinek-<train>.sdk.zip`); unzip it and point `-PsdkRepo` at the directory it
// unpacks into. There is NO maven-central publication of the contracts, so an external
// consumer resolves the SDK from the RC, and the RC is the thing that pins the version.
//
// No defaults, on purpose: a default could only be one that fails to resolve, and the
// failure would arrive hundreds of lines below the line that matters.
val sdkRepo: String = requireNotNull(providers.gradleProperty("sdkRepo").orNull) {
    """
    -PsdkRepo is required: point it at the directory the SDK bundle unpacks into, e.g.
    -PsdkRepo=/tmp/sdk
    """.trimIndent()
}
val sdkVersion: String = requireNotNull(providers.gradleProperty("sdkVersion").orNull) {
    """
    -PsdkVersion is required: this project resolves the published SDK contracts and has no
    default version, because a default could only be one that fails to resolve. Pass the
    version explicitly, e.g. -PsdkVersion=0.63.0
    """.trimIndent()
}

repositories {
    mavenCentral()
    maven {
        name = "sdk"
        url = uri(sdkRepo)
    }
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    // `compileOnly`: this project ships its own classes and the HOST supplies the contracts
    // at runtime. A plugin that bundled the contracts could run beside a second copy of
    // them, which is exactly the two-authorities split the published seam exists to prevent.
    //
    // ONLY `pipeline-domain` is on this list, and that is the point of PG-0: the Saga
    // contract is IN the published domain contract, so an external consumer needs nothing
    // else. If a future milestone needs another published module, it adds it here.
    //
    // Nothing else is declared, and that is a decision rather than an omission: the Saga
    // contract is plain data with no serialization of its own, because the ENGINE owns the
    // durable form of an obligation. A consumer that brought its own JSON layer, or pinned
    // its own version of one, would be duplicating an authority that is not its.
    compileOnly("dev.rubentxu.pipeline.v2:pipeline-domain:$sdkVersion")
}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
