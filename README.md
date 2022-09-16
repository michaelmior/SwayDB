# <img src="docs/swaydb_logo.png" align = "right"/> SwayDB [![Gitter Chat][gitter-badge]][gitter-link] [![Build status][build-badge]][build-link] [![Maven central][maven-badge]][maven-link]

[gitter-badge]: https://badges.gitter.im/Join%20Chat.svg

[gitter-link]: https://gitter.im/SwayDB-chat/Lobby

[slack-badge]: https://img.shields.io/badge/slack-join%20chat-e01563.svg

[maven-badge]: https://img.shields.io/maven-central/v/io.swaydb/swaydb_2.12.svg

[maven-link]: https://search.maven.org/search?q=g:io.swaydb%20AND%20a:swaydb_2.12

[build-badge]: https://github.com/simerplaha/SwayDB/workflows/Build/badge.svg

[build-link]: https://github.com/simerplaha/SwayDB/actions

**Persistent** and **in-memory** key-value storage engine for the JVM aimed at high performance & resource efficiency.

**Small footprint**: around 7.1 MB jar size. No external core
dependency ([#307](https://github.com/simerplaha/SwayDB/issues/307)).

**Scalable on a single machine**: Distribute data to multiple local SSDs. Allocate single or multiple `Threads`
for reads, caching & compaction.

**Branches**: This is the `development` branch. See [`master`](https://github.com/simerplaha/SwayDB/tree/master) for
stable version.

**Status**: Under [testing](https://github.com/simerplaha/SwayDB/issues?q=is%3Aissue+is%3Aopen+label%3A%22Test+cases%22)
& [performance](https://github.com/simerplaha/SwayDB/issues?q=is%3Aissue+is%3Aopen+label%3APerformance) optimisations.
See [project status](#Project-status).

[Documentation](https://swaydb.io)

# Sponsors

Thank you [JetBrains](https://www.jetbrains.com/?from=SwayDB)
& [JProfiler](https://www.ej-technologies.com/products/jprofiler/overview.html)
for full-featured open-source licences to their awesome development tools!

<table>
  <tr>
    <td><a href="https://www.jetbrains.com/?from=SwayDB" target="_blank"><img src="/docs/jetbrains_logo.png" alt="Jetbrains support"/></a></td>
    <td><a href="https://www.ej-technologies.com/products/jprofiler/overview.html" target="_blank"><img src="/docs/jprofiler_logo.png" alt="JProfiler support"/></a></td>
    <td><a href="https://github.com/sponsors/simerplaha" target="_blank">[Become a sponsor]</a></td>
  </tr>
</table>

## Overview

- Simple data types - `Map`, `Set`, `Queue`, `SetMap` & `MultiMap` with native Java and Scala collections support.
- Conditional updates using any pure [JVM function](https://swaydb.io/api/pure-functions/?language=java) - **No query
  language**.
- Atomic updates and inserts with `Transaction` API.
- **Non-blocking core** with configurable APIs for blocking, non-blocking and/or reactive use-cases.
- Single or multiple disks persistent, in-memory or eventually persistent.
- [Streams](https://swaydb.io/api/stream/?language=java) - Async & sync forward and reverse data streaming/iteration.
- TTL - non-blocking, non-polling asynchronous auto [expiring](https://swaydb.io/api/expire/?language=java) key-values.
- Range operations to update, remove & expire a range of key-values.
- Key only iterations (Lazily fetched values).
- [Configurable compression](https://swaydb.io/configuration/compressions/?language=scala) with LZ4 & Snappy
- Configurable core internals to support custom workloads.
- Duplicate values elimination
  with [compressDuplicateValues](https://swaydb.io/configuration/valuesConfig/?language=scala).

## Use cases

Highly configurable to suit **different workloads**. Some known use-cases are:

- General key-value storage
- Message queues
- Time-series or Events data
- Caching
- Application logs
- Archiving data or cold storage with high file level compression

## Quick start

- [Java - Quick start](https://swaydb.io/quick-start/?language=java&data-type=map&functions=off)
- [Scala - Quick start](https://swaydb.io/quick-start/?language=scala&data-type=map&functions=off)
- [Kotlin - Quick start](https://github.com/simerplaha/SwayDB.kotlin.examples/blob/master/src/main/kotlin/quickstart/QuickStartMapSimple.kt)

## Contributing

Contributions are encouraged and welcomed. We are here to help and answer any questions.

**Code of conduct** - Be nice, welcoming, friendly & supportive of each other. Follow the Apache
foundation's [COC](https://www.apache.org/foundation/policies/conduct.html).

- **Contributing to data management API**
    - Build new data structures extending existing data structures.
      See [MultiMap](https://github.com/simerplaha/SwayDB/blob/master/swaydb/src/main/scala/swaydb/MultiMap.scala)
      for example which is an extension
      of [Map](https://github.com/simerplaha/SwayDB/blob/master/swaydb/src/main/scala/swaydb/Map.scala).
        - Graph
        - List
        - Geospatial
        - Logs
        - Observables
        - Full-text search
        - etc
    - Add support for external serialising, streaming & compression libraries.
    - Test and improve existing data structures.

- **Contributing to core API**
    - See issues
      labelled [good first issue](https://github.com/simerplaha/SwayDB/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
      .

- **Contributing to core internals**
    - See code marked `TODO`.
    - Pick any small section to improve. You will find that everything is a function and can be unit-tested
      independently, so you can easily pick anything to work on.

## Project status

Under [testing](https://github.com/simerplaha/SwayDB/issues?q=is%3Aissue+is%3Aopen+label%3A%22Test+cases%22)
& [performance](https://github.com/simerplaha/SwayDB/issues?q=is%3Aissue+is%3Aopen+label%3APerformance) optimisations.

Your feedback and support is very important to get to production. Please get involved
via [chat](https://gitter.im/SwayDB-chat/Lobby), [discussion](https://github.com/simerplaha/SwayDB/discussions),
[issues](https://github.com/simerplaha/SwayDB/issues)
or by [becoming a sponsor](https://github.com/sponsors/simerplaha).

Future releases might not be backward compatible until we are production ready.

See tasks labelled [Production release](https://github.com/simerplaha/SwayDB/labels/Production%20release)
that are required before becoming production ready.

## Related GitHub projects

- [SwayDB.java.examples](https://github.com/simerplaha/SwayDB.java.examples) - Java examples demonstrating features and
  APIs.
- [SwayDB.kotlin.examples](https://github.com/simerplaha/SwayDB.kotlin.examples) - Kotlin examples demonstrating
  features and APIs.
- [SwayDB.scala.examples](https://github.com/simerplaha/SwayDB.scala.examples) - Scala examples demonstrating features
  and APIs.
- [SwayDB.benchmark](https://github.com/simerplaha/SwayDB.benchmark) - Performance benchmarks.
