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
import org.http4s.dsl.io._
import scalaj.http.HttpResponse

import model.Repo

object StarsService extends Service {

  // config
  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org = "snowplow"
  val heroRepo = "snowplow"
  val topN = 5
  val minStarsThreshold = 10

  override val service = HttpService[IO] {
    case GET -> Root / "stars" / "top-n" => getTopN(org, topN, heroRepo, minStarsThreshold)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), l => Ok(l.asJson.noSpaces)))
    case GET -> Root / "stars" / "hero-repo" => getStars(org, heroRepo)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), r => Ok(r.asJson.noSpaces)))
  }

  def getTopN(
    org: String,
    n: Int,
    heroRepo: String,
    minStarsThreshold: Int
  ): IO[Either[GHException, List[Repo]]] = (for {
    rs <- EitherT(utils.getRepos(gh, org))
    repos = rs
      .filter(_.status.stargazers_count >= minStarsThreshold)
      .map(_.name)
      .filter(_ != heroRepo)
    stars <- EitherT(getStars(org, repos))
    sorted = stars.sortBy(-_.stars)
    topN = sorted.take(n)
    othersCombined = Monoid.combineAll(sorted.drop(n))
    others = othersCombined.copy(
      name = "others",
      starsTimeline = othersCombined.starsTimeline.sortBy(_.label)
    )
  } yield others :: topN).value

  def getStars(org: String, repoNames: List[String]): IO[Either[GHException, List[Repo]]] =
    repoNames
      .traverse(r => getStars(org, r))
      .map(_.sequence)

  def getStars(org: String, repoName: String): IO[Either[GHException, Repo]] = (for {
    stargazers <- EitherT(utils.autoPaginate(p => listStargazers(org, repoName, Some(p))))
    // we keep only yyyy-mm
    starTimestamps = stargazers.map(_.starred_at).flatten.map(_.take(7))
    timeline = utils.computeTimeline(starTimestamps)
  } yield Repo(repoName, timeline._1, timeline._2)).value

  def listStargazers(
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[Stargazer]]]] =
    gh.activities.listStargazers(org, repoName, true, page).exec[IO, HttpResponse[String]]()
}