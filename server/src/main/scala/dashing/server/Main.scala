package dashing.server

import cats.effect.IO
import cats.implicits._
import fs2.Stream
import org.http4s.server.blaze._
import org.http4s.util.{ExitCode, StreamApp}
import pureconfig._

import model.DashingConfig

object Main extends StreamApp[IO] {

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    implicit def hint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))
    loadConfig[DashingConfig] match {
      case Right(c) =>
        val apiService =
          StarsService.service(c.ghToken, c.org, c.heroRepo, c.topNRepos) <+>
            PullRequestsService.service(c.ghToken, c.org)
        BlazeBuilder[IO]
          .bindHttp(8080, "localhost")
          .mountService(RenderingService.service)
          .mountService(apiService, "/api")
          .serve
      case Left(e) => Stream.eval(IO {
        System.err.println(e)
        ExitCode.error
      })
    }
  }
}