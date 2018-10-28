package dashing.server

import scala.concurrent.ExecutionContext

import cats.effect.{Effect, IO, Timer}
import cats.implicits._
import com.typesafe.config.ConfigFactory
import fs2.{Stream, StreamApp}
import fs2.StreamApp.ExitCode
import io.circe.generic.auto._
import io.circe.config.syntax._
import org.http4s.server.blaze._

import model.DashingConfig

object DashingServer extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timer = IO.timer(global)

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    ServerStream.stream[IO]
}

object ServerStream {

  def stream[F[_]: Effect: Timer](implicit ec: ExecutionContext): Stream[F, ExitCode] =
    ConfigFactory.load().as[DashingConfig] match {
      case Right(c) =>
        for {
          cache <- Stream.eval(
            Cache.createCache[F, String, String](Cache.TimeSpec.fromDuration(c.cacheDuration)))
          apiService =
            new StarsService[F].service(cache, c.ghToken, c.org, c.heroRepo, c.topNRepos) <+>
            new PullRequestsService[F]().service(cache, c.ghToken, c.org)
          server <- BlazeBuilder[F]
            .bindHttp(c.port, c.host)
            .mountService(new RenderingService[F]().service)
            .mountService(apiService, "/api")
            .serve
        } yield server
      case Left(e) => Stream.eval(Effect[F].delay {
        System.err.println(e.getMessage)
        ExitCode.Error
      })
    }
}
