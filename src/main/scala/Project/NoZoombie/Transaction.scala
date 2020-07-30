package Project.NoZoombie

import java.util.concurrent.atomic.AtomicReference

import Project.Model
import Project.Model.{Product, ThreadInterrupt}
import Project.Properties.ProjectProperties
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{Consumer, Transactional}
import akka.kafka.{ProducerMessage, Subscriptions}
import akka.stream.scaladsl.{Merge, RestartSource, Sink, Source}
import akka.stream.{ActorAttributes, Supervision}
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.duration._
import scala.io.AnsiColor._

object Transaction extends App {

  implicit val system: ActorSystem = akka.actor.ActorSystem("system")


  /** LISTA PRODUKTÓW DO PRZESŁANIA */
  var listOfProduct = List[Model.Product](
    new Model.Product(1, "łosoś", 5, 10,0),
    new Model.Product(2, "banan", 2, 3,0),
    new Model.Product(3, "woda", 3, 2,0),
    new Model.Product(4, "chleb", 1, 4.60,0),
    new Model.Product(5, "jogurt", 1, 3.20,0),
    new Model.Product(6, "ryż", 3, 15,0),
    new Model.Product(7, "baton", 1, 15,0),
    new Model.Product(8, "cukier", 1, 2.5,0),
    new Model.Product(9, "makaron", 4, 1.3,0),
    new Model.Product(10, "ser", 3, 25,0),
    new Model.Product(11, "łosoś", 5, 10,0),
    new Model.Product(12, "banan", 2, 3,0),
    new Model.Product(13, "woda", 3, 2,0),
    new Model.Product(14, "chleb", 1, 4.60,0),
    new Model.Product(15, "jogurt", 1, 3.20,0),
    new Model.Product(16, "ryż", 3, 15,0),
    new Model.Product(17, "baton", 1, 15,0),
    new Model.Product(18, "cukier", 1, 2.5,0),
    new Model.Product(19, "makaron", 4, 1.3,0),
    new Model.Product(20, "ser", 3, 25,0),
    new Model.Product(21, "łosoś", 5, 10,0),
    new Model.Product(22, "banan", 2, 3,0),
    new Model.Product(23, "woda", 3, 2,0),
    new Model.Product(24, "chleb", 1, 4.60,0),
    new Model.Product(25, "jogurt", 1, 3.20,0),
    new Model.Product(26, "ryż", 3, 15,0),
    new Model.Product(27, "baton", 1, 15,0),
    new Model.Product(28, "cukier", 1, 2.5,0),
    new Model.Product(29, "makaron", 4, 1.3,0),
    new Model.Product(30, "banan", 2, 3,0))


  /** WĄTEK ODPOWIADAJĄCY ZA RZUCANIE BŁĘDU PODCZAS TRANSAKCJI */
  val thread = new ThreadInterrupt()
  new Thread(thread).start()


  /** PRODUCENT WYSYŁAJĄCY PRODUKTY DO TRANSAKCJI */
  val producer = Source(listOfProduct)
    .throttle(1, 1.second)
    .map { product =>
      println(f"${WHITE}Send -> ID: ${product.id}%-6s| name: ${product.name}%-9s| amount: ${product.amount}%-3s| price: ${product.price}%-6s${RESET}")
      ProducerMessage.single(
        new ProducerRecord[String, String]("producerToTransactionNoZoombie",
          product.toString)
      )
    }


  /** TRANSAKCJA PRZESYŁAJĄCA DANE DO KONSUMENTA */
  val innerControl = new AtomicReference[Control](Consumer.NoopControl)
  val transaction = Transactional
    .source(ProjectProperties.consumerSettings_1, Subscriptions.topics("producerToTransactionNoZoombie"))
    .map { productInstance =>
      val product = productInstance.record.value()
      val totalPrice = BigDecimal(x).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

      println(f"ReSend -> ID: ${valueArray(0)}%-4s| name: ${valueArray(1)}%-9s|" +
        f" total price: ${totalPrice}%-6s")

      if (thread.flag) {
        println(s"${RED}Error was thrown. Every change within from last commit will be aborted.${RESET}")
        throw new Throwable()
      }

      ProducerMessage.single(new ProducerRecord("transactionToConsumerNoZoombie", msg.record.key, msg.record.value),
        msg.partitionOffset)
    }
    .mapMaterializedValue(c => innerControl.set(c))
    .via(Transactional.flow(ProjectProperties.producerTransaction30SecondsSettings, "transaction1"))


  /** KONSUMENT */
  var finalPrice = 0.0
  val consumer = {
    Consumer
      .plainSource(ProjectProperties.consumerSettings_2, Subscriptions.topics("transactionToConsumerNoZoombie"))
      .map((msg) => {
        val valueArray = msg.value().split(",")
        val x = valueArray(2).toDouble * valueArray(3).toDouble
        val price = BigDecimal(x).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

        println(f"${CYAN}Receive <- ID: ${valueArray(0)}%-3s| name: ${valueArray(1)}%-9s|" +
          f" amount: ${valueArray(2)}%-3s| totalPrice: ${valueArray(3)}%-6s${RESET}")

        finalPrice += price
        if (valueArray(0).trim.toInt == 30) {
          println(s"\n${RED}FINAL PRICE: $finalPrice${RESET}")
        }
      })
  }


  /** WYWOŁANIE SOURC-ÓW JEDNOCZEŚNIE WRAZ Z ICH RESTARTEM W RAZIE BŁĘDU */
  val decider: Supervision.Decider = {
    case e: Exception => {
      println(s"${RED}Exception handled, recovering stream: ${e.getMessage} ${RESET}")
      Supervision.Stop
    }
    case _ => Supervision.Stop
  }

  val totalSource = Source.combine(producer, transaction, consumer)(Merge(_))

  RestartSource.onFailuresWithBackoff(
    minBackoff = 1.seconds,
    maxBackoff = 5.seconds,
    randomFactor = 0.2
  ) { () =>
    totalSource
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
  }
    .runWith(Sink.ignore)
}