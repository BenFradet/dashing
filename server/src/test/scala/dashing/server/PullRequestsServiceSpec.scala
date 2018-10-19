package dashing.server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

class PullRequestsServiceSpec extends Specification with IOMatchers {
  args(skipAll = sys.env.get("GITHUB4S_ACCESS_TOKEN").isEmpty)

  def serve(req: Request[IO]): Response[IO] = (for {
    cache <- Cache.createCache[IO, String, String](Cache.TimeSpec.fromDuration(12.hours))
    service <- new PullRequestsService[IO]()
      .service(cache, sys.env.getOrElse("GITHUB4S_ACCESS_TOKEN", ""), "igwp")
      .orNotFound(req)
  } yield service).unsafeRunSync

  "PullRequestsService" should {
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
