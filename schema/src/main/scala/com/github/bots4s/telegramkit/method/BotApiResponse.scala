package com.github.bots4s.telegramkit.method

case class BotApiResponse[T](ok: Boolean, errorCode: Option[Int], result: Option[T], description: Option[String])

