package dashing.client
package modules

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

import scala.concurrent.ExecutionContext.Implicits.global

import components._
import model._

object Dashboard {
  final case class Props(router: RouterCtl[Dashboard])
  type State = Map[String, Int]
  object State {
    def empty: State = Map.empty[String, Int]
  }

  private val component = ScalaComponent.builder[Props]("Dashboard")
    .initialState(State.empty)
    .renderBackend[DashboardBackend]
    .componentDidMount(s => s.backend.updateStars("snowplow-docker"))
    .build

  final class DashboardBackend($: BackendScope[Props, State]) {

    def updateStars(repo: String) = Callback.future {
      Api.fetchStars(repo).map(s => $.setState(s))
    }

    def render(s: State) = {
      <.div(^.cls := "container",
        <.h2("Dashboard"),
        Chart(Chart.ChartProps(
          "Stars",
          Chart.LineChart,
          ChartData(
            s.keys.toSeq,
            Seq(ChartDataset(s.values.map(_.toDouble).toSeq, "Stars"))
          ),
          500,
          300
        ))
      )
    }
  }

  def apply(router: RouterCtl[Dashboard]) = component(Props(router))
}