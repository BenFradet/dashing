package dashing
package server

import cats.Parallel
import cats.effect.{Clock, Effect, Sync, Timer}
import cats.implicits._
import io.chrisdavenport.mules.Cache
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import model._

class StarsRoutes[F[_]: Effect: Timer: Parallel] extends Http4sDsl[F] {
  import StarsRoutes._

  /**
   * Stars routes, exposing:
   * - /stars/top-n, for a star timeline regarding the n most starred repositories
   * - /stars/hero-repo, for a star timeline regarding the hero repository
   * @param cache for already requested star information
   * @param graphQL to interact with the github graphql API
   * @param config stars dashboards configuration
   * @return http routes
   */
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
  /**
   * Get a star timeline for the n most starred repositories, the others being grouped as one
   * timeline
   * @param cache for already requested star information
   * @param graphQL to interact with the github graphql API
   * @param org the github organization in which to look for star timelines
   * @param n the n most starred repositories will be presented as individiual timelines while the
   * others will be grouped together as one timeline
   * @param heroRepo the name of the hero repository
   * @param minStarsThreshold the minimum number of stars for a repository to be part of a timeline
   * @return a [[Repos]] which contains the top n star timelines in F
   */
  def getTopN[F[_]: Sync: Clock: Parallel](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    org: String,
    n: Int,
    heroRepo: String,
    minStarsThreshold: Int = 50
  ): F[Repos] = for {
    rs <- utils.lookupOrInsert(cache)(s"repos-$org", graphQL.getOrgRepositories(org))
    repos = rs.repositoriesAndStars
      .filter(_.firstHundredStars >= minStarsThreshold)
      .map(_.repository)
      .filter(_ != heroRepo)
    stars <- repos.parTraverse { r =>
      getStars(cache, graphQL, org, r)
    }
    sorted = stars.sortBy(-_.stars)
    topN = sorted.take(n)
  } yield Repos(topN)

  /**
   * Get a star timeline for a repository
   * @param cache for already requested star information
   * @param graphQL to interact with the github graphql API
   * @param org the github organization in which to look for star timelines
   * @param name of the repository
   * @return a [[Repo]] which contains the specified repository's star timeline as well as current
   * number of stars in F
   */
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
