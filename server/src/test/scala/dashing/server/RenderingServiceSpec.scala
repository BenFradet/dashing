package dashing.server

import cats.effect.IO
import org.http4s._
import org.http4s.dsl._
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

class RenderingServiceSpec extends Specification with IOMatchers {
  def serve(req: Request[IO]): Response[IO] = RenderingService.service.orNotFound(req).unsafeRunSync

  val index = """
    |<html>
      |<head>
        |<meta charset="UTF-8" />
        |<link rel="stylesheet" href="bootstrap.css" />
        |<link rel="stylesheet" href="app.css" />
        |<link rel="stylesheet" href="default.css" />
        |<script src="highlight.pack.js"></script>
        |<script src="client-jsdeps.js"></script>
      |</head>
      |<body>
        |<script src="client-fastopt.js"></script>
      |</body>
    |</html>""".stripMargin.replaceAll("\n", "")

  "RenderingService" should {
    "respond to / with index.html" in {
      val response = serve(Request(GET, Uri(path = "/")))
      response.status must_== (Ok)
      response.as[String] must returnValue(index)
    }

    "respond to /{{resource}} with the appropriate resource" in {
      val response = serve(Request(GET, Uri(path = "/client-fastopt.js")))
      response.status must_== (Ok)
    }
  }
}