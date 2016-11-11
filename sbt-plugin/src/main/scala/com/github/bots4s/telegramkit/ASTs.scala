package com.github.bots4s.telegramkit

/**
 * Base trait for Telegram Bot Schema AST
 */
sealed trait BotApiType

/**
 * Simple container, i.e. Name[Nested]
 * @param name container name
 * @param nested container element type
 */
final case class ContainerType(name: String, nested: BotApiType) extends BotApiType

/**
 * Constant defined by "must be" clause
 */
final case class ConstantValue(nested: BotApiType, value: String) extends BotApiType

/**
 * Union type
 * @param nested union members
 */
final case class UnionType(nested: Seq[BotApiType]) extends BotApiType {
  // TODO: enum support
  val group =
    if (nested.forall(t => t.isInstanceOf[SchemaType])) {
      nested.map(_.asInstanceOf[SchemaType]).toSet
    } else Set.empty[SchemaType]
}

/**
 * Schema's primitive type like Long or String
 */
final case class PrimitiveType(name: String) extends BotApiType

/**
 * (non-primitive) Type defined in schema
 */
final case class SchemaType(name: String) extends BotApiType

/**
 * Syntactic sugar for PrimitiveType("String") fields that take specific set of values
 */
final case class EnumType(values: Set[String]) extends BotApiType


/**
 * Base trait for simplified AST, which will be converted to resulting Scala code
 */
sealed trait CodeGenEntity {
  def name: String
}

final case class Field(name: String, t: BotApiType, description: String) extends CodeGenEntity
final case class Method(name: String, params: Seq[Field], returnType: BotApiType) extends CodeGenEntity
final case class Structure(name: String, fields: Seq[Field]) extends CodeGenEntity
final case class Trait(name: String, children: Set[SchemaType]) extends CodeGenEntity
final case class Enumeration(name: String, values: Set[String]) extends CodeGenEntity
case object Json4sFormats extends CodeGenEntity {
  override def name: String = "Json4sFormats"
}
