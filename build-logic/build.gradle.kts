plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    implementation(libs.sqlite.jdbc)
    implementation(libs.gson)

    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("exerciseBank") {
            id = "gymtracker.exercise-bank"
            implementationClass = "gymtracker.buildlogic.ExerciseBankPlugin"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
