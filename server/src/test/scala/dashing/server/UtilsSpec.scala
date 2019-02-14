package dashing.server

import java.time.YearMonth

import scala.concurrent.duration._

import cats.effect.{IO, Timer}
import cats.effect.laws.util.TestContext
import cats.syntax.option._
import io.chrisdavenport.mules.{Cache, TimeSpec}
import org.http4s.testing.{Http4sMatchers, IOMatchers}
import org.specs2.mutable.Specification

import model._

class UtilsSpec extends Specification with Http4sMatchers[IO] with IOMatchers {
  args(skipAll = sys.env.get("GITHUB_ACCESS_TOKEN").isEmpty)

  "utils.computeQuarterlyTimeline" should {
    "compute a quarterly count" in {
      utils.computeQuarterlyTimeline(List("2018-01", "2018-12"), 365.days) must_== Map(
        "2018 Q1" -> 1d,
        "2018 Q2" -> 0d,
        "2018 Q3" -> 0d,
        "2018 Q4" -> 1d,
        "2019 Q1" -> 0d,
      )
    }
    "cutoff based on the lookback" in {
      utils.computeQuarterlyTimeline(List("2018-01", "2018-05"), 1.day) must_==
        Map("2019 Q1" -> 0d)
    }
    "return an empty map if the timeline doesn't contain YYYY-MM" in {
      utils.computeQuarterlyTimeline(List("test", "test2"), 365.days) must_== Map.empty
    }
  }

  "utils.computeMonthlyTimeline" should {
    "compute a monthly count" in {
      utils.computeMonthlyTimeline(List("2018-01", "2018-05"), 365.days) must_== Map(
        "2018-02" -> 0d,
        "2018-03" -> 0d,
        "2018-04" -> 0d,
        "2018-05" -> 1d,
        "2018-06" -> 0d,
        "2018-07" -> 0d,
        "2018-08" -> 0d,
        "2018-09" -> 0d,
        "2018-10" -> 0d,
        "2018-11" -> 0d,
        "2018-12" -> 0d,
        "2019-01" -> 0d,
        "2019-02" -> 0d,
      )
    }
    "cutoff based on the lookback" in {
      utils.computeMonthlyTimeline(List("2018-01", "2018-05"), 1.day) must_== Map("2019-02" -> 0d)
    }
    "return an empty map if the timeline doesn't contain YYYY-MM" in {
      utils.computeMonthlyTimeline(List("test", "test2"), 365.days) must_== Map.empty
    }
  }

  "utils.computeCumulativeMonthlyTimeline" should {
    "compute a monthly cumulative count" in {
      utils.computeCumulativeMonthlyTimeline(List("2018-01", "2018-05")) must_== Map(
        "2018-01" -> 1d,
        "2018-02" -> 1d,
        "2018-03" -> 1d,
        "2018-04" -> 1d,
        "2018-05" -> 2d
      ) -> 2
    }
    "return an empty map if the timeline doesn't contain YYYY-MM" in {
      utils.computeCumulativeMonthlyTimeline(List("test", "test2")) must_== Map.empty -> 0d
    }
  }

  "utils.fillCumulativeTimeline" should {
    "fill the holes in a timeline" in {
      utils.fillCumulativeTimeline(List(1, 2, 3, 4, 5), Map(1 -> 4, 3 -> 1, 5 -> 2)) must_==
        List(1 -> 4, 2 -> 4, 3 -> 1, 4 -> 1, 5 -> 2) -> 2
    }
  }

  "utils.cumulativeCount" should {
    "do a cumulative count" in {
      utils.cumulativeCount(List(1, 1, 2, 3, 4)) must_== Map(1 -> 2, 2 -> 3, 3 -> 4, 4 -> 5)
    }
  }

  "utils.count" should {
    "do a count" in {
      utils.count(List(1, 1, 2, 3, 4)) must_== Map(1 -> 2, 2 -> 1, 3 -> 1, 4 -> 1)
    }
  }

  "utils.getSuccessiveMonths" should {
    "list all successive months between two dates" in {
      utils.getSuccessiveMonths(YearMonth.of(2014, 5), YearMonth.of(2014, 6)) must_==
        List(YearMonth.of(2014, 5), YearMonth.of(2014, 6))
    }
    "list all successive months if they aren't in chronological header" in {
      utils.getSuccessiveMonths(YearMonth.of(2014, 6), YearMonth.of(2014, 5)) must_==
        List(YearMonth.of(2014, 5), YearMonth.of(2014, 6))
    }
    "give back a list of one if both months are the same" in {
      val ym = YearMonth.of(2014, 6)
      utils.getSuccessiveMonths(ym, ym) must_== List(ym)
    }
  }

  "utils.getSuccessiveQuarters" should {
    "list the Quarters between two YearMonths" in {
      utils.getSuccessiveQuarters(YearMonth.of(2014, 6), YearMonth.of(2014, 7)) must_==
        List(Quarter(2014, 2), Quarter(2014, 3))
    }
    "list the Quarters between two YearMontsh if they are not in chronological order" in {
      utils.getSuccessiveQuarters(YearMonth.of(2014, 7), YearMonth.of(2014, 6)) must_==
        List(Quarter(2014, 2), Quarter(2014, 3))
    }
    "give back a list of one quarter if both YearMonths are the same" in {
      val ym = YearMonth.of(2014, 6)
      utils.getSuccessiveQuarters(ym, ym) must_== List(Quarter(2014, 2))
    }
  }

  "utils.getQuarter" should {
    "provide the Quarter from a YearMonth" in {
      utils.getQuarter(YearMonth.of(2014, 5)) must_== Quarter(2014, 2)
    }
  }

  "utils.lookupOrInsert" should {
    val ctx = TestContext()
    implicit val testTimer: Timer[IO] = ctx.timer[IO]
    "insert a value if it's not in a cache" in {
      val key = "s"
      val prsInfo = PullRequestsInfo(List.empty, None, true)
      val setup = for {
        cache <- Cache.createCache[IO, String, PageInfo](TimeSpec.unsafeFromDuration(1.second).some)
        v1 <- utils.lookupOrInsert(cache)(key, IO.pure(prsInfo))
        v2 <- cache.lookup(key)
      } yield (v1, v2)
      setup.unsafeRunSync must_== ((prsInfo, Some(prsInfo)))
    }
    "lookup a value if it's in a cache" in {
      val key = "s"
      val prsInfo = PullRequestsInfo(List.empty, None, true)
      val setup = for {
        cache <- Cache.createCache[IO, String, PageInfo](TimeSpec.unsafeFromDuration(1.second).some)
        _ <- cache.insert(key, prsInfo)
        v1 <- utils.lookupOrInsert(cache)(key, IO.pure(prsInfo))
        v2 <- cache.lookup(key)
      } yield (v1, v2)
      setup.unsafeRunSync must_== ((prsInfo, Some(prsInfo)))
    }
  }
}
