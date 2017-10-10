package dashing
package server

import cats.Monoid
import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.cats.effect.jvm.Implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl._
import scalaj.http.HttpResponse

object ApiService extends Service {

  // config
  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org = "snowplow"
  val heroRepo = "snowplow"
  val topN = 5

  override val service = HttpService[IO] {
    case GET -> Root / "stars" / "top-n" => getTopN(org, topN, heroRepo)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), l => Ok(l.asJson.noSpaces)))
    case GET -> Root / "stars" / "hero-repo" => getStars(org, heroRepo)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), r => Ok(r.asJson.noSpaces)))
  }

  def getTopN(
    org: String,
    n: Int,
    heroRepo: String
  ): IO[Either[GHException, List[Repo]]] = (for {
    rs    <- EitherT(getRepoNames(org))
    repos  = rs.filter(_ != heroRepo)
    stars <- EitherT(getStars(org, repos))
    sorted = stars.sortBy(-_.stars)
    topN   = sorted.take(n)
    rest   = Monoid.combineAll(sorted.drop(n)).copy(name = "others")
  } yield rest :: topN).value

  def getRepoNames(org: String): IO[Either[GHException, List[String]]] = (for {
    repos <- EitherT(utils.autoPaginate(p => listRepos(org, Some(p))))
    repoNames = repos.map(_.name)
  } yield repoNames).value

  def getStars(org: String, repoNames: List[String]): IO[Either[GHException, List[Repo]]] =
    repoNames
      .traverse(r => getStars(org, r))
      .map(_.sequence)

  def getStars(org: String, repoName: String): IO[Either[GHException, Repo]] = (for {
    stargazers <- EitherT(utils.autoPaginate(p => listStargazers(org, repoName, Some(p))))
    repo = Repo(repoName, stargazers)
  } yield repo).value

  def listRepos(
    org: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[Repository]]]] =
    gh.repos.listOrgRepos(org, Some("sources"), page).exec[IO, HttpResponse[String]]()

  def listStargazers(
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[Stargazer]]]] =
    gh.activities.listStargazers(org, repoName, true, page).exec[IO, HttpResponse[String]]()
}