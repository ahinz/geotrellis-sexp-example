package gtexample

import scala.util.parsing.combinator._
import scala.collection.mutable.{Map => MMap}

import geotrellis._

object Parser {

  sealed trait ParseNode
  case class FnNode(fn: String, args: Seq[ParseNode]) extends ParseNode
  case class IntLiteralNode(i: Int) extends ParseNode
  case class StringLiteralNode(l: String) extends ParseNode
  case object NullLiteralNode extends ParseNode
  case class VariableNode(ref: String, node: ParseNode) extends ParseNode {
    override def toString = s"Var($ref)"
  }

  def randomVariable(node: ParseNode) = {
    VariableNode("$" + Integer.toHexString(node.hashCode), node)
  }

  object SexpParse extends RegexParsers with JavaTokenParsers {
    def intl = "[0-9]+".r ^^ { s => IntLiteralNode(Integer.parseInt(s)) }
    def symbol = "[a-zA-Z0-9.]+".r
    def nulls = "null".r ^^ { s => NullLiteralNode }
    def str = stringLiteral ^^ { s =>
      StringLiteralNode(s.substring(1,s.length - 1)) }

    def sexp:Parser[ParseNode] =
      ("(" ~> symbol ~ (sexp | intl | nulls | str).* <~ ")") ^^ {
        case fn ~ params =>
          FnNode(fn, params)
      }

    def sexps = sexp.*

    def apply(input: String): Seq[ParseNode] = parseAll(sexps, input) match {
      case Success(result, _) => result
      case failure : NoSuccess => sys.error(failure.msg)
    }
  }

  def findHashes(m: Map[Int, Seq[ParseNode]], p: ParseNode):Map[Int, Seq[ParseNode]] =
    p match {
      case FnNode(_, args) => args.foldLeft(m.updated(p.hashCode, p +: m.getOrElse(p.hashCode, Seq()))) { (s,v) =>
        findHashes(s, v) }
      case _ => m
    }

  def walkTree(p: ParseNode, hashes: Map[Int, VariableNode]):ParseNode =
    p match {
      case FnNode(fn, args) => hashes.get(p.hashCode).getOrElse(
        FnNode(fn, args.map(a => walkTree(a, hashes))))
      case e => e
    }

  def findNonUniqueHashes(p: ParseNode) =
    findHashes(Map.empty, p)
      .filter(kv => kv._2.length > 1)
      .mapValues(a => randomVariable(a.head))

  def toOp[T](p: ParseNode, run: Op[Any] => Any, symbols: MMap[VariableNode,Any]=MMap.empty):Op[Any] = p match {
    case FnNode(fn, args) => {
      val clazz = Class.forName(fn)
      val nargs = args.length

      val constr = clazz.getConstructors()
        .filter(_.getParameterTypes().length == nargs)
        .head

      constr.newInstance(args.map(a =>
        toOp(a, run, symbols)):_*).asInstanceOf[Op[Any]]
    }
    case NullLiteralNode => Literal(null)
    case IntLiteralNode(l) => Literal(l)
    case StringLiteralNode(l) => Literal(l)
    case v@VariableNode(ref, node) =>
      Literal(symbols.getOrElseUpdate(v, run(toOp(node, run, symbols))))
  }

  def apply(s: String) = SexpParse(s).head
}
