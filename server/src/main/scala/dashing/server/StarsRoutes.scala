package dashing
package server

import scala.concurrent.ExecutionContext

import cats.Monoid
import cats.data.EitherT
import cats.effect.{Effect, Sync, Timer}
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.cats.effect.jvm.Implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import scalaj.http.HttpResponse

import model.{Repo, Repos}

class StarsRoutes[F[_]: Effect: Timer] extends Http4sDsl[F] {
  import StarsRoutes._

  def routes(
    cache: Cache[F, String, String],
    token: String,
    org: String,
    heroRepo: String,
    topN: Int
  )(implicit ec: ExecutionContext): HttpRoutes[F] = {
    val gh = Github(Some(token))
    HttpRoutes.of[F] {
      case GET -> Root / "stars" / "top-n" => for {
        topN <- cache.lookupOrInsert("top-n",
          getTopN(gh, org, topN, heroRepo).value.map(_.map(_.repos.asJson.noSpaces)))
        res <- topN.fold(ex => NotFound(ex.getMessage), l => Ok(l))
      } yield res
      case GET -> Root / "stars" / "hero-repo" => for {
        stars <- cache.lookupOrInsert("hero-repo",
          getStars(gh, org, heroRepo).value.map(_.map(_.asJson.noSpaces)))
        res <- stars.fold(ex => NotFound(ex.getMessage), r => Ok(r))
      } yield res
    }
  }
}

object StarsRoutes {

  def getTopN[F[_]: Sync](
    gh: Github,
    org: String,
    n: Int,
    heroRepo: String,
    minStarsThreshold: Int = 10
  ): EitherT[F, GHException, Repos] = for {
    rs <- utils.getRepos[F](gh, org)
    repos = rs
      .filter(_.status.stargazers_count >= minStarsThreshold)
      .map(_.name)
      .filter(_ != heroRepo)
    stars <- getStars(gh, org, repos)
    sorted = stars.repos.sortBy(-_.stars)
    topN = sorted.take(n)
    othersCombined = Monoid.combineAll(sorted.drop(n))
    others = othersCombined.copy(
      name = "others",
      starsTimeline = othersCombined.starsTimeline.sortBy(_.label).dropRight(1)
    )
  } yield Repos(others :: topN)

  def getStars[F[_]: Sync](
    gh: Github,
    org: String,
    repoNames: List[String]
  ): EitherT[F, GHException, Repos] =
    repoNames.traverse(r => getStars(gh, org, r)).map(Repos.apply)

  def getStars[F[_]: Sync](
      gh: Github, org: String, repoName: String): EitherT[F, GHException, Repo] = for {
    stargazers <- utils.autoPaginate(p => listStargazers(gh, org, repoName, Some(p)))
    // we keep only yyyy-mm
    starTimestamps = stargazers.map(_.starred_at).flatten.map(_.take(7))
    timeline = utils.computeCumulativeMonthlyTimeline(starTimestamps)
  } yield Repo(repoName, timeline._1, timeline._2)

  def listStargazers[F[_]: Sync](
    gh: Github,
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): F[Either[GHException, GHResult[List[Stargazer]]]] =
    gh.activities.listStargazers(org, repoName, true, page).exec[F, HttpResponse[String]]()
}
