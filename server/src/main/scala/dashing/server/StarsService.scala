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

object StarsService {

  def service(
    token: String,
    org: String,
    heroRepo: String,
    topN: Int
  ): HttpService[IO] = {
    val gh = Github(Some(token))
    HttpService[IO] {
      case GET -> Root / "stars" / "top-n" => getTopN(gh, org, topN, heroRepo)
        .flatMap(_.fold(ex => NotFound(ex.getMessage), l => Ok(l.asJson.noSpaces)))
      case GET -> Root / "stars" / "hero-repo" => getStars(gh, org, heroRepo)
        .flatMap(_.fold(ex => NotFound(ex.getMessage), r => Ok(r.asJson.noSpaces)))
    }
  }

  def getTopN(
    gh: Github,
    org: String,
    n: Int,
    heroRepo: String,
    minStarsThreshold: Int = 10
  ): IO[Either[GHException, List[Repo]]] = (for {
    rs <- EitherT(utils.getRepos(gh, org))
    repos = rs
      .filter(_.status.stargazers_count >= minStarsThreshold)
      .map(_.name)
      .filter(_ != heroRepo)
    stars <- EitherT(getStars(gh, org, repos))
    sorted = stars.sortBy(-_.stars)
    topN = sorted.take(n)
    othersCombined = Monoid.combineAll(sorted.drop(n))
    others = othersCombined.copy(
      name = "others",
      starsTimeline = othersCombined.starsTimeline.sortBy(_.label)
    )
  } yield others :: topN).value

  def getStars(
    gh: Github,
    org: String,
    repoNames: List[String]
  ): IO[Either[GHException, List[Repo]]] =
    repoNames
      .traverse(r => getStars(gh, org, r))
      .map(_.sequence)

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