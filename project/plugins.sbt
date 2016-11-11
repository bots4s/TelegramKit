resolvers += Classpaths.sbtPluginReleases

resolvers += Resolver.mavenLocal

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.1")
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.0.0.BETA1")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("com.github.bots4s" % "telegramkit-sbt-plugin" % "0.1-SNAPSHOT")
