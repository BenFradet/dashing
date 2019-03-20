package dashing.server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import cats.effect.{ContextShift, IO}
import io.chrisdavenport.mules._
import org.http4s._
import org.http4s.client.JavaNetClientBuilder
import org.http4s.dsl.io._
import org.http4s.syntax.kleisli._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

import model.{PageInfo, StarDashboardsConfig}

class StarsRoutesSpec extends Specification with IOMatchers {
  args(skipAll = sys.env.get("GITHUB_ACCESS_TOKEN").isEmpty)

  implicit val timer = IO.timer(global)
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def serve(req: Request[IO]): Response[IO] = (for {
    cache <- MemoryCache.createMemoryCache[IO, String, PageInfo](TimeSpec.fromDuration(12.hours))
    client = JavaNetClientBuilder[IO](global).create
    graphQL = new GraphQL(client, sys.env.getOrElse("GITHUB_ACCESS_TOKEN", ""))
    service <- new StarsRoutes[IO]()
      .routes(cache, graphQL, StarDashboardsConfig("igwp", "igwp", 2))
      .orNotFound(req)
  } yield service).unsafeRunSync

  "StarsRoutes" should {
    "respond to /stars/top-n" in {
      val response = serve(Request(GET, Uri(path = "/stars/top-n")))
      response.status must_== (Ok)
    }
    "respond to /stars/hero-repo" in {
      val response = serve(Request(GET, Uri(path = "/stars/hero-repo")))
      response.status must_== (Ok)
    }
  }
}
