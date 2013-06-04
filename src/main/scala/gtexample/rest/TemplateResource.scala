package gtexample

import javax.ws.rs._
import javax.ws.rs.core.Response
import geotrellis._
import geotrellis.statistics.Histogram
import geotrellis.data._

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

/**
 * Simple hello world rest service that responds to "/hello"
 */
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

  // def main(args: Array[String]) {
  //   val s = SexpParse("""(RenderPng (Add (Load "1") (Load "2")) (Hist (Add (Load "1") (Load "2"))))""").head

  //   val nonUniqueHashes = findNonUniqueHashes(s)
  //   val simp = walkTree(s, nonUniqueHashes)

  //   println("=====")
  //   println(s)
  //   println("=====")
  //   println(simp)
  //   println("=====")

  //   val fn = FnNode("Load",Seq(StringLiteralNode("55")))
  //   println(toOp(fn))
  //   println(Server.run(toOp(s)))
  // }
