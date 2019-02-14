package dashing
package server

import cats.Monoid
import cats.effect.{Clock, Effect, Sync, Timer}
import cats.implicits._
import io.chrisdavenport.mules.Cache
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import model._

class StarsRoutes[F[_]: Effect: Timer] extends Http4sDsl[F] {
  import StarsRoutes._

  def routes(
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    config: StarDashboardsConfig
  ): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "stars" / "top-n" => for {
        topN <- getTopN(cache, graphQL, config.org, config.topNRepos, config.heroRepo)
          .map(_.repos.asJson.noSpaces)
          .attempt
        res <- topN.fold(ex => NotFound(ex.getMessage), l => Ok(l))
      } yield res
      case GET -> Root / "stars" / "hero-repo" => for {
        heroRepo <- getStars(cache, graphQL, config.org, config.heroRepo)
          .map(_.asJson.noSpaces)
          .attempt
        res <- heroRepo.fold(ex => NotFound(ex.getMessage), h => Ok(h))
      } yield res
    }
}

object StarsRoutes {
  def getTopN[F[_]: Sync: Clock](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    org: String,
    n: Int,
    heroRepo: String,
    minStarsThreshold: Int = 10
  ): F[Repos] = for {
    rs <- utils.lookupOrInsert(cache)(s"repos-$org", graphQL.getOrgRepositories(org))
    repos = rs.repositoriesAndStars
      .filter(_.firstHundredStars >= minStarsThreshold)
      .map(_.repository)
      .filter(_ != heroRepo)
    stars <- repos.traverse { r =>
      getStars(cache, graphQL, org, r)
    }
    sorted = stars.sortBy(-_.stars)
    topN = sorted.take(n)
    othersCombined = Monoid.combineAll(sorted.drop(n))
    others = othersCombined.copy(
      name = "others",
      starsTimeline = othersCombined.starsTimeline.toList.sortBy(_._1).dropRight(1).toMap
    )
  } yield Repos(others :: topN)

  def getStars[F[_]: Sync : Clock](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    org: String,
    repoName: String
  ): F[Repo] = for {
    stargazers <-
      utils.lookupOrInsert(cache)(s"stars-$org-$repoName", graphQL.listStargazers(org, repoName))
    // we keep only yyyy-mm
    starTimestamps = stargazers.starsTimeline.map(_.take(7))
    timeline = utils.computeCumulativeMonthlyTimeline(starTimestamps)
  } yield Repo(repoName, timeline._1, timeline._2)
}
