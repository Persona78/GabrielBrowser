// AGP 9.1.1 é o mínimo que suporta compileSdk 37 (exigido pelo GeckoView 159+).
// O AGP 9 já inclui suporte a Kotlin: não é preciso o plugin org.jetbrains.kotlin.android.
plugins {
    id("com.android.application") version "9.1.1" apply false
}
