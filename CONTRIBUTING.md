# Contributing

The following is guide for creating a Kotatsu parsers. Thanks for taking the time to contribute!

## Prerequisites

Before you start, please note that the ability to use following technologies is **required**.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)
- Web scraping ([JSoup](https://jsoup.org/)) or JSON API

### Tools

- [Android Studio](https://developer.android.com/studio)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community edition is enough)
- Android device (or emulator)

Kotatsu parsers is not a part of Android application, but you can easily develop and test it directly inside an Android
application project and relocate it to the library project when done.

### Before you start

First, take a look at `kotatsu-parsers` project structure. Each parser is a single class that
extends `MangaParser` class and have a `MangaSourceParser` annotation.
Also pay attention on extensions in `util` package. For example, extensions from `Jsoup` file
should be used instead of existing JSoup functions because they have better nullability support
and improved error messages.

## Writing your parser

So, you want to create a parser, that will provide access to manga from a website.
First, you should explore a website for API availability.
If it does not contain any documentation about
API, [explore network requests](https://firefox-source-docs.mozilla.org/devtools-user/):
some websites use ajax.

- [Example](https://github.com/KotatsuApp/kotatsu-parsers/blob/master/src/main/kotlin/org/koitharu/kotatsu/parsers/site/ru/DesuMeParser.kt)
  of Json API usage.
- [Example](https://github.com/KotatsuApp/kotatsu-parsers/blob/master/src/main/kotlin/org/koitharu/kotatsu/parsers/site/be/AnibelParser.kt)
  of GraphQL API usage
- [Example](https://github.com/KotatsuApp/kotatsu-parsers/blob/master/src/main/kotlin/org/koitharu/kotatsu/parsers/site/en/MangaTownParser.kt)
  of pure HTML parsing.

If website is based on some engine it is rationally to use common base class for this one (for example, Madara wordress
theme
and the `MadaraParser` class)

### Parser class skeleton

Parser class must have exactly one primary constructor parameter of type `MangaLoaderContext` and have an
`MangaSourceParser` annotation that provides internal name, title and language of a manga source.

All functions in `MangaParser` class are documented. Pay attention to some peculiarities:

- Never hardcode domain. Specify default domain in `configKeyDomain` field and obtain an actual one using `getDomain()`.
- All ids must be unique and domain-independent. Use `generateUid` functions with relative url or some internal id which
  is unique across the manga source.
- `sortOrders` set should not be empty. If your source is not support sorting, specify one most relevance value.
- If you cannot obtain direct links to pages images inside `getPages` method, it is ok to use an intermediate url
  as `Page.url` and fetch a direct link at `getPageUrl` function.
- You can use _asserts_ to check some optional fields. For example. `Manga.author` field is not required, but if your
  source provide such information, add `assert(it != null)`. This will not have any effect on production but help to
  find issues during unit testing.
- If your source website (or it's api) uses pages for pagination instead of offset you should extend `PagedMangaParser`
  instead of `MangaParser`.
- If your source website (or it's api) do not provide pagination (has only one page of content) you should extend
  `SinglePageMangaParser` instead of `MangaParser` nor `PagedMangaParser.
- Your parser may also implement the `Interceptor` interface for additional manipulation of all network requests and/or
  responses, including image loading.

## Development process

During the development it is recommended (but not necessary) to write it directly
in the Kotatsu android application project. You can use `core.parser.DummyParser` class as a sandbox. `Dummy` manga
source is available in debug Kotatsu build.

Once parser is ready you can relocate your code into `kotatsu-parsers` library project in a `site` package and create a
Pull Request.

### Testing

It is recommended to run unit tests before submitting a PR.

- Temporary modify the `MangaSources` annotation class: specify your parser(s) name(s) and change mode
  to `EnumSource.Mode.INCLUDE`
- Run the `MangaParserTest` (`gradlew :test --tests "org.koitharu.kotatsu.parsers.MangaParserTest"`)
- Optionally, you can run the `generateTestsReport` gradle task to get a pretty readable html report from test results.

## Help

If you need a help or have some questions, ask a community in our [Telegram chat](https://t.me/kotatsuapp)
or [Discord server](https://discord.gg/NNJ5RgVBC5).
