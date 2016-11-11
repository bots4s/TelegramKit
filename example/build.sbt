scalaVersion in ThisBuild := "2.11.8"
scalacOptions in ThisBuild ++= Seq("-deprecation", "-target:jvm-1.7")

resolvers += Resolver.bintrayRepo("bots4s", "TelegramKit")

libraryDependencies ++= Seq(
  "com.github.bots4s" %% "telegramkit-akka" % "0.1",
  "com.github.bots4s" %% "telegramkit-json4s" % "0.1"
)
