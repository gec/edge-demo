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

import akka.actor.{ Actor, Props }

import scala.concurrent.Future

case class LineState(power: Double, current: Double, voltage: Double)

case class SimulatorState(
  voltageNominal: Double,
  voltagePcc: Double,
  currentPcc: Double,
  frequency: Double,
  pccStatus: Boolean,
  custBkrStatus: Boolean)
object Simulator {
  /*case object DoConfiguration
  case class ConfigurationSuccess(mapping: ModelMapping)
  case class ConfigurationFailure(ex: Throwable)*/
  case object Tick

  /*def props(
    endpoint: Endpoint,
    session: Session,
    publishMeasurements: MeasurementsPublished => Unit,
    updateStatus: StackStatusUpdated => Unit,
    load: LoadRecord,
    pvIntermittency: PvIntermittency): Props = {
    Props(classOf[SimEndpoint], endpoint, session, publishMeasurements, updateStatus, load, pvIntermittency)
  }*/
}
import Simulator._
class Simulator(
    start: SimulatorState,
    chps: Seq[ChpSim],
    esses: Seq[EssSim],
    pvs: Seq[PvSim],
    loads: Seq[LoadSim]) {
  import Simulator._
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

  private def essConstantCharging(simulation: SimulatorState, now: Long, deltaMs: Long) /*: Seq[(ModelUUID, MeasValueHolder)]*/ = {

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

    /*println("computeGridConnected powerFlow: " + powerFlow)
    println("computeGridConnected updatedCurrent: " + updatedCurrent)
    println("computeGridConnected currentDiff: " + currentDiff)
    println("computeGridConnected voltageDiff: " + voltageDiff)
    println("computeGridConnected adjustedVoltage: " + adjustedVoltage)
    println("computeGridConnected adjustedFrequency: " + adjustedFrequency)
    println("computeGridConnected nextFrequency: " + nextFrequency)*/

    state = simulation.copy(
      voltagePcc = adjustedVoltage,
      currentPcc = updatedCurrent,
      frequency = nextFrequency)

    LineState(powerFlow, updatedCurrent, adjustedVoltage)

    /*def addPowerValues[A](tups: (A, Double)) = tups match { case (info, power) => (info, power, power / adjustedVoltage, adjustedVoltage) }

    val pvWithLine = pvWithPower.map(addPowerValues)
    val chpWithLine = chpWithPower.map(addPowerValues)
    val essWithLine = essWithPower.map(addPowerValues)
    val loadWithLine = loadWithPower.map(addPowerValues)

    val equipUpdates = pvWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      chpWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      essWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      loadWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) }

    val breakerUpdates = Seq(
      (simulation.custBkr.demandPower.getUuid, DoubleMeasValue(powerFlow)),
      (simulation.custBkr.voltage.getUuid, DoubleMeasValue(adjustedVoltage)),
      (simulation.custBkr.current.getUuid, DoubleMeasValue(updatedCurrent)),
      (simulation.custBkr.frequency.getUuid, DoubleMeasValue(nextFrequency)))

    equipUpdates ++ breakerUpdates*/
  }

  private def computeGridDisconnected(simulation: SimulatorState,
    pvWithPower: Seq[(PvSim, Double)],
    chpWithPower: Seq[(ChpSim, Double)],
    essWithPower: Seq[(EssSim, Double)],
    loadWithPower: Seq[(LoadSim, Double)]) /*: Seq[(ModelUUID, MeasValueHolder)]*/ = {

    state = simulation.copy(
      voltagePcc = 0.0,
      currentPcc = 0.0,
      frequency = 0.0)

    LineState(0.0, 0.0, 0.0)

    /*
    def setPowerValues[A](tups: (A, Double)) = tups match {
      case (info, power) => (info, 0.0, 0.0, 0.0)
    }

    val pvWithLine = pvWithPower.map(setPowerValues)
    val chpWithLine = chpWithPower.map(setPowerValues)
    val essWithLine = essWithPower.map(setPowerValues)
    val loadWithLine = loadWithPower.map(setPowerValues)


    val equipUpdates = pvWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      chpWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      essWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      loadWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) }

    val breakerUpdates = Seq(
      (custBkr.demandPower.getUuid, DoubleMeasValue(0.0)),
      (custBkr.voltage.getUuid, DoubleMeasValue(0.0)),
      (custBkr.current.getUuid, DoubleMeasValue(0.0)),
      (custBkr.frequency.getUuid, DoubleMeasValue(0.0)))

    equipUpdates ++ breakerUpdates*/
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
      //computeGridDisconnected(simulation, pvWithPower, chpWithPower, essWithPower, loadWithPower)
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

/*class SimEndpoint(
    endpoint: Endpoint,
    session: Session,
    publishMeasurements: MeasurementsPublished => Unit,
    updateStatus: StackStatusUpdated => Unit,
    load: LoadRecord,
    pvIntermittency: PvIntermittency) extends Actor with MessageScheduling with Logging {

  private var state = Option.empty[Simulation]
  private var measSub = Option.empty[SubscriptionBinding]
  private var lastTime = Option.empty[Long]
  private var lastValues = Map.empty[ModelUUID, MeasValueHolder]

  self ! DoConfiguration

  def receive = {
    case DoConfiguration => setup()
    case ConfigurationSuccess(modelMapping) => {
      logger.debug(s"Configuration success for ${endpoint.getName}")
      onConfigSuccess(modelMapping)
      lastTime = Some(System.currentTimeMillis())
      scheduleMsg(2000, Tick)
    }
    case ConfigurationFailure(ex) => {
      logger.error(s"Could not configure simulator for endpoint ${endpoint.getName}:", ex)
      scheduleMsg(5000, DoConfiguration)
    }
    case cmdReq: CommandRequest => {
      logger.info(s"Command request for ${cmdReq.getCommandUuid.getValue}")

      state.foreach { sim =>
        sim.handleMap.get(cmdReq.getCommandUuid) match {
          case None =>
            logger.warn(s"No handler for command ${cmdReq.getCommandUuid.getValue}")
            sender ! Future.successful(CommandResult.newBuilder().setStatus(CommandStatus.NOT_SUPPORTED).build())
          case Some(handler) =>
            handler(cmdReq)
            sender ! Future.successful(CommandResult.newBuilder().setStatus(CommandStatus.SUCCESS).build())
            tick()
        }
      }
    }
    case measNot: MeasurementNotification => {
      state.foreach { current =>
        val pointUuid = measNot.getPointUuid
        val meas = measNot.getValue
        if (pointUuid == current.custBkr.status.getUuid) {
          Utils.measToBoolOpt(meas).foreach { newStatus =>
            val oldStatus = current.custBkrStatus
            if (oldStatus != newStatus) {
              state = Some(current.copy(custBkrStatus = newStatus))
              tick()
            }
          }
        } else if (pointUuid == current.pccBkr.status.getUuid) {
          Utils.measToBoolOpt(meas).foreach { newStatus =>
            val oldStatus = current.pccStatus
            if (oldStatus != newStatus) {
              state = Some(current.copy(pccStatus = newStatus))
              tick()
            }
          }
        }
      }
    }
    case Tick => {
      tick()
      scheduleMsg(2000, Tick)
    }
  }

  private def setup(): Unit = {
    val configFut = Configuration.populateMicrogrid(session, endpoint)
    configFut.onSuccess {
      case cfg => self ! ConfigurationSuccess(cfg)
    }
    configFut.onFailure {
      case ex: Throwable => self ! ConfigurationFailure(ex)
    }
  }

  private def tick(): Unit = {
    state.foreach { current =>
      val now = System.currentTimeMillis()
      val last = lastTime.getOrElse(now)
      lastTime = Some(now)
      val deltaMs = now - last

      val updates = current.esses.find(_.currentState.mode == Smoothing) match {
        case Some(smoothingEss) =>
          essSmoothing(current, now, deltaMs, smoothingEss)

        case None =>
          current.esses.find(_.currentState.mode == GridForming) match {
            case Some(gridFormingEss) =>
              essGridForming(current, now, deltaMs, gridFormingEss)
            case None =>
              essConstantCharging(current, now, deltaMs)
          }
      }

      publishUpdates(updates)
    }
  }

  private def publishUpdates(values: Seq[(ModelUUID, MeasValueHolder)]): Unit = {

    val changes = values.filterNot { case (uuid, v) => lastValues.get(uuid) == Some(v) }

    publishMeasurements(MeasurementsPublished(System.currentTimeMillis(), changes.map(tup => (tup._1, tup._2.toMeas)), Seq()))

    lastValues = lastValues ++ values
  }

  private def essConstantCharging(simulation: Simulation, now: Long, deltaMs: Long): Seq[(ModelUUID, MeasValueHolder)] = {

    val pvFactor = pvIntermittency.pollInputFileForReduction()
    simulation.pvs.foreach(_.updateReduction(pvFactor))
    simulation.chps.foreach(_.tick(deltaMs))
    simulation.esses.foreach(_.tick(deltaMs))

    val pvWithPower = simulation.pvs.map(sim => (sim, sim.atTime(now)))
    val chpWithPower = simulation.chps.map(sim => (sim, sim.currentState.currentValue))
    val essWithPower = simulation.esses.map(sim => (sim, sim.currentState.output))
    val loadWithPower = simulation.loads.map(sim => (sim, sim.valueAt(now)))

    computeLine(simulation, pvWithPower, chpWithPower, essWithPower, loadWithPower)
  }

  private def computeGridConnected(simulation: Simulation,
    pvWithPower: Seq[(PvSim, Double)],
    chpWithPower: Seq[(ChpSim, Double)],
    essWithPower: Seq[(EssSim, Double)],
    loadWithPower: Seq[(LoadSim, Double)]): Seq[(ModelUUID, MeasValueHolder)] = {

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

    /*println("computeGridConnected powerFlow: " + powerFlow)
    println("computeGridConnected updatedCurrent: " + updatedCurrent)
    println("computeGridConnected currentDiff: " + currentDiff)
    println("computeGridConnected voltageDiff: " + voltageDiff)
    println("computeGridConnected adjustedVoltage: " + adjustedVoltage)
    println("computeGridConnected adjustedFrequency: " + adjustedFrequency)
    println("computeGridConnected nextFrequency: " + nextFrequency)*/

    state = Some(simulation.copy(
      voltagePcc = adjustedVoltage,
      currentPcc = updatedCurrent,
      frequency = nextFrequency))

    def addPowerValues[A](tups: (A, Double)) = tups match { case (info, power) => (info, power, power / adjustedVoltage, adjustedVoltage) }

    val pvWithLine = pvWithPower.map(addPowerValues)
    val chpWithLine = chpWithPower.map(addPowerValues)
    val essWithLine = essWithPower.map(addPowerValues)
    val loadWithLine = loadWithPower.map(addPowerValues)

    val equipUpdates = pvWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      chpWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      essWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      loadWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) }

    val breakerUpdates = Seq(
      (simulation.custBkr.demandPower.getUuid, DoubleMeasValue(powerFlow)),
      (simulation.custBkr.voltage.getUuid, DoubleMeasValue(adjustedVoltage)),
      (simulation.custBkr.current.getUuid, DoubleMeasValue(updatedCurrent)),
      (simulation.custBkr.frequency.getUuid, DoubleMeasValue(nextFrequency)))

    equipUpdates ++ breakerUpdates
  }

  private def computeGridDisconnected(simulation: Simulation,
    pvWithPower: Seq[(PvSim, Double)],
    chpWithPower: Seq[(ChpSim, Double)],
    essWithPower: Seq[(EssSim, Double)],
    loadWithPower: Seq[(LoadSim, Double)]): Seq[(ModelUUID, MeasValueHolder)] = {

    state = Some(simulation.copy(
      voltagePcc = 0.0,
      currentPcc = 0.0,
      frequency = 0.0))

    def setPowerValues[A](tups: (A, Double)) = tups match {
      case (info, power) => (info, 0.0, 0.0, 0.0)
    }

    val pvWithLine = pvWithPower.map(setPowerValues)
    val chpWithLine = chpWithPower.map(setPowerValues)
    val essWithLine = essWithPower.map(setPowerValues)
    val loadWithLine = loadWithPower.map(setPowerValues)

    val equipUpdates = pvWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      chpWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      essWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) } ++
      loadWithLine.flatMap { case (sim, power, current, voltage) => sim.updates(power, current, voltage) }

    val breakerUpdates = Seq(
      (simulation.custBkr.demandPower.getUuid, DoubleMeasValue(0.0)),
      (simulation.custBkr.voltage.getUuid, DoubleMeasValue(0.0)),
      (simulation.custBkr.current.getUuid, DoubleMeasValue(0.0)),
      (simulation.custBkr.frequency.getUuid, DoubleMeasValue(0.0)))

    equipUpdates ++ breakerUpdates
  }

  private def computeLine(simulation: Simulation,
    pvWithPower: Seq[(PvSim, Double)],
    chpWithPower: Seq[(ChpSim, Double)],
    essWithPower: Seq[(EssSim, Double)],
    loadWithPower: Seq[(LoadSim, Double)]): Seq[(ModelUUID, MeasValueHolder)] = {

    val gridConnected = simulation.custBkrStatus && simulation.pccStatus

    if (gridConnected) {

      computeGridConnected(simulation, pvWithPower, chpWithPower, essWithPower, loadWithPower)

    } else {

      computeGridDisconnected(simulation, pvWithPower, chpWithPower, essWithPower, loadWithPower)
    }
  }

  private def essSmoothing(simulation: Simulation, now: Long, deltaMs: Long, smoothEss: EssSim): Seq[(ModelUUID, MeasValueHolder)] = {

    val nominalPv = simulation.pvs.map(sim => sim.valueWithoutReduction(now)).sum
    val pvFactor = pvIntermittency.pollInputFileForReduction()
    simulation.pvs.foreach(_.updateReduction(pvFactor))
    val pvWithPower = simulation.pvs.map(sim => (sim, sim.atTime(now)))
    val totalPvGen = pvWithPower.map(_._2).sum

    val smoothTarget = totalPvGen - nominalPv
    smoothEss.setSmoothTarget(smoothTarget)

    simulation.esses.foreach(_.tick(deltaMs))
    simulation.chps.foreach(_.tick(deltaMs))

    val chpWithPower = simulation.chps.map(sim => (sim, sim.currentState.currentValue))
    val essWithPower = simulation.esses.map(sim => (sim, sim.currentState.output))
    val loadWithPower = simulation.loads.map(sim => (sim, sim.valueAt(now)))

    computeLine(simulation, pvWithPower, chpWithPower, essWithPower, loadWithPower)
  }

  private def essGridForming(simulation: Simulation, now: Long, deltaMs: Long, formingEss: EssSim): Seq[(ModelUUID, MeasValueHolder)] = {

    val pvFactor = pvIntermittency.pollInputFileForReduction()
    simulation.pvs.foreach(_.updateReduction(pvFactor))
    simulation.chps.foreach(_.tick(deltaMs))

    val nonFormingEsses = simulation.esses.filterNot(_.currentState.mode == GridForming)
    nonFormingEsses.foreach(_.tick(deltaMs))

    val pvWithPower = simulation.pvs.map(sim => (sim, sim.atTime(now)))
    val chpWithPower = simulation.chps.map(sim => (sim, sim.currentState.currentValue))
    val nonFormingEssWithPower = nonFormingEsses.map(sim => (sim, sim.currentState.output))
    val loadWithPower = simulation.loads.map(sim => (sim, sim.valueAt(now)))

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

  private def onConfigSuccess(modelMapping: ModelMapping): Unit = {

    val chps = modelMapping.chpMappings.map {
      case (chpMap, initial) =>
        val target = initial.outputTarget.flatMap(Utils.measAnyNumericToDouble).getOrElse(0.0)
        new ChpSim(chpMap, ChpState(target, target, fault = false))
    }
    val esses = modelMapping.essMappings.map {
      case (essMap, initial) =>
        val mode = initial.mode.flatMap(Utils.measToInt).map(_.toInt).flatMap(EssMode.parse).getOrElse(EssSim.Constant)
        val soc = initial.percentSoc.flatMap(Utils.measAnyNumericToDouble).getOrElse(50.0)
        val target = initial.chargeRateTarget.flatMap(Utils.measAnyNumericToDouble).getOrElse(0.0)
        new EssSim(essMap, EssState(mode, (soc / 100.0) * essMap.params.capacity, target, target, fault = false))
    }
    val pvs = modelMapping.pvMappings.map {
      case pvMap => new PvSim(pvMap, PvState(1.0, fault = false))
    }
    val loads = modelMapping.loadMapping.map { loadMap => new LoadSim(loadMap, load, LoadState(0)) }

    val chpHandlers = chps.flatMap(_.controlHandlers())
    val essHandlers = esses.flatMap(_.controlHandlers())
    val pvHandlers = pvs.flatMap(_.controlHandlers())

    val handlerMap = (chpHandlers ++ essHandlers ++ pvHandlers).toMap

    val (pccMapping, origPccMeasOpt) = modelMapping.pccMapping
    val (custBkrMapping, origCustBkrMeasOpt) = modelMapping.custBkr

    val pccStatus = origPccMeasOpt.flatMap(Utils.measToBoolOpt).getOrElse(true)
    val custBkrStatus = origPccMeasOpt.flatMap(Utils.measToBoolOpt).getOrElse(true)

    val simulation = Simulation(480, 0.0, 0.0, 0.0, pccStatus, custBkrStatus, chps, esses, pvs, loads, pccMapping, custBkrMapping, handlerMap)

    measSub = Some(modelMapping.breakerStatusSub)
    modelMapping.breakerStatusSub.start(notification => self ! notification)

    updateStatus(StackStatusUpdated(FrontEndConnectionStatus.Status.COMMS_UP))

    state = Some(simulation)
  }

  override def postStop(): Unit = {
    measSub.foreach(_.cancel())
  }
}*/
