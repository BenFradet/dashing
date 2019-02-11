package dashing.server

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import cats.implicits._
import com.typesafe.config.ConfigFactory
import fs2.Stream
import io.chrisdavenport.mules.{Cache, TimeSpec}
import io.circe.generic.auto._
import io.circe.config.syntax._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Router
import org.http4s.server.blaze._
import org.http4s.syntax.kleisli._

import model.{DashingConfig, PageInfo}

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
            Cache.createCache[F, String, String](TimeSpec.fromDuration(c.cacheDuration)))
          piCache <- Stream.eval(
            Cache.createCache[F, String, PageInfo](TimeSpec.fromDuration(c.cacheDuration)))
          client <- BlazeClientBuilder[F](ec).stream
          graphQL = new GraphQL(client, c.ghToken)
          apiService =
            new StarsRoutes[F].routes(cache, c.ghToken, c.starDashboards) <+>
            new PullRequestsRoutes[F]().routes(cache, c.ghToken, c.prDashboards) <+>
            new PullRequestsRoutes2[F]().routes(piCache, graphQL, c.prDashboards)
          httpApp = Router(
            "/" -> new RenderingRoutes[F](ec).routes,
            "/api" -> apiService
          ).orNotFound
          server <- BlazeServerBuilder[F]
            .bindHttp(c.port, c.host)
            .withHttpApp(httpApp)
            .withResponseHeaderTimeout(10.minute)
            .withIdleTimeout(10.minute)
            .serve
        } yield server
      case Left(e) => Stream.eval(ConcurrentEffect[F].delay {
        System.err.println(e.getMessage)
        ExitCode.Error
      })
    }
}
