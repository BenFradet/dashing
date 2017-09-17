package dashing.server

import cats.effect.IO
import org.http4s._
import org.http4s.dsl._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

class HelloServiceSpec extends Specification with IOMatchers {
  def serve(req: Request[IO]): Response[IO] = HelloService.service.orNotFound(req).unsafeRunSync

  "HelloService" should {
    "respond to /hello/{{name}}" in {
      val response = serve(Request(GET, Uri(path = "/hello/ben")))
      response.status must_== (Ok)
      response.as[String] must returnValue("Hello ben")
    }
  }
}