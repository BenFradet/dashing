package dashing.server

import java.time.YearMonth
import java.time.temporal.IsoFields

import cats.Monad
import cats.data.OptionT
import cats.effect.{Clock, Sync}
import cats.implicits._
import io.chrisdavenport.mules.Cache

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.Try

import model._

object utils {

  /**
   * Compute an event timeline by month
   * @param timeline a list of month timestamps
   * @return a timeline which takes the form of a map of month to number of events and the
   * cumulative number of events
   */
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

  /**
   * Compute an event timeline by month
   * @param timeline a list of month timestamps
   * @param lookback how far to lookback when computing the timeline
   * @return a timeline which takes the form of a map of month to number of events in F
   */
  def computeMonthlyTimeline[F[_]: Monad](
    timeline: List[String],
    lookback: FiniteDuration
  )(implicit C: YearMonthClock[F]): F[Map[String, Double]] = (for {
    nowYearMonth <- OptionT.liftF(C.now)
    lookbackYearMonth <- OptionT.liftF(C.now).map(_.minusMonths(lookback.toDays / 30))
    successiveMonths = getSuccessiveMonths(lookbackYearMonth, nowYearMonth)
    yearMonths = timeline
      .map(ym => Try(YearMonth.parse(ym)).toOption)
      .sequence
    yms <- OptionT.fromOption[F](yearMonths)
    yearMonthsFiltered = yms.filter(_.compareTo(lookbackYearMonth) >= 0)
    counts = count(yms)
    filledTL = successiveMonths.map(e => e.toString -> counts.getOrElse(e, 0).toDouble).toMap
  } yield filledTL).value.map(_.getOrElse(Map.empty))

  /**
   * Compute an event timeline by quarter
   * @param timeline a list of quarter timestamps
   * @param lookback how far to lookback when computing the timeline
   * @return a timeline which takes the form of a map of month to number of events in F
   */
  def computeQuarterlyTimeline[F[_]: Monad](
    timeline: List[String],
    lookback: FiniteDuration
  )(implicit C: YearMonthClock[F]): F[Map[String, Double]] = (for {
    nowYearMonth <- OptionT.liftF(C.now)
    lookbackYearMonth <- OptionT.liftF(C.now).map(_.minusMonths(lookback.toDays / 30))
    successiveQuarters = getSuccessiveQuarters(lookbackYearMonth, nowYearMonth)
    yearMonths = timeline
      .map(ym => Try(YearMonth.parse(ym)).toOption)
      .sequence
    yms <- OptionT.fromOption[F](yearMonths)
    quarters = yms.map(getQuarter)
    counts = count(quarters)
    filledTL = successiveQuarters.map(e => e.toString -> counts.getOrElse(e, 0).toDouble).toMap
  } yield filledTL).value.map(_.getOrElse(Map.empty))

  /** Get all the quarters between two [[YearMonth]]s */
  def getSuccessiveQuarters(ym1: YearMonth, ym2: YearMonth): List[Quarter] =
    getSuccessiveMonths(ym1, ym2).map(getQuarter).distinct

  /** Extract the quarter from a [[YearMonth]] */
  def getQuarter(ym: YearMonth): Quarter =
    Quarter(ym.getYear, ym.atEndOfMonth.get(IsoFields.QUARTER_OF_YEAR))

  /** Get all the months between two [[YearMonth]]s */
  def getSuccessiveMonths(ym1: YearMonth, ym2: YearMonth): List[YearMonth] =
    (if (ym1.isBefore(ym2)) {
      Stream.iterate(ym1)(_.plusMonths(1)).takeWhile(!_.isAfter(ym2))
    } else {
      Stream.iterate(ym2)(_.plusMonths(1)).takeWhile(!_.isAfter(ym1))
    }).toList

  /** Form a full timeline from a sparse timeline by cumulating counts */
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

  /**
   * Lookup a data point in the cache, if it's not present insert it
   * @param cache to lookup or insert into
   * @param k key to lookup in the cache
   * @param v value to insert if missing in F
   * @return the value, whether looked up or inserted
   */
  def lookupOrInsert[F[_]: Sync : Clock, K, PI <: PageInfo: ClassTag](
    c: Cache[F, K, PageInfo]
  )(k: K, v: F[PI]): F[PI] = for {
    cached <- c.lookup(k)
    res <- cached match {
      case Some(value: PI) => Sync[F].pure(value)
      case _ => for {
        value <- v
        _ <- c.insert(k, value)
      } yield value
    }
  } yield res

}
