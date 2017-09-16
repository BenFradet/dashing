package dashing.client

import org.scalajs.dom
import japgolly.scalajs.react._, vdom.html_<^._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.extra.router._

import model._
import modules._

object Main {

  val routerConfig = RouterConfigDsl[Dashboard].buildConfig { dsl =>
    import dsl._
    (emptyRule
      //| staticRoute(root, MainDash) ~> render(<.h1("Welcome!"))
      | staticRoute(root, MainDash) ~> renderR(ctl => Dashboard(ctl))
    ).notFound(redirectToPage(MainDash)(Redirect.Replace))
      .setTitle(p => s"Dashboard $p | Dashing")
      .renderWith(layout)
      .verify(MainDash)
  }

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
          nav("Home", MainDash)
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