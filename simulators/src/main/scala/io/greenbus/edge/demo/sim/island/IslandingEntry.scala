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
package io.greenbus.edge.demo.sim.island

import io.greenbus.edge.api.{ EndpointId, Path }
import io.greenbus.edge.peer.AmqpEdgeService

import scala.concurrent.ExecutionContext.Implicits.global

object IslandingEntry {

  def main(args: Array[String]): Unit = {

    val services = AmqpEdgeService.build("127.0.0.1", 50001, 10000)
    services.start()
    val producerServices = services.producer

    val params = ControlParams("Rankin", 250.0, 187.0, 50.0)

    val builder = producerServices.endpointBuilder(EndpointId(Path(Seq("Rankin", "App", "Islanding"))))

    val publisher = new IslandAppPublisher(builder, 30, 30)
    val subscriber = new IslandAppSubscriber(services.consumer.subscriptionClient)

    val app = new IslandingApp(services.eventThread, publisher, subscriber, services.consumer.queuingServiceClient, params)
  }
}
