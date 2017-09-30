package dashing.server

import cats.effect.IO
import org.http4s._
import org.http4s.dsl._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

class HelloServiceSpec extends Specification with IOMatchers {
  def serve(req: Request[IO]): Response[IO] = ApiService.service.orNotFound(req).unsafeRunSync

  "ApiService" should {
    "respond to /stars/{{repo}}" in {
      val response = serve(Request(GET, Uri(path = "/stars/snowplow-docker")))
      response.status must_== (Ok)
      response.as[String] must returnValue(
        """{"2016-03-09T13:07:49Z":1,"2016-03-17T14:54:58Z":2,"2016-10-05T07:21:56Z":3}""")
    }
  }
}