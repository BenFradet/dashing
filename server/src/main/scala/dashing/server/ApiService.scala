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
  val minStarsThreshold = 10

  override val service = HttpService[IO] {
    case GET -> Root / "stars" => getTopN(org, topN, heroRepo, minStarsThreshold)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), l => Ok(l.asJson.noSpaces)))
    case GET -> Root / "stars" / heroRepo => getStars(org, heroRepo)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), r => Ok(r.asJson.noSpaces)))
  }

  def getTopN(
    org: String,
    n: Int,
    heroRepo: String,
    minStarsThreshold: Int
  ): IO[Either[GHException, List[Repo]]] = (for {
    rs    <- EitherT(getRepos(org, minStarsThreshold))
    repos  = rs.filter(_ != heroRepo)
    stars <- EitherT(getStars(org, repos))
    sorted = stars.sortBy(-_.stars)
    topN   = sorted.take(n)
    rest   = Monoid.combineAll(sorted.drop(n)).copy(repoName = "others")
  } yield rest :: topN).value

  def getRepos(org: String, minStarsThreshold: Int): IO[Either[GHException, List[String]]] = (for {
    repos <- EitherT(gh.repos.listOrgRepos(org, Some("sources"), Some(Pagination(1, 100)))
      .exec[IO, HttpResponse[String]]())
    repoNames = repos.result
      .filter(_.status.stargazers_count >= minStarsThreshold)
      .map(_.name)
  } yield repoNames).value

  def getStars(org: String, repoNames: List[String]): IO[Either[GHException, List[Repo]]] =
    repoNames.traverse(r => getStars(org, r))
      .map(_.sequence)

  def getStars(org: String, repoName: String): IO[Either[GHException, Repo]] = (for {
    reposAndPages <- EitherT(getFirstPageStars(org, repoName))
    otherPages <- EitherT(getPaginatedStars(org, repoName, reposAndPages._2))
    allPages = Monoid.combineAll(reposAndPages._1 :: otherPages)
  } yield allPages).value

  def getFirstPageStars(org: String, repoName: String): IO[Either[GHException, (Repo, List[Pagination])]] =
    (for {
      stargazers <- EitherT(listStargazers(org, repoName, Some(Pagination(1, 100))))
      initial = Repo(repoName, stargazers.result)
      paginations = utils.getNrPages(stargazers.headers).toList
        .flatMap(2 to _)
        .map(Pagination(_, 100))
    } yield (initial, paginations)).value

  def getPaginatedStars(
    org: String,
    repoName: String,
    pags: List[Pagination]
  ): IO[Either[GHException, List[Repo]]] = (for {
    stargazers <- EitherT(pags.traverse(p => listStargazers(org, repoName, Some(p))).map(_.sequence))
    repos = stargazers.map(s => Repo(repoName, s.result))
  } yield repos).value

  def listStargazers(
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[Stargazer]]]] =
    gh.activities.listStargazers(org, repoName, true, page).exec[IO, HttpResponse[String]]()
}