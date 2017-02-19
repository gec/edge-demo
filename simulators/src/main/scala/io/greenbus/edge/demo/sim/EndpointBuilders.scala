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
import io.greenbus.edge.client._

object EndpointBuilders {

  def tsDouble(v: Double, now: Long, indexes: Map[Path, IndexableValue] = Map(), meta: Map[Path, Value] = Map()): TimeSeriesValueEntry = {
    TimeSeriesValueEntry(
      TimeSeriesSample(now, ValueDouble(v)),
      MetadataDesc(indexes, meta))
  }
  def tsEnum(v: Int, now: Long, indexes: Map[Path, IndexableValue] = Map(), meta: Map[Path, Value] = Map()): TimeSeriesValueEntry = {
    TimeSeriesValueEntry(
      TimeSeriesSample(now, ValueUInt64(v)),
      MetadataDesc(indexes, meta))
  }
  def tsBool(v: Boolean, now: Long, indexes: Map[Path, IndexableValue] = Map(), meta: Map[Path, Value] = Map()): TimeSeriesValueEntry = {
    TimeSeriesValueEntry(
      TimeSeriesSample(now, ValueBool(v)),
      MetadataDesc(indexes, meta))
  }

  val faultType = "fault"
  val outputPowerType = "outputPower"
  val outputTargetType = "outputTarget"

  def buildLoad(): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map.empty[Path, IndexableValue]
    val meta = Map.empty[Path, Value]
    val latestKvs = Map.empty[Path, LatestKeyValueEntry]

    val timeSeries = Map(
      LoadMapping.power -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      LoadMapping.voltage -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("V"))),
      LoadMapping.current -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("A"))),
      LoadMapping.kvar -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kVAR"))))

    val outputs = Map.empty[Path, OutputEntry]

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, outputs)
  }

  def buildChp(): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map.empty[Path, IndexableValue]
    val meta = Map.empty[Path, Value]
    val latestKvs = Map.empty[Path, LatestKeyValueEntry]

    val timeSeries = Map(
      ChpMapping.power -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.powerCapacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.powerTarget -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputTargetType)), meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueString(faultType))))

    val outputs = Map(
      ChpMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map())),
      ChpMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map())))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, outputs)
  }

  def buildEss(): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map.empty[Path, IndexableValue]
    val meta = Map.empty[Path, Value]
    val latestKvs = Map.empty[Path, LatestKeyValueEntry]

    val timeSeries = Map(
      EssMapping.percentSoc -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString("percentSoc")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.mode -> tsEnum(0, now),
      EssMapping.socMax -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString("socMax")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.socMin -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString("socMin")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.chargeDischargeRate -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.chargeRateMax -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.dischargeRateMax -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.capacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kWh"))),
      EssMapping.efficiency -> tsDouble(0.0, now),
      EssMapping.chargeRateTarget -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputTargetType)), meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueString(faultType))))

    val outputs = Map(
      PvMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map())),
      PvMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map())))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, outputs)
  }

  def buildPv(): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map.empty[Path, IndexableValue]
    val meta = Map.empty[Path, Value]
    val latestKvs = Map.empty[Path, LatestKeyValueEntry]


    val timeSeries = Map(
      PvMapping.pvOutputPower -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      PvMapping.pvCapacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      PvMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueString(faultType))))

    /*val timeSeries = Map(
      PvMapping.pvOutputPower -> TimeSeriesValueEntry(
        TimeSeriesSample(System.currentTimeMillis(), ValueDouble(0.0)),
        MetadataDesc(
          Map(Path("gridValueType") -> ValueString("outputPower")),
          Map())),
      PvMapping.pvCapacity -> TimeSeriesValueEntry(
        TimeSeriesSample(System.currentTimeMillis(), ValueDouble(0.0)),
        MetadataDesc(
          Map(),
          Map())),
      PvMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueString(faultType))))*/

    val outputs = Map(
      PvMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map())),
      PvMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map())))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, outputs)
  }

}