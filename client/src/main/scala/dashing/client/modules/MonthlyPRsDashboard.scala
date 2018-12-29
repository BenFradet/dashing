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
            s.prsByOrg.values.headOption.getOrElse(Seq.empty).toSeq.map(_.label),
            s.prsByOrg.map { case (org, prs) =>
              ChartDataset(
                prs.map(_.value).toSeq,
                s"PRs opened in $org",
                "#D83F87",
                "rgba(216, 63, 135, 0.5)",
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
