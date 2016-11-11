package com.github.bots4s.telegramkit

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