package dashing.modules

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

import scala.util.Random

import dashing.Main.Dashboard
import dashing.components._

object Dashboard {
  case class Props(router: RouterCtl[Dashboard])

  val cp = Chart.ChartProps(
    "Test dash",
    Chart.LineChart,
    ChartData(
      Random.alphanumeric.map(_.toUpper.toString).distinct.take(10),
      Seq(ChartDataset(Iterator.continually(Random.nextDouble() * 10).take(10).toSeq, "Data 1"))
    ),
    500,
    300
  )

  private val component = ScalaComponent.builder[Props]("Dashboard")
    .render_P { p =>
      <.div(
        <.h2("Dashboard"),
        Chart(cp)
      )
    }
    .build

  def apply(router: RouterCtl[Dashboard]) = component(Props(router))
}