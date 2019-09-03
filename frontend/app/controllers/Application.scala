package controllers

import actors.{GlobalOhlc, MessageToJson, TraderParameters}
import akka.actor.{ActorSystem, Props}
import akka.stream.Materializer
import ch.epfl.ts.data.{Quote, Register, Transaction}
import ch.epfl.ts.evaluation.EvaluationReport
import com.typesafe.config.{Config, ConfigFactory}
import javax.inject.Inject
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import scala.reflect.ClassTag

class Application @Inject()(cc: ControllerComponents)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {
  val config: Config = ConfigFactory.load()

  def index: Action[AnyContent] = Action {
    Ok(views.html.index("hello"))
  }

  def quote: WebSocket = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef { out =>
      Props(classOf[MessageToJson[Quote]], out,
        config.getString("akka.backend.fetchersActorSelection"), implicitly[ClassTag[Quote]])
    }
  }

  def globalOhlc: WebSocket = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef { out =>
      GlobalOhlc.props(out)
    }
  }

  def transaction: WebSocket = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef { out =>
      Props(classOf[MessageToJson[Transaction]], out,
        config.getString("akka.backend.marketsActorSelection"), implicitly[ClassTag[Transaction]])
    }
  }

  def traderRegistration: WebSocket = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef { out =>
      Props(classOf[MessageToJson[Register]], out,
        config.getString("akka.backend.tradersActorSelection"), implicitly[ClassTag[Register]])
    }
  }

  def traderParameters: WebSocket = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef {
      out => Props(classOf[TraderParameters], out)
    }
  }

  def evaluationReport: WebSocket = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef { out =>
      Props(classOf[MessageToJson[EvaluationReport]], out,
        config.getString("akka.backend.evaluatorsActorSelection"), implicitly[ClassTag[EvaluationReport]])
    }
  }
}