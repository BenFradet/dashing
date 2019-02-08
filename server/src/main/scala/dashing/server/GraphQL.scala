package dashing.server

import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.effect.Sync
import fs2.{Chunk, Pull}
import io.circe.Decoder
import io.circe.generic.auto._
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.http4s.client.dsl._
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client

class GraphQL[F[_]: Sync](client: Client[F], token: String) extends Http4sClientDsl[F] {
  import GraphQL._

  def getPRs(owner: String, name: String): F[List[AuthorAndTimestamp]] = for {
    prs <- autoPaginate((p: Pagination) => getPRsWithPagination(owner, name)(p))
    list = prs.foldLeft(List.empty[AuthorAndTimestamp])((acc, e) => acc ++ e.pullRequests)
  } yield list

  private def getPRsWithPagination(
    owner: String, name: String
  )(pagination: Pagination): F[PullRequestsInfo] = {
    val cursor = pagination.cursor.map(c => s"""after:"$c"""").getOrElse("")
    val query =
      s"""query {repository(owner:"$owner",name:"$name"){pullRequests(states:[OPEN,CLOSED,MERGED]first:${pagination.size} $cursor){edges{node{author{login}createdAt}}pageInfo{endCursor hasNextPage}}}}"""
    request(Query(query))
  }

  def listStargazers(owner: String, name: String): F[List[String]] = for {
    prs <- autoPaginate((p: Pagination) => listStargazersWithPagination(owner, name)(p))
    list = prs.foldLeft(List.empty[String])((acc, e) => acc ++ e.starsTimeline)
  } yield list

  private def listStargazersWithPagination(
    owner: String, name: String
  )(pagination: Pagination): F[StarsInfo] = {
    val cursor = pagination.cursor.map(c => s"""after:"$c"""").getOrElse("")
    val query =
      s"""query {repository(owner:"$owner",name:"$name"){stargazers(first:${pagination.size} $cursor){edges{starredAt}pageInfo{endCursor hasNextPage}}}}"""
    request(Query(query))
  }

  def getOrgMembers(org: String): F[List[String]] = for {
    members <- autoPaginate((p: Pagination) => getOrgMembersWithPagination(org)(p))
    list = members.foldLeft(List.empty[String])((acc, e) => acc ++ e.members)
  } yield list

  private def getOrgMembersWithPagination(
    org: String
  )(pagination: Pagination): F[OrgMembersInfo] = {
    val cursor = pagination.cursor.map(c => s"""after:"$c"""").getOrElse("")
    val query =
      s"""query {organization(login: "$org"){membersWithRole(first: ${pagination.size} $cursor){nodes{login}pageInfo{endCursor hasNextPage}}}}"""
    request(Query(query))
  }

  private def request[A: Decoder](query: Query): F[A] = {
    val request = Request[F](
      method = Method.POST,
      uri = ghEndpoint,
      headers = Headers(Header("Authorization", s"token $token"))
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
        autoPaginate(Pagination(100, first.endCursor.some))(call).stream.compile.toList
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
              autoPaginate(pagination.copy(cursor = Some(pageInfoLike.endCursor)))(call)
            else
              Pull.done
          )
        }
    }

  final case class Pagination(size: Int, cursor: Option[String])

  final case class AuthorAndTimestamp(
    author: String,
    timestamp: String
  )
  object AuthorAndTimestamp {
    implicit val decoder: Decoder[AuthorAndTimestamp] = Decoder.instance { c =>
      for {
        author <- c.downField("author").get[String]("login")
        timestamp <- c.get[String]("createdAt")
      } yield AuthorAndTimestamp(author, timestamp)
    }.prepare(_.downField("node"))
  }

  final case class RepositoryAndStars(
    repository: String,
    firstHundredStars: Int
  )
  object RepositoryAndStars {
    implicit val decoder: Decoder[RepositoryAndStars] = Decoder.instance { c =>
      for {
        name <- c.get[String]("name")
        firstHundredStars <- c.downField("stargazers").get[Int]("totalCount")
      } yield RepositoryAndStars(name, firstHundredStars)
    }
  }

  sealed trait PageInfo {
    def endCursor: String
    def hasNextPage: Boolean
  }
  final case class PullRequestsInfo(
    pullRequests: List[AuthorAndTimestamp],
    endCursor: String,
    hasNextPage: Boolean
  ) extends PageInfo
  object PullRequestsInfo {
    implicit val decoder: Decoder[PullRequestsInfo] = Decoder.instance { c =>
      for {
        prs <- c.get[List[AuthorAndTimestamp]]("edges")
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield PullRequestsInfo(prs, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("repository").downField("pullRequests"))
  }
  final case class StarsInfo(
    starsTimeline: List[String],
    endCursor: String,
    hasNextPage: Boolean
  ) extends PageInfo
  object StarsInfo {
    implicit val decoder: Decoder[StarsInfo] = Decoder.instance { c =>
      for {
        rawStars <- c.get[List[Map[String, String]]]("edges")
        starsTimeline = rawStars.map(_.values).flatten
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield StarsInfo(starsTimeline, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("repository").downField("stargazers"))
  }
  final case class OrgMembersInfo(
    members: List[String],
    endCursor: String,
    hasNextPage: Boolean
  ) extends PageInfo
  object OrgMembersInfo {
    implicit val decoder: Decoder[OrgMembersInfo] = Decoder.instance { c =>
      for {
        rawMembers <- c.get[List[Map[String, String]]]("nodes")
        members = rawMembers.map(_.values).flatten
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield OrgMembersInfo(members, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("organization").downField("membersWithRole"))
  }
  final case class OrgRepositoriesInfo(
    repositoriesAndStars: List[RepositoryAndStars],
    endCursor: String,
    hasNextPage: Boolean
  ) extends PageInfo
  object OrgRepositoriesInfo {
    implicit val decoder: Decoder[OrgRepositoriesInfo] = Decoder.instance { c =>
      for {
        repositoriesAndStars <- c.get[List[RepositoryAndStars]]("nodes")
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield OrgRepositoriesInfo(repositoriesAndStars, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("organization").downField("repositories"))
  }
}
