package dashing.server

import scala.concurrent.ExecutionContext

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
import org.http4s.HttpService
import org.http4s.dsl.Http4sDsl
import scalaj.http.HttpResponse

import model.{GHObject, GHObjectTimeline}

class PullRequestsService[F[_]: Effect: Timer] extends Http4sDsl[F] {
  import PullRequestsService._

  def service(
    cache: Cache[F, String, String],
    token: String,
    org: String
  )(implicit ec: ExecutionContext): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "prs" => for {
        prs <- cache.lookupOrInsert("prs", getPRs(Github(Some(token)), org).map(_.map(_.asJson.noSpaces)))
        res <- prs.fold(ex => NotFound(ex.getMessage), t => Ok(t))
      } yield res
    }
}

object PullRequestsService {

  def getPRs[F[_]: Sync](gh: Github, org: String): F[Either[GHException, GHObjectTimeline]] = (for {
    repos <- EitherT(utils.getRepos[F](gh, org))
    repoNames = repos.map(_.name)
    prs <- EitherT(getPRs(gh, org, repoNames))
    members <- EitherT(utils.getOrgMembers[F](gh, org))
    (prsByMember, prsByNonMember) = prs.partition(pr => members.toSet.contains(pr.author))
    memberPRsCounted = utils.computeTimeline(prsByMember.map(_.created.take(7)))._1
    nonMemberPRsCounted = utils.computeTimeline(prsByNonMember.map(_.created.take(7)))._1
  } yield GHObjectTimeline(memberPRsCounted, nonMemberPRsCounted)).value

  def getPRs[F[_]: Sync](
    gh: Github,
    org: String,
    repoNames: List[String]
  ): F[Either[GHException, List[GHObject]]] = (for {
    nested <- EitherT(
      repoNames
        .traverse(getPRs(gh, org, _))
        .map(_.sequence)
    )
    flattened = nested.flatten
  } yield flattened).value

  def getPRs[F[_]: Sync](
      gh: Github, org: String, repoName: String): F[Either[GHException, List[GHObject]]] =
    (for {
      prs <- EitherT(utils.autoPaginate(p => listPRs(gh, org, repoName, Some(p))))
      pullRequests = prs
        .map(pr => (pr.user.map(_.login), pr.created_at.some).bisequence)
        .flatten
        .map(pr => GHObject(pr._1, pr._2))
    } yield pullRequests).value

  def listPRs[F[_]: Sync](
    gh: Github,
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): F[Either[GHException, GHResult[List[PullRequest]]]] =
    gh.pullRequests.list(org, repoName).exec[F, HttpResponse[String]]()
}
