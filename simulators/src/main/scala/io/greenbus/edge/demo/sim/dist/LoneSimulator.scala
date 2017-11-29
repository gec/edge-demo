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
package io.greenbus.edge.demo.sim.dist

import io.greenbus.edge.demo.sim.{ BreakerSim, EssSim, LineState, SimulatorState }

class LoneSimulator(start: SimulatorState,
    pccBkr: BreakerSim,
    custBkr: BreakerSim,
    esses: Seq[EssSim]) {
  import EssSim._

  private var state: SimulatorState = start
  private var lastTime = Option.empty[Long]

  def tick(now: Long,
    totalPvGen: Double,
    totalChpGen: Double,
    totalEssLoad: Double,
    totalLoad: Double): LineState = {

    val current = state
    val last = lastTime.getOrElse(now)
    lastTime = Some(now)
    val deltaMs = now - last

    esses.find(_.currentState.mode == Smoothing) match {
      case Some(smoothingEss) =>
        essSmoothing(current, now, deltaMs, smoothingEss, totalPvGen, totalChpGen, totalEssLoad, totalLoad)

      case None =>
        esses.find(_.currentState.mode == GridForming) match {
          case Some(gridFormingEss) =>
            essGridForming(current, now, deltaMs, gridFormingEss, totalPvGen, totalChpGen, totalEssLoad, totalLoad)
          case None =>
            essConstantCharging(current, now, deltaMs, totalPvGen, totalChpGen, totalEssLoad, totalLoad)
        }
    }
  }

  private def essConstantCharging(simulation: SimulatorState, now: Long, deltaMs: Long,
    totalPvGen: Double,
    totalChpGen: Double,
    totalEssLoad: Double,
    totalLoad: Double): LineState = {

    esses.foreach(_.tick(deltaMs))
    computeLine(simulation, totalPvGen, totalChpGen, totalEssLoad, totalLoad)
  }

  private def essSmoothing(simulation: SimulatorState, now: Long, deltaMs: Long, smoothEss: EssSim,
    totalPvGen: Double,
    totalChpGen: Double,
    totalEssLoad: Double,
    totalLoad: Double) = {

    smoothEss.setSmoothTarget(0.0) // Aren't doing intermittency for now

    computeLine(simulation, totalPvGen, totalChpGen, totalEssLoad, totalLoad)
  }

  private def essGridForming(simulation: SimulatorState, now: Long, deltaMs: Long, formingEss: EssSim,
    totalPvGen: Double,
    totalChpGen: Double,
    totalEssLoad: Double,
    totalLoad: Double) = {

    val nonFormingEsses = esses.filterNot(_.currentState.mode == GridForming)
    nonFormingEsses.foreach(_.tick(deltaMs))

    val gridConnected = pccBkr.status && custBkr.status

    if (!gridConnected) {

      val localDemand = (totalLoad + totalEssLoad) - (totalPvGen + totalChpGen)

      formingEss.setSmoothTarget(-1 * localDemand)
      formingEss.tick(deltaMs)

      computeGridConnected(simulation, totalPvGen, totalChpGen, totalEssLoad + formingEss.currentState.output, totalLoad)

    } else {

      formingEss.setSmoothTarget(0.0)
      formingEss.tick(deltaMs)

      computeGridConnected(simulation, totalPvGen, totalChpGen, totalEssLoad, totalLoad)
    }
  }

  private def computeLine(simulation: SimulatorState,
    totalPvGen: Double,
    totalChpGen: Double,
    totalEssLoad: Double,
    totalLoad: Double): LineState = {

    //val gridConnected = simulation.custBkrStatus && simulation.pccStatus
    val gridConnected = pccBkr.status && custBkr.status

    if (gridConnected) {

      computeGridConnected(simulation, totalPvGen, totalChpGen, totalEssLoad, totalLoad)

    } else {

      LineState(0.0, 0.0, 0.0)
    }
  }

  private def computeGridConnected(simulation: SimulatorState,
    totalPvGen: Double,
    totalChpGen: Double,
    totalEssLoad: Double,
    totalLoad: Double): LineState = {

    val powerFlow = (totalLoad + totalEssLoad) - (totalPvGen + totalChpGen)
    val updatedCurrent = if (simulation.voltagePcc != 0.0) powerFlow / simulation.voltagePcc else 0.0

    val currentDiff = updatedCurrent - simulation.currentPcc

    val voltageDiff = if (updatedCurrent != 0.0) Math.abs((simulation.voltageNominal / updatedCurrent) / 10000) else 0.0

    val adjustedVoltage = if (currentDiff > 0) {
      simulation.voltageNominal + voltageDiff
    } else if (currentDiff < 0) {
      simulation.voltageNominal - voltageDiff
    } else {
      simulation.voltageNominal
    }

    val adjustedFrequency = if (currentDiff > 0) {
      simulation.frequency + 0.0001
    } else if (currentDiff < 0) {
      simulation.frequency - 0.0001
    } else {
      simulation.frequency
    }

    val nextFrequency = if (adjustedFrequency > 60.15 || adjustedFrequency < 59.85) {
      60.0
    } else {
      adjustedFrequency
    }

    state = simulation.copy(
      voltagePcc = adjustedVoltage,
      currentPcc = updatedCurrent,
      frequency = nextFrequency)

    LineState(powerFlow, updatedCurrent, adjustedVoltage)
  }
}