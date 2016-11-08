package au.csiro.data61.magda

import akka.actor.ActorSystem
import akka.stream.Materializer
import au.csiro.data61.magda.model.misc._

import scala.concurrent.{ExecutionContext, Future}

trait Registry {
  def initialize(): Future[Any]
  def add(source: String, dataSets: List[DataSet]): Future[Any]
  def needsReindexing(): Future[Boolean]
}
object Registry {
  // TODO: There's undoubtably a cleverer way to do this in scala
  var singletonProvider: Option[Registry] = None

  def apply()(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer): Registry = {
    singletonProvider = singletonProvider match {
      case Some(provider) => Some(provider)
      case None => Some(new MagdaRegistry())
    }

    singletonProvider.get
  }
}