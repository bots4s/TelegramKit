package com.github.bots4s.telegramkit

import java.io.{File, PrintWriter}

final case class Schema(methods: Seq[Method], models: Seq[Structure], private val traits: Seq[Trait]) {
  lazy val allTraits = traits ++ derivedTraits
  lazy val allEnums = derivedEnums

  /**
   * Derives traits from initial schema
   */
  def derivedTraits: Seq[Trait] = {
    val allFields = methods.flatMap(_.params) ++ models.flatMap(_.fields)
    val possibleNames = allFields.flatMap { f =>
      oneOfGroups(f.t).map(f.name -> _)
    }.groupBy(_._2).mapValues(_.map(_._1)).toSeq.sortBy(-_._2.size)

    var result = Seq.empty[(String, Set[SchemaType])]
    possibleNames.foreach { case (oneOfGroup, names) =>
      val availableNames = names.filterNot(n => result.exists(_._1 == n))
      if (availableNames.nonEmpty) {
        val mostUsedName = availableNames.groupBy(identity).maxBy(_._2.size)._1
        result :+= mostUsedName.capitalize -> oneOfGroup
      }
    }
    result.map { case (name, classes) => Trait(name, classes) }
  }

  /**
   * Recursively extracts oneOf groups
   * @param t type to find oneOf groups related to
   */
  def oneOfGroups(t: BotApiType): Seq[Set[SchemaType]] = t match {
    case ContainerType(name, nested) => oneOfGroups(nested)
    case ConstantValue(nested, value) => oneOfGroups(nested)
    case t @ UnionType(nested) => if (t.group.isEmpty) nested.flatMap(oneOfGroups) else Seq(t.group)
    case _ => Seq.empty
  }

  /**
   * Derives enumerations from initial schema
   */
  def derivedEnums: Seq[Enumeration] = {
    val allFields = methods.flatMap(m => m.params.map(m -> _)) ++ models.flatMap(m => m.fields.map(m -> _))
    val possibleNames = allFields.flatMap { case (ent, field) =>
      enums(field.t).flatMap { enum =>
        val fn = field.name.capitalize
        val snameNoVerb = ent match {
          case m: Method => m.name.drop(1).dropWhile(_.isLower)
          case s: Structure => s.name
        }
        val names = Seq(fn) ++ (if (snameNoVerb.endsWith(fn)) Some(snameNoVerb) else Some(snameNoVerb + fn))
        names.map(enum -> _)
      }
    }.groupBy(_._1).mapValues(_.map(_._2))

    var result = Seq.empty[Enumeration]
    possibleNames.foreach { case (enum, names) =>
      val availableNames = names.filterNot(n => result.exists(_.name == n))
      if (!result.exists(_.values == enum) && availableNames.nonEmpty) {
        val mostUsedName = availableNames.groupBy(identity).maxBy(n => n._2.length * 100 + n._1.length)._1
        result :+= Enumeration(mostUsedName.capitalize, enum)
      }
    }
    result
  }

  /**
   * Recursively extracts related enumerations
   * @param t type to find enumerations related to
   */
  def enums(t: BotApiType): Seq[Set[String]] = t match {
    case ContainerType(name, nested) => enums(nested)
    case ConstantValue(nested, value) => enums(nested)
    case UnionType(nested) => nested.flatMap(enums)
    case EnumType(values) => Seq(values)
    case _ => Seq.empty
  }

  def toScala(t: BotApiType, declareDefault: Boolean = false): (String, Seq[String]) = t match {
    case ContainerType(name, nested) =>
      val default = name match {
        case _ if !declareDefault => ""
        case "Option" => " = None"
        case "Seq" => " = Seq.empty"
        case _ => ""
      }
      val (nestedDecl, nestedImports) = toScala(nested)
      (name + "[" + nestedDecl + "]" + default, nestedImports)
    case ConstantValue(nested, value) =>
      val (nestedDecl, nestedImports) = toScala(nested)
      (nestedDecl + " = " + value, nestedImports)
    case t @ UnionType(nested) =>
      allTraits.find(_.children == t.group) match {
        case Some(t) =>
          t.name -> Seq(t.name)
        case None =>
          val (nestedDecls, nestedImports) = nested.map(toScala(_)).unzip
          (nestedDecls.reduceLeft(s"Either[" + _ + ", " + _ + "]"), nestedImports.flatten)
      }
    case EnumType(values) =>
      allEnums.find(_.values == values) match {
        case Some(enum) => (enum.name + ".Value", Seq(enum.name))
        case None => ("String", Seq.empty)
      }
    case PrimitiveType(name) => (name, Seq.empty)
    case SchemaType(name) => (name, Seq(name))
  }

  def toScala(entity: CodeGenEntity): (String, Seq[String]) = entity match {
    case m: Method =>
      val (fieldDecls, fieldImports) = m.params.map(toScala).unzip
      val multipart = fieldImports.flatten.contains("InputFile")
      val requestType = if (multipart) "BotApiRequestMultipart" else "BotApiRequestJson"
      val (returnDecl, returnImports) = toScala(m.returnType)
      val imports = (fieldImports.flatten ++ returnImports).distinct
      val fieldsDeclaration = StrUtils.identLines(fieldDecls, sep = ",")
      s"final case class ${m.name}($fieldsDeclaration) extends $requestType[$returnDecl]" -> imports

    case s: Structure =>
      val (fieldDecls, _) = s.fields.filter(!_.t.isInstanceOf[ConstantValue]).map(toScala).unzip
      val (valsDecls, _) = s.fields.filter(_.t.isInstanceOf[ConstantValue]).map(toScala).unzip
      val fieldsDeclaration = StrUtils.identLines(fieldDecls, sep = ",")
      val valsDeclaration = StrUtils.identLines(valsDecls.map("lazy val " + _))
      val body = if (valsDeclaration.trim.isEmpty) "" else s"{$valsDeclaration}"
      val traits = allTraits.filter(_.children.exists(_.name == s.name)).map(_.name)
      val parent = if (traits.nonEmpty) "extends " + traits.reduceLeft(_ + " with " + _) else ""
      s"final case class ${s.name}($fieldsDeclaration) $parent $body" -> Seq.empty

    case e: Enumeration =>
      val vals = e.values.toSeq.sorted.map { v =>
        val name = StrUtils.camelize(v).capitalize
        s"""val $name = Value("$v")"""
      }
      s"object ${e.name} extends Enumeration {${StrUtils.identLines(vals)}}" -> Seq.empty

    case t: Trait => ("trait " + t.name, Seq.empty)
    case f: Field =>
      val (decl, imports) = toScala(f.t, declareDefault = true)
      (StrUtils.escapeKeyword(StrUtils.camelize(f.name)) + ": " + decl, imports)

    case Json4sFormats =>
      val enumSerializers = allEnums.map(e => s"new EnumNameSerializer(${e.name})")
      val fieldSerializers = (methods ++ models).collect {
        case m: Method if m.params.exists(_.t.isInstanceOf[ConstantValue]) => m.name
        case s: Structure if s.fields.exists(_.t.isInstanceOf[ConstantValue]) => s.name
      }.map(name => s"FieldSerializer[$name](includeLazyVal = true)")
      val formats = StrUtils.identLines(enumSerializers ++ fieldSerializers, sep = " +", ident = 4)
      s"""|import org.json4s.{FieldSerializer, DefaultFormats, NoTypeHints}
          |import org.json4s.ext.EnumNameSerializer
          |import com.github.bots4s.telegramkit.model._
          |import com.github.bots4s.telegramkit.method._
          |
          |object Json4sFormats {
          |  implicit val formats = DefaultFormats.withHints(NoTypeHints) +$formats
          |}
          |
      """.stripMargin -> Seq.empty
  }

  def generateSchema(srcManaged: String, rootPkg: String): Seq[File] = {
    val entities = methods ++ models ++ allTraits ++ allEnums
    generate(entities, srcManaged, rootPkg)
  }

  def generateJson4s(srcManaged: String, rootPkg: String): Seq[File] = {
    val entities = Seq(Json4sFormats)
    generate(entities, srcManaged, rootPkg)
  }

  private[this] def generate(entities: Seq[CodeGenEntity], srcManaged: String, rootPkg: String): Seq[File] = {
    entities.map { result =>
      val pkg = result match {
        case _: Method => rootPkg + ".method"
        case Json4sFormats => rootPkg
        case _ => rootPkg + ".model"
      }
      val dirPath = srcManaged + "/" + pkg.replaceAll("\\.", "/")
      val filePath = dirPath + "/" + result.name + ".scala"
      new File(dirPath).mkdirs()
      val outputFile = new File(filePath)
      val pw = new PrintWriter(outputFile)

      val (body, imports) = toScala(result)
      val importsDecl = StrUtils.identLines(imports.map("import " + rootPkg + ".model." + _), ident = 0)
      val header = s"package $pkg" + System.lineSeparator + importsDecl + System.lineSeparator

      pw.write(header + body)
      pw.close()
      outputFile
    }
  }
}

object StrUtils {
  val ScalaKeywords = Set("abstract", "case", "catch", "class", "def", "do", "else", "extends",
    "false", "final", "finally", "for", "forSome", "if", "implicit", "import",
    "lazy", "match", "new", "null", "object", "override", "package", "private",
    "protected", "return", "sealed", "super", "this", "throw", "trait", "try",
    "true", "type", "val", "var", "while", "with", "yield")

  def escapeKeyword(s: String): String = {
    if (ScalaKeywords.contains(s)) '`' + s + '`' else s
  }

  def camelize(word: String): String = {
    val lst = word.split("_").toList
    val w = (lst.headOption.map(s => s.substring(0, 1).toUpperCase + s.substring(1)).get ::
      lst.tail.map(s => s.substring(0, 1).toUpperCase + s.substring(1))).mkString("")
    w.substring(0, 1).toLowerCase + w.substring(1)
  }

  def identLines(lines: Seq[String], sep: String = "", ident: Int = 2): String = {
    val identStr = Seq.fill(ident)(" ").mkString
    lines.map(identStr + _).mkString(System.lineSeparator, sep + System.lineSeparator, System.lineSeparator)
  }
}
