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
    ).notFound(redirectToPage(Home)(Redirect.Replace))
      .setTitle(p => s"Dashboard $p | Dashing")
      .renderWith(layout)
      .verify(Home, HeroRepoDash)
  }

  val home =
    <.div(
      <.h1("Welcome!"),
      <.ul(
        <.li(
          <.p(
            <.a(^.href := "#hero-repo", "Hero repo stars:"),
            " stars timeline for the hero repository"
          )
        )
      )
    )

  def layout(c: RouterCtl[Dashboard], r: Resolution[Dashboard]) =
    <.div(
      navMenu(c),
      <.div(^.cls := "container", r.render())
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
          nav("Hero repo stars", HeroRepoDash)
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