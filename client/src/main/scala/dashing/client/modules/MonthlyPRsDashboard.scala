package dashing.client
package modules

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

import scala.concurrent.ExecutionContext.Implicits.global

import components._
import model._

object MonthlyPRsDashboard {
  final case class Props(router: RouterCtl[Dashboard])

  private val component = ScalaComponent.builder[Props]("Monthly pull requests dashboard")
    .initialState(PRsState.empty)
    .renderBackend[DashboardBackend]
    .componentDidMount(s => s.backend.updatePRs)
    .build

  private val colors = List(
    "rgba(216, 63, 135, 1.0)",
    "rgba(42, 27, 61, 1.0)",
    "rgba(68, 49, 141, 1.0)",
    "rgba(130, 100, 167, 1.0)",
    "rgba(164, 179, 182, 1.0)",
  )

  final class DashboardBackend($: BackendScope[Props, PRsState]) {

    def updatePRs = Callback.future {
      Api.fetchMonthlyPRs.map { t =>
        $.setState(PRsState(t))
      }
    }

    def render(s: PRsState) = {
      <.div(^.cls := "container",
        <.h2("Monthly pull requests dashboard"),
        Chart(Chart.ChartProps(
          s"Number of pull requests opened by non-members per month per organization",
          Chart.BarChart,
          ChartData(
            s.prsByOrg.values.headOption.map(_.keys.toSeq.sorted).getOrElse(Seq.empty),
            s.prsByOrg
              .zip(Stream.continually(colors).flatten)
              .map { case ((org, prs), bgc) =>
                ChartDatasetFlat(
                  prs.toList.sortBy(_._1).map(_._2),
                  org,
                  "rgba(0, 0, 0, 0)",
                  bgc,
                  1
                )
              }.toSeq
          )
        ))
      )
    }
  }

  def apply(router: RouterCtl[Dashboard]) = component(Props(router))
}
