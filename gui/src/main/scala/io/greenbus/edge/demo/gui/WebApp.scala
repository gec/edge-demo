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

import com.typesafe.scalalogging.LazyLogging
import org.eclipse.jetty.server.{ Server, ServerConnector }
import org.eclipse.jetty.servlet.{ DefaultServlet, ServletContextHandler, ServletHolder }
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.websocket.api.{ Session, WebSocketAdapter }
import org.eclipse.jetty.websocket.servlet.{ WebSocketServlet, WebSocketServletFactory }

object WebApp {

  def main(args: Array[String]): Unit = {

    val server = new Server
    val connector = new ServerConnector(server)
    connector.setPort(8080)
    server.addConnector(connector)

    val ctx = new ServletContextHandler(ServletContextHandler.SESSIONS)
    ctx.setBaseResource(Resource.newClassPathResource("/web"))
    //ctx.setResourceBase("web/")
    ctx.setContextPath("/")
    server.setHandler(ctx)

    //    val holderStatic = new ServletHolder("static-home", classOf[DefaultServlet])
    //    holderStatic.setInitParameter("resourceBase", homePath)
    //    holderStatic.setInitParameter("dirAllowed", "true")
    //    holderStatic.setInitParameter("pathInfoOnly", "true")
    //    ctx.addServlet(holderStatic, "/home/*")

    val holder = new ServletHolder("edgegui", classOf[EdgeServlet])
    holder.setInitParameter("dirAllowed", "true")
    holder.setInitParameter("pathInfoOnly", "true")
    ctx.addServlet(holder, "/socket/*")

    val holderPwd = new ServletHolder("default", classOf[DefaultServlet])
    holderPwd.setInitParameter("dirAllowed", "true")
    ctx.addServlet(holderPwd, "/")

    try {
      server.start()
      server.dump(System.err)
      server.join()
    } catch {
      case ex: Throwable => ex.printStackTrace()
    }
  }
}

class EdgeServlet extends WebSocketServlet {
  def configure(webSocketServletFactory: WebSocketServletFactory): Unit = {
    webSocketServletFactory.register(classOf[EdgeSocket])
  }
}

class EdgeSocket extends WebSocketAdapter with LazyLogging {

  override def onWebSocketBinary(payload: Array[Byte], offset: Int, len: Int): Unit = {
    logger.info("Got web socket binary")
    super.onWebSocketBinary(payload, offset, len)
  }

  override def onWebSocketConnect(sess: Session): Unit = {
    logger.info("Got web socket connect")
    try {
      sess.getRemote.sendString(""" {"blah" : "meh"} """)
    } catch {
      case ex: Throwable => logger.warn("Problem writing to socket: " + ex)
    }
    super.onWebSocketConnect(sess)

  }

  override def onWebSocketError(cause: Throwable): Unit = {
    logger.info("Got web socket error: " + cause)
    super.onWebSocketError(cause)
  }

  override def onWebSocketText(message: String): Unit = {
    logger.info("Got web socket text: " + message)
    super.onWebSocketText(message)
  }

  override def onWebSocketClose(statusCode: Int, reason: String): Unit = {
    logger.info("Got web socket close")
    super.onWebSocketClose(statusCode, reason)
  }
}

