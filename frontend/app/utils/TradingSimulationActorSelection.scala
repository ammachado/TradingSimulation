package utils

import com.typesafe.config.{Config, ConfigFactory}
import akka.actor.{ActorContext, ActorSelection}

class TradingSimulationActorSelection(context: ActorContext, actorSelection: String = "/user/*") {
  val config: Config = ConfigFactory.load()
  val name: String = config.getString("akka.backend.systemName")
  val hostname: String = config.getString("akka.backend.hostname")
  val port: String = config.getString("akka.backend.port")
  val actors: ActorSelection = context.actorSelection("akka.tcp://" + name + "@" + hostname + ":" + port + actorSelection)
  
  def get: ActorSelection = actors
}