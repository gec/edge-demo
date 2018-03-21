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
import io.greenbus.edge.data.{ ValueBool, ValueDouble, ValueString, ValueUInt32 }
import io.greenbus.edge.demo.sim.EndpointBuilders.PvPublisher
import io.greenbus.edge.flow
import play.api.libs.json.Json

import scala.util.Random

object EllipseParams {
  import play.api.libs.json._
  implicit val writer = Json.writes[EllipseParams]
  implicit val reader = Json.reads[EllipseParams]
}

case class EllipseParams(a: Double, b: Double, dx: Double)

object PvParams {
  import play.api.libs.json._
  implicit val writer = Json.writes[PvParams]
  implicit val reader = Json.reads[PvParams]

  def basic: PvParams = PvParams(2000, 5000, EllipseParams(0.035, 200, -12))
}

case class PvParams(tickMs: Long, conditionIntervalMs: Long, curve: EllipseParams)

object PvMapping {
  val equipmentType = "PV"

  val pvOutputPower = Path("OutputPower")
  val pvCapacity = Path("PowerCapacity")
  val faultStatus = Path("FaultStatus")

  val params = Path("Params")
  val events = Path("Events")

  val faultEnable = Path("FaultEnable")
  val faultDisable = Path("FaultDisable")

  val sunCondition = Path("Conditions")
  val setSunCondition = Path("SetConditions")

  val pointTypes: Seq[Path] = Seq(pvOutputPower, pvCapacity, faultStatus, sunCondition)
  val outputTypes: Seq[Path] = Seq(faultEnable, faultDisable, setSunCondition)
}

object PvSim {
  import Utils._

  def powerAtTime(now: Long, params: EllipseParams): Double = {

    val nowInDailyHours = (now - getStartOfCurrentDay()).toDouble / millisecondsInAnHour.toDouble

    val x = nowInDailyHours + params.dx

    val powerSquared = (params.b * params.b) - (x * x) / (params.a * params.a)

    if (powerSquared > 0) Math.sqrt(powerSquared) else 0.0
  }

  sealed trait Mode
  case object Sunny extends Mode
  case object Cloudy extends Mode
  case object PartlyCloudy extends Mode

  case class ConditionState(mode: Mode, lastIntervalStart: Long)
  case class PvState(cloudReduction: Double, fault: Boolean, conditionState: ConditionState)
}

import PvSim._
class PvSim(params: PvParams, initialState: PvState, publisher: PvPublisher) extends SimulatorComponent {

  private var state = initialState
  private val r = new Random(System.currentTimeMillis())

  def currentState: PvState = state

  publisher.params.update(EndpointBuilders.jsonKeyValue(Json.toJson(params).toString()))

  def updates(line: LineState, time: Long): Unit = {
    publisher.pvOutputPower.update(ValueDouble(atTime(time)), time)
    publisher.pvCapacity.update(ValueDouble(params.curve.b), time)
    publisher.faultStatus.update(ValueBool(state.fault), time)
    publisher.sunConditions.update(ValueUInt32(0), time)
    publisher.buffer.flush()
  }

  publisher.sunConditionsOutputReceiver.bind(new flow.Responder[OutputParams, OutputResult] {
    def handle(obj: OutputParams, respond: (OutputResult) => Unit): Unit = {
      obj.outputValueOpt.foreach { v =>
        Utils.valueAsInt(v).foreach(mode => onModeUpdate(mode.toInt))
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

  private def onModeUpdate(mode: Int): Unit = {
    mode match {
      case 0 =>
        state = state.copy(cloudReduction = 1.0, conditionState = state.conditionState.copy(mode = Sunny))
      case 1 =>
        state = state.copy(conditionState = state.conditionState.copy(mode = Cloudy))
      case 2 =>
        state = state.copy(conditionState = state.conditionState.copy(mode = PartlyCloudy))
      case _ =>
    }
  }

  private def checkReduction(now: Long): Unit = {
    state.conditionState.mode match {
      case Sunny => None
      case Cloudy =>
        if ((now - state.conditionState.lastIntervalStart) > params.conditionIntervalMs) {
          val reduction = (r.nextDouble() * 0.2) + 0.5
          val intervalStart = (now / params.conditionIntervalMs) * params.conditionIntervalMs
          state = state.copy(cloudReduction = reduction, conditionState = state.conditionState.copy(lastIntervalStart = intervalStart))
        }
      case PartlyCloudy =>
        if ((now - state.conditionState.lastIntervalStart) > params.conditionIntervalMs) {
          val range = r.nextDouble() * 2.0 - 1.0
          val reduction = if (range < 0) {
            range * 0.10 + 0.6
          } else {
            range * 0.3 + 0.6
          }
          val intervalStart = (now / params.conditionIntervalMs) * params.conditionIntervalMs
          state = state.copy(cloudReduction = reduction, conditionState = state.conditionState.copy(lastIntervalStart = intervalStart))
        }
    }
  }

  def atTime(now: Long): Double = {
    if (!state.fault) {
      checkReduction(now)
      valueWithoutReduction(now) * state.cloudReduction
    } else {
      0.0
    }
  }

  def updateReduction(reduction: Double): Unit = {
    state = state.copy(cloudReduction = reduction)
  }

  def valueWithoutReduction(time: Long): Double = {
    PvSim.powerAtTime(time, params.curve)
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
}