package au.csiro.data61.magda
import akka.actor.ActorSystem
import akka.stream.Materializer
import au.csiro.data61.magda.model.misc.DataSet

import scala.concurrent.{ExecutionContext, Future, Promise}

class MagdaRegistry(implicit val system: ActorSystem, implicit val ec: ExecutionContext, implicit val materializer: Materializer) extends Registry {
  override def add(source: String, dataSets: List[DataSet]): Future[Any] = {

    dataSets.foreach(ds => println(ds.title))
    Future(true)
  }

  override def needsReindexing(): Future[Boolean] = {
    Future(true)
  }
}
