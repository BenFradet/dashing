package dashing.server

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

class PullRequestsServiceSpec extends Specification with IOMatchers {
  args(skipAll = sys.env.get("GITHUB4S_ACCESS_TOKEN").isEmpty)

  def serve(req: Request[IO]): Response[IO] =
    PullRequestsService.service(sys.env.getOrElse("GITHUB4S_ACCESS_TOKEN", ""), "igwp")
      .orNotFound(req).unsafeRunSync

  "PullRequestsService" should {
    "respond to /prs" in {
      val response = serve(Request(GET, Uri(path = "/prs")))
      println(response)
      response.status must_== (Ok)
    }
  }
}
