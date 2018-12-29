package dashing.server

import cats.data.EitherT
import cats.effect.{Effect, Sync, Timer}
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.cats.effect.jvm.Implicits._
import io.chrisdavenport.mules.Cache
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import scalaj.http.HttpResponse

import model.GHObject

class PullRequestsRoutes[F[_]: Effect: Timer] extends Http4sDsl[F] {
  import PullRequestsRoutes._

  def routes(
    cache: Cache[F, String, String],
    token: String,
    orgs: List[String]
  ): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "prs-quarterly" => for {
        prs <- utils.lookupOrInsert(cache)("prs-quarterly",
          getPRsForOrgs(Github(Some(token)), orgs, getQuarterlyPRs(_, _))
            .value.map(_.map(_.asJson.noSpaces)))
        res <- prs.fold(ex => NotFound(ex.getMessage), t => Ok(t))
      } yield res
      case GET -> Root / "prs-monthly" => for {
        prs <- utils.lookupOrInsert(cache)("prs-monthly",
          getPRsForOrgs(Github(Some(token)), orgs, getMonthlyPRs(_, _))
            .value.map(_.map(_.asJson.noSpaces)))
        res <- prs.fold(ex => NotFound(ex.getMessage), t => Ok(t))
      } yield res
    }
}

object PullRequestsRoutes {

  def getPRsForOrgs[F[_]: Sync](
    gh: Github,
    orgs: List[String],
    byOrg: (Github, String) => EitherT[F, GHException, Map[String, Double]]
  ): EitherT[F, GHException, Map[String, Map[String, Double]]] =
    orgs.map(o => (EitherT.rightT[F, GHException](o), byOrg(gh, o)).tupled)
      .sequence
      .map(_.toMap)

  def getMonthlyPRs[F[_]: Sync](
    gh: Github,
    org: String
  ): EitherT[F, GHException, Map[String, Double]] = for {
    prs <- getPRs(gh, org)
    monthlyPRs = utils.computeMonthlyTimeline(prs.map(_.created.take(7)))
  } yield monthlyPRs

  def getQuarterlyPRs[F[_]: Sync](
    gh: Github,
    org: String
  ): EitherT[F, GHException, Map[String, Double]] =
    for {
      prs <- getPRs(gh, org)
      quarterlyPRs = utils.computeQuarterlyTimeline(prs.map(_.created.take(7)))
    } yield quarterlyPRs

  def getPRs[F[_]: Sync](gh: Github, org: String): EitherT[F, GHException, List[GHObject]] = for {
    repos <- utils.getRepos[F](gh, org)
    repoNames = repos.map(_.name)
    prs <- getPRs(gh, org, repoNames)
    members <- utils.getOrgMembers[F](gh, org)
    prsByNonMember = prs.filterNot(pr => members.toSet.contains(pr.author))
  } yield prsByNonMember

  def getPRs[F[_]: Sync](
    gh: Github,
    org: String,
    repoNames: List[String]
  ): EitherT[F, GHException, List[GHObject]] = for {
    nested <- repoNames.traverse(getPRs(gh, org, _))
    flattened = nested.flatten
  } yield flattened

  def getPRs[F[_]: Sync](
      gh: Github, org: String, repoName: String): EitherT[F, GHException, List[GHObject]] =
    for {
      prs <- utils.autoPaginate(p => listPRs(gh, org, repoName, Some(p)))
      pullRequests = prs
        .map(pr => (pr.user.map(_.login), pr.created_at.some).bisequence)
        .flatten
        .map(pr => GHObject(pr._1, pr._2))
    } yield pullRequests

  def listPRs[F[_]: Sync](
    gh: Github,
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): F[Either[GHException, GHResult[List[PullRequest]]]] =
    gh.pullRequests.list(org, repoName, Nil, page).exec[F, HttpResponse[String]]()
}
