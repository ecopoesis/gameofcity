import sbt._
import Keys._
import com.scalapenos.sbt.prompt._
import SbtPrompt.autoImport._

object GameOfCityBuild extends Build {
  
  lazy val root = Project(
    id = "gameOfCity",
    base = file("."),
    settings = Project.defaultSettings ++
    Seq(
      organization := "org.miker.gameofcity",
      scalaVersion := "2.11.5",
      scalacOptions in (Compile,doc) ++= Seq("-unchecked", "-deprecation", "-feature", "-language:postfixOps", "-groups", "-implicits", "-target:jvm-1.8"),
      javacOptions ++= Seq(
        "-source","1.8",
        "-target","1.8"
      ),
      promptTheme := prompt
    )
  )

  val prompt = PromptTheme(List(
    text("[", fg(green)),
    currentProject(fg(blue)),
    text("]", fg(green)),
    text(" ", NoStyle),
    text("(", fg(green)),
    gitBranch(clean = fg(magenta), dirty = fg(magenta)),
    gitPromptlet {
      case Some(git) if git.status.dirty => StyledText(" *", fg(red))
      case _ => StyledText.Empty
    },
    text(")", fg(green)),
    text("> ", fg(green))
  ))
}
