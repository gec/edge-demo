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
package io.greenbus.edge.demo.gui

import akka.actor.{ Actor, ActorRef, PoisonPill, Props }
import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge.{ ClientSubscriptionParams, Path }
import io.greenbus.edge.amqp.AmqpService
import io.greenbus.edge.client.{ EdgeConnection, EdgeConnectionImpl, EdgeSubscription }

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._

object PeerLinkMgr {

  def connect(service: AmqpService, host: String, port: Int)(implicit e: ExecutionContext): Future[EdgeConnection] = {
    val connFut = service.connect(host, port, 10000)
    connFut.flatMap(_.open()).map(cl => new EdgeConnectionImpl(service.eventLoop, cl))
  }

  def subscribeToSets(connection: EdgeConnection)(implicit e: ExecutionContext): Future[EdgeSubscription] = {
    connection.openSubscription(ClientSubscriptionParams(endpointSetPrefixes = Seq(Path(Seq()))))
  }

  case class SocketConnected(socket: Socket)
  case class SocketDisconnected(socket: Socket)
  case class SocketMessage(text: String, socket: Socket)

  def props: Props = {
    Props(classOf[PeerLinkMgr])
  }
}
class PeerLinkMgr extends Actor with LazyLogging {
  import PeerLinkMgr._

  private var linkMap = Map.empty[Socket, ActorRef]

  def receive = {
    case SocketConnected(sock) => {
      logger.info("Got socket connected " + sock)
      val linkActor = context.actorOf(PeerLink.props(sock))
      linkMap += (sock -> linkActor)
    }
    case SocketDisconnected(sock) => {
      logger.info("Got socket disconnected " + sock)
      linkMap.get(sock).foreach { ref =>
        ref ! PoisonPill
        linkMap -= sock
      }
    }
    case SocketMessage(text, sock) => {
      linkMap.get(sock).foreach(_ ! PeerLink.FromSocket(text))
    }
  }
}

object PeerLink {

  case object DoInit
  case class FromSocket(text: String)

  def props(socket: Socket): Props = {
    Props(classOf[PeerLink], socket)
  }
}
class PeerLink(socket: Socket) extends Actor with LazyLogging {
  import PeerLink._

  self ! DoInit

  def receive = {
    case DoInit =>
      socket.send(""" { "from" : "actor" } """)
    case FromSocket(text) =>
      logger.info("Got socket text: " + text + ", " + socket)
  }
}

class GuiSocketMgr(mgr: ActorRef) extends SocketMgr with LazyLogging {
  def connected(socket: Socket): Unit = {
    mgr ! PeerLinkMgr.SocketConnected(socket)
  }

  def disconnected(socket: Socket): Unit = {
    mgr ! PeerLinkMgr.SocketDisconnected(socket)
  }

  def handle(text: String, socket: Socket): Unit = {
    mgr ! PeerLinkMgr.SocketMessage(text, socket)
  }
}
