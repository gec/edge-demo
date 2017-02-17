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

case class LineState(power: Double, current: Double, voltage: Double)

case class SimulatorState(
  voltageNominal: Double,
  voltagePcc: Double,
  currentPcc: Double,
  frequency: Double,
  pccStatus: Boolean,
  custBkrStatus: Boolean)

class Simulator(
    start: SimulatorState,
    chps: Seq[ChpSim],
    esses: Seq[EssSim],
    pvs: Seq[PvSim],
    loads: Seq[LoadSim]) {
  import EssSim._

  private var state: SimulatorState = start
  private var lastTime = Option.empty[Long]

  def tick(now: Long): LineState = {
    val current = state
    val last = lastTime.getOrElse(now)
    lastTime = Some(now)
    val deltaMs = now - last

    esses.find(_.currentState.mode == Smoothing) match {
      case Some(smoothingEss) =>
        essSmoothing(current, now, deltaMs, smoothingEss)

      case None =>
        esses.find(_.currentState.mode == GridForming) match {
          case Some(gridFormingEss) =>
            essGridForming(current, now, deltaMs, gridFormingEss)
          case None =>
            essConstantCharging(current, now, deltaMs)
        }
    }
  }

  private def essConstantCharging(simulation: SimulatorState, now: Long, deltaMs: Long): LineState = {

    val pvFactor = 1.0 //pvIntermittency.pollInputFileForReduction()
    pvs.foreach(_.updateReduction(pvFactor))
    chps.foreach(_.tick(deltaMs))
    esses.foreach(_.tick(deltaMs))

    val pvWithPower = pvs.map(sim => (sim, sim.atTime(now)))
    val chpWithPower = chps.map(sim => (sim, sim.currentState.currentValue))
    val essWithPower = esses.map(sim => (sim, sim.currentState.output))
    val loadWithPower = loads.map(sim => (sim, sim.valueAt(now)))

    computeLine(simulation, pvWithPower, chpWithPower, essWithPower, loadWithPower)
  }

  private def computeGridConnected(simulation: SimulatorState,
    pvWithPower: Seq[(PvSim, Double)],
    chpWithPower: Seq[(ChpSim, Double)],
    essWithPower: Seq[(EssSim, Double)],
    loadWithPower: Seq[(LoadSim, Double)]): LineState = {

    val totalPvGen = pvWithPower.map(_._2).sum
    val totalChpGen = chpWithPower.map(_._2).sum
    val totalEssLoad = essWithPower.map(_._2).sum
    val totalLoad = loadWithPower.map(_._2).sum

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

  private def computeGridDisconnected(simulation: SimulatorState,
    pvWithPower: Seq[(PvSim, Double)],
    chpWithPower: Seq[(ChpSim, Double)],
    essWithPower: Seq[(EssSim, Double)],
    loadWithPower: Seq[(LoadSim, Double)]): LineState = {

    state = simulation.copy(
      voltagePcc = 0.0,
      currentPcc = 0.0,
      frequency = 0.0)

    LineState(0.0, 0.0, 0.0)
  }

  private def computeLine(simulation: SimulatorState,
    pvWithPower: Seq[(PvSim, Double)],
    chpWithPower: Seq[(ChpSim, Double)],
    essWithPower: Seq[(EssSim, Double)],
    loadWithPower: Seq[(LoadSim, Double)]): LineState = {

    val gridConnected = simulation.custBkrStatus && simulation.pccStatus

    if (gridConnected) {

      computeGridConnected(simulation, pvWithPower, chpWithPower, essWithPower, loadWithPower)

    } else {

      LineState(0.0, 0.0, 0.0)
    }
  }

  private def essSmoothing(simulation: SimulatorState, now: Long, deltaMs: Long, smoothEss: EssSim) /*: Seq[(ModelUUID, MeasValueHolder)]*/ = {

    val nominalPv = pvs.map(sim => sim.valueWithoutReduction(now)).sum
    val pvFactor = 1.0 //pvIntermittency.pollInputFileForReduction()
    pvs.foreach(_.updateReduction(pvFactor))
    val pvWithPower = pvs.map(sim => (sim, sim.atTime(now)))
    val totalPvGen = pvWithPower.map(_._2).sum

    val smoothTarget = totalPvGen - nominalPv
    smoothEss.setSmoothTarget(smoothTarget)

    esses.foreach(_.tick(deltaMs))
    chps.foreach(_.tick(deltaMs))

    val chpWithPower = chps.map(sim => (sim, sim.currentState.currentValue))
    val essWithPower = esses.map(sim => (sim, sim.currentState.output))
    val loadWithPower = loads.map(sim => (sim, sim.valueAt(now)))

    computeLine(simulation, pvWithPower, chpWithPower, essWithPower, loadWithPower)
  }

  private def essGridForming(simulation: SimulatorState, now: Long, deltaMs: Long, formingEss: EssSim) /*: Seq[(ModelUUID, MeasValueHolder)]*/ = {

    val pvFactor = 1.0 //pvIntermittency.pollInputFileForReduction()
    pvs.foreach(_.updateReduction(pvFactor))
    chps.foreach(_.tick(deltaMs))

    val nonFormingEsses = esses.filterNot(_.currentState.mode == GridForming)
    nonFormingEsses.foreach(_.tick(deltaMs))

    val pvWithPower = pvs.map(sim => (sim, sim.atTime(now)))
    val chpWithPower = chps.map(sim => (sim, sim.currentState.currentValue))
    val nonFormingEssWithPower = nonFormingEsses.map(sim => (sim, sim.currentState.output))
    val loadWithPower = loads.map(sim => (sim, sim.valueAt(now)))

    val gridConnected = simulation.custBkrStatus && simulation.pccStatus

    if (!gridConnected) {

      val totalPvGen = pvWithPower.map(_._2).sum
      val totalChpGen = chpWithPower.map(_._2).sum
      val totalEssLoad = nonFormingEssWithPower.map(_._2).sum
      val totalLoad = loadWithPower.map(_._2).sum

      val localDemand = (totalLoad + totalEssLoad) - (totalPvGen + totalChpGen)

      formingEss.setSmoothTarget(-1 * localDemand)
      formingEss.tick(deltaMs)

      computeGridConnected(simulation, pvWithPower, chpWithPower, nonFormingEssWithPower :+ (formingEss, formingEss.currentState.output), loadWithPower)

    } else {

      formingEss.setSmoothTarget(0.0)
      formingEss.tick(deltaMs)

      computeGridConnected(simulation, pvWithPower, chpWithPower, nonFormingEssWithPower :+ (formingEss, 0.0), loadWithPower)
    }
  }
}

