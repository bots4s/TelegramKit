package com.github.bots4s.telegramkit.model

import java.nio.file.Path

sealed trait InputFile

object InputFile {
  private[telegramkit] final case class InputFilePath(path: Path) extends InputFile
  private[telegramkit] final case class InputFileData(filename: String, contents: Array[Byte]) extends InputFile

  def apply(path: Path): InputFile =
    InputFilePath(path)

  def apply(filename: String, contents: Array[Byte]): InputFile =
    InputFileData(filename, contents)
}

