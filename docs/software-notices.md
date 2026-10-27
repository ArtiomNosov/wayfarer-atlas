# Software notices

Runtime components are taken from their official Maven distributions; their POM license declarations were inspected for the versions used by this release.

| Components | Publisher/project | License |
| --- | --- | --- |
| Jetpack Compose, Activity, Lifecycle, WorkManager and AndroidX transitive libraries | Android Open Source Project / AndroidX | Apache License2.0 |
| Kotlin standard library | JetBrains / Kotlin contributors | Apache License2.0 |
| kotlinx.coroutines | JetBrains / contributors | Apache License2.0 |
| Gson2.13.2 | Google / Gson contributors | Apache License2.0 |

A full [Apache2.0 license](licenses/Apache-2.0.txt) is included here, copied unchanged from the AndroidX license text already retained inside the built APK under META-INF/androidx/. Distribution metadata retained by the Android build is not removed. Version declarations are in app/build.gradle.kts; resolved runtime dependencies can be inspected with scripts/gradle.sh :app:dependencies --configuration releaseRuntimeClasspath.

Country/reference data have separate Unicode3.0, MIT, CC BY4.0 and public-domain terms; see [data sources](data-sources.md), app assets/licenses.txt and data/sources/. This file does not change those terms. Build/test tools are not bundled in the application or source archive and retain their own distribution licenses.
