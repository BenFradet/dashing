package dashing.server

import cats.data.EitherT
import cats.effect.IO
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.cats.effect.jvm.Implicits._
import github4s.free.domain._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._
import scalaj.http.HttpResponse

object PullRequestsService extends Service {

  // config
  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org = "snowplow"

  override val service = HttpService[IO] {
    case GET -> Root / "prs" => getOrgMembers(org)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), l => Ok(l.asJson.noSpaces)))
  }

  def getOrgMembers(
    org: String
  ): IO[Either[GHException, List[String]]] = (for {
    ms <- EitherT(utils.autoPaginate(p => listOrgMembers(org, Some(p))))
    members = ms.map(_.login)
  } yield members).value

  def listOrgMembers(
    org: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[User]]]] =
    gh.organizations.listMembers(org, pagination = page).exec[IO, HttpResponse[String]]()
}