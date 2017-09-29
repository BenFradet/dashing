package dashing.server

import cats.effect.IO
import github4s.Github
import github4s.Github._
import github4s.cats.effect.jvm.Implicits._
import org.http4s._
import org.http4s.dsl._
import scalaj.http.HttpResponse

object ApiService extends Service {

  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))

  override val service = HttpService[IO] {
    case GET -> Root / name => getUser(name)
  }

  def getUser(s: String): IO[Response[IO]] =
    gh.users.get(s).exec[IO, HttpResponse[String]]()
      .flatMap(_.fold(ex => NotFound(ex.getMessage), r => Ok(r.result.login)))
}