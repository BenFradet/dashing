package dashing.server

import scala.concurrent.ExecutionContext

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import cats.implicits._
import com.typesafe.config.ConfigFactory
import fs2.Stream
import io.circe.generic.auto._
import io.circe.config.syntax._
import org.http4s.server.Router
import org.http4s.server.blaze._
import org.http4s.syntax.kleisli._

import model.DashingConfig

object DashingServer extends IOApp {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  override def run(args: List[String]): IO[ExitCode] = ServerStream.stream[IO].compile.lastOrError
}

object ServerStream {

  def stream[F[_]: ConcurrentEffect: ContextShift: Timer](
    implicit ec: ExecutionContext
  ): Stream[F, ExitCode] =
    ConfigFactory.load().as[DashingConfig] match {
      case Right(c) =>
        for {
          cache <- Stream.eval(
            Cache.createCache[F, String, String](Cache.TimeSpec.fromDuration(c.cacheDuration)))
          apiService =
            new StarsRoutes[F].routes(cache, c.ghToken, c.org, c.heroRepo, c.topNRepos) <+>
            new PullRequestsRoutes[F]().routes(cache, c.ghToken, c.org)
          httpApp = Router(
            "/" -> new RenderingRoutes[F](ec).routes,
            "/api" -> apiService
          ).orNotFound
          server <- BlazeServerBuilder[F]
            .bindHttp(c.port, c.host)
            .withHttpApp(httpApp)
            .serve
        } yield server
      case Left(e) => Stream.eval(ConcurrentEffect[F].delay {
        System.err.println(e.getMessage)
        ExitCode.Error
      })
    }
}
