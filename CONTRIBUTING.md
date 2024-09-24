# Contributing

The following is a guide for creating Kotatsu parsers. Thanks for taking the time to contribute!

## Prerequisites

Before you start, please note that the ability to use the following technologies is **required**.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)
- Web scraping ([JSoup](https://jsoup.org/)) or JSON API

### Tools

- [Android Studio](https://developer.android.com/studio)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community edition is enough)
- Android device (or emulator)

Kotatsu parsers are not a part of the Android application, but you can easily develop and test it directly inside an
Android application project and relocate it to the library project when done.

### Before you start

First, take a look at the `kotatsu-parsers` project structure. Each parser is a single class that
extends the `MangaParser` class and has a `MangaSourceParser` annotation.
Also, pay attention to extensions in the `util` package. For example, extensions from the `Jsoup` file
should be used instead of existing JSoup functions because they have better nullability support
and improved error messages.

## Writing your parser

So, you want to create a parser, that will provide access to manga from a website.
First, you should explore a website to learn about API availability.
If it does not contain any documentation about
API, [explore network requests](https://firefox-source-docs.mozilla.org/devtools-user/):
some websites use AJAX.

- [Example](https://github.com/KotatsuApp/kotatsu-parsers/blob/master/src/main/kotlin/org/koitharu/kotatsu/parsers/site/ru/DesuMeParser.kt)
  of Json API usage.
- [Example](https://github.com/KotatsuApp/kotatsu-parsers/blob/master/src/main/kotlin/org/koitharu/kotatsu/parsers/site/be/AnibelParser.kt)
  of GraphQL API usage
- [Example](https://github.com/KotatsuApp/kotatsu-parsers/blob/master/src/main/kotlin/org/koitharu/kotatsu/parsers/site/en/MangaTownParser.kt)
  of pure HTML parsing.

If the website is based on some engine it is rationally to use a common base class for this one (for example, Madara
Wordpress theme and the `MadaraParser` class)

### Parser class skeleton

The parser class must have exactly one primary constructor parameter of type `MangaLoaderContext` and have an
`MangaSourceParser` annotation that provides the internal name, title, and language of a manga source.

All members of the `MangaParser` class are documented. Pay attention to some peculiarities:

- Never hardcode domain. Specify the default domain in the `configKeyDomain` field and obtain an actual one using
  `domain`.
- All IDs must be unique and domain-independent. Use `generateUid` functions with a relative URL or some internal id
  that is unique across the manga source.
- The `availableSortOrders` set should not be empty. If your source does not support sorting, specify one most relevant
  value.
- If you cannot obtain direct links to page images inside the `getPages` method, it is ok to use an intermediate URL
  as `Page.url` and fetch a direct link in the `getPageUrl` function.
- You can use _asserts_ to check some optional fields. For example, the `Manga.author` field is not required, but if
  your source provides this information, add `assert(it != null)`. This will not have any effect on production but help
  to find issues during unit testing.
- If your source website (or its API) uses pages for pagination instead of offset you should extend `PagedMangaParser`
  instead of `MangaParser`.
- If your source website (or its API) does not provide pagination (has only one page of content) you should extend
  `SinglePageMangaParser` instead of `MangaParser` or `PagedMangaParser`.
- Your parser may also implement the `Interceptor` interface for additional manipulation of all network requests and
  responses, including image loading.

## Development process

During the development, it is recommended (but not necessary) to write it directly
in the Kotatsu Android application project. You can use the `core.parser.DummyParser` class as a sandbox. The `Dummy`
manga source is available in the debug Kotatsu build.

Once the parser is ready you can relocate your code into the `kotatsu-parsers` library project in a `site` package and
create a Pull Request.

### Testing

It is recommended that unit tests be run before submitting a PR.

- Temporary modify the `MangaSources` annotation class: specify your parser(s) name(s) and change mode
  to `EnumSource.Mode.INCLUDE`
- Run the `MangaParserTest` (`gradlew :test --tests "org.koitharu.kotatsu.parsers.MangaParserTest"`)
- Optionally, you can run the `generateTestsReport` gradle task to get a pretty readable html report from test results.

## Help

If you need help or have some questions, ask a community in our [Telegram chat](https://t.me/kotatsuapp)
or [Discord server](https://discord.gg/NNJ5RgVBC5).
