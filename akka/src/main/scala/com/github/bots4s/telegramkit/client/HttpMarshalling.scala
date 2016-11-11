package com.github.bots4s.telegramkit.client

import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, Multipart}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}

import com.github.bots4s.telegramkit.marshalling.{BotMarshaller, BotUnmarshaller}
import com.github.bots4s.telegramkit.method.{BotApiRequest, BotApiRequestJson, BotApiRequestMultipart}
import com.github.bots4s.telegramkit.model._


private[client] trait HttpMarshalling { this: BotApiClient =>
  implicit def unmarshaller[T: Manifest](implicit um: BotUnmarshaller[T]): FromEntityUnmarshaller[T] = {
    Unmarshaller.stringUnmarshaller.map(body => um(body))
  }

  implicit def marshaller[T](implicit m: BotMarshaller[BotApiRequestJson[T]]): ToEntityMarshaller[BotApiRequest[T]] = {
    Marshaller.strict {
      case request: BotApiRequestJson[T] =>
        val content = m(request)
        Marshalling.Opaque(() => HttpEntity(ContentTypes.`application/json`, content))
      case request: BotApiRequestMultipart[T] =>
        def flatten(value: Any): Any = value match {
          case Some(x) => flatten(x)
          case e: Either[_, _] => flatten(e.fold(identity, identity))
          case _ => value
        }
        val fields = request.getClass.getDeclaredFields.iterator.zip(request.productIterator).map {
          case (k, v) => HttpMarshalling.snakeCase(k.getName) -> flatten(v)
        }.filterNot(_._2 == None)

        if (fields.isEmpty) {
          Marshalling.Opaque(() => HttpEntity.empty(ContentTypes.`application/octet-stream`))
        } else {
          val parts = fields map {
            case (k, v @ (_: String | _: Boolean | _: Long | _: Float)) =>
              Multipart.FormData.BodyPart(k, HttpEntity(v.toString))
            case (k, InputFile.InputFilePath(path)) =>
              Multipart.FormData.BodyPart.fromPath(k, MediaTypes.`application/octet-stream`, path)
            case (k, InputFile.InputFileData(filename, bs)) =>
              Multipart.FormData.BodyPart(k, HttpEntity(ContentTypes.`application/octet-stream`, bs), Map("filename" -> filename))
            case (k, other) =>
              log.warning(s"unexpected field in multipart request, $k: $other")
              Multipart.FormData.BodyPart(k, HttpEntity(""))
          }
          Marshalling.Opaque(() => Multipart.FormData(parts.toSeq: _*).toEntity())
        }
    }
  }
}

private[client] object HttpMarshalling {
  private val CapitalLetter = "[A-Z]".r
  def snakeCase(s: String): String =
    CapitalLetter.replaceAllIn(s, { "_" + _.group(0).toLowerCase })
}