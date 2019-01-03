package dashing.server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import cats.effect.IO
import io.chrisdavenport.mules.{Cache, TimeSpec}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.syntax.kleisli._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

import model.StarDashboardsConfig

class StarsRoutesSpec extends Specification with IOMatchers {
  args(skipAll = sys.env.get("GITHUB4S_ACCESS_TOKEN").isEmpty)

  implicit val timer = IO.timer(global)

  def serve(req: Request[IO]): Response[IO] = (for {
    cache <- Cache.createCache[IO, String, String](TimeSpec.fromDuration(12.hours))
    service <- new StarsRoutes[IO]()
      .routes(cache, sys.env.getOrElse("GITHUB4S_ACCESS_TOKEN", ""),
        StarDashboardsConfig("igwp", "igwp", 2))
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
