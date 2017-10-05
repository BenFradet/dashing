package dashing

package object shared {
  final case class Repo(repoName: String, stars: Map[String, Int], maxStars: Int)
}