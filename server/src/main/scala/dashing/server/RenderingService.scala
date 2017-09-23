package dashing.server

import cats.effect.IO
import org.http4s._
import org.http4s.MediaType._
import org.http4s.dsl._
import org.http4s.headers._

object RenderingService extends Service {

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

  def static(file: String, req: Request[IO]) =
    StaticFile.fromResource("/" + file, Some(req))
      .orElse(StaticFile.fromURL(getClass.getResource("/" + file), Some(req)))
      .getOrElseF(NotFound())

  override val service = HttpService[IO] {
    case GET -> Root =>
      Ok(index.render)
        .withContentType(Some(`Content-Type`(`text/html`, Charset.`UTF-8`)))
    case req @ GET -> Root / path if List(".js", ".css", ".map", ".ico").exists(path.endsWith) =>
      static(path, req)
  }
}