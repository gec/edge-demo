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

/*trait TypeConfiguration[Params, Result] {
  val equipmentType: String
  val pointTypes: Seq[String]
  val commandTypes: Seq[String]

  def extractParams(v: StoredValue): Option[Params]

  def defaultParams: Params

  def populate(equip: Entity, params: Params, points: Seq[Point], commands: Seq[Command]): Result
}


object EssMapping extends TypeConfiguration[EssParams, EssMapping] {

  val equipmentType: String = "ESS"

  val percentSoc = "%SOC"
  val mode = "ESSMode"
  val socMax = "SOC_Max"
  val socMin = "SOC_Min"
  val chargeDischargeRate = "OutputPower"
  val chargeRateMax = "ChargeRateMax"
  val dischargeRateMax = "DischargeRateMax"
  val capacity = "EnergyCapacity"
  val efficiency = "Efficiency"
  val chargeRateTarget = "ChargeRateTarget"
  val faultStatus = "FaultStatus"

  val pointTypes = Seq(percentSoc, socMax, socMin, chargeDischargeRate, chargeRateMax, dischargeRateMax, capacity, efficiency, chargeRateTarget, mode, faultStatus)

  val setChargeRate = "SetChargeRateTarget"
  val setBatteryMode = "SetMode"
  val faultEnable = "FaultEnable"
  val faultDisable = "FaultDisable"
  val commandTypes = Seq(setChargeRate, setBatteryMode, faultEnable, faultDisable)

  def defaultParams: EssParams = EssParams(100.0, 100.0, 0.0, 50.0, 50.0, 0.8)

  def populate(equip: Entity, params: EssParams, points: Seq[Point], commands: Seq[Command]): EssMapping = {
    EssMapping(equip,
      params,
      percentSoc = findPoint(equip.getName, points, percentSoc),
      socMax = findPoint(equip.getName, points, socMax),
      socMin = findPoint(equip.getName, points, socMin),
      chargeDischargeRate = findPoint(equip.getName, points, chargeDischargeRate),
      chargeRateMax = findPoint(equip.getName, points, chargeRateMax),
      dischargeRateMax = findPoint(equip.getName, points, dischargeRateMax),
      capacity = findPoint(equip.getName, points, capacity),
      efficiency = findPoint(equip.getName, points, efficiency),
      chargeRateTarget = findPoint(equip.getName, points, chargeRateTarget),
      batteryMode = findPoint(equip.getName, points, mode),
      faultStatus = findPoint(equip.getName, points, faultStatus),
      setChargeRate = findCommand(equip.getName, commands, setChargeRate),
      setBatteryMode = findCommand(equip.getName, commands, setBatteryMode),
      faultEnable = findCommand(equip.getName, commands, faultEnable),
      faultDisable = findCommand(equip.getName, commands, faultDisable))
  }

  def extractParams(v: StoredValue): Option[EssParams] = {
    Configuration.svToJson[EssParams](v, _.as[EssParams])
  }

  def updates(mapping: EssMapping, current: EssState, prevOpt: Option[EssState]): Seq[(ModelUUID, Measurement)] = {
    import com.greenenergycorp.mmc.sim.Mappings._

    val extractors = Seq(
      ((current: EssState, prev: EssState) => current.energy != prev.energy, (mapping.percentSoc.getUuid, doubleMeas(calcSoc(mapping.params, current)))),
      ((current: EssState, prev: EssState) => current.mode != prev.mode, (mapping.batteryMode.getUuid, intMeas(current.mode.numeric))),
      ((current: EssState, prev: EssState) => current.output != prev.output, (mapping.chargeDischargeRate.getUuid, doubleMeas(current.output))),
      ((current: EssState, prev: EssState) => current.target != prev.target, (mapping.chargeRateTarget.getUuid, doubleMeas(current.target))))

    extractUpdates(current, prevOpt, extractors)
  }

  def calcSoc(params: EssParams, state: EssState): Double = {
    val soc = if (params.capacity != 0) state.energy / params.capacity else 0.0
    soc * 100.0
  }
}
case class EssMapping(equip: Entity,
                      params: EssParams,
                      percentSoc: Point,
                      socMax: Point,
                      socMin: Point,
                      chargeDischargeRate: Point,
                      chargeRateMax: Point,
                      dischargeRateMax: Point,
                      capacity: Point,
                      efficiency: Point,
                      chargeRateTarget: Point,
                      batteryMode: Point,
                      faultStatus: Point,
                      setChargeRate: Command,
                      setBatteryMode: Command,
                      faultEnable: Command,
                      faultDisable: Command) {

  def measPoints = Seq(percentSoc, batteryMode, chargeRateTarget)
}

case class InitialEssMeas(mode: Option[Measurement], percentSoc: Option[Measurement], chargeRateTarget: Option[Measurement])*/

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
}
import EssSim._
class EssSim( /*mapping: EssMapping,*/ params: EssParams, initialState: EssState) {

  private var state = initialState

  def currentState: EssState = state

  /*def updates(power: Double, current: Double, voltage: Double): Seq[(ModelUUID, MeasValueHolder)] = {
    val params = mapping.params
    Seq(
      (mapping.percentSoc.getUuid, DoubleMeasValue(EssMapping.calcSoc(params, state))),
      (mapping.socMax.getUuid, DoubleMeasValue(params.socMax)),
      (mapping.socMin.getUuid, DoubleMeasValue(params.socMin)),
      (mapping.chargeDischargeRate.getUuid, DoubleMeasValue(state.output)),
      (mapping.chargeRateMax.getUuid, DoubleMeasValue(params.maxChargeRate)),
      (mapping.dischargeRateMax.getUuid, DoubleMeasValue(params.maxDischargeRate)),
      (mapping.capacity.getUuid, DoubleMeasValue(params.capacity)),
      (mapping.efficiency.getUuid, DoubleMeasValue(params.efficiency)),
      (mapping.chargeRateTarget.getUuid, DoubleMeasValue(state.target)),
      (mapping.batteryMode.getUuid, IntMeasValue(state.mode.numeric)),
      (mapping.faultStatus.getUuid, BoolMeasValue(state.fault)))
  }*/

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

  /*def controlHandlers(): Seq[(ModelUUID, CommandRequest => Unit)] = {

    def chargeRateHandler(cmdReq: CommandRequest): Unit = {
      Utils.commandReqAsDouble(cmdReq).foreach(onTargetChargeRateUpdate)
    }
    def setModeHandler(cmdReq: CommandRequest): Unit = {
      Utils.commandReqAsInt(cmdReq).map(_.toInt).foreach(onModeUpdate)
    }

    Seq(
      (mapping.setChargeRate.getUuid, chargeRateHandler),
      (mapping.setBatteryMode.getUuid, setModeHandler),
      (mapping.faultEnable.getUuid, _ => onFaultEnable()),
      (mapping.faultDisable.getUuid, _ => onFaultDisable()))
  }*/

  def setSmoothTarget(target: Double): Unit = {
    state = state.copy(output = target)
  }

  private def onModeUpdate(mode: Int): Unit = {
    EssMode.parse(mode).foreach {
      case modeUpdate @ Constant => state = state.copy(mode = modeUpdate, output = if (!state.fault) state.target else 0.0)
      case modeUpdate => state = state.copy(mode = modeUpdate)
    }
  }

  private def onTargetChargeRateUpdate(target: Double): Unit = {
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
  }

  private def onFaultEnable(): Unit = {
    state = state.copy(output = 0.0, fault = true)
  }
  private def onFaultDisable(): Unit = {
    if (state.fault) {
      state = state.copy(output = state.target, fault = false)
    }
  }

}