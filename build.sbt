import org.scoverage.coveralls.CoverallsPlugin.CoverallsKeys._

val ProjectName = "telegramkit"

lazy val root = (project in file("."))
  .settings(noPublishingSettings)
  .aggregate(telegramKitSchema, telegramKitJson4s, telegramKitAkka)

lazy val telegramKitSchema = (project in file("schema"))
  .enablePlugins(TelegramKitPlugin)
  .settings(commonSettings)
  .settings(
    name := ProjectName + "-schema",
    telegramkitJsonLibrary in Compile := Common
  )

lazy val telegramKitJson4s = (project in file("json4s"))
  .enablePlugins(TelegramKitPlugin)
  .settings(commonSettings)
  .settings(
    name := ProjectName + "-json4s",
    telegramkitJsonLibrary in Compile := Json4s,
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % "3.3.0",
      "org.json4s" %% "json4s-ext" % "3.3.0"
    )
  )
  .dependsOn(telegramKitSchema)

lazy val telegramKitAkka = (project in file("akka"))
  .settings(commonSettings)
  .settings(
    name := ProjectName + "-akka",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-experimental" % "2.4.11",
      "org.slf4j" % "slf4j-api" % "1.7.21"
    )
  ).dependsOn(telegramKitSchema)

lazy val commonSettings =
  artifactSettings ++ compilationSettings ++ testSettings ++ publicationSettings

lazy val artifactSettings = Seq (
  organization := "com.github.bots4s",
  homepage := Some(url("https://github.com/bots4s/telegramkit")),
  startYear := Some(2016),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
)

lazy val compilationSettings =
  Seq(
    scalaVersion := "2.11.8",
    javacOptions ++= Seq(
      "-Xlint:all"
    ),
    scalacOptions in GlobalScope ++= Seq(
      "-encoding", "utf8",
      "-deprecation",
      "-unchecked",
      "-feature",
      "-language:_",
      "-Xcheckinit",
      "-Xlint",
      "-Xlog-reflective-calls"
    ),
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val testSettings =
  CoverallsPlugin.projectSettings ++
    Seq(
      scalacOptions in Test ++= Seq("-Yrangepos")
    )

lazy val publicationSettings = Seq(
  publishMavenStyle := true,
  scalacOptions ++= Seq(
    "-target:jvm-1.8"
  ),
  bintrayOrganization := Some("bots4s"),
  bintrayRepository := "TelegramKit",
  bintrayReleaseOnPublish := false,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra :=
    <scm>
      <url>https://github.com/bots4s/telegramkit.git</url>
      <connection>scm:git:git@github.com:bots4s/telegramkit.git</connection>
      <tag>HEAD</tag>
    </scm>
      <issueManagement>
        <system>github</system>
        <url>https://github.com/bots4s/telegramkit/issues</url>
      </issueManagement>
      <developers>
        <developer>
          <name>Lev Khomich</name>
          <email>levkhomich@gmail.com</email>
          <url>http://github.com/bots4s/telegramkit</url>
        </developer>
      </developers>
)

lazy val noPublishingSettings = Seq(
  publish := (),
  publishLocal := (),
  // aggregation is performed by coverageAggregate, so we can skip regular one
  aggregate in coveralls := false,
  childCoberturaFiles := Seq.empty,
  // workaround for sbt-pgp
  packagedArtifacts := Map.empty
)
