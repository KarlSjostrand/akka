import akka.{ AkkaBuild, Dependencies, Formatting }

AkkaBuild.defaultSettings

Formatting.formatSettings

publishArtifact in Compile := false

AkkaBuild.dontPublishSettings