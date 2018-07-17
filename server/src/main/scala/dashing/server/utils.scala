package dashing.server

import java.time.YearMonth

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.cats.effect.jvm.Implicits._
import org.http4s.Uri
import scalaj.http.HttpResponse

import scala.util.Try

import model._

object utils {

  def getRepos[F[_]: Sync](gh: Github, org: String): EitherT[F, GHException, List[Repository]] =
    autoPaginate { p =>
      gh.repos.listOrgRepos(org, Some("sources"), Some(p)).exec[F, HttpResponse[String]]()
    }

  def getOrgMembers[F[_]: Sync](gh: Github, org: String): EitherT[F, GHException, List[String]] =
    for {
      ms <- autoPaginate { p =>
        gh.organizations.listMembers(org, pagination = Some(p)).exec[F, HttpResponse[String]]()
      }
      members = ms.map(_.login)
    } yield members

  def computeMonthlyTimeline(timeline: List[String]): (Timeline, Int) = (for {
    min <- timeline.minimumOption
    max <- timeline.maximumOption
    minMonth <- Try(YearMonth.parse(min)).toOption
    maxMonth <- Try(YearMonth.parse(max)).toOption
    successiveMonths = getSuccessiveMonths(minMonth, maxMonth).map(_.toString)
    counts = count(timeline)
    filledTL = fillTimeline(successiveMonths, counts)
    dataPoints = filledTL._1.map(t => DataPoint(t._1, t._2.toDouble))
  } yield (dataPoints, filledTL._2)).getOrElse((List.empty, 0))

  def getSuccessiveMonths(ym1: YearMonth, ym2: YearMonth): List[YearMonth] =
    (if (ym1.isBefore(ym2)) {
      Stream.iterate(ym1)(_.plusMonths(1)).takeWhile(!_.isAfter(ym2))
    } else {
      Stream.iterate(ym2)(_.plusMonths(1)).takeWhile(!_.isAfter(ym1))
    }).toList

  def fillTimeline[T](timeline: List[T], counts: Map[T, Int]): (List[(T, Int)], Int) = {
    val tl = timeline.foldLeft((List.empty[(T, Int)], 0)) { case ((acc, cnt), e) =>
      val c = counts.getOrElse(e, cnt)
      ((e, c) :: acc, c)
    }
    (tl._1.reverse, tl._2)
  }

  def count[T](list: List[T]): Map[T, Int] =
    list.foldLeft((Map.empty[T, Int], 0)) { case ((m, c), month) =>
      val cnt = m.getOrElse(month, c) + 1
      (m + (month -> cnt), cnt)
    }._1

  def autoPaginate[F[_]: Sync, T](
    call: Pagination => F[Either[GHException, GHResult[List[T]]]]
  ): EitherT[F, GHException, List[T]] = for {
    firstPage <- EitherT(call(Pagination(1, 100)))
    pages = (utils.getNrPages(firstPage.headers) match {
      case Some(n) if n >= 2 => (2 to n).toList
      case _ => Nil
    }).map(Pagination(_, 100))
    restPages <- EitherT(pages.traverse(call(_)).map(_.sequence))
  } yield firstPage.result ++ restPages.map(_.result).flatten

  private final case class Relation(name: String, url: String)
  def getNrPages(headers: Map[String, Seq[String]]): Option[Int] = for {
    links <- headers.map { case (k, v) => k.toLowerCase -> v }.get("link")
    h <- links.headOption
    relations = h.split(", ").flatMap {
      case relPattern(url, name) => Some(Relation(name, url))
      case _ => None
    }
    lastRelation <- relations.find(_.name == "last")
    uri <- Uri.fromString(lastRelation.url).toOption
    lastPage <- uri.params.get("page")
    nrPages <- Try(lastPage.toInt).toOption
  } yield nrPages

  // fucks up syntax highlighting so at the end of the file
  private val relPattern = """<(.*?)>; rel="(\w+)"""".r
}
