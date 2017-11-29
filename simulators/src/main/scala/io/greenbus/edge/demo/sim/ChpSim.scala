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

import io.greenbus.edge.api._
import io.greenbus.edge.data.{ NumericConvertible, ValueBool, ValueDouble, ValueString }
import io.greenbus.edge.demo.sim.EndpointBuilders.ChpPublisher
import io.greenbus.edge.flow
import play.api.libs.json.Json

object ChpParams {
  import play.api.libs.json._
  implicit val writer = Json.writes[ChpParams]
  implicit val reader = Json.reads[ChpParams]

  def basic = ChpParams(256.0, 10.0)
}
case class ChpParams(powerCapacity: Double, rampRatekWps: Double)

object ChpMapping {

  val equipmentType: String = "CHP"

  val power = Path("OutputPower")
  val powerTarget = Path("OutTarget")
  val powerCapacity = Path("PowerCapacity")

  val params = Path("Params")
  val events = Path("Events")

  val setTarget = Path("SetOutTarget")

  val faultStatus = Path("FaultStatus")
  val faultEnable = Path("FaultEnable")
  val faultDisable = Path("FaultDisable")

  val pointTypes: Seq[Path] = Seq(power, powerTarget, powerCapacity, faultStatus)
  val commandTypes: Seq[Path] = Seq(setTarget, faultEnable, faultDisable)

  def defaultParams: ChpParams = ChpParams(256.0, 10.0)
}

object ChpSim {

  case class ChpState(currentValue: Double, outputTarget: Double, fault: Boolean)

  private def boundTargetRate(target: Double, params: ChpParams): Double = {
    if (target > params.powerCapacity) {
      params.powerCapacity
    } else if (target < 0.0) {
      0.0
    } else {
      target
    }
  }
}
import ChpSim._
class ChpSim(params: ChpParams, initialState: ChpState, publisher: ChpPublisher) extends SimulatorComponent {

  private var state = initialState

  def currentState: ChpState = state

  publisher.params.update(EndpointBuilders.jsonKeyValue(Json.toJson(params).toString()))

  def updates(line: LineState, time: Long): Unit = {
    publisher.power.update(ValueDouble(state.currentValue), time)
    publisher.powerTarget.update(ValueDouble(state.outputTarget), time)
    publisher.powerCapacity.update(ValueDouble(params.powerCapacity), time)
    publisher.faultStatus.update(ValueBool(state.fault), time)
    publisher.buffer.flush()
  }

  publisher.setTargetReceiver.bind(new flow.Responder[OutputParams, OutputResult] {
    def handle(obj: OutputParams, respond: (OutputResult) => Unit): Unit = {
      obj.outputValueOpt.foreach {
        case v: NumericConvertible => onTargetUpdate(v.toDouble)
        case _ =>
      }
      respond(OutputSuccess(None))
    }
  })

  publisher.faultEnableReceiver.bind(new flow.Responder[OutputParams, OutputResult] {
    def handle(obj: OutputParams, respond: (OutputResult) => Unit): Unit = {
      onFaultEnable()
      respond(OutputSuccess(None))
    }
  })

  publisher.faultDisableReceiver.bind(new flow.Responder[OutputParams, OutputResult] {
    def handle(obj: OutputParams, respond: (OutputResult) => Unit): Unit = {
      onFaultDisable()
      respond(OutputSuccess(None))
    }
  })

  def tick(deltaMs: Long): Unit = {
    if (!state.fault) {

      if (state.currentValue != state.outputTarget) {
        val deltaSeconds = deltaMs.toDouble / 1000.0

        val powerDeltaAbs = params.rampRatekWps * deltaSeconds

        val updatedValue = if (state.currentValue < state.outputTarget) {
          val nextValue = state.currentValue + powerDeltaAbs
          if (nextValue > state.outputTarget) state.outputTarget else nextValue
        } else {
          val nextValue = state.currentValue - powerDeltaAbs
          if (nextValue < state.outputTarget) state.outputTarget else nextValue
        }
        state = state.copy(currentValue = updatedValue)
      }

    } else {
      state = state.copy(currentValue = 0.0)
    }
  }

  private def onFaultEnable(): Boolean = {
    publisher.events.update(Path(Seq("fault", "occur")), ValueString("Fault occurred"), System.currentTimeMillis())
    publisher.buffer.flush()

    if (!state.fault) {
      state = state.copy(fault = true)
      true
    } else {
      false
    }
  }
  private def onFaultDisable(): Boolean = {
    publisher.events.update(Path(Seq("fault", "clear")), ValueString("Fault cleared"), System.currentTimeMillis())
    publisher.buffer.flush()

    if (state.fault) {
      state = state.copy(fault = false)
      true
    } else {
      false
    }
  }

  private def onTargetUpdate(rate: Double): Boolean = {
    publisher.events.update(Path(Seq("output", "target")), ValueString("Charge rate target updated: " + rate), System.currentTimeMillis())
    publisher.buffer.flush()

    val boundTarget = boundTargetRate(rate, params)
    if (boundTarget != state.outputTarget) {
      state = state.copy(outputTarget = boundTarget)
      true
    } else {
      false
    }
  }
}
