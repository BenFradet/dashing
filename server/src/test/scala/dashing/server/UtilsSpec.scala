package dashing.server

import java.time.YearMonth

import scala.concurrent.duration._

import cats.effect.IO
import github4s.Github
import org.http4s.testing.{Http4sMatchers, IOMatchers}
import org.specs2.mutable.Specification

import model._

class UtilsSpec extends Specification with Http4sMatchers[IO] with IOMatchers {

  val gh = Github(sys.env.get("GITHUB_ACCESS_TOKEN"))

  "utils.getRepos" should {
    "retrieve the list of repos in an org" in {
      utils.getRepos[IO](gh, "igwp").map(_.size) must returnRight(4)
    }
    "be a left if the org doesn't exist" in {
      utils.getRepos[IO](gh, "notexist")
        .leftMap(_.getMessage.take(10)) must returnLeft("Failed inv")
    }
  }

  "utils.getOrgMembers" should {
    "retrieve the list of repos in an org" in {
      utils.getOrgMembers[IO](gh, "igwp").map(_.size) must returnRight(1)
    }
    "be a left if the org doesn't exist" in {
      utils.getOrgMembers[IO](gh, "notexist")
        .leftMap(_.getMessage.take(10)) must returnLeft("Failed inv")
    }
  }

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
        "2018-01" -> 1d,
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
      )
    }
    "cutoff based on the lookback" in {
      utils.computeMonthlyTimeline(List("2018-01", "2018-05"), 1.day) must_== Map("2019-01" -> 0d)
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

  "utils.getNrPages" should {
    "retrieve the correct number of pages" in {
      utils.getNrPages(Map("Link" ->
        Seq("""<http://github.com?per_page=100&page=20>; rel="last""""))) must_== Some(20)
    }
    "none if no Link headers" in {
      utils.getNrPages(Map("H" -> Seq("abc"))) must_== None
    }
    "none if the Link head doesn't match the regex" in {
      utils.getNrPages(Map("Link" ->
        Seq("""http://github.com?per_page=100&page=20; rel="last""""))) must_== None
    }
    "none if there is not last relation" in {
      utils.getNrPages(Map("Link" ->
        Seq("""<http://github.com?per_page=100&page=20>; rel="next""""))) must_== None
    }
    "none if the link is not a properly formed url" in {
      utils.getNrPages(Map("Link" -> Seq("""<abc>; rel="last""""))) must_== None
    }
    "none if the url doesn't contain a page query param" in {
      utils.getNrPages(Map("Link" ->
        Seq("""<http://github.com?per_page=100>; rel="last""""))) must_== None
    }
    "none if the page query param is not an int" in {
      utils.getNrPages(Map("Link" ->
        Seq("""<http://github.com?per_page=100&page=abc>; rel="last""""))) must_== None
    }
  }
}
