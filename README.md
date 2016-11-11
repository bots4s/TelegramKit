TelegramKit
===========

Up-to-date Telegram Bot API library.

Key features:
- strictly typed
- fully asynchronous
- supports full Telegram API (no webhooks yet)
- code generation right from Telegram's Bot API page
- configurable dependencies

Dependencies
============

Currently, all artifacts are published to bintray, so you will need to add corresponding resolver:
```
resolvers += Resolver.bintrayRepo("bots4s", "TelegramKit")
```

You should choose JSON and HTTP client dependencies suitable for your project from the
list of supported libraries.

JSON artifacts:
```
"com.github.bots4s" %% "telegramkit-json4s" % "0.1"
```

HTTP client artifacts:
```
"com.github.bots4s" %% "telegramkit-akka" % "0.1"
```

Basic example
=============

```scala
import akka.actor.ActorSystem

import com.github.bots4s.telegramkit.model.Update
import com.github.bots4s.telegramkit.marshalling.json4s._

object Bot extends App {
  implicit val system = ActorSystem()
  new TelegramBot("token") {
    override def receiveUpdate(update: Update): Unit = {
      log.error("received " + update)
    }
  }.startPolling()
}
```

See complete example [here](https://github.com/bots4s/telegramkit/tree/master/example).

Building
========

In order to start hacking, you will need to publish project's sbt-plugin locally:
```bash
cd sbt-plugin && sbt publishLocal && cd -
```

WIP note
========

Please, be aware, that this framework is actively developed, thus, source compatibility between
releases is not guaranteed yet. Any feedback is very much appreciated.
