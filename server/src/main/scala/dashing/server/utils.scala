package dashing.server

import java.time.YearMonth
import java.time.temporal.IsoFields

import cats.data.EitherT
import cats.effect.{Clock, Sync}
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.cats.effect.jvm.Implicits._
import io.chrisdavenport.mules.Cache
import org.http4s.Uri
import scalaj.http.HttpResponse

import scala.concurrent.duration.FiniteDuration
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

  def computeCumulativeMonthlyTimeline(timeline: List[String]): (Map[String, Double], Int) = (for {
    min <- timeline.minimumOption
    max <- timeline.maximumOption
    minMonth <- Try(YearMonth.parse(min)).toOption
    maxMonth <- Try(YearMonth.parse(max)).toOption
    successiveMonths = getSuccessiveMonths(minMonth, maxMonth).map(_.toString)
    counts = cumulativeCount(timeline)
    filledTL = fillCumulativeTimeline(successiveMonths, counts)
    dataPoints = filledTL._1.map(t => t._1 -> t._2.toDouble).toMap
  } yield (dataPoints, filledTL._2)).getOrElse((Map.empty, 0))

  def computeMonthlyTimeline(
    timeline: List[String],
    lookback: FiniteDuration
  ): Map[String, Double] = (for {
    nowYearMonth <- YearMonth.now.some
    lookbackYearMonth = YearMonth.now.minusMonths(lookback.toDays / 30)
    successiveMonths = getSuccessiveMonths(lookbackYearMonth, nowYearMonth)
    yearMonths <- timeline
      .map(ym => Try(YearMonth.parse(ym)).toOption)
      .sequence
    yearMonthsFiltered = yearMonths.filter(_.compareTo(lookbackYearMonth) >= 0)
    counts = count(yearMonths)
    filledTL = successiveMonths.map(e => e.toString -> counts.getOrElse(e, 0).toDouble).toMap
  } yield filledTL).getOrElse(Map.empty)

  def computeQuarterlyTimeline(
    timeline: List[String],
    lookback: FiniteDuration
  ): Map[String, Double] = (for {
    nowYearMonth <- YearMonth.now.some
    lookbackYearMonth = YearMonth.now.minusMonths(lookback.toDays / 30)
    successiveQuarters = getSuccessiveQuarters(lookbackYearMonth, nowYearMonth)
    yearMonths <- timeline
      .map(ym => Try(YearMonth.parse(ym)).toOption)
      .sequence
    quarters = yearMonths.map(getQuarter)
    counts = count(quarters)
    filledTL = successiveQuarters.map(e => e.toString -> counts.getOrElse(e, 0).toDouble).toMap
  } yield filledTL).getOrElse(Map.empty)

  def getSuccessiveQuarters(ym1: YearMonth, ym2: YearMonth): List[Quarter] =
    getSuccessiveMonths(ym1, ym2).map(getQuarter).distinct

  def getQuarter(ym: YearMonth): Quarter =
    Quarter(ym.getYear, ym.atEndOfMonth.get(IsoFields.QUARTER_OF_YEAR))

  def getSuccessiveMonths(ym1: YearMonth, ym2: YearMonth): List[YearMonth] =
    (if (ym1.isBefore(ym2)) {
      Stream.iterate(ym1)(_.plusMonths(1)).takeWhile(!_.isAfter(ym2))
    } else {
      Stream.iterate(ym2)(_.plusMonths(1)).takeWhile(!_.isAfter(ym1))
    }).toList

  def fillCumulativeTimeline[T](timeline: List[T], counts: Map[T, Int]): (List[(T, Int)], Int) = {
    val tl = timeline.foldLeft((List.empty[(T, Int)], 0)) { case ((acc, cnt), e) =>
      val c = counts.getOrElse(e, cnt)
      ((e, c) :: acc, c)
    }
    (tl._1.reverse, tl._2)
  }

  def cumulativeCount[T](list: List[T]): Map[T, Int] =
    list.foldLeft((Map.empty[T, Int], 0)) { case ((m, c), e) =>
      val cnt = m.getOrElse(e, c) + 1
      (m + (e -> cnt), cnt)
    }._1

  def count[T](list: List[T]): Map[T, Int] =
    list.foldLeft(Map.empty[T, Int]) { case (m, e) =>
      m + (e -> (m.getOrElse(e, 0) + 1))
    }

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

  def lookupOrInsert[F[_]: Sync : Clock, K, V, E](
    c: Cache[F, K, V]
  )(k: K, v: F[Either[E, V]]): F[Either[E, V]] = for {
    cached <- c.lookup(k)
    res <- cached match {
      case Some(value) => Sync[F].pure(Right(value))
      case _ => for {
        value <- v
        _ <- value match {
          case Right(va) => c.insert(k, va)
          case _ => Sync[F].pure(())
        }
      } yield value
    }
  } yield res

  // fucks up syntax highlighting so at the end of the file
  private val relPattern = """<(.*?)>; rel="(\w+)"""".r
}
