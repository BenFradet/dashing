package dashing.server

import cats.effect.IO
import github4s.Github
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._

object PullRequestsService extends Service {

  // config
  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org = "snowplow"

  override val service = HttpService[IO] {
    case GET -> Root / "prs" => utils.getOrgMembers(gh, org)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), l => Ok(l.asJson.noSpaces)))
  }
}