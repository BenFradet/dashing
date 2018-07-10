package dashing
package server

import scala.concurrent.ExecutionContext

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

import model.{Repo, Repos}

object StarsService {

  def service(
    cache: Cache[IO, String, String],
    token: String,
    org: String,
    heroRepo: String,
    topN: Int
  )(implicit ec: ExecutionContext): HttpService[IO] = {
    val gh = Github(Some(token))
    HttpService[IO] {
      case GET -> Root / "stars" / "top-n" => for {
        topN <- cache.lookupOrInsert("top-n",
          getTopN(gh, org, topN, heroRepo).map(_.map(_.asJson.noSpaces)))
        res <- topN.fold(ex => NotFound(ex.getMessage), l => Ok(l))
      } yield res
      case GET -> Root / "stars" / "hero-repo" => for {
        stars <- cache.lookupOrInsert("hero-repo",
          getStars(gh, org, heroRepo).map(_.map(_.asJson.noSpaces)))
        res <- stars.fold(ex => NotFound(ex.getMessage), r => Ok(r))
      } yield res
    }
  }

  def getTopN(
    gh: Github,
    org: String,
    n: Int,
    heroRepo: String,
    minStarsThreshold: Int = 10
  ): IO[Either[GHException, Repos]] = (for {
    rs <- EitherT(utils.getRepos[IO](gh, org))
    repos = rs
      .filter(_.status.stargazers_count >= minStarsThreshold)
      .map(_.name)
      .filter(_ != heroRepo)
    stars <- EitherT(getStars(gh, org, repos))
    sorted = stars.repos.sortBy(-_.stars)
    topN = sorted.take(n)
    othersCombined = Monoid.combineAll(sorted.drop(n))
    others = othersCombined.copy(
      name = "others",
      starsTimeline = othersCombined.starsTimeline.sortBy(_.label)
    )
  } yield Repos(others :: topN)).value

  def getStars(
    gh: Github,
    org: String,
    repoNames: List[String]
  ): IO[Either[GHException, Repos]] =
    repoNames
      .traverse(r => getStars(gh, org, r))
      .map(_.sequence)
      .map(_.map(Repos.apply))

  def getStars(gh: Github, org: String, repoName: String): IO[Either[GHException, Repo]] = (for {
    stargazers <- EitherT(utils.autoPaginate(p => listStargazers(gh, org, repoName, Some(p))))
    // we keep only yyyy-mm
    starTimestamps = stargazers.map(_.starred_at).flatten.map(_.take(7))
    timeline = utils.computeTimeline(starTimestamps)
  } yield Repo(repoName, timeline._1, timeline._2)).value

  def listStargazers(
    gh: Github,
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[Stargazer]]]] =
    gh.activities.listStargazers(org, repoName, true, page).exec[IO, HttpResponse[String]]()
}
