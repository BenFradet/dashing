package dashing.server

import java.time.YearMonth

import cats.effect.Sync

trait YearMonthClock[F[_]] {
  def now(): F[YearMonth]
}

object YearMonthClock {
  def create[F[_]: Sync]: YearMonthClock[F] =
    new YearMonthClock[F] {
      override def now(): F[YearMonth] =
        Sync[F].delay(YearMonth.now)
    }
}
