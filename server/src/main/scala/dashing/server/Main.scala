package dashing.server

import cats.effect.IO
import cats.implicits._
import fs2.Stream
import org.http4s.server.blaze._
import org.http4s.util.{ExitCode, StreamApp}

object Main extends StreamApp[IO] {

  val apiService = StarsService.service <+> PullRequestsService.service

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(8080, "localhost")
      .mountService(RenderingService.service)
      .mountService(apiService, "/api")
      .serve
}