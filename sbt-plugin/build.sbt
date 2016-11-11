organization := "com.github.bots4s"
name := "telegramkit-sbt-plugin"
version := "0.1-SNAPSHOT"

homepage := Some(url("https://github.com/bots4s/telegram-kit"))
startYear := Some(2016)
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

sbtPlugin := true
publishMavenStyle := false
scalaVersion in Compile := "2.10.5"
scalacOptions in Compile ++= Seq("-deprecation", "-target:jvm-1.7")
libraryDependencies ++= Seq(
  "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
  "ch.qos.logback" % "logback-classic" % "1.1.3"
)
