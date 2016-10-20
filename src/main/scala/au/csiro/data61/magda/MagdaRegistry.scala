package au.csiro.data61.magda
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshal, PredefinedToEntityMarshallers}
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import au.csiro.data61.magda.model.misc.{DataSet, Protocols}
import au.csiro.data61.magda.util.Http._
import spray.json.JsObject
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.{ExecutionContext, Future, Promise}

case class RecordSection(
  id: String,
  name: String,
  data: Option[JsObject]
)

case class Record(
  id: String,
  name: String,
  sections: List[RecordSection]
)

case class Source(
  `type`: String,
  url: String
)

class MagdaRegistry(implicit val system: ActorSystem, implicit val ec: ExecutionContext, implicit val materializer: Materializer) extends Registry with Protocols {
  private val http = Http()

  private implicit val recordSectionFormat = jsonFormat3(RecordSection)
  private implicit val recordFormat = jsonFormat3(Record)
  private implicit val sourceFormat = jsonFormat2(Source)

  override def add(source: String, dataSets: List[DataSet]): Future[Any] = {

    Future.sequence[HttpResponse, List](dataSets.take(25).map(dataset => {
      val source = Source(
        `type` = "CKAN", // TODO
        url = "https://data.gov.au/api/3/action/package_show?id=" + dataset.identifier
      )

      val record = Record(
        // TODO: prefix the identifier, e.g. "dga:" + dataset.identifier
        id = dataset.identifier,
        name = dataset.title.getOrElse(dataset.identifier),
        sections = List(
          RecordSection(
            id = "source",
            name = "Source",
            data = Some(source.toJson.asJsObject())
          ),
          RecordSection(
            id = "summary",
            name = "Summary",
            data = Some(dataset.toJson.asJsObject())
          )
        )
      )

      Marshal(record).to[MessageEntity].flatMap(entity => {
        http.singleRequest(HttpRequest(
          // TODO: get the base URL from configuration
          // TODO: URI encode the ID
          uri = "http://localhost:9001/api/0.1/records/" + dataset.identifier,
          method = HttpMethods.PUT,
          entity = entity
        ))
      })
    }))
  }

  override def needsReindexing(): Future[Boolean] = {
    Future(true)
  }
}
