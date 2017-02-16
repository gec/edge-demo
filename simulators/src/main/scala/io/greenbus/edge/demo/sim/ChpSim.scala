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

object ChpParams {
  import play.api.libs.json._
  implicit val writer = Json.writes[ChpParams]
  implicit val reader = Json.reads[ChpParams]

  def basic = ChpParams(256.0, 10.0)
}
case class ChpParams(powerCapacity: Double, rampRatekWps: Double)

/*object ChpMapping extends TypeConfiguration[ChpParams, ChpMapping] {

  val equipmentType: String = "CHP"

  val power = "OutputPower"
  val powerTarget = "OutTarget"
  val powerCapacity = "PowerCapacity"

  val setTarget = "SetOutTarget"

  val faultStatus = "FaultStatus"
  val faultEnable = "FaultEnable"
  val faultDisable = "FaultDisable"

  val pointTypes: Seq[String] = Seq(power, powerTarget, powerCapacity, faultStatus)
  val commandTypes: Seq[String] = Seq(setTarget, faultEnable, faultDisable)

  def defaultParams: ChpParams = ChpParams(256.0, 10.0)

  def populate(equip: Entity, params: ChpParams, points: Seq[Point], commands: Seq[Command]): ChpMapping = {
    ChpMapping(equip,
      params,
      outputPower = findPoint(equip.getName, points, power),
      outputTarget = findPoint(equip.getName, points, powerTarget),
      powerCapacity = findPoint(equip.getName, points, powerCapacity),
      setOutputTarget = findCommand(equip.getName, commands, setTarget),
      faultStatus = findPoint(equip.getName, points, faultStatus),
      faultEnable = findCommand(equip.getName, commands, faultEnable),
      faultDisable = findCommand(equip.getName, commands, faultDisable))
  }

  def extractParams(v: StoredValue): Option[ChpParams] = {
    Configuration.svToJson[ChpParams](v, _.as[ChpParams])
  }

  def initialUpdate(mapping: ChpMapping, params: ChpParams, state: ChpState): Seq[(ModelUUID, Measurement)] = {
    Seq(
      (mapping.outputPower.getUuid, doubleMeas(state.currentValue)),
      (mapping.outputTarget.getUuid, doubleMeas(state.outputTarget)),
      (mapping.powerCapacity.getUuid, doubleMeas(params.powerCapacity)))
  }

  def tick(deltaMs: Long, sim: ChpSim): Double = {
    sim.tick(deltaMs)
    sim.currentState.currentValue
  }
}
case class ChpMapping(equip: Entity, params: ChpParams, outputPower: Point, outputTarget: Point, powerCapacity: Point, setOutputTarget: Command, faultStatus: Point, faultEnable: Command, faultDisable: Command) {

  def measPoints = Seq(outputTarget)
}

case class InitialChpMeas(outputTarget: Option[Measurement])
*/

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
class ChpSim( /*mapping: ChpMapping,*/ params: ChpParams, initialState: ChpState) {

  private var state = initialState

  def currentState: ChpState = state

  /*def updates(power: Double, current: Double, voltage: Double): Seq[(ModelUUID, MeasValueHolder)] = {
    Seq(
      (mapping.outputPower.getUuid, DoubleMeasValue(power)),
      (mapping.outputTarget.getUuid, DoubleMeasValue(state.outputTarget)),
      (mapping.powerCapacity.getUuid, DoubleMeasValue(mapping.params.powerCapacity)),
      (mapping.faultStatus.getUuid, BoolMeasValue(state.fault)))
  }*/

  def tick(deltaMs: Long): Unit = {
    if (!state.fault && state.currentValue != state.outputTarget) {

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
  }

  /*def controlHandlers(): Seq[(ModelUUID, CommandRequest => Unit)] = {

    def handler(cmdReq: CommandRequest): Unit = {
      Utils.commandReqAsDouble(cmdReq).foreach(onTargetUpdate)
    }

    Seq(
      (mapping.setOutputTarget.getUuid, handler),
      (mapping.faultEnable.getUuid, _ => onFaultEnable()),
      (mapping.faultDisable.getUuid, _ => onFaultDisable()))
  }*/

  private def onFaultEnable(): Unit = {
    state = state.copy(currentValue = 0.0, fault = true)
  }
  private def onFaultDisable(): Unit = {
    if (state.fault) {
      state = state.copy(currentValue = 0.0, fault = false)
    }
  }

  private def onTargetUpdate(rate: Double): Unit = {
    val boundTarget = boundTargetRate(rate, params)
    state = state.copy(outputTarget = boundTarget)
  }
}
