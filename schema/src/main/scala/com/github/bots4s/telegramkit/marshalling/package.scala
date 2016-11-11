package com.github.bots4s.telegramkit

package object marshalling {
  type BotMarshaller[T] = T => String
  type BotUnmarshaller[T] = String => T
}
