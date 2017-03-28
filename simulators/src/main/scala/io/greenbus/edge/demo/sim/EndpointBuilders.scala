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
import io.greenbus.edge.api.stream.{ EndpointBuilder, KeyMetadata, OutputStatusHandle }
import io.greenbus.edge.colset.gateway.{ AppendEventSink, EventSink }
import play.api.libs.json.Json

object EndpointBuilders {

  /*def tsDouble(v: Double, now: Long, indexes: Map[Path, IndexableValue] = Map(), meta: Map[Path, Value] = Map()): TimeSeriesValueEntry = {
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
  }*/
  val pccBkr = "pccBkr"
  val custBkr = "custBkr"

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

  class BreakerPublisher(pcc: Boolean, builder: EndpointBuilder) {

    private val gridType = if (pcc) pccBkr else custBkr
    builder.setIndexes(Map(Path("gridDeviceType") -> ValueString(gridType)))

    val events = builder.topicEventValue(BreakerMapping.events)

    val bkrPower = builder.seriesValue(BreakerMapping.bkrPower, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val bkrVoltage = builder.seriesValue(BreakerMapping.bkrVoltage, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kV"))))
    val bkrCurrent = builder.seriesValue(BreakerMapping.bkrCurrent, KeyMetadata(metadata = Map(Path("unit") -> ValueString("A"))))
    val bkrStatus = builder.seriesValue(BreakerMapping.bkrStatus, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(bkrStatusType), Path("bkrStatusRole") -> ValueString(gridType)), metadata = Map(breakerStatusMappingKv)))

    val bkrTrip = builder.outputStatus(BreakerMapping.bkrTrip, KeyMetadata(Map(Path("gridOutputType") -> ValueString(s"${gridType}Switch")), Map(Path("simpleInputType") -> ValueString("indication"))))
    val bkrTripReceiver = builder.registerOutput(BreakerMapping.bkrTrip)
    val bkrClose = builder.outputStatus(BreakerMapping.bkrClose, KeyMetadata(Map(Path("gridOutputType") -> ValueString(s"${gridType}Switch")), Map(Path("simpleInputType") -> ValueString("indication"))))
    val bkrCloseReceiver = builder.registerOutput(BreakerMapping.bkrClose)

    val buffer = builder.build(20, 20)
  }

  /*def buildBreaker(pcc: Boolean): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val gridType = if (pcc) "pccBkr" else "custBkr"

    val indexes = Map(Path("gridDeviceType") -> ValueString(gridType))
    val meta = Map.empty[Path, Value]
    val latestKvs = Map.empty[Path, LatestKeyValueEntry]

    val events = Map(BreakerMapping.events -> EventEntry(MetadataDesc(Map(), Map())))

    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      BreakerMapping.bkrPower -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      BreakerMapping.bkrVoltage -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kV"))),
      BreakerMapping.bkrCurrent -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("A"))),
      BreakerMapping.bkrStatus -> tsBool(true, now, indexes = Map(Path("gridValueType") -> ValueString(bkrStatusType)), meta = Map(breakerStatusMappingKv)))

    val outputs = Map(
      BreakerMapping.bkrTrip -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridOutputType") -> ValueString(s"${gridType}Switch")), Map(Path("simpleInputType") -> ValueString("indication")))),
      BreakerMapping.bkrClose -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridOutputType") -> ValueString(s"${gridType}Switch")), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }*/

  /*def buildLoad(params: LoadParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("gridDeviceType") -> ValueString("load"))
    val meta = Map.empty[Path, Value]

    val latestKvs = Map(
      LoadMapping.params -> kv(ValueString(Json.toJson(params).toString(), Some("application/json"))))

    val events = Map.empty[Path, EventEntry]
    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      LoadMapping.power -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      LoadMapping.voltage -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kV"))),
      LoadMapping.current -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("A"))),
      LoadMapping.kvar -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kVAR"))))

    val outputs = Map.empty[Path, OutputEntry]

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }*/

  class LoadPublisher(builder: EndpointBuilder) {

    builder.setIndexes(Map(Path("gridDeviceType") -> ValueString("gen")))

    val params = builder.latestKeyValue(LoadMapping.params)

    val power = builder.seriesValue(LoadMapping.power, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val voltage = builder.seriesValue(LoadMapping.voltage, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kV"))))
    val current = builder.seriesValue(LoadMapping.current, KeyMetadata(metadata = Map(Path("unit") -> ValueString("A"))))
    val kvar = builder.seriesValue(LoadMapping.kvar, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kVAR"))))

    val buffer = builder.build(20, 20)
  }

  class ChpPublisher(builder: EndpointBuilder) {

    builder.setIndexes(Map(Path("gridDeviceType") -> ValueString("gen")))

    val params = builder.latestKeyValue(ChpMapping.params)

    val events = builder.topicEventValue(ChpMapping.events)

    val power = builder.seriesValue(ChpMapping.power, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val powerCapacity = builder.seriesValue(ChpMapping.powerCapacity, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kW"))))
    val powerTarget = builder.seriesValue(ChpMapping.powerTarget, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputTargetType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val faultStatus = builder.seriesValue(ChpMapping.faultStatus, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(faultType)), metadata = Map(faultMappingKv)))

    val faultEnable = builder.outputStatus(ChpMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultEnableReceiver = builder.registerOutput(ChpMapping.faultEnable)
    val faultDisable = builder.outputStatus(ChpMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultDisableReceiver = builder.registerOutput(ChpMapping.faultDisable)

    val buffer = builder.build(20, 20)
  }

  /*def buildChp(params: ChpParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("gridDeviceType") -> ValueString("gen"))
    val meta = Map.empty[Path, Value]

    val latestKvs = Map(
      ChpMapping.params -> kv(ValueString(Json.toJson(params).toString(), Some("application/json"))))

    val events = Map(ChpMapping.events -> EventEntry(MetadataDesc(Map(), Map())))

    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      ChpMapping.power -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.powerCapacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.powerTarget -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputTargetType)), meta = Map(Path("unit") -> ValueString("kW"))),
      ChpMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueString(faultType)), meta = Map(faultMappingKv)))

    val outputs = Map(
      ChpMapping.setTarget -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridOutputType") -> ValueSimpleString("setOutputTarget")), Map(Path("simpleInputType") -> ValueString("double")))),
      ChpMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))),
      ChpMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }*/

  object EssPublisher {

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

    val setModeMetadata = Map(Path("simpleInputType") -> ValueString("integer"), modeMapKv)

  }
  class EssPublisher(builder: EndpointBuilder) {
    import EssPublisher._

    builder.setIndexes(Map(Path("gridDeviceType") -> ValueString("ess")))

    val params = builder.latestKeyValue(EssMapping.params)

    val events = builder.topicEventValue(EssMapping.events)

    val percentSoc = builder.seriesValue(EssMapping.percentSoc, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString("percentSoc")), metadata = Map(Path("unit") -> ValueString("%"))))
    val mode = builder.seriesValue(EssMapping.mode, KeyMetadata(metadata = Map(EssPublisher.modeMapKv)))
    val socMax = builder.seriesValue(EssMapping.socMax, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString("socMax")), metadata = Map(Path("unit") -> ValueString("%"))))
    val socMin = builder.seriesValue(EssMapping.socMin, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString("socMin")), metadata = Map(Path("unit") -> ValueString("%"))))
    val chargeDischargeRate = builder.seriesValue(EssMapping.chargeDischargeRate, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val chargeRateMax = builder.seriesValue(EssMapping.chargeRateMax, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kW"))))
    val dischargeRateMax = builder.seriesValue(EssMapping.dischargeRateMax, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kW"))))
    val capacity = builder.seriesValue(EssMapping.capacity, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kWh"))))
    val efficiency = builder.seriesValue(EssMapping.efficiency)
    val chargeRateTarget = builder.seriesValue(EssMapping.chargeRateTarget, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputTargetType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val faultStatus = builder.seriesValue(ChpMapping.faultStatus, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(faultType)), metadata = Map(faultMappingKv)))

    val batteryMode = builder.outputStatus(EssMapping.setBatteryMode, KeyMetadata(Map(Path("gridValueType") -> ValueString("setEssMode")), setModeMetadata))
    val batteryModeReceiver = builder.registerOutput(EssMapping.setBatteryMode)
    val setChargeRate = builder.outputStatus(EssMapping.setChargeRate, KeyMetadata(Map(Path("gridValueType") -> ValueString("setOutputTarget")), Map(Path("simpleInputType") -> ValueString("double"))))
    val setChargeRateReceiver = builder.registerOutput(EssMapping.setChargeRate)

    val faultEnable = builder.outputStatus(EssMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultEnableReceiver = builder.registerOutput(EssMapping.faultEnable)
    val faultDisable = builder.outputStatus(EssMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultDisableReceiver = builder.registerOutput(EssMapping.faultDisable)

    val buffer = builder.build(20, 20)
  }

  /*def buildEss(params: EssParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("gridDeviceType") -> ValueString("ess"))
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
      EssMapping.percentSoc -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString("percentSoc")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.mode -> tsEnum(0, now, meta = Map(modeMapKv)),
      EssMapping.socMax -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString("socMax")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.socMin -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString("socMin")), meta = Map(Path("unit") -> ValueString("%"))),
      EssMapping.chargeDischargeRate -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.chargeRateMax -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.dischargeRateMax -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.capacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kWh"))),
      EssMapping.efficiency -> tsDouble(0.0, now),
      EssMapping.chargeRateTarget -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputTargetType)), meta = Map(Path("unit") -> ValueString("kW"))),
      EssMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueString(faultType)), meta = Map(faultMappingKv)))

    val setModeMetadata = Map(Path("simpleInputType") -> ValueString("integer"), modeMapKv)

    val events = Map(EssMapping.events -> EventEntry(MetadataDesc(Map(), Map())))

    val outputs = Map(
      EssMapping.setBatteryMode -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridValueType") -> ValueString("setEssMode")), setModeMetadata)),
      EssMapping.setChargeRate -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(Path("gridValueType") -> ValueString("setOutputTarget")), Map(Path("simpleInputType") -> ValueString("double")))),
      EssMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))),
      EssMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }*/

  /*def buildPv(params: PvParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("gridDeviceType") -> ValueString("gen"))
    val meta = Map.empty[Path, Value]

    val latestKvs = Map(
      PvMapping.params -> kv(ValueString(Json.toJson(params).toString(), Some("application/json"))))

    val events = Map(PvMapping.events -> EventEntry(MetadataDesc(Map(), Map())))

    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      PvMapping.pvOutputPower -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),
      PvMapping.pvCapacity -> tsDouble(0.0, now, meta = Map(Path("unit") -> ValueString("kW"))),
      PvMapping.faultStatus -> tsBool(false, now, indexes = Map(Path("gridValueType") -> ValueString(faultType)), meta = Map(faultMappingKv)))

    val outputs = Map(
      PvMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))),
      PvMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }*/

  class PvPublisher(builder: EndpointBuilder) {

    builder.setIndexes(Map(Path("gridDeviceType") -> ValueString("gen")))

    val params = builder.latestKeyValue(PvMapping.params)

    val events = builder.topicEventValue(PvMapping.events)

    val pvOutputPower = builder.seriesValue(PvMapping.pvOutputPower, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val pvCapacity = builder.seriesValue(PvMapping.pvCapacity, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kW"))))
    val faultStatus = builder.seriesValue(PvMapping.faultStatus, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(faultType)), metadata = Map(faultMappingKv)))

    val faultEnable = builder.outputStatus(PvMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultEnableReceiver = builder.registerOutput(PvMapping.faultEnable)
    val faultDisable = builder.outputStatus(PvMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultDisableReceiver = builder.registerOutput(PvMapping.faultDisable)

    val buffer = builder.build(20, 20)
  }

}