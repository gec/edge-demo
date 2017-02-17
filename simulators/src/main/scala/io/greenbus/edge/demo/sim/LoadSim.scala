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

import io.greenbus.edge.{ Path, Value, ValueDouble }

object LoadParams {
  import play.api.libs.json._
  implicit val writer = Json.writes[LoadParams]
  implicit val reader = Json.reads[LoadParams]
}

case class LoadParams(dataIndex: Int, stageReduction1: Option[Double], stageReduction2: Option[Double], stageReduction3: Option[Double], voltage: Option[Double])

object LoadMapping {

  val equipmentType = "Load"

  val power = Path("LoadPower")
  val voltage = Path("Voltage")
  val current = Path("Current")
  val kvar = Path("kvar")
  val pointTypes = Seq(power, voltage, current, kvar)

  val commandTypes = Seq()

  def defaultParams: LoadParams = LoadParams(0, Some(0.95), Some(0.9), Some(0.85), Some(480))
}

object LoadSim {

  case class LoadState(reductionStage: Int)
}

import LoadSim._
import io.greenbus.edge.Path
class LoadSim(params: LoadParams, data: LoadRecord, initialState: LoadState) extends SimulatorComponent {

  private val stageReduction1 = params.stageReduction1.getOrElse(0.95)
  private val stageReduction2 = params.stageReduction1.getOrElse(0.9)
  private val stageReduction3 = params.stageReduction1.getOrElse(0.85)

  private var state = initialState

  def updateReductionStage(stage: Int): Unit = {
    state = state.copy(reductionStage = stage)
  }

  def updates(line: LineState, time: Long): Seq[SimUpdate] = {
    val power = valueAt(time)
    val kvars = power * 0.02
    Seq(
      TimeSeriesUpdate(LoadMapping.power, ValueDouble(power)),
      TimeSeriesUpdate(LoadMapping.current, ValueDouble(line.current)),
      TimeSeriesUpdate(LoadMapping.voltage, ValueDouble(line.voltage)),
      TimeSeriesUpdate(LoadMapping.kvar, ValueDouble(kvars)))
  }

  def handlers: Map[Path, (Option[Value]) => Boolean] = Map()

  def valueAt(time: Long): Double = {

    val currentPower = {
      val (hourInYear, fractionOfHour) = Utils.hourInYearAndFraction()

      try {
        val valueAtPrevHourBoundary = data.hourlies(hourInYear)(params.dataIndex)

        val nextHour = if (hourInYear + 1 >= Utils.hoursInYear) 0 else hourInYear + 1

        val valueAtNextHourBoundary = data.hourlies(nextHour)(params.dataIndex)

        (valueAtNextHourBoundary - valueAtPrevHourBoundary) * fractionOfHour + valueAtPrevHourBoundary

      } catch {
        case ex: Throwable =>
          0.0
      }
    }

    val reduction = state.reductionStage match {
      case 1 => stageReduction1
      case 2 => stageReduction2
      case 3 => stageReduction3
      case _ => 0.0
    }

    currentPower - currentPower * reduction
  }
}
