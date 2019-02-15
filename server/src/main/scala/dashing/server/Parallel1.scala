package dashing.server

import cats.Parallel

// taken from https://github.com/typelevel/cats/issues/2233

sealed trait Parallel1[F[_]] extends Serializable {
  type A[_]
  def parallel: Parallel[F, A]
}

object Parallel1 {
  type Aux[F[_], A0[_]] = Parallel1[F] { type A[x] = A0[x] }

  implicit def parallel1Instance[F[_], A0[_]](implicit P: Parallel[F, A0]): Parallel1.Aux[F, A0] =
    new Parallel1[F] {
      type A[x] = A0[x]
      val parallel = P
    }

  implicit def parallelFromParallel1[F[_]](implicit P: Parallel1[F]): Parallel[F, P.A] =
    P.parallel
}
