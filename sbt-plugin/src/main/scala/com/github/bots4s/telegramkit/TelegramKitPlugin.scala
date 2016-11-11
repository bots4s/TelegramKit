package com.github.bots4s.telegramkit

import sbt.Keys._
import sbt._


object TelegramKitPlugin extends AutoPlugin {

  override def requires = sbt.plugins.JvmPlugin

  object autoImport {
    sealed trait JsonLibrary
    object Json4s extends JsonLibrary
    object Common extends JsonLibrary

    val telegramkitGen = TaskKey[Seq[File]](
      "telegramkit-gen",
      "generate code from Telegram Bot API page"
    )

    val telegramkitJsonLibrary = Def.settingKey[JsonLibrary]("Target JSON library to generate wrappers for")
  }

  import autoImport._

  lazy val settings: Seq[Setting[_]] = Seq(
    telegramkitJsonLibrary := Common,
    telegramkitGen <<= (
      telegramkitJsonLibrary,
      sourceManaged
    ) map { (jsonLibrary, srcManaged) =>
      println("Generating wrappers: " + jsonLibrary)
      val schemaUrl = "https://core.telegram.org/bots/api"
      val schema = SchemaParser.parse(schemaUrl)
      jsonLibrary match {
        case Json4s =>
          schema.generateJson4s(srcManaged.getPath, "com.github.bots4s.telegramkit")
        case Common =>
          schema.generateSchema(srcManaged.getPath, "com.github.bots4s.telegramkit")
      }
    },
    sourceGenerators <+= telegramkitGen
  )

  override def projectSettings: Seq[Setting[_]] =
    inConfig(Compile)(settings) ++ inConfig(Test)(settings)
}
