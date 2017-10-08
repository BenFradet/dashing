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

  private val component = ScalaComponent.builder[Props]("Hero repo dashboard")
    .initialState(RepoState.empty)
    .renderBackend[DashboardBackend]
    .componentDidMount(s => s.backend.updateStars)
    .build

  final class DashboardBackend($: BackendScope[Props, RepoState]) {

    def updateStars = Callback.future {
      Api.fetchHeroRepoStars
        .map { r =>
          val timeline = r.starsTimeline.toList.sortBy(_._1)
          $.setState(RepoState(r.name, timeline, r.stars))
        }
    }

    def render(s: RepoState) = {
      <.div(^.cls := "container",
        <.h2("Hero repo dashboard"),
        Chart(Chart.ChartProps(
          s"${s.name} stars: ${s.stars}",
          Chart.LineChart,
          ChartData(
            s.starsTimeline.map(_._1),
            Seq(ChartDataset(s.starsTimeline.map(_._2.toDouble), s"${s.name} stars", "#0E0B16"))
          )
        ))
      )
    }
  }

  def apply(router: RouterCtl[Dashboard]) = component(Props(router))
}