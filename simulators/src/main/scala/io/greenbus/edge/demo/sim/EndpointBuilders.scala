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
import play.api.libs.json.Json

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

  def kv(v: Value, indexes: Map[Path, IndexableValue] = Map(), meta: Map[Path, Value] = Map()): LatestKeyValueEntry = {
    LatestKeyValueEntry(
      v, MetadataDesc(indexes, meta))
  }

  val faultType = "fault"
  val outputPowerType = "outputPower"
  val outputTargetType = "outputTarget"
  val bkrStatusType = "breakerStatus"

  val boolMappingKey = Path("boolMapping")

  val faultMapping = ValueArray(Vector(
    ValueObject(Map(
      "value" -> ValueBool(false),
      "name" -> ValueString("Clear"))),
    ValueObject(Map(
      "value" -> ValueBool(true),
      "name" -> ValueString("Fault")))))
  val faultMappingKv = boolMappingKey -> faultMapping

  val breakerStatusMapping = ValueArray(Vector(
    ValueObject(Map(
      "value" -> ValueBool(false),
      "name" -> ValueString("Open"))),
    ValueObject(Map(
      "value" -> ValueBool(true),
      "name" -> ValueString("Closed")))))
  val breakerStatusMappingKv = boolMappingKey -> breakerStatusMapping

  def buildBreaker(pcc: Boolean): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val gridType = if (pcc) "pccBkr" else "custBkr"

    val indexes = Map(Path("gridDeviceType") -> ValueSimpleString(gridType))
    val meta = Map.empty[Path, Value]
    val latestKvs = Map.empty[Path, LatestKeyValueEntry]

    val events = Map(BreakerMapping.events -> EventEntry(MetadataDesc(Map(), Map())))

    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      BreakerMapping.bkrPower -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      BreakerMapping.bkrVoltage -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kV"))),
      BreakerMapping.bkrCurrent -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("A"))),
      BreakerMapping.bkrStatus -> tsBool(true, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(bkrStatusType)), meta = Map(breakerStatusMappingKv)))

    val outputs = Map(
      BreakerMapping.bkrTrip -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridOutputType") -> ValueSimpleString(s"${gridType}Trip")), Map(Path("simpleInputType") -> ValueString("indication")))),
      BreakerMapping.bkrClose -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridOutputType") -> ValueSimpleString(s"${gridType}Close")), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }

  def buildLoad(params: LoadParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("gridDeviceType") -> ValueSimpleString("load"))
    val meta = Map.empty[Path, Value]

    val latestKvs = Map(
      LoadMapping.params -> kv(ValueString(Json.toJson(params).toString(), Some("application/json"))))

    val events = Map.empty[Path, EventEntry]
    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      LoadMapping.power -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      LoadMapping.voltage -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kV"))),
      LoadMapping.current -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("A"))),
      LoadMapping.kvar -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kVAR"))))

    val outputs = Map.empty[Path, OutputEntry]

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }

  def buildChp(params: ChpParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("gridDeviceType") -> ValueSimpleString("gen"))
    val meta = Map.empty[Path, Value]

    val latestKvs = Map(
      ChpMapping.params -> kv(ValueString(Json.toJson(params).toString(), Some("application/json"))))

    val events = Map(ChpMapping.events -> EventEntry(MetadataDesc(Map(), Map())))

    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      ChpMapping.power -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.powerCapacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.powerTarget -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(outputTargetType)), meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(faultType)), meta = Map(faultMappingKv)))

    val outputs = Map(
      ChpMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))),
      ChpMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }

  def buildEss(params: EssParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("gridDeviceType") -> ValueSimpleString("ess"))
    val meta = Map.empty[Path, Value]

    val latestKvs = Map(
      EssMapping.params -> kv(ValueString(Json.toJson(params).toString(), Some("application/json"))))

    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val modeMapping = ValueArray(Vector(
      ValueObject(Map(
        "index" -> ValueUInt32(0),
        "name" -> ValueString("Constant"))),
      ValueObject(Map(
        "index" -> ValueUInt32(1),
        "name" -> ValueString("Smoothing"))),
      ValueObject(Map(
        "index" -> ValueUInt32(2),
        "name" -> ValueString("GridForming")))))

    val modeMapKv = Path("integerMapping") -> modeMapping

    val timeSeries = Map(
      EssMapping.percentSoc -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString("percentSoc")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.mode -> tsEnum(0, now, meta = Map(modeMapKv)),
      EssMapping.socMax -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString("socMax")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.socMin -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString("socMin")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.chargeDischargeRate -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.chargeRateMax -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.dischargeRateMax -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.capacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kWh"))),
      EssMapping.efficiency -> tsDouble(0.0, now),
      EssMapping.chargeRateTarget -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(outputTargetType)), meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(faultType)), meta = Map(faultMappingKv)))

    val setModeMetadata = Map(Path("simpleInputType") -> ValueString("integer"), modeMapKv)

    val events = Map(EssMapping.events -> EventEntry(MetadataDesc(Map(), Map())))

    val outputs = Map(
      EssMapping.setBatteryMode -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridValueType") -> ValueSimpleString("setEssMode")), setModeMetadata)),
      EssMapping.setChargeRate -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridValueType") -> ValueSimpleString("setOutputTarget")), Map(Path("simpleInputType") -> ValueString("double")))),
      EssMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))),
      EssMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }

  def buildPv(params: PvParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("gridDeviceType") -> ValueSimpleString("gen"))
    val meta = Map.empty[Path, Value]

    val latestKvs = Map(
      PvMapping.params -> kv(ValueString(Json.toJson(params).toString(), Some("application/json"))))

    val events = Map(PvMapping.events -> EventEntry(MetadataDesc(Map(), Map())))

    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      PvMapping.pvOutputPower -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      PvMapping.pvCapacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      PvMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(faultType)), meta = Map(faultMappingKv)))

    val outputs = Map(
      PvMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))),
      PvMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }

}