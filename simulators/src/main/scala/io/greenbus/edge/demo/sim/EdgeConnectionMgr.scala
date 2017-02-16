/**
 * Copyright 2011-2017 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.greenbus.edge.demo.sim

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge.amqp.AmqpService
import io.greenbus.edge.amqp.impl.AmqpIoImpl
import io.greenbus.edge.client.{ EdgeConnection, EdgeConnectionImpl }

import scala.concurrent.{ ExecutionContext, Future }

/*

  def runPublisher(conn: EdgeConnection, eventThread: CallMarshaller): Unit = {

    val desc = buildDesc(1)
    val sessionId = PersistenceSessionId(UUID.randomUUID(), 0)
    val pub = new EndpointPublisherImpl(eventThread, testEndpointId, desc)

    val pubConnFut = conn.connectPublisher(testEndpointId, sessionId, pub)
    val publisherConnected = Await.result(pubConnFut, 5000.milliseconds)

    logger.info("Publisher connected?")

    pub.keyValueStreams.get(Path("key01")).foreach(_.push(ValueString("the updated value")))
    pub.timeSeriesStreams.get(Path("key02")).foreach(_.push(TimeSeriesSample(System.currentTimeMillis(), ValueDouble(55.3))))

    pub.flush()

    pub.outputReceiver.bind { batch =>
      batch.requests.foreach { req =>
        println(s"Got request ${req.key}, ${req.outputRequest}")
        req.resultAsync(OutputSuccess(None))
      }
    }

    System.in.read()
  }

  def main(args: Array[String]): Unit = {
    val service = new AmqpIoImpl()

    val connFut = service.connect("127.0.0.1", 50001, 10000)

    val conn = Await.result(connFut, 5000.milliseconds)

    val sessionFut = conn.open()

    val session = Await.result(sessionFut, 5000.milliseconds)

    val edgeConnection = new EdgeConnectionImpl(service.eventLoop, session)

    runPublisher(edgeConnection, service.eventLoop)
  }
 */
object EdgeConnectionMgr {

  case object DoConnect

  def connect(service: AmqpService, host: String, port: Int, timeoutMs: Long)(implicit e: ExecutionContext): Future[EdgeConnection] = {
    val connFut = service.connect(host, port, timeoutMs)
    val sessFut = connFut.flatMap(_.open())
    //sessFut.failed.foreach(ex => connFut.foreach(_.close()))
    sessFut.map(cl => new EdgeConnectionImpl(service.eventLoop, cl))
  }

}
class EdgeConnectionMgr(service: AmqpService) extends Actor with LazyLogging {
  import EdgeConnectionMgr._

  private var edgeConnOpt = Option.empty[EdgeConnection]

  def receive = {
    case DoConnect =>
  }
}

/*class PublisherMgr extends Actor with LazyLogging {

  def receive = {

  }
}*/
