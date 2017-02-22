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

import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge._

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

class BreakerSim(initial: Boolean) extends SimulatorComponent with LazyLogging {

  private var bkrStatus: Boolean = initial

  private val queue = new SimEventQueue
  def eventQueue: EventQueue = queue

  def status: Boolean = bkrStatus

  def updates(line: LineState, time: Long): Seq[SimUpdate] = {
    Seq(
      TimeSeriesUpdate(BreakerMapping.bkrStatus, ValueBool(bkrStatus)),
      TimeSeriesUpdate(BreakerMapping.bkrPower, ValueDouble(line.power)),
      TimeSeriesUpdate(BreakerMapping.bkrVoltage, ValueDouble(line.voltage)),
      TimeSeriesUpdate(BreakerMapping.bkrCurrent, ValueDouble(line.current)))
  }

  def handlers: Map[Path, (Option[Value]) => Boolean] = {

    Map(
      (BreakerMapping.bkrTrip, { _: Option[Value] => handleTrip() }),
      (BreakerMapping.bkrClose, { _: Option[Value] => handleClose() }))
  }

  def handleTrip(): Boolean = {
    logger.info(s"Breaker trip")
    queue.enqueue(BreakerMapping.events, TopicEvent(Path(Seq("breaker", "trip")), Some(ValueString("Breaker tripped"))))
    bkrStatus = false
    true
  }
  def handleClose(): Boolean = {
    logger.info(s"Breaker close")
    queue.enqueue(BreakerMapping.events, TopicEvent(Path(Seq("breaker", "close")), Some(ValueString("Breaker closed"))))
    bkrStatus = true
    true
  }
}
