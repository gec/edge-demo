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

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.scalalogging.LazyLogging

object EdgeGui {

  val globalSocketMgr = new AtomicReference[SocketMgr](null)

  def main(args: Array[String]): Unit = {

    val mgr = new GuiSocketMgr
    globalSocketMgr.set(mgr)

    val server = new EdgeGuiServer(8080)
    server.run()
  }
}

class GuiSocketMgr extends SocketMgr with LazyLogging {
  def connected(socket: Socket): Unit = {
    logger.info("Got socket connected " + socket)
    socket.send(""" { "second" : "thing" } """)
  }

  def disconnected(socket: Socket): Unit = {
    logger.info("Got socket disconnected " + socket)
  }

  def handle(text: String, socket: Socket): Unit = {
    logger.info("Got socket test: " + text + ", " + socket)
  }
}

