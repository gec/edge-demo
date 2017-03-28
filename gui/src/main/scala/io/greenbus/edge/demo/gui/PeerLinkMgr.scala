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
import com.google.protobuf.util.JsonFormat
import com.typesafe.scalalogging.LazyLogging
/*import io.greenbus.edge._
import io.greenbus.edge.amqp.AmqpService
import io.greenbus.edge.client.{ EdgeConnection, EdgeConnectionImpl, EdgeOutputClient, EdgeSubscription }
import io.greenbus.edge.proto.{ ClientToServerMessage, ServerToClientMessage }
import io.greenbus.edge.proto.convert.Conversions
import io.greenbus.edge.proto

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.collection.JavaConversions._

object PeerLinkMgr {

  def connect(service: AmqpService, host: String, port: Int)(implicit e: ExecutionContext): Future[EdgeConnection] = {
    val connFut = service.connect(host, port, 10000)
    connFut.flatMap(_.open()).map(cl => new EdgeConnectionImpl(service.eventLoop, cl))
  }

  def subscribeToSets(connection: EdgeConnection)(implicit e: ExecutionContext): Future[EdgeSubscription] = {
    connection.openSubscription(ClientSubscriptionParams(endpointSetPrefixes = Seq(Path(Seq()))))
  }

  case class Connected(edgeConnection: EdgeConnection)

  case class SocketConnected(socket: Socket)
  case class SocketDisconnected(socket: Socket)
  case class SocketMessage(text: String, socket: Socket)

  def props: Props = {
    Props(classOf[PeerLinkMgr])
  }
}
class PeerLinkMgr extends Actor with LazyLogging {
  import PeerLinkMgr._

  private var edgeOpt = Option.empty[EdgeConnection]
  private var linkMap = Map.empty[Socket, ActorRef]

  def receive = {
    case Connected(edgeConnection) => {
      edgeOpt = Some(edgeConnection)
      linkMap.foreach {
        case (_, ref) => ref ! PeerLink.Connected(edgeConnection)
      }
    }
    case SocketConnected(sock) => {
      logger.info("Got socket connected " + sock)
      val linkActor = context.actorOf(PeerLink.props(sock))
      edgeOpt.foreach(c => linkActor ! PeerLink.Connected(c))
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

class PeerSubMgr(events: CallMarshaller, socket: Socket, printer: JsonFormat.Printer)(implicit e: ExecutionContext) extends LazyLogging {

  private var connectionOpt = Option.empty[EdgeConnection]
  private var paramsMap = Map.empty[Long, ClientSubscriptionParams]
  private var subsMap = Map.empty[Long, EdgeSubscription]

  private def doSubscription(conn: EdgeConnection, key: Long, params: ClientSubscriptionParams): Unit = {
    val subFut = conn.openSubscription(params)
    subFut.foreach { sub =>
      events.marshal {
        if (paramsMap.get(key).contains(params)) {
          subsMap += (key -> sub)
          sub.notifications.bind(not =>
            try {
              val msg = ServerToClientMessage.newBuilder()
                .putSubscriptionNotification(key, Conversions.toProto(not))
                .build()

              val json = printer.print(msg)
              socket.send(json)
            } catch {
              case ex: Throwable =>
                logger.error("Problem writing proto message: " + ex)
            })

        } else {
          sub.close()
        }
      }
    }
  }

  def add(key: Long, params: ClientSubscriptionParams): Unit = {
    paramsMap += (key -> params)
    connectionOpt.foreach { conn =>
      subsMap.get(key).foreach(_.close())
      doSubscription(conn, key, params)
    }
  }
  def remove(key: Long): Unit = {
    paramsMap -= key
    subsMap.get(key).foreach(_.close())
    subsMap -= key
  }

  def connected(conn: EdgeConnection): Unit = {
    subsMap.values.foreach(_.close())
    connectionOpt = Some(conn)
    paramsMap.foreach {
      case (key, params) => doSubscription(conn, key, params)
    }
  }

  def disconnected(): Unit = {
    connectionOpt = None
    subsMap.values.foreach(_.close())
  }

  def close(): Unit = {
    subsMap.values.foreach(_.close())
  }

}

class PeerOutputMgr(events: CallMarshaller, socket: Socket, printer: JsonFormat.Printer)(implicit e: ExecutionContext) extends LazyLogging {

  private var outputClientOpt = Option.empty[EdgeOutputClient]
  private var queue = Vector.empty[ClientOutputRequest]

  def enqueue(request: ClientOutputRequest): Unit = {
    outputClientOpt match {
      case None => queue = queue :+ request
      case Some(client) =>
        doRequest(client, request)
    }
  }

  def doRequest(client: EdgeOutputClient, request: ClientOutputRequest): Unit = {
    logger.debug("Issuing: " + request)
    val fut = client.issueOutput(request.key, request.value)
    fut.foreach { result =>
      try {
        val resp = proto.ClientOutputResponseMessage.newBuilder()
          .putResults(request.correlation, Conversions.toProto(result))
          .build()

        val msg = ServerToClientMessage.newBuilder()
          .setOutputResponse(resp)
          .build()

        val json = printer.print(msg)
        socket.send(json)
      } catch {
        case ex: Throwable =>
          logger.error("Problem writing proto message: " + ex)
      }
    }
    fut.failed.foreach { ex => logger.warn("Error sending client output request: " + ex) }
  }

  def connected(conn: EdgeConnection): Unit = {
    conn.openOutputClient().foreach { cl =>
      events.marshal {
        outputClientOpt.foreach(_.close())
        outputClientOpt = Some(cl)
        queue.foreach {
          case req => doRequest(cl, req)
        }
        queue = Vector.empty[ClientOutputRequest]
      }
    }
  }

  def close(): Unit = {
    outputClientOpt.foreach(_.close())
  }

}

object PeerLink {

  case object DoInit
  case class FromSocket(text: String)
  case class Connected(edgeConnection: EdgeConnection)

  def props(socket: Socket): Props = {
    Props(classOf[PeerLink], socket)
  }
}
class PeerLink(socket: Socket) extends Actor with CallMarshalActor with LazyLogging {
  import PeerLink._
  import context.dispatcher

  private val printer = JsonFormat.printer()
  private val subMgr = new PeerSubMgr(this.marshaller, socket, printer)
  private var outputMgr = new PeerOutputMgr(this.marshaller, socket, printer)

  private val parser = JsonFormat.parser()

  self ! DoInit

  def receive = {
    case DoInit =>

    case Connected(edgeConnection) =>
      subMgr.connected(edgeConnection)
      outputMgr.connected(edgeConnection)

    case FromSocket(text) => {
      logger.info("Got socket text: " + text + ", " + socket)

      val b = ClientToServerMessage.newBuilder()
      try {
        parser.merge(text, b)
        val proto = b.build()
        println(proto)

        proto.getSubscriptionsRemovedList.foreach(k => subMgr.remove(k))

        proto.getSubscriptionsAddedMap.foreach {
          case (key, paramsProto) =>
            Conversions.fromProto(paramsProto) match {
              case Left(err) => logger.error("Could not parse params proto: " + err)
              case Right(obj) => subMgr.add(key, obj)
            }
        }

        if (proto.hasOutputRequest) {
          val req = proto.getOutputRequest
          req.getRequestsList.foreach { r =>
            Conversions.fromProto(r) match {
              case Left(err) => logger.error("Could not parse output request proto: " + err)
              case Right(obj) => outputMgr.enqueue(obj)
            }
          }
        }

      } catch {
        case ex: Throwable =>
          logger.warn("Error parsing json: " + ex)
      }
    }
    case MarshalledCall(f) => f()
  }

  override def postStop(): Unit = {
    subMgr.close()
    outputMgr.close()
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
*/ 