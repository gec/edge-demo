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

