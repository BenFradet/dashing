package dashing
package server

import cats.Monoid
import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain.Pagination
import github4s.cats.effect.jvm.Implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl._
import scalaj.http.HttpResponse

object ApiService extends Service {

  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org = "snowplow"
  val topN = 5
  val pagOpt = Some(Pagination(1, 1000))

  override val service = HttpService[IO] {
    case GET -> Root / "stars" =>
      getTopN(org, topN).flatMap(_.fold(ex => NotFound(ex.getMessage), l => Ok(l.asJson.noSpaces)))
  }

  def getTopN(org: String, n: Int): IO[Either[GHException, List[Repo]]] = (for {
    repos <- EitherT(getRepos(org))
    stars <- EitherT(getStars(org, repos))
    sorted = stars.sortBy(-_.stars)
    topN   = sorted.take(n)
    rest   = Monoid.combineAll(sorted.drop(n)).copy(repoName = "others")
  } yield rest :: topN).value

  def getRepos(org: String): IO[Either[GHException, List[String]]] = (for {
    repos <- EitherT(
      gh.repos.listOrgRepos(org, Some("sources"), pagOpt).exec[IO, HttpResponse[String]]())
    repoNames = repos.result.map(_.name)
  } yield repoNames).value

  def getStars(org: String, repoNames: List[String]): IO[Either[GHException, List[Repo]]] =
    repoNames.traverse(r => getStars(org, r))
      .map(_.sequence)

  def getStars(org: String, repoName: String): IO[Either[GHException, Repo]] =
    (for {
      stargazers <- EitherT(
        gh.activities.listStargazers(org, repoName, true, pagOpt).exec[IO, HttpResponse[String]]())
      repo = Repo(repoName, stargazers.result)
    } yield repo).value
}