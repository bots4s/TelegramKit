package com.github.bots4s.telegramkit

import java.net.{HttpURLConnection, URL}
import scala.xml.Node
import scala.xml.parsing.NoBindingFactoryAdapter

import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

object SchemaParser extends App {
  private lazy val log = LoggerFactory.getLogger("SchemaParser")

  private lazy val HtmlTagPattern = "<[^>]+>([^<]+)</[^>]+>"
  private lazy val TypeExtractorPattern = """((<em>)?(A|a)rray(</em>)?[ ]+of[ ]+)*<(a|em)[^>]*>[^<]*</(a|em)>""".r

  private lazy val EnumValuePattern = """(?:<(?:a|em)[^>]*>|"|”|“)([a-z_]+)(?:</(?:a|em)>|"|”|“)""".r
  private lazy val EnumPattern = s"""$EnumValuePattern([^,.]*(?:,|or)\\s+$EnumValuePattern)+""".r
  private lazy val MustBeValuePattern = """must\s+be\s+<em>([a-z_]+)</em>""".r

  def extractEntitySections(root: Node): Seq[Seq[Node]] = {
    var entities = Seq[Seq[Node]]()
    var acc = Seq[Node]()
    (root \\ "body" \\ "div").filter { node =>
      val id = node.attribute("id")
      id.isDefined && id.get.text == "dev_page_content"
    }.head.child.foreach { node =>
      if (node.label.matches("h[1-9]")) {
        entities :+= acc
        acc = Seq[Node]()
        if (node.label == "h4") {
          acc :+= node
        }
      } else if (acc.nonEmpty) {
        acc :+= node
      }
    }
    if (acc.nonEmpty)
      entities :+= acc
    entities
  }

  def parseType(t: String, descr: Option[String] = None): BotApiType = {
    if (t.startsWith("Array of") || t.startsWith("<em>Array</em> of")) {
      ContainerType("Seq", parseType(t.drop(9).trim))
    } else if (t.contains(" or ")) {
      val parts = t.split(" or ").map(_.trim)
      UnionType(parts.map(parseType(_)))
    } else {
      t match {
        case "Integer" | "Int" => PrimitiveType("Long")
        case "String" =>
          val maybeEnum = descr.map { d =>
            EnumPattern.findAllMatchIn(d.toLowerCase)
          }.getOrElse(Seq.empty).map { m =>
            val text = m.group(0)
            text.split("(,|\\s+or\\s+)").toSeq.flatMap(t => EnumValuePattern.findFirstMatchIn(t)).map(_.group(1))
          }.toSeq.headOption
          maybeEnum match {
            case None =>
              descr.flatMap(MustBeValuePattern.findFirstMatchIn) match {
                case Some(m) => ConstantValue(PrimitiveType("String"), '"' + m.group(1) + '"')
                case None => PrimitiveType("String")
              }
            case Some(enum) => EnumType(enum.toSet)
          }
        case "Boolean" | "True" => PrimitiveType("Boolean")
        case "Float" | "Float number" => PrimitiveType("Float")
        case other => SchemaType(other.trim.capitalize)
      }
    }
  }

  def parse(schemaUrl: String): Schema = {
    val root = load(new URL(schemaUrl))
    val entities = extractEntitySections(root)

    val methodsAndStructures = entities.filter(_.nonEmpty).flatMap { nodes =>
      try {
        val name = nodes.head.child.last.text.trim
        // InputFile is implemented separately
        if (name.contains(" ") || name == "InputFile" || name == "") {
          None
        } else {
          val rows = nodes.find(_.label == "table").map { table =>
            (table \\ "tr").map(row => (row \\ "td").map(_.child.map {
              case node if node.label == "a" => node.child.mkString(" ")
              case node => node.toString
            }.mkString(" "))).tail
          }.getOrElse(Nil)

          val otherNodes = nodes.tail.filterNot(_.label == "table")

          if (nodes.exists(_.label == "ul") && rows.isEmpty) {
            val children = (nodes.find(_.label == "ul").get \\ "li").map(_.child)
              .collect {
                case Seq(link) if link.label == "a" => link.text
              }
            if (children.nonEmpty)
              Some(Trait(name.capitalize, children.map(c => SchemaType(c)).toSet))
            else
              None
          } else if (name.head.isLower) {
            val returnType = otherNodes.mkString(" ").split("\\.").toSeq.filter(_.toLowerCase.contains("return"))
              .map { result =>
                val types = TypeExtractorPattern.findAllMatchIn(result).map {
                  _.group(0).replaceAll(HtmlTagPattern, "$1").trim
                }
                if (types.nonEmpty)
                  Some(types.mkString(" or "))
                else
                  None
              }.find(_.nonEmpty).flatten.getOrElse("True")
            val args = rows.map {
              case parameter :: t :: required :: descr :: Nil =>
                val optional = required != "Yes"
                val tp = parseType(t, Some(descr))
                Field(StrUtils.camelize(parameter), if (optional) ContainerType("Option", tp) else tp, descr)
            }
            log.info(s"generated method $name: $returnType")
            Some(Method(name.capitalize, args, parseType(returnType)))
          } else {
            val args = rows.map {
              case field :: t :: descr :: Nil =>
                val optional = descr.startsWith("<em>Optional</em>")
                val tp = parseType(t, Some(descr))
                Field(StrUtils.camelize(field), if (optional) ContainerType("Option", tp) else tp, descr)
            }
            log.info(s"generated structure $name")
            Some(Structure(name.capitalize, args))
          }
        }
      } catch {
        case e: Exception =>
          log.error("error processing entity, raw data:" + nodes.mkString("," + System.lineSeparator), e)
          throw e
      }
    }

    val methods = methodsAndStructures.collect { case m: Method => m }
    val structures = methodsAndStructures.collect { case s: Structure => s }
    val traits = methodsAndStructures.collect { case t: Trait => t }
    val schema = Schema(methods, structures, traits)

    log.info("generated traits: " + schema.allTraits.map(_.name).mkString(", "))
    log.info("generated enumerations: " + schema.derivedEnums.map(_.name).mkString(", "))
    schema
  }

  def load(url: URL, headers: Map[String, String] = Map.empty): Node = {
    val adapter = new NoBindingFactoryAdapter
    val parser = (new SAXFactoryImpl).newSAXParser
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    for ((k, v) <- headers)
      conn.setRequestProperty(k, v)
    val source = new InputSource(conn.getInputStream)
    adapter.loadXML(source, parser)
  }

}
