package com.github.bots4s.telegramkit

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer

import com.github.bots4s.telegramkit.client.BotApiClient


abstract class TelegramBot(token: String)(implicit val system: ActorSystem) extends LongPollingSupport {
  protected implicit val materializer = ActorMaterializer()
  protected implicit def ec = system.dispatcher
  protected val log = Logging(system, getClass.getSimpleName)

  val client = new BotApiClient(token)

  def fileUrl(path: String): String = {
    s"https://api.telegram.org/file/bot$token/$path"
  }

}
