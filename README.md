# Kotatsu parsers

This library provides a collection of manga parsers for convenient access manga available on the web. It can be used in
JVM and Android applications.

![Sources count](https://img.shields.io/badge/dynamic/yaml?url=https%3A%2F%2Fraw.githubusercontent.com%2FKotatsuApp%2Fkotatsu-parsers%2Frefs%2Fheads%2Fmaster%2F.github%2Fsummary.yaml&query=total&label=manga%20sources&color=%23E9321C) [![](https://jitpack.io/v/KotatsuApp/kotatsu-parsers.svg)](https://jitpack.io/#KotatsuApp/kotatsu-parsers) ![License](https://img.shields.io/github/license/KotatsuApp/Kotatsu) [![Telegram](https://img.shields.io/badge/chat-telegram-60ACFF)](https://t.me/kotatsuapp) [![Discord](https://img.shields.io/discord/898363402467045416?color=5865f2&label=discord)](https://discord.gg/NNJ5RgVBC5)

## Usage

1. Add it to your root build.gradle at the end of repositories:

   ```groovy
   allprojects {
	   repositories {
		   ...
		   maven { url 'https://jitpack.io' }
	   }
   }
   ```

2. Add the dependency

   For Java/Kotlin project:
    ```groovy
    dependencies {
        implementation("com.github.KotatsuApp:kotatsu-parsers:$parsers_version")
    }
    ```

   For Android project:
    ```groovy
    dependencies {
        implementation("com.github.KotatsuApp:kotatsu-parsers:$parsers_version") {
            exclude group: 'org.json', module: 'json'
        }
    }
    ```

   Versions are available on [JitPack](https://jitpack.io/#KotatsuApp/kotatsu-parsers)

   When used in Android
   projects, [core library desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring) with
   the [NIO specification](https://developer.android.com/studio/write/java11-nio-support-table) should be enabled to
   support Java 8+ features.


3. Usage in code

   ```kotlin
   val parser = mangaLoaderContext.newParserInstance(MangaParserSource.MANGADEX)
   ```

   `mangaLoaderContext` is an implementation of the `MangaLoaderContext` class.
   See examples
   of [Android](https://github.com/KotatsuApp/Kotatsu/blob/devel/app/src/main/kotlin/org/koitharu/kotatsu/core/parser/MangaLoaderContextImpl.kt)
   and [Non-Android](https://github.com/KotatsuApp/kotatsu-dl/blob/master/src/jvmMain/kotlin/org/koitharu/kotatsu_dl/logic/MangaLoaderContextImpl.kt)
   implementation.

   Note that the `MangaParserSource.DUMMY` parsers cannot be instantiated.

## Projects that use the library

- [Kotatsu](https://github.com/KotatsuApp/Kotatsu)
- [kotatsu-dl](https://github.com/KotatsuApp/kotatsu-dl)
- [Shirizu (WIP)](https://github.com/ztimms73/shirizu)
- [OtakuWorld](https://github.com/jakepurple13/OtakuWorld)

## Contribution

See [CONTRIBUTING.md](./CONTRIBUTING.md) for the guidelines.

## DMCA disclaimer

The developers of this application have no affiliation with the content available in the app. It is collected from
sources freely available through any web browser.
