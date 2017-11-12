package dashing.server

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

class StarsServiceSpec extends Specification with IOMatchers {
  args(skipAll = sys.env.get("GITHUB4S_ACCESS_TOKEN").isEmpty)

  def serve(req: Request[IO]): Response[IO] =
    StarsService.service(sys.env.getOrElse("GITHUB4S_ACCESS_TOKEN", ""), "igwp", "igwp", 2)
      .orNotFound(req).unsafeRunSync

  "StarsService" should {
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