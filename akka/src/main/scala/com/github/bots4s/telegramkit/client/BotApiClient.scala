package com.github.bots4s.telegramkit.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.control.ControlThrowable

import com.github.bots4s.telegramkit.marshalling.{BotMarshaller, BotUnmarshaller}
import com.github.bots4s.telegramkit.method._


final case class BotApiException(statusCode: Int, summary: String, detail: Option[String]) extends
  ExceptionWithErrorInfo(ErrorInfo(summary, detail.getOrElse("")))

class BotApiClient(token: String)(implicit system: ActorSystem, mt: Materializer) extends HttpMarshalling {
  import system.dispatcher

  private[this] val http = Http()
  protected val botApiEndpoint = "https://api.telegram.org"
  protected val log = Logging(system, getClass.getSimpleName)

  def request[R: Manifest](request: BotApiRequest[R])(implicit m: BotMarshaller[BotApiRequest[R]], um: BotUnmarshaller[BotApiResponse[R]]): Future[R] = {
    Marshal(request).to[RequestEntity].map { entity =>
      log.debug("calling " + request.name)
      HttpRequest(HttpMethods.POST, Uri(s"$botApiEndpoint/bot$token/${request.name}"), entity = entity)
    }.flatMap(http.singleRequest(_)).flatMap { response =>
      if (request.name != "GetUpdates") {
        log.info(request.name + " response " + response.entity.toStrict(1.second)
          .value.flatMap(_.toOption.map(_.data.utf8String)).getOrElse(""))
      }
      Unmarshal(response.entity).to[BotApiResponse[R]]
    }.flatMap {
      case BotApiResponse(true, _, Some(result), _) =>
        Future.successful(result)
      case BotApiResponse(false, maybeErrorCode, _, maybeDescr) =>
        val errorCode = maybeErrorCode.getOrElse(500)
        Future.failed(BotApiException(errorCode, "error response " + errorCode, maybeDescr))
      case BotApiResponse(true, maybeErrorCode, None, maybeDescr) =>
        val statusCode = maybeErrorCode.getOrElse(200)
        Future.failed(BotApiException(statusCode, "empty result " + statusCode, maybeDescr))
    }.recover {
      case e: ControlThrowable =>
        log.error(e, "control error during processing of " + request.name)
        throw e
      case e: Throwable =>
        if (request.name != "GetUpdates") {
          log.error(e, "error during processing of " + request.name)
        }
        throw e
    }
  }
}
