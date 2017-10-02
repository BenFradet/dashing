package dashing.server

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.cats.effect.jvm.Implicits._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl._
import scalaj.http.HttpResponse

object ApiService extends Service {

  type Repo = String
  type Organization = String

  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org: Organization = "snowplow"

  override val service = HttpService[IO] {
    case GET -> Root / "stars" =>
      getAllStars(org).flatMap(_.fold(ex => NotFound(ex.getMessage), m => Ok(m.asJson.noSpaces)))
  }

  def getAllStars(org: Organization): IO[Either[GHException, Map[Repo, Map[String, Int]]]] = (for {
    repos <- EitherT(getRepos(org))
    stars <- EitherT(getStars(org, repos))
  } yield stars).value

  def getRepos(org: Organization): IO[Either[GHException, List[Repo]]] = (for {
    repos <- EitherT(gh.repos.listOrgRepos(org, Some("sources")).exec[IO, HttpResponse[String]]())
    repoNames = repos.result.map(_.name)
  } yield repoNames).value

  def getStars(org: Organization, repos: List[Repo]): IO[Either[GHException, Map[Repo, Map[String, Int]]]] =
    repos.traverse(r => getStars(org, r))
      .map(_.sequence.map(_.toMap))

  def getStars(org: Organization, repo: Repo): IO[Either[GHException, (Repo, Map[String, Int])]] =
    (for {
      stargazers <- EitherT(
        gh.activities.listStargazers(org, repo, true).exec[IO, HttpResponse[String]]())
      stars = repo -> stargazers.result
        .map(_.starred_at)
        .flatten
        .sorted
        .foldLeft((Map.empty[String, Int], 0)) {
          case ((m, c), dt) =>
            val month = dt.take(7)
            val cnt = m.getOrElse(month, 1) + c
            (m + (month -> cnt), cnt)
        }._1
    } yield stars).value
}