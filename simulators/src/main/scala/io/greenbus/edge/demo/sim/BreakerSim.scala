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

import io.greenbus.edge._
import io.greenbus.edge.api._
import io.greenbus.edge.data.{ ValueBool, ValueDouble, ValueString }
import io.greenbus.edge.demo.sim.EndpointBuilders.BreakerPublisher

object BreakerMapping {

  val equipmentType: String = "Breaker"

  val bkrPower = Path("DemandPower")
  val bkrVoltage = Path("PCCVoltage")
  val bkrCurrent = Path("PCCCurrent")
  //val bkrFrequency = Path("Frequency")

  val bkrStatus = Path("BreakerStatus")
  val events = Path("Events")

  val bkrTrip = Path("BreakerTrip")
  val bkrClose = Path("BreakerClose")

  val pointTypes = Seq(bkrPower, bkrStatus, bkrVoltage, bkrCurrent)
  val commandTypes = Seq(bkrTrip, bkrClose)
}

class BreakerSim(initial: Boolean, publisher: BreakerPublisher) extends SimulatorComponent {

  private var bkrStatus: Boolean = initial

  def status: Boolean = bkrStatus

  def updates(line: LineState, time: Long): Unit = {

    publisher.bkrStatus.update(ValueBool(bkrStatus), time)
    publisher.bkrPower.update(ValueDouble(line.power), time)
    publisher.bkrVoltage.update(ValueDouble(line.voltage), time)
    publisher.bkrCurrent.update(ValueDouble(line.current), time)

    publisher.buffer.flush()
  }

  publisher.bkrTripReceiver.bind(new flow.Responder[OutputParams, OutputResult] {
    def handle(obj: OutputParams, respond: (OutputResult) => Unit): Unit = {
      handleTrip()
      respond(OutputSuccess(None))
    }
  })

  publisher.bkrCloseReceiver.bind(new flow.Responder[OutputParams, OutputResult] {
    def handle(obj: OutputParams, respond: (OutputResult) => Unit): Unit = {
      handleClose()
      respond(OutputSuccess(None))
    }
  })

  def handleTrip(): Boolean = {
    publisher.events.update(Path(Seq("breaker", "trip")), ValueString("Breaker tripped"), System.currentTimeMillis())
    publisher.buffer.flush()
    bkrStatus = false
    true
  }
  def handleClose(): Boolean = {
    publisher.events.update(Path(Seq("breaker", "close")), ValueString("Breaker closed"), System.currentTimeMillis())
    publisher.buffer.flush()
    bkrStatus = true
    true
  }
}
