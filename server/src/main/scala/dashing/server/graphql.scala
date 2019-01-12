package dashing.server

import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.effect.Sync
import fs2.{Chunk, Pull}

object graphql {
  def getPRs(owner: String, name: String): String =
    s"""query {repository(owner:\"$owner\",name:\"$name\"){pullRequests(states:[OPEN,CLOSED,MERGED]first:100){edges{node{author{login}createdAt}}pageInfo{endCursor hasNextPage}}}}"""

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

  sealed trait PageInfo {
    def endCursor: String
    def hasNextPage: Boolean
  }
}
