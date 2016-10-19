package au.csiro.data61.magda.api

import java.net.URL
import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.server.Rejection
import akka.http.scaladsl.server.RejectionHandler
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import au.csiro.data61.magda.external._
import ch.megard.akka.http.cors.CorsDirectives
import ch.megard.akka.http.cors.CorsSettings
import au.csiro.data61.magda.model.temporal._
import au.csiro.data61.magda.model.misc
import au.csiro.data61.magda.model.misc._

class Api(implicit val config: Config, implicit val system: ActorSystem,
          implicit val ec: ExecutionContext, implicit val materializer: Materializer) extends misc.Protocols with CorsDirectives {
  val logger = Logging(system, getClass)

  implicit def rejectionHandler = RejectionHandler.newBuilder()
    .handleAll[MethodRejection] { rejections ⇒
      val methods = rejections map (_.supported)
      lazy val names = methods map (_.name) mkString ", "

      cors() {
        options {
          complete(s"Supported methods : $names.")
        } ~
          complete(
            MethodNotAllowed,
            s"HTTP method not allowed, supported methods: $names!"
          )
      }
    }
    .result()

  val myExceptionHandler = ExceptionHandler {
    case e: Exception ⇒ {
      logger.error(e, "Exception encountered")

      cors() {
        complete(HttpResponse(InternalServerError, entity = "You are probably seeing this message because Alex messed up"))
      }
    }
  }

  implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
  val routes = cors() {
    handleExceptions(myExceptionHandler) {
      path("ping") { complete("OK") }
    }
  }

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}