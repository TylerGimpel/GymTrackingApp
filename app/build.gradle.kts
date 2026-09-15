import gymtracker.buildlogic.CheckRoomSchemasFrozenTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    id("gymtracker.exercise-bank")
}

/**
 * Room's exported schema.
 *
 * This directory is committed: it is the record of what shipped at each database version, and it is
 * what the prepopulated exercise bank is generated from. Hand-written DDL would be free to drift
 * from the entities, and a prepopulated file whose identity hash disagrees with Room's is a runtime
 * crash rather than a compile error.
 */
val roomSchemaDirectory = layout.projectDirectory.dir("schemas")
val roomDatabaseVersion = 10

android {
    namespace = "com.tylergimpel.gymtracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tylergimpel.gymtracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // Licence files several Compose artifacts each ship, which collide when merged.
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

room {
    schemaDirectory(roomSchemaDirectory.asFile.absolutePath)
}

/**
 * Room rewrites the exported schema for whatever version it is compiling, so an entity change
 * without a version bump silently rewrites the record of a version already in the wild. This is
 * what notices.
 */
val checkRoomSchemasFrozen = tasks.register<CheckRoomSchemasFrozenTask>("checkRoomSchemasFrozen") {
    schemaDirectory.set(roomSchemaDirectory)
    currentVersion.set(roomDatabaseVersion)
    repositoryRoot.set(rootProject.layout.projectDirectory)
}

tasks.named("check") { dependsOn(checkRoomSchemasFrozen) }

exerciseBank {
    schemaDirectory.set(roomSchemaDirectory)
    databaseClass.set("com.tylergimpel.gymtracker.data.GymDatabase")
    databaseVersion.set(roomDatabaseVersion)
    assetPath.set("databases/gym-bank.db")
}

dependencies {
    constraints {
        // room-migration, which room-testing pulls in for MigrationTestHelper, needs
        // kotlinx-serialization 1.8.1. AGP resolves the androidTest classpath consistently with the
        // app's, so pinning it here lifts both; leaving them apart throws AbstractMethodError from
        // inside the serializer at runtime rather than failing to resolve.
        implementation(libs.kotlinx.serialization.core)
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
