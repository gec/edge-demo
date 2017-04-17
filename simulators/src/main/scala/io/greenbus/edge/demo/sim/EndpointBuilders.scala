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

import java.util.UUID

import io.greenbus.edge.api._
import io.greenbus.edge.api.stream.{ EndpointBuilder, KeyMetadata }
import io.greenbus.edge.data._
import play.api.libs.json.Json

object EndpointBuilders {

  def jsonKeyValue(json: String): Value = {
    ValueString(json)
  }

  def toVMap(map: Map[String, Value]): ValueMap = {
    ValueMap(map.map {
      case (k, v) => (ValueString(k), v)
    })
  }

  val pccBkr = "pccBkr"
  val custBkr = "custBkr"

  val faultType = "fault"
  val outputPowerType = "outputPower"
  val outputTargetType = "outputTarget"
  val bkrStatusType = "breakerStatus"

  val boolMappingKey = Path("boolMapping")

  val faultMapping = ValueList(Vector(
    toVMap(Map(
      "value" -> ValueBool(false),
      "name" -> ValueString("Clear"))),
    toVMap(Map(
      "value" -> ValueBool(true),
      "name" -> ValueString("Fault")))))
  val faultMappingKv = boolMappingKey -> faultMapping

  val breakerStatusMapping = ValueList(Vector(
    toVMap(Map(
      "value" -> ValueBool(false),
      "name" -> ValueString("Open"))),
    toVMap(Map(
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

    private val uuid = UUID.randomUUID()
    bkrTrip.update(OutputKeyStatus(uuid, 0, None))
    bkrClose.update(OutputKeyStatus(uuid, 0, None))

    val buffer = builder.build(20, 20)
  }

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

    val setTarget = builder.outputStatus(ChpMapping.setTarget, KeyMetadata(Map(Path("gridOutputType") -> ValueString("setOutputTarget")), Map(Path("simpleInputType") -> ValueString("double"))))
    val setTargetReceiver = builder.registerOutput(ChpMapping.setTarget)

    val faultEnable = builder.outputStatus(ChpMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultEnableReceiver = builder.registerOutput(ChpMapping.faultEnable)
    val faultDisable = builder.outputStatus(ChpMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultDisableReceiver = builder.registerOutput(ChpMapping.faultDisable)

    private val uuid = UUID.randomUUID()
    setTarget.update(OutputKeyStatus(uuid, 0, None))
    faultEnable.update(OutputKeyStatus(uuid, 0, None))
    faultDisable.update(OutputKeyStatus(uuid, 0, None))

    val buffer = builder.build(20, 20)
  }

  object EssPublisher {

    val modeMapping = ValueList(Vector(
      toVMap(Map(
        "index" -> ValueUInt32(0),
        "name" -> ValueString("Constant"))),
      toVMap(Map(
        "index" -> ValueUInt32(1),
        "name" -> ValueString("Smoothing"))),
      toVMap(Map(
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
    val mode = builder.seriesValue(EssMapping.mode, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString("essMode")), metadata = Map(EssPublisher.modeMapKv)))
    val socMax = builder.seriesValue(EssMapping.socMax, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString("socMax")), metadata = Map(Path("unit") -> ValueString("%"))))
    val socMin = builder.seriesValue(EssMapping.socMin, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString("socMin")), metadata = Map(Path("unit") -> ValueString("%"))))
    val chargeDischargeRate = builder.seriesValue(EssMapping.chargeDischargeRate, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputPowerType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val chargeRateMax = builder.seriesValue(EssMapping.chargeRateMax, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kW"))))
    val dischargeRateMax = builder.seriesValue(EssMapping.dischargeRateMax, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kW"))))
    val capacity = builder.seriesValue(EssMapping.capacity, KeyMetadata(metadata = Map(Path("unit") -> ValueString("kWh"))))
    val efficiency = builder.seriesValue(EssMapping.efficiency)
    val chargeRateTarget = builder.seriesValue(EssMapping.chargeRateTarget, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(outputTargetType)), metadata = Map(Path("unit") -> ValueString("kW"))))
    val faultStatus = builder.seriesValue(ChpMapping.faultStatus, KeyMetadata(indexes = Map(Path("gridValueType") -> ValueString(faultType)), metadata = Map(faultMappingKv)))

    val batteryMode = builder.outputStatus(EssMapping.setBatteryMode, KeyMetadata(Map(Path("gridOutputType") -> ValueString("setEssMode")), setModeMetadata))
    val batteryModeReceiver = builder.registerOutput(EssMapping.setBatteryMode)
    val setChargeRate = builder.outputStatus(EssMapping.setChargeRate, KeyMetadata(Map(Path("gridOutputType") -> ValueString("setOutputTarget")), Map(Path("simpleInputType") -> ValueString("double"))))
    val setChargeRateReceiver = builder.registerOutput(EssMapping.setChargeRate)

    val faultEnable = builder.outputStatus(EssMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultEnableReceiver = builder.registerOutput(EssMapping.faultEnable)
    val faultDisable = builder.outputStatus(EssMapping.faultEnable, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
    val faultDisableReceiver = builder.registerOutput(EssMapping.faultDisable)

    private val uuid = UUID.randomUUID()
    batteryMode.update(OutputKeyStatus(uuid, 0, None))
    setChargeRate.update(OutputKeyStatus(uuid, 0, None))
    faultEnable.update(OutputKeyStatus(uuid, 0, None))
    faultDisable.update(OutputKeyStatus(uuid, 0, None))

    val buffer = builder.build(20, 20)
  }

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

    private val uuid = UUID.randomUUID()
    faultEnable.update(OutputKeyStatus(uuid, 0, None))
    faultDisable.update(OutputKeyStatus(uuid, 0, None))

    val buffer = builder.build(20, 20)
  }

}