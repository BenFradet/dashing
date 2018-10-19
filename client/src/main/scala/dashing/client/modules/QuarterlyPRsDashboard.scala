package dashing.client
package modules

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

import scala.concurrent.ExecutionContext.Implicits.global

import components._
import model._

object QuarterlyPRsDashboard {
  final case class Props(router: RouterCtl[Dashboard])

  private val component = ScalaComponent.builder[Props]("Quarterly pull requests dashboard")
    .initialState(GHObjectState.empty)
    .renderBackend[DashboardBackend]
    .componentDidMount(s => s.backend.updatePRs)
    .build

  final class DashboardBackend($: BackendScope[Props, GHObjectState]) {

    def updatePRs = Callback.future {
      Api.fetchQuarterlyPRs.map { t =>
        $.setState(GHObjectState(t.toList))
      }
    }

    def render(s: GHObjectState) = {
      <.div(^.cls := "container",
        <.h2("Quarterly pull requests dashboard"),
        Chart(Chart.ChartProps(
          s"Number of pull requests opened by non-members per quarter",
          Chart.BarChart,
          ChartData(
            s.nonMembers.map(_.label).toSeq,
            Seq(
              ChartDataset(
                s.nonMembers.map(_.value).toSeq,
                "pull requests opened",
                "#D83F87",
                "rgba(216, 63, 135, 0.5)",
                1
              )
            )
          )
        ))
      )
    }
  }

  def apply(router: RouterCtl[Dashboard]) = component(Props(router))
}
