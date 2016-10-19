
package au.csiro.data61.magda

import akka.actor.{ Actor, ActorLogging, ActorSystem, DeadLetter, Props }
import akka.event.Logging
import akka.stream.ActorMaterializer
import au.csiro.data61.magda.api.Api
import au.csiro.data61.magda.crawler.Supervisor
import au.csiro.data61.magda.external.InterfaceConfig
import com.typesafe.config.{ ConfigObject, ConfigValue }

import scala.collection.JavaConversions._

object MagdaApp extends App {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val config = AppConfig.conf

  val logger = Logging(system, getClass)

  logger.info("Starting MAGDA CKAN Crawler with env {}", AppConfig.env)

  val listener = system.actorOf(Props(classOf[Listener]))
  system.eventStream.subscribe(listener, classOf[DeadLetter])

  val interfaceConfigs = config.getConfig("indexedServices").root().map {
    case (name: String, serviceConfig: ConfigValue) =>
      InterfaceConfig(serviceConfig.asInstanceOf[ConfigObject].toConfig)
  }.toSeq

  val supervisor = system.actorOf(Props(new Supervisor(system, config, interfaceConfigs)))

  // Index erryday 
  //  system.scheduler.schedule(0 millis, 1 days, supervisor, Start(List((ExternalInterfaceType.CKAN, new URL(config.getString("services.dga-api.baseUrl"))))))

  val api = new Api()
}

class Listener extends Actor with ActorLogging {
  def receive = {
    case d: DeadLetter => log.debug(d.message.toString())
  }
}