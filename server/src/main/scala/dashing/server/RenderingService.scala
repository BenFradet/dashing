package dashing.server

import cats.effect.Effect
import cats.syntax.functor._
import org.http4s.{Charset, HttpService, Request, StaticFile}
import org.http4s.MediaType._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._

class RenderingService[F[_]: Effect] extends Http4sDsl[F] {

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
    StaticFile.fromResource("/" + file, Some(req))
      .orElse(StaticFile.fromURL(getClass.getResource("/" + file), Some(req)))
      .getOrElseF(NotFound())

  val service = HttpService[F] {
    case GET -> Root =>
      Ok(index.render)
        .map(_.withContentType(`Content-Type`(`text/html`, Charset.`UTF-8`)))
    case req @ GET -> Root / path if List(".js", ".css", ".map", ".ico").exists(path.endsWith) =>
      static(path, req)
  }

}
