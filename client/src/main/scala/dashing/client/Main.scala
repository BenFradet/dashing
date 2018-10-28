package dashing.client

import japgolly.scalajs.react._, vdom.html_<^._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.extra.router._
import org.scalajs.dom

import model._
import modules._

object Main {

  val routerConfig = RouterConfigDsl[Dashboard].buildConfig { dsl =>
    import dsl._
    (emptyRule
      | staticRoute(root, Home) ~> render(home)
      | staticRoute("#hero-repo", HeroRepoDash) ~> renderR(ctl => HeroRepoDashboard(ctl))
      | staticRoute("#topn-repos", TopNReposDash) ~> renderR(ctl => TopNReposDashboard(ctl))
      | staticRoute("#prs-quarterly", QuarterlyPRsDash) ~> renderR(ctl => QuarterlyPRsDashboard(ctl))
      | staticRoute("#prs-monthly", MonthlyPRsDash) ~> renderR(ctl => MonthlyPRsDashboard(ctl))
    ).notFound(redirectToPage(Home)(Redirect.Replace))
      .setTitle(p => s"Dashboard $p | Dashing")
      .renderWith(layout)
      .verify(Home, HeroRepoDash, TopNReposDash, QuarterlyPRsDash, MonthlyPRsDash)
  }

  val home =
    <.div(
      <.h1("Dashing! Monitor your open source organization's health."),
      <.ul(
        <.li(
          <.p(
            <.a(^.href := "#hero-repo", "Hero repo stars:"),
            " stars timeline for the hero repository"
          )
        ),
        <.li(
          <.p(
            <.a(^.href := "#topn-repos", "Top N repos stars:"),
            " stars timeline for the top N repositories, hero repo excluded"
          )
        ),
        <.li(
          <.p(
            <.a(^.href := "#prs-quarterly", "Quarterly opened PRs:"),
            " opened by non-members"
          )
        ),
        <.li(
          <.p(
            <.a(^.href := "#prs-monthly", "Monthly opened PRs:"),
            " opened by non-members"
          )
        )
      )
    )

  def layout(c: RouterCtl[Dashboard], r: Resolution[Dashboard]) =
    <.div(
      navMenu(c),
      <.div(^.cls := "container", r.render()),
      footer
    )

  val footer =
    <.div(^.cls := "footer",
      <.p(
        "This project is open source, check it out on ",
        <.a(^.href := "https://github.com/benfradet/dashing", "Github"),
        "."
      )
    )

  val navMenu = ScalaComponent.builder[RouterCtl[Dashboard]]("Menu")
    .render_P { ctl =>
      def nav(name: String, target: Dashboard) =
        <.li(
          ^.cls := "navbar-brand active",
          ctl.setOnClick(target),
          name
        )

      <.div(
        ^.cls := "navbar navbar-default",
        <.ul(
          ^.cls := "navbar-header",
          nav("Home", Home),
          nav("Hero repo stars", HeroRepoDash),
          nav("Top N repos stars", TopNReposDash),
          nav("Quaterly opened PRs", QuarterlyPRsDash),
          nav("Monthly opened PRs", MonthlyPRsDash)
        )
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

  def main(args: Array[String]): Unit = {
    val router = Router(BaseUrl.fromWindowOrigin_/, routerConfig.logToConsole)
    router().renderIntoDOM(dom.document.body)
  }
}
