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
package io.greenbus.edge.demo.sim.dist

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge.api.{ EndpointId, Path, ProducerService }
import io.greenbus.edge.demo.sim.EndpointBuilders.PvPublisher
import io.greenbus.edge.demo.sim._
import io.greenbus.edge.peer.AmqpEdgeConnectionManager
import io.greenbus.edge.thread.CallMarshaller

object LonePvSimulator {
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

    def buildMgr(marshaller: CallMarshaller) = {
      new LonePvSimulatorMgr(ctx, producerServices)
    }

    val sim = system.actorOf(SimulatorActor.props(buildMgr))
  }
}
class LonePvSimulatorMgr(ctx: SimulatorContext, service: ProducerService) extends Tickable with LazyLogging {
  private val pvParams = PvParams.basic
  private val pv1 = new PvSim(pvParams, PvSim.PvState(1.0, fault = false), new PvPublisher(service.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "PV")))))

  def tick(): Unit = {
    val now = System.currentTimeMillis()
    pv1.updates(LineState(0.0, 0.0, 0.0), now)
  }
}
