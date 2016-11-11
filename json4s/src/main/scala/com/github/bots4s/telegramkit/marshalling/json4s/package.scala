package com.github.bots4s.telegramkit.marshalling

import org.json4s.Extraction
import org.json4s.jackson.JsonMethods.{compact, parse, render}

import com.github.bots4s.telegramkit.Json4sFormats.formats


package object json4s {
  implicit def json4sMarshaller[T]: BotMarshaller[T] = { x: T =>
    compact(render(Extraction.decompose(x).underscoreKeys))
  }
  implicit def json4sUnmarshaller[T : Manifest]: BotUnmarshaller[T] = { x: String =>
    parse(x).camelizeKeys.extract[T]
  }
}
