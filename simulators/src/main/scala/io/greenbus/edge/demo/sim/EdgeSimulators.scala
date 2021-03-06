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

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.greenbus.edge.peer.AmqpEdgeConnectionManager

import scala.concurrent.Await
import scala.concurrent.duration._

object EdgeSimulators {

  def main(args: Array[String]): Unit = {

    val rootConfig = ConfigFactory.load()
    val slf4jConfig = ConfigFactory.parseString("""akka { loggers = ["akka.event.slf4j.Slf4jLogger"] }""")
    val akkaConfig = slf4jConfig.withFallback(rootConfig)
    val system = ActorSystem("brokerTest", akkaConfig)

    val host = Option(System.getProperty("host")).getOrElse("127.0.0.1")
    val port = Option(System.getProperty("port")).map(Integer.parseInt).getOrElse(55672)

    val ctx = SimulatorContext(
      equipmentPrefix = Seq("Durham", "MGRID"))

    import system.dispatcher

    val services = AmqpEdgeConnectionManager.build(host, port, 10000)
    services.start()
    val producerServices = services.bindProducerServices()

    val sim = system.actorOf(SimulatorActor.props(ctx, producerServices))

  }
}

case class SimulatorContext(equipmentPrefix: Seq[String])