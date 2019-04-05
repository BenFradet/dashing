package dashing.server

import cats.Monoid
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.effect.Sync
import fs2.{Chunk, Pull}
import io.circe.Decoder
import io.circe.generic.auto._
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.http4s.client.dsl._
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client

import model._

class GraphQL[F[_]: Sync](client: Client[F], token: String) extends Http4sClientDsl[F] {
  import GraphQL._

  def getPRs(owner: String, name: String): F[PullRequestsInfo] = for {
    prs <- autoPaginate((p: Pagination) => getPRsWithPagination(owner, name)(p))
    finalInfo = Monoid.combineAll(prs)
  } yield finalInfo

  private def getPRsWithPagination(
    owner: String, name: String
  )(pagination: Pagination): F[PullRequestsInfo] = {
    val cursor = pagination.cursor.map(c => s"""after:"$c"""").getOrElse("")
    val query =
      s"""query {repository(owner:"$owner",name:"$name"){pullRequests(states:[OPEN,CLOSED,MERGED]first:${pagination.size} $cursor){edges{node{author{login}createdAt}}pageInfo{endCursor hasNextPage}}}}"""
    request(Query(query))
  }

  def listStargazers(owner: String, name: String): F[StarsInfo] = for {
    prs <- autoPaginate((p: Pagination) => listStargazersWithPagination(owner, name)(p))
    finalInfo = Monoid.combineAll(prs)
  } yield finalInfo

  private def listStargazersWithPagination(
    owner: String, name: String
  )(pagination: Pagination): F[StarsInfo] = {
    val cursor = pagination.cursor.map(c => s"""after:"$c"""").getOrElse("")
    val query =
      s"""query {repository(owner:"$owner",name:"$name"){stargazers(first:${pagination.size} $cursor){edges{starredAt}pageInfo{endCursor hasNextPage}}}}"""
    request(Query(query))
  }

  def getOrgMembers(org: String): F[OrgMembersInfo] = for {
    members <- autoPaginate((p: Pagination) => getOrgMembersWithPagination(org)(p))
    finalInfo = Monoid.combineAll(members)
  } yield finalInfo

  private def getOrgMembersWithPagination(
    org: String
  )(pagination: Pagination): F[OrgMembersInfo] = {
    val cursor = pagination.cursor.map(c => s"""after:"$c"""").getOrElse("")
    val query =
      s"""query {organization(login: "$org"){membersWithRole(first: ${pagination.size} $cursor){nodes{login}pageInfo{endCursor hasNextPage}}}}"""
    request(Query(query))
  }

  def getOrgRepositories(org: String): F[OrgRepositoriesInfo] = for {
    repositories <- autoPaginate((p: Pagination) => getOrgRepositoriesWithPagination(org)(p))
    finalInfo = Monoid.combineAll(repositories)
  } yield finalInfo

  private def getOrgRepositoriesWithPagination(
    org: String
  )(pagination: Pagination): F[OrgRepositoriesInfo] = {
    val cursor = pagination.cursor.map(c => s"""after:"$c"""").getOrElse("")
    val query =
      s"""query {organization(login: "$org"){repositories(first: ${pagination.size} $cursor){nodes{ name stargazers(first: 100){totalCount}}pageInfo{endCursor hasNextPage}}}}"""
    request(Query(query))
  }

  private def request[A: Decoder](query: Query): F[A] = {
    val request = Request[F](
      method = Method.POST,
      uri = ghEndpoint,
      headers = Headers.of(Header("Authorization", s"token $token"))
    ).withEntity(query)
    client.expect[A](request)(jsonOf[F, A])
  }
}

object GraphQL {
  val ghEndpoint = Uri.uri("https://api.github.com/graphql")

  final case class Query(query: String)

  def autoPaginate[F[_]: Sync, T <: PageInfo](call: Pagination => F[T]): F[List[T]] = for {
    first <- call(Pagination(100, None))
    rest <-
      if (first.hasNextPage)
        autoPaginate(Pagination(100, first.endCursor))(call).stream.compile.toList
      else
        Sync[F].pure(Nil)
  } yield first :: rest

  private def autoPaginate[F[_], T <: PageInfo](
    pagination: Pagination
  )(call: Pagination => F[T]): Pull[F, T, Unit] =
    pagination match {
      case Pagination(_, None) => Pull.done
      case _ =>
        Pull.eval(call(pagination)).flatMap { pageInfoLike =>
          Pull.output(Chunk(pageInfoLike)) >> (
            if (pageInfoLike.hasNextPage)
              autoPaginate(pagination.copy(cursor = pageInfoLike.endCursor))(call)
            else
              Pull.done
          )
        }
    }

  final case class Pagination(size: Int, cursor: Option[String])
}
