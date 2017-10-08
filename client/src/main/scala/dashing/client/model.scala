package dashing.client

import scala.scalajs.js

object model {
  sealed trait Dashboard
  case object MainDash extends Dashboard

  @js.native
  trait Repo extends js.Object {
    def name: String
    def starsTimeline: js.Dictionary[Int]
    def stars: Int
  }
}