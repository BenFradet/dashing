package dashing.client
package modules

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

import scala.concurrent.ExecutionContext.Implicits.global

import components._
import model._

object TopNReposDashboard {
  final case class Props(router: RouterCtl[Dashboard])

  private val component = ScalaComponent.builder[Props]("Hero repo dashboard")
    .initialState(List(RepoState.empty))
    .renderBackend[DashboardBackend]
    .componentDidMount(s => s.backend.updateStars)
    .build

  private val colors = List("#D83F87", "#2A1B3D", "#44318D", "#8264A7", "#A4B3B6")

  final class DashboardBackend($: BackendScope[Props, List[RepoState]]) {

    def updateStars = Callback.future {
      Api.fetchTopNStars
        .map { l =>
          $.setState(l.map(r => RepoState(r.name, r.starsTimeline.toMap, r.stars)))
        }
    }

    def render(s: List[RepoState]) = {
      <.div(^.cls := "container",
        <.h2("Top N repos dashboard"),
        Chart(Chart.ChartProps(
          s"Top ${s.size - 1} repos stars",
          Chart.LineChart,
          ChartData(
            s.headOption.map(_.starsTimeline.keys.toSeq.sorted).getOrElse(Seq.empty),
            s.zip(Stream.continually(colors).flatten).map { case (r, c) =>
              ChartDataset(
                r.starsTimeline.toList.sortBy(_._1).map(_._2),
                s"${r.name}",
                c
              )
            }.toSeq
          )
        ))
      )
    }
  }

  def apply(router: RouterCtl[Dashboard]) = component(Props(router))
}
