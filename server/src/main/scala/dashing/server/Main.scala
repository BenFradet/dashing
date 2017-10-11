package dashing.server

import cats.effect.IO
import fs2.Stream
import org.http4s.server.blaze._
import org.http4s.util.StreamApp

object Main extends StreamApp[IO] {
  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] =
    BlazeBuilder[IO]
      .bindHttp(8080, "localhost")
      .mountService(RenderingService.service)
      .mountService(StarsService.service, "/api")
      .serve
}