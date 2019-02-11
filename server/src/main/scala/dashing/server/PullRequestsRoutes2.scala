package dashing.server

import scala.concurrent.duration.FiniteDuration

import cats.Monoid
import cats.effect.{Clock, Effect, Sync, Timer}
import cats.implicits._
import io.chrisdavenport.mules.Cache
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import model._

class PullRequestsRoutes2[F[_]: Effect: Timer] extends Http4sDsl[F] {
  import PullRequestsRoutes2._

  def routes(
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    config: PRDashboardsConfig,
  ): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "prs-monthly-2" => for {
        prs <-
          getMonthlyPRs(cache, graphQL, config.orgs, config.lookback, config.peopleToIgnore.toSet)
            .map(_.asJson.noSpaces)
            .attempt
        res <- prs.fold(ex => InternalServerError(ex.getMessage), j => Ok(j))
      } yield res
    }
}

object PullRequestsRoutes2 {
  def getMonthlyPRs[F[_]: Sync: Clock](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    orgs: List[String],
    lookback: FiniteDuration,
    peopleToIgnore: Set[String],
  ): F[Map[String, Map[String, Double]]] = orgs.traverse { org =>
    for {
      prs <- utils.lookupOrInsert(cache)(s"prs-$org", getPRs(cache, graphQL, org, peopleToIgnore))
      monthlyPRs = utils.computeMonthlyTimeline(prs.pullRequests.map(_.timestamp.take(7)), lookback)
    } yield org -> monthlyPRs
  }.map(_.toMap)

  def getPRs[F[_]: Sync: Clock](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    org: String,
    peopleToIgnore: Set[String],
  ): F[PullRequestsInfo] = for {
    repos <- utils.lookupOrInsert(cache)(s"repos-$org", graphQL.getOrgRepositories(org))
    prs <- repos.repositoriesAndStars.traverse(rs => graphQL.getPRs(org, rs.repository))
    members <- utils.lookupOrInsert(cache)(s"members-$org", graphQL.getOrgMembers(org))
    allPRs = Monoid.combineAll(prs)
    filteredPRs = allPRs.pullRequests.filterNot(pr =>
      members.members.toSet.contains(pr.author) || peopleToIgnore.contains(pr.author))
  } yield PullRequestsInfo(filteredPRs, None, false)
}
