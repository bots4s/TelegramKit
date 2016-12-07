package com.github.bots4s.telegramkit

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.NotUsed
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.ControlThrowable

import com.github.bots4s.telegramkit.marshalling._
import com.github.bots4s.telegramkit.method.{BotApiRequest, BotApiResponse, GetUpdates, SetWebhook}
import com.github.bots4s.telegramkit.model.Update


trait LongPollingSupport { this: TelegramBot =>
  protected def timeout: Int = 30

  def receiveUpdate(update: Update): Unit

  private[this] def apiUpdateSource(implicit m: BotMarshaller[BotApiRequest[Seq[Update]]], um: BotUnmarshaller[BotApiResponse[Seq[Update]]], mt: Materializer, ec: ExecutionContext): Source[Update, NotUsed] = {
    val iterator = Iterator.iterate(Future.successful((0L, scala.collection.immutable.Seq.empty[Update]))) {
      _ flatMap { case (lastKnownOffset, updates) =>
        val offset = if (updates.isEmpty) lastKnownOffset.toInt else updates.maxBy(_.updateId).updateId
        client.request(GetUpdates(Some(offset + 1), timeout = Some(timeout))).recover {
          case e: ControlThrowable =>
            log.error(e, "error during update polling")
            throw e
          case e: Throwable =>
            if (!e.getClass.getCanonicalName.contains("UnexpectedDisconnectException"))
              log.error(e, "error during update polling")
            scala.collection.immutable.Seq.empty
        }.map { offset -> scala.collection.immutable.Seq(_:_*) }
      }
    }
    Source.fromIterator(() => iterator).mapAsync(1)(_.map(_._2)).mapConcat(identity)
  }

  def startPolling()(implicit
    m1: BotMarshaller[BotApiRequest[Boolean]],
    um1: BotUnmarshaller[BotApiResponse[Boolean]],
    m2: BotMarshaller[BotApiRequest[Seq[Update]]],
    um2: BotUnmarshaller[BotApiResponse[Seq[Update]]]
  ): Unit = {
    client.request(SetWebhook("")).foreach { _ =>
      apiUpdateSource.runForeach(receiveUpdate)
    }
  }
}
