package gtexample

import javax.ws.rs._
import javax.ws.rs.core.{Response, UriInfo, Context => RContext}
import geotrellis._
import geotrellis.statistics.Histogram
import geotrellis.data._
import scala.collection.JavaConversions._
import com.typesafe.config.ConfigFactory

import java.io.File
import scala.sys.process.Process

object S {
  val server = process.Server("server")

  val sexp = """
(geotrellis.io.RenderPng
  (geotrellis.io.LoadRaster "SBN_car_share" null)
  (gtexample.GetColorBreaks
    (geotrellis.statistics.op.stat.GetHistogramMap
      (geotrellis.io.LoadRaster "SBN_car_share" null)))
  (geotrellis.statistics.op.stat.GetHistogramMap
      (geotrellis.io.LoadRaster "SBN_car_share" null))
  0)
"""

  val serviceLoaders:Map[String,File => String] = Map(
    ".svc" -> { f: File =>
      val fname = f.getName()
      println(s"Loading service from file $fname")
      scala.io.Source.fromFile(f).mkString },
    ".py" -> { f: File =>
      val fname = f.getName()
      println(s"Python loader $fname")
      Process(s"python $fname", Some(f.getParentFile()))!!
    })

  def loadFile(f: File):Option[String] =
    serviceLoaders
      .filter(kv => f.getName().endsWith(kv._1) &&
        !f.getName().startsWith(".#"))
      .values
      .headOption
      .map(loaderfn => loaderfn(f))

  val services = Map(new File(ConfigFactory.load()
    .getString("geotrellis.servicedir"))
    .listFiles()
    .flatMap(file => loadFile(file))
    .map(s => Parser.service(s))
    .map(s => (s.name, s)):_*)
}

case class GetColorBreaks(h:Op[Histogram])
     extends Op[ColorBreaks] {

  val cs = ColorRamps.HeatmapBlueToYellowToRedSpectrum.toArray

  def _run(context:Context) = runAsync(List(h, cs))

  val nextSteps:Steps = {
    case (histogram:Histogram) :: (colors:Array[_]) :: Nil => {
      step2(histogram, colors.asInstanceOf[Array[Int]])
    }
  }

  def step2(histogram:Histogram, colors:Array[Int]) = {
    val limits = histogram.getQuantileBreaks(colors.length)
    Result(ColorBreaks.assign(limits, colors))
  }
}

@Path("/service")
class Service {
  @GET
  @Path("")
  def list() = {
    val r = S.services.values.map(_.name).mkString("[\"","\",\"","\"]")

    Response.ok()
    .`type`("application/json")
    .entity(r)
    .build()
  }

  @GET
  @Path("/{service}")
  def svc(@PathParam("service") service: String,
    @RContext context: UriInfo) = {
    val now = System.nanoTime

    val m = mapAsScalaMap(context.getQueryParameters()).toMap.mapValues(_.head)

    val parseTree = S.services(service).applyParams(m)
    val simp = Parser.walkTree(parseTree, Parser.findNonUniqueHashes(parseTree))

    val op = Parser.toOp(simp, { o =>
      println(s"[Preload] $o")
      S.server.run(o)
    })

    println(op)

    val r = S.server.run(op)

    val mime = r match {
      case _:Array[_] => "image/png"
      case _ => "application/json"
    }

    val tdelta = (System.nanoTime - now) / 1000000
    println("%d milliseconds".format(tdelta))

    Response.ok()
      .`type`(mime)
      .entity(r)
      .build()
  }
}

@Path("/hello")
class TemplateResource {

  @GET
  @Path("/0")
  def hello() = {
    val now = System.nanoTime

    val p = Parser(S.sexp)
    println(p)
    val op = Parser.toOp(p, x => sys.error("Not supported"))
    println(op)

    val r = S.server.run(op)

    val tdelta = (System.nanoTime - now) / 1000000
    println("%d milliseconds".format(tdelta))

    Response.ok()
      .`type`("image/png")
      .entity(r)
      .build()
  }

  @GET
  @Path("/1")
  def hello1() = {
    val now = System.nanoTime
    val p = Parser(S.sexp)
    println(p)

    // Perform reductions to variables
    val simp = Parser.walkTree(p, Parser.findNonUniqueHashes(p))
    println(simp)

    val op = Parser.toOp(simp, { o =>
      println(s"[Preload] $o")
      S.server.run(o)
    })

    println(op)

    val r = S.server.run(op)

    val tdelta = (System.nanoTime - now) / 1000000
    println("%d milliseconds".format(tdelta))

    Response.ok()
      .`type`("image/png")
      .entity(r)
      .build()
  }
}
