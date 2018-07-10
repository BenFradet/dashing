package dashing.server

import scala.concurrent.duration._

import cats.effect.IO
import cats.implicits._
import com.typesafe.config.ConfigFactory
import fs2.{Stream, StreamApp}
import fs2.StreamApp.ExitCode
import io.circe.generic.auto._
import io.circe.config.syntax._
import org.http4s.server.blaze._

import model.DashingConfig

object Main extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    ConfigFactory.load().as[DashingConfig] match {
      case Right(c) =>
        for {
          cache <- Stream.eval(
            Cache.createCache[IO, String, String](Cache.TimeSpec.fromDuration(12.hours)))
          apiService = StarsService.service(cache, c.ghToken, c.org, c.heroRepo, c.topNRepos) <+>
            new PullRequestsService[IO]().service(cache, c.ghToken, c.org)
          server <- BlazeBuilder[IO]
            .bindHttp(8080, "localhost")
            .mountService(RenderingService.service)
            .mountService(apiService, "/api")
            .serve
        } yield server
      case Left(e) => Stream.eval(IO {
        System.err.println(e.getMessage)
        ExitCode.Error
      })
    }
}
