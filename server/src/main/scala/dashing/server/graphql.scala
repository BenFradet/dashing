package dashing.server

import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.effect.Sync
import fs2.{Chunk, Pull}
import io.circe.Decoder
import io.circe.generic.auto._
import org.http4s.{Method, Request, Uri}
import org.http4s.client.dsl._
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client

class GraphQL[F[_]: Sync](client: Client[F]) extends Http4sClientDsl[F] {
  import GraphQL._

  def getPRs(owner: String, name: String)(pagination: Pagination): F[PullRequestsInfo] = {
    val cursor = pagination.cursor.map("after:" + _).getOrElse("")
    val query =
      s"""query {repository(owner:"$owner",name:"$name"){pullRequests(states:[OPEN,CLOSED,MERGED]first:${pagination.size} $cursor){edges{node{author{login}createdAt}}pageInfo{endCursor hasNextPage}}}}"""
    val request = Request[F](
      method = Method.POST,
      uri = ghEndpoint,
    ).withEntity(Query(query))
    client.expect[PullRequestsInfo](request)(jsonOf[F, PullRequestsInfo])
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
        author <- c.downField("node").downField("author").get[String]("login")
        timestamp <- c.downField("node").get[String]("createdAt")
      } yield AuthorAndTimestamp(author, timestamp)
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
      val downCursor = c
        .downField("data")
        .downField("repository")
        .downField("pullRequests")
      for {
        prs <- downCursor.get[List[AuthorAndTimestamp]]("edges")
        pageInfoCursor = downCursor.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield PullRequestsInfo(prs, endCursor, hasNextPage)
    }
  }
}
