package dashing.server

import cats.effect.IO
import cats.implicits._
import com.typesafe.config.{ ConfigFactory, ConfigMemorySize }
import fs2.Stream
import io.circe.generic.auto._
import io.circe.config.syntax._
import org.http4s.server.blaze._
import org.http4s.util.{ExitCode, StreamApp}

import model.DashingConfig

object Main extends StreamApp[IO] {

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    ConfigFactory.load().as[DashingConfig] match {
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
        System.err.println(e.getMessage)
        ExitCode.error
      })
    }
}