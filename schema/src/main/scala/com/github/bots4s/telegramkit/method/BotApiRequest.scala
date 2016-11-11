package com.github.bots4s.telegramkit.method

trait BotApiRequest[T] extends Product {
  def name: String = {
    val sn = getClass.getSimpleName
    val pos = sn.indexOf('$')
    if (pos >= 0) sn.take(pos) else sn
  }
}
trait BotApiRequestJson[T] extends BotApiRequest[T]
trait BotApiRequestMultipart[T] extends BotApiRequest[T]


