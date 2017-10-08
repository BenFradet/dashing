package dashing.client
package modules

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

import scala.concurrent.ExecutionContext.Implicits.global

import components._
import model._

object HeroRepoDashboard {
  final case class Props(router: RouterCtl[Dashboard])
  final case class HeroRepoDashboardState(
    name: String, starsTimeline: List[(String, Int)], stars: Int)
  object HeroRepoDashboardState {
    def empty = HeroRepoDashboardState("", List.empty, 0)
  }

  private val component = ScalaComponent.builder[Props]("Hero repo dashboard")
    .initialState(HeroRepoDashboardState.empty)
    .renderBackend[DashboardBackend]
    .componentDidMount(s => s.backend.updateStars("snowplow"))
    .build

  final class DashboardBackend($: BackendScope[Props, HeroRepoDashboardState]) {

    def updateStars(repo: String) = Callback.future {
      Api.fetchHeroRepoStars(repo)
        .map { r =>
          val timeline = r.starsTimeline.toList.sortBy(_._1)
          $.setState(HeroRepoDashboardState(r.name, timeline, r.stars))
        }
    }

    def render(s: HeroRepoDashboardState) = {
      <.div(^.cls := "container",
        <.h2("Hero repo dashboard"),
        Chart(Chart.ChartProps(
          s"${s.name} stars: ${s.stars}",
          Chart.LineChart,
          ChartData(
            s.starsTimeline.map(_._1),
            Seq(ChartDataset(s.starsTimeline.map(_._2.toDouble), s"${s.name} stars"))
          )
        ))
      )
    }
  }

  def apply(router: RouterCtl[Dashboard]) = component(Props(router))
}