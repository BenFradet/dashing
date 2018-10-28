package dashing.server

import scala.concurrent.ExecutionContext

import cats.effect.{ContextShift, Effect}
import cats.syntax.functor._
import org.http4s.{Charset, HttpService, MediaType, Request, StaticFile}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._

class RenderingService[F[_]: Effect: ContextShift](ec: ExecutionContext) extends Http4sDsl[F] {

  val index = {
    import scalatags.Text.all._
    html(
      head(
        meta(charset:="UTF-8"),
        link(rel:="stylesheet", href:="bootstrap.css"),
        link(rel:="stylesheet", href:="app.css"),
        link(rel:="stylesheet", href:="default.css"),
        script(src:="highlight.pack.js"),
        script(src:="client-jsdeps.js")
      ),
      body(
        script(src:="client-fastopt.js")
      )
    )
  }

  def static(file: String, req: Request[F]) =
    StaticFile.fromResource("/" + file, ec, Some(req))
      .orElse(StaticFile.fromURL(getClass.getResource("/" + file), ec, Some(req)))
      .getOrElseF(NotFound())

  val service = HttpService[F] {
    case GET -> Root =>
      Ok(index.render)
        .map(_.withContentType(`Content-Type`(new MediaType("text", "html"), Charset.`UTF-8`)))
    case req @ GET -> Root / path if List(".js", ".css", ".map", ".ico").exists(path.endsWith) =>
      static(path, req)
  }

}
