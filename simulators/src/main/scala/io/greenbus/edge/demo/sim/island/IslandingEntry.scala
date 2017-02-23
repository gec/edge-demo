package io.greenbus.edge.demo.sim.island

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.greenbus.edge.{NamedEndpointId, Path}
import io.greenbus.edge.amqp.impl.AmqpIoImpl
import io.greenbus.edge.client.EdgeConnectionImpl

import scala.concurrent.Await
import scala.concurrent.duration._

object IslandingEntry {

  def main(args: Array[String]): Unit = {
    val service = new AmqpIoImpl()

    val connFut = service.connect("127.0.0.1", 50001, 10000)

    val conn = Await.result(connFut, 5000.milliseconds)

    val sessionFut = conn.open()

    val session = Await.result(sessionFut, 5000.milliseconds)

    val edgeConnection = new EdgeConnectionImpl(service.eventLoop, session)

    val rootConfig = ConfigFactory.load()
    val slf4jConfig = ConfigFactory.parseString("""akka { loggers = ["akka.event.slf4j.Slf4jLogger"] }""")
    val akkaConfig = slf4jConfig.withFallback(rootConfig)
    val system = ActorSystem("brokerTest", akkaConfig)

    val params = ControlParams("Rankin", 250.0, 187.0, 50.0)
    val sim = system.actorOf(IslandingApp.props(edgeConnection, NamedEndpointId(Path(Seq("Rankin", "App", "Islanding"))), params))
  }
}
