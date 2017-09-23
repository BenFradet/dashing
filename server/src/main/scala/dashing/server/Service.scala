package dashing.server

import cats.effect.IO
import org.http4s._

trait Service {
  def service: HttpService[IO]
}