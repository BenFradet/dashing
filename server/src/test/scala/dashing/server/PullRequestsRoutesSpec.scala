package dashing.server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import cats.effect.{ContextShift, IO}
import io.chrisdavenport.mules.{Cache, TimeSpec}
import org.http4s._
import org.http4s.client.JavaNetClientBuilder
import org.http4s.dsl.io._
import org.http4s.syntax.kleisli._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

import model.PRDashboardsConfig

class PullRequestsRoutesSpec extends Specification with IOMatchers {
  args(skipAll = sys.env.get("GITHUB_ACCESS_TOKEN").isEmpty)

  implicit val timer = IO.timer(global)
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def serve(req: Request[IO]): Response[IO] = (for {
    cache <- Cache.createCache[IO, String, String](TimeSpec.fromDuration(12.hours))
    client = JavaNetClientBuilder[IO](global).create
    service <- new PullRequestsRoutes[IO]()
      .routes(client, cache, sys.env.getOrElse("GITHUB_ACCESS_TOKEN", ""),
        PRDashboardsConfig(List("igwp"), List.empty, 365.days))
      .orNotFound(req)
  } yield service).unsafeRunSync

  "PullRequestsRoutes" should {
    "respond to /prs-quarterly" in {
      val response = serve(Request(GET, Uri(path = "/prs-quarterly")))
      response.status must_== (Ok)
    }
    "respond to /prs-monthly" in {
      val response = serve(Request(GET, Uri(path = "/prs-monthly")))
      response.status must_== (Ok)
    }
  }
}
