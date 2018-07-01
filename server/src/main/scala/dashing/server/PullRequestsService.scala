package dashing.server

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

import model.{GHObject, GHObjectTimeline}

object PullRequestsService {

  def service(token: String, org: String): HttpService[IO] = HttpService[IO] {
    case GET -> Root / "prs" => getPRs(Github(Some(token)), org)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), t => Ok(t.asJson.noSpaces)))
  }

  def getPRs(gh: Github, org: String): IO[Either[GHException, GHObjectTimeline]] = (for {
    repos <- EitherT(utils.getRepos(gh, org))
    repoNames = repos.map(_.name)
    prs <- EitherT(getPRs(gh, org, repoNames))
    members <- EitherT(utils.getOrgMembers(gh, org))
    (prsByMember, prsByNonMember) = prs.partition(pr => members.toSet.contains(pr.author))
    memberPRsCounted = utils.computeTimeline(prsByMember.map(_.created.take(7)))._1
    nonMemberPRsCounted = utils.computeTimeline(prsByNonMember.map(_.created.take(7)))._1
  } yield GHObjectTimeline(memberPRsCounted, nonMemberPRsCounted)).value

  def getPRs(
    gh: Github,
    org: String,
    repoNames: List[String]
  ): IO[Either[GHException, List[GHObject]]] = (for {
    nested <- EitherT(
      repoNames
        .traverse(getPRs(gh, org, _))
        .map(_.sequence)
    )
    flattened = nested.flatten
  } yield flattened).value

  def getPRs(gh: Github, org: String, repoName: String): IO[Either[GHException, List[GHObject]]] =
    (for {
      prs <- EitherT(utils.autoPaginate(p => listPRs(gh, org, repoName, Some(p))))
      pullRequests = prs
        .map(pr => (pr.user.map(_.login), pr.created_at.some).bisequence)
        .flatten
        .map(pr => GHObject(pr._1, pr._2))
    } yield pullRequests).value

  def listPRs(
    gh: Github,
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[PullRequest]]]] =
    gh.pullRequests.list(org, repoName).exec[IO, HttpResponse[String]]()
}
