package dashing.server

import cats.effect.IO
import org.http4s._
import org.http4s.dsl._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

class ApiServiceSpec extends Specification with IOMatchers {
  args(skipAll = true)

  def serve(req: Request[IO]): Response[IO] = ApiService.service.orNotFound(req).unsafeRunSync

  "ApiService" should {
    "respond to /stars" in {
      val response = serve(Request(GET, Uri(path = "/stars")))
      response.status must_== (Ok)
    }
  }
}