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

object EssMapping {

  val equipmentType: String = "ESS"

  val percentSoc = Path("SOC")
  val mode = Path("ESSMode")
  val socMax = Path("SOC_Max")
  val socMin = Path("SOC_Min")
  val chargeDischargeRate = Path("OutputPower")
  val chargeRateMax = Path("ChargeRateMax")
  val dischargeRateMax = Path("DischargeRateMax")
  val capacity = Path("EnergyCapacity")
  val efficiency = Path("Efficiency")
  val chargeRateTarget = Path("ChargeRateTarget")
  val faultStatus = Path("FaultStatus")

  val pointTypes = Seq(percentSoc, socMax, socMin, chargeDischargeRate, chargeRateMax, dischargeRateMax, capacity, efficiency, chargeRateTarget, mode, faultStatus)

  val setChargeRate = Path("SetChargeRateTarget")
  val setBatteryMode = Path("SetMode")
  val faultEnable = Path("FaultEnable")
  val faultDisable = Path("FaultDisable")
  val commandTypes = Seq(setChargeRate, setBatteryMode, faultEnable, faultDisable)

}

object EssParams {
  import play.api.libs.json._
  implicit val writer = Json.writes[EssParams]
  implicit val reader = Json.reads[EssParams]

  def basic = EssParams(100.0, 100.0, 0.0, 50.0, 50.0, 0.8)
}
case class EssParams(
    capacity: Double,
    socMax: Double,
    socMin: Double,
    maxDischargeRate: Double,
    maxChargeRate: Double,
    efficiency: Double) {

  def maxEnergy = (socMax / 100.0) * capacity
  def minEnergy = (socMin / 100.0) * capacity
}

object EssSim {

  object EssMode {
    def apply(v: Int): EssMode = {
      v match {
        case 0 => Constant
        case 1 => Smoothing
        case 2 => GridForming
        case _ => throw new IllegalArgumentException("Invalid ESS mode value")
      }
    }

    def parse(v: Int): Option[EssMode] = {
      v match {
        case 0 => Some(Constant)
        case 1 => Some(Smoothing)
        case 2 => Some(GridForming)
        case _ => None
      }
    }
  }
  sealed abstract class EssMode(val numeric: Int)
  case object Constant extends EssMode(0)
  case object Smoothing extends EssMode(1)
  case object GridForming extends EssMode(2)

  case class EssState(mode: EssMode, energy: Double, output: Double, target: Double, fault: Boolean)

  private def boundTargetRate(target: Double, params: EssParams): Double = {
    if (target > params.maxChargeRate) {
      params.maxChargeRate
    } else if (target < -params.maxDischargeRate) {
      params.maxDischargeRate * -1
    } else {
      target
    }
  }

  private def boundOutputBySoc(target: Double, state: EssState, params: EssParams): Double = {
    if (target > 0 && state.energy >= params.maxEnergy) {
      0.0
    } else if (target < 0 && state.energy <= params.minEnergy) {
      0.0
    } else {
      target
    }
  }

  def calcSoc(params: EssParams, state: EssState): Double = {
    val soc = if (params.capacity != 0) state.energy / params.capacity else 0.0
    soc * 100.0
  }
}
import EssSim._
class EssSim( /*mapping: EssMapping,*/ params: EssParams, initialState: EssState) extends SimulatorComponent {

  private var state = initialState

  def currentState: EssState = state

  def updates(line: LineState, time: Long): Seq[SimUpdate] = {
    import EssMapping._
    Seq(
      TimeSeriesUpdate(percentSoc, ValueDouble(EssSim.calcSoc(params, state))),
      TimeSeriesUpdate(socMax, ValueDouble(params.socMax)),
      TimeSeriesUpdate(socMin, ValueDouble(params.socMin)),
      TimeSeriesUpdate(chargeDischargeRate, ValueDouble(state.output)),
      TimeSeriesUpdate(chargeRateMax, ValueDouble(params.maxChargeRate)),
      TimeSeriesUpdate(dischargeRateMax, ValueDouble(params.maxDischargeRate)),
      TimeSeriesUpdate(capacity, ValueDouble(params.capacity)),
      TimeSeriesUpdate(efficiency, ValueDouble(params.efficiency)),
      TimeSeriesUpdate(chargeRateTarget, ValueDouble(state.target)),
      TimeSeriesUpdate(mode, ValueUInt64(state.mode.numeric)),
      TimeSeriesUpdate(faultStatus, ValueBool(state.fault)))
  }

  def handlers: Map[Path, (Option[Value]) => Boolean] = {
    import EssMapping._

    def chargeRateHandler(vOpt: Option[Value]): Boolean = {
      vOpt.flatMap(Utils.valueAsDouble).exists(onTargetChargeRateUpdate)
    }
    def setModeHandler(vOpt: Option[Value]): Boolean = {
      vOpt.flatMap(Utils.valueAsInt).map(_.toInt).exists(onModeUpdate)
    }

    Map(
      (setChargeRate, chargeRateHandler _),
      (setBatteryMode, setModeHandler _),
      (faultEnable, { _: Option[Value] => onFaultEnable() }),
      (faultDisable, { _: Option[Value] => onFaultDisable() }))
  }

  def tick(deltaMs: Long): Unit = {
    if (!state.fault) {

      val deltaHours = Utils.millisecondsToHours(deltaMs)

      val energyDelta = deltaHours * state.output

      val nextEnergy = energyDelta + state.energy

      val (resultEnergy, resultOutput) = if (energyDelta > 0 && nextEnergy >= params.maxEnergy) {
        (params.maxEnergy, 0.0)
      } else if (energyDelta < 0 && nextEnergy <= params.minEnergy) {
        (params.minEnergy, 0.0)
      } else {
        (nextEnergy, state.output)
      }

      state = state.copy(
        energy = resultEnergy,
        output = resultOutput)
    }
  }

  def setSmoothTarget(target: Double): Unit = {
    state = state.copy(output = target)
  }

  private def onModeUpdate(mode: Int): Boolean = {
    EssMode.parse(mode).foreach {
      case modeUpdate @ Constant => state = state.copy(mode = modeUpdate, output = if (!state.fault) state.target else 0.0)
      case modeUpdate => state = state.copy(mode = modeUpdate)
    }
    true
  }

  private def onTargetChargeRateUpdate(target: Double): Boolean = {
    state.mode match {
      case Constant =>
        val boundTarget = boundTargetRate(target, params)

        state = state.copy(
          output = boundTarget,
          target = boundTarget)

      case Smoothing =>
        val boundTarget = boundTargetRate(target, params)
        state = state.copy(target = boundTarget)

      case GridForming =>
        val boundTarget = boundTargetRate(target, params)
        state = state.copy(target = boundTarget)
    }

    true
  }

  private def onFaultEnable(): Boolean = {
    if (!state.fault) {
      state = state.copy(fault = true)
      true
    } else {
      false
    }
  }
  private def onFaultDisable(): Boolean = {
    if (state.fault) {
      state = state.copy(fault = false)
      true
    } else {
      false
    }
  }

}