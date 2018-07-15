package dashing.client
package modules

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

import scala.concurrent.ExecutionContext.Implicits.global

import components._
import model._

object PRsDashboard {
  final case class Props(router: RouterCtl[Dashboard])

  private val component = ScalaComponent.builder[Props]("Pull requests dashboard")
    .initialState(GHObjectState.empty)
    .renderBackend[DashboardBackend]
    .componentDidMount(s => s.backend.updatePRs)
    .build

  final class DashboardBackend($: BackendScope[Props, GHObjectState]) {

    def updatePRs = Callback.future {
      Api.fetchPRs.map { t =>
        $.setState(GHObjectState(t.members.toList, t.nonMembers.toList))
      }
    }

    def render(s: GHObjectState) = {
      <.div(^.cls := "container",
        <.h2("Pull requests dashboard"),
        Chart(Chart.ChartProps(
          s"Number of pull requests opened by members and non-members",
          Chart.BarChart,
          ChartData(
            s.members.map(_.label).toSeq,
            Seq(
              ChartDataset(
                s.members.map(_.value).toSeq,
                "opened by members",
                "#D83F87",
                "rgba(216, 63, 135, 0.5)",
                1
              ),
              ChartDataset(
                s.nonMembers.map(_.value).toSeq,
                "opened by non-members",
                "#2A1B3D",
                "rgba(42, 27, 61, 0.5)",
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
