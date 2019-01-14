package dashing.server

import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.effect.Sync
import fs2.{Chunk, Pull}
import io.circe.Decoder
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client

object graphql {
  val ghEndpoint = Uri.uri("https://api.github.com/graphql")

  def getPRs[F[_]](
    client: Client[F],
    owner: String,
    name: String
  )(pagination: Pagination): String = {
    val cursor = pagination.cursor.map("after:" + _).getOrElse("")
    val query =
      s"""query {repository(owner:\"$owner\",name:\"$name\"){pullRequests(states:[OPEN,CLOSED,MERGED]first:${pagination.size} $cursor){edges{node{author{login}createdAt}}pageInfo{endCursor hasNextPage}}}}"""
    query
    //client.expect[String](ghEndpoint)
  }

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
}
