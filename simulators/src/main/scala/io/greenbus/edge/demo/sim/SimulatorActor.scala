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

import java.util.UUID

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge.{CallMarshaller, PersistenceSessionId}
import io.greenbus.edge.client.EdgeConnection

import scala.concurrent.duration._

object SimulatorActor {

  case object DoSetup
  case class Connected(conn: EdgeConnection)
  case object Tick

  def props(): Props = {
    Props[SimulatorActor]()
  }

}
class SimulatorActor extends Actor with CallMarshalActor with LazyLogging {
  import SimulatorActor._

  private val sessionId = PersistenceSessionId(UUID.randomUUID(), 0)
  private val mgr = new SimulatorMgr(marshaller, LoadParser.fromFile("data/olney-2014-load-hourly.tsv"))
  private val publishers = mgr.publishers

  self ! DoSetup

  def receive = {
    case DoSetup => {
      mgr.setup()
      mgr.tick()
      scheduleMsg(1000, Tick)
    }
    case Tick => {
      mgr.tick()
      scheduleMsg(1000, Tick)
    }
    case Connected(conn) => {
      logger.info("Got edge connection")
      val futs = publishers.map { case (id, pub) => conn.connectPublisher(id, sessionId, pub) }
    }
    case MarshalledCall(f) => f()
  }


  protected def scheduleMsg(timeMs: Long, msg: AnyRef) {
    import context.dispatcher
    context.system.scheduler.scheduleOnce(
      Duration(timeMs, MILLISECONDS),
      self,
      msg)
  }
}

trait CallMarshalActor {
  self: Actor =>

  private val actorSelf = this.self

  case class MarshalledCall(f: () => Unit)

  protected def marshaller: CallMarshaller = new CallMarshaller {
    def marshal(f: => Unit) = actorSelf ! MarshalledCall(() => f)
  }

}
