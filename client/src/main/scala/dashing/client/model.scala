package dashing.client

object model {
  sealed trait Dashboard
  case object MainDash extends Dashboard
}