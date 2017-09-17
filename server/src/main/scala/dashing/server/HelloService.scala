package dashing.server

import cats.effect.IO
import org.http4s._
import org.http4s.dsl._

object HelloService {
  val service = HttpService[IO] {
    case GET -> Root / "hello" / name => Ok(s"Hello $name")
  }
}