package dashing

import org.scalajs.dom
import japgolly.scalajs.react._, vdom.html_<^._
import japgolly.scalajs.react.extra.router._

object Main {
  sealed trait Dashboard
  case object MainDash extends Dashboard

  val routerConfig = RouterConfigDsl[Dashboard].buildConfig { dsl =>
    import dsl._
    (emptyRule
      | staticRoute(root, MainDash) ~> render(<.h1("Welcome!"))
    ).notFound(redirectToPage(MainDash)(Redirect.Replace))
      .setTitle(p => s"Dashboard $p | Dashing")
  }

  def main(args: Array[String]): Unit = {
    val router = Router(BaseUrl.fromWindowOrigin_/, routerConfig.logToConsole)
    router().renderIntoDOM(dom.document.body)
  }
}