plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * The exercise bank generator lives in the `build-logic` included build, which Gradle builds but
 * does not test on its own. Exposing its tests here keeps the rules that guard the shipped data
 * verifiable from the root build rather than only when someone remembers to run them.
 */
val checkBuildLogic = tasks.register("checkBuildLogic") {
    group = "verification"
    description = "Runs the exercise bank build-logic tests."
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}

tasks.register("check") {
    group = "verification"
    description = "Runs every check in the build, including the build-logic tests."
    dependsOn(checkBuildLogic)
    dependsOn(subprojects.map { "${it.path}:check" })
}
