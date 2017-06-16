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
import io.greenbus.edge.data._
import io.greenbus.edge.edm.core.EdgeCoreModel
import play.api.libs.json.Json

object EndpointBuilders {

  def jsonKeyValue(json: String): Value = {
    ValueString(json)
  }

  def analogStatusMetadata(unit: String, decimalPoints: Int, other: (Path, Value)*): Map[Path, Value] = {
    Map(
      EdgeCoreModel.seriesType(EdgeCoreModel.SeriesType.AnalogStatus),
      EdgeCoreModel.unitMetadata(unit),
      EdgeCoreModel.analogDecimalPoints(decimalPoints)) ++
      other
  }

  def labeledBool(truth: String, falsity: String, other: (Path, Value)*): Map[Path, Value] = {
    Map(
      EdgeCoreModel.seriesType(EdgeCoreModel.SeriesType.BooleanStatus),
      EdgeCoreModel.labeledBooleanMetadata(truth, falsity)) ++
      other
  }

  def labeledInteger(labels: Map[Long, String], other: (Path, Value)*): Map[Path, Value] = {
    Map(
      EdgeCoreModel.seriesType(EdgeCoreModel.SeriesType.IntegerEnum),
      EdgeCoreModel.labeledIntegerMetadata(labels)) ++
      other
  }

  def indicationOutput(other: (Path, Value)*): Map[Path, Value] = {
    Map(
      EdgeCoreModel.outputType(EdgeCoreModel.OutputType.SimpleIndication)) ++ other
  }

  def doubleSetpointOutput(other: (Path, Value)*): Map[Path, Value] = {
    Map(
      EdgeCoreModel.outputType(EdgeCoreModel.OutputType.AnalogSetpoint)) ++ other
  }

  def labeledEnumOutput(labels: Map[Long, String], other: (Path, Value)*): Map[Path, Value] = {
    Map(
      EdgeCoreModel.outputType(EdgeCoreModel.OutputType.EnumerationSetpoint),
      EdgeCoreModel.requestIntegerLabels(labels)) ++ other
  }

  val pccBkr = "pccBkr"
  val custBkr = "custBkr"

  val faultType = "fault"
  val outputPowerType = "outputPower"
  val outputTargetType = "outputTarget"
  val bkrStatusType = "breakerStatus"

  val faultMappingKv = EdgeCoreModel.labeledBooleanMetadata("Fault", "Clear")

  val breakerStatusMappingKv = EdgeCoreModel.labeledBooleanMetadata("Closed", "Open")

  class BreakerPublisher(pcc: Boolean, builder: EndpointBuilder) {

    private val gridType = if (pcc) pccBkr else custBkr
    builder.setMetadata(Map(Path("gridDeviceType") -> ValueString(gridType)))

    val events = builder.topicEventValue(BreakerMapping.events)

    val bkrPower = builder.seriesValue(BreakerMapping.bkrPower, KeyMetadata(metadata = analogStatusMetadata("kW", 2, Path("gridValueType") -> ValueString(outputPowerType))))
    val bkrVoltage = builder.seriesValue(BreakerMapping.bkrVoltage, KeyMetadata(metadata = analogStatusMetadata("kV", 2)))
    val bkrCurrent = builder.seriesValue(BreakerMapping.bkrCurrent, KeyMetadata(metadata = analogStatusMetadata("A", 2)))
    val bkrStatus = builder.seriesValue(BreakerMapping.bkrStatus, KeyMetadata(metadata = labeledBool("Closed", "Open", Path("gridValueType") -> ValueString(bkrStatusType), Path("bkrStatusRole") -> ValueString(gridType))))

    val bkrTrip = builder.outputStatus(BreakerMapping.bkrTrip, KeyMetadata(metadata = indicationOutput(Path("gridOutputType") -> ValueString(s"${gridType}Switch"))))
    val bkrTripReceiver = builder.registerOutput(BreakerMapping.bkrTrip)
    val bkrClose = builder.outputStatus(BreakerMapping.bkrClose, KeyMetadata(metadata = indicationOutput(Path("gridOutputType") -> ValueString(s"${gridType}Switch"))))
    val bkrCloseReceiver = builder.registerOutput(BreakerMapping.bkrClose)

    private val uuid = UUID.randomUUID()
    bkrTrip.update(OutputKeyStatus(uuid, 0, None))
    bkrClose.update(OutputKeyStatus(uuid, 0, None))

    val buffer = builder.build()
  }

  class LoadPublisher(builder: EndpointBuilder) {

    builder.setMetadata(Map(Path("gridDeviceType") -> ValueString("gen")))

    val params = builder.latestKeyValue(LoadMapping.params)

    val power = builder.seriesValue(LoadMapping.power, KeyMetadata(metadata = analogStatusMetadata("kW", 2, Path("gridValueType") -> ValueString(outputPowerType))))
    val voltage = builder.seriesValue(LoadMapping.voltage, KeyMetadata(metadata = analogStatusMetadata("kV", 2)))
    val current = builder.seriesValue(LoadMapping.current, KeyMetadata(metadata = analogStatusMetadata("A", 2)))
    val kvar = builder.seriesValue(LoadMapping.kvar, KeyMetadata(metadata = analogStatusMetadata("kVAR", 2)))

    val buffer = builder.build()
  }

  class ChpPublisher(builder: EndpointBuilder) {

    builder.setMetadata(Map(Path("gridDeviceType") -> ValueString("gen")))

    val params = builder.latestKeyValue(ChpMapping.params)

    val events = builder.topicEventValue(ChpMapping.events)

    val power = builder.seriesValue(ChpMapping.power, KeyMetadata(metadata = analogStatusMetadata("kW", 2, Path("gridValueType") -> ValueString(outputPowerType))))
    val powerCapacity = builder.seriesValue(ChpMapping.powerCapacity, KeyMetadata(metadata = analogStatusMetadata("kW", 2)))
    val powerTarget = builder.seriesValue(ChpMapping.powerTarget, KeyMetadata(metadata = analogStatusMetadata("kW", 2, Path("gridValueType") -> ValueString(outputTargetType))))
    val faultStatus = builder.seriesValue(ChpMapping.faultStatus, KeyMetadata(metadata = labeledBool("Fault", "Clear", Path("gridValueType") -> ValueString(faultType))))

    val setTarget = builder.outputStatus(ChpMapping.setTarget, KeyMetadata(metadata = doubleSetpointOutput(Path("gridOutputType") -> ValueString("setOutputTarget"))))
    val setTargetReceiver = builder.registerOutput(ChpMapping.setTarget)

    val faultEnable = builder.outputStatus(ChpMapping.faultEnable, KeyMetadata(metadata = indicationOutput()))
    val faultEnableReceiver = builder.registerOutput(ChpMapping.faultEnable)
    val faultDisable = builder.outputStatus(ChpMapping.faultEnable, KeyMetadata(metadata = indicationOutput()))
    val faultDisableReceiver = builder.registerOutput(ChpMapping.faultDisable)

    private val uuid = UUID.randomUUID()
    setTarget.update(OutputKeyStatus(uuid, 0, None))
    faultEnable.update(OutputKeyStatus(uuid, 0, None))
    faultDisable.update(OutputKeyStatus(uuid, 0, None))

    val buffer = builder.build()
  }

  object EssPublisher {

    val modeMapping = Map(0L -> "Constant", 1L -> "Smoothing", 2L -> "GridForming")
  }
  class EssPublisher(builder: EndpointBuilder) {
    import EssPublisher._

    builder.setMetadata(Map(Path("gridDeviceType") -> ValueString("ess")))

    val params = builder.latestKeyValue(EssMapping.params)

    val events = builder.topicEventValue(EssMapping.events)

    val percentSoc = builder.seriesValue(EssMapping.percentSoc, KeyMetadata(metadata = analogStatusMetadata("%", 2, Path("gridValueType") -> ValueString("percentSoc"))))
    val mode = builder.seriesValue(EssMapping.mode, KeyMetadata(metadata = labeledInteger(modeMapping, Path("gridValueType") -> ValueString("essMode"))))
    val socMax = builder.seriesValue(EssMapping.socMax, KeyMetadata(metadata = analogStatusMetadata("%", 2, Path("gridValueType") -> ValueString("socMax"))))
    val socMin = builder.seriesValue(EssMapping.socMin, KeyMetadata(metadata = analogStatusMetadata("%", 2, Path("gridValueType") -> ValueString("socMin"))))
    val chargeDischargeRate = builder.seriesValue(EssMapping.chargeDischargeRate, KeyMetadata(metadata = analogStatusMetadata("kW", 2, Path("gridValueType") -> ValueString(outputPowerType))))
    val chargeRateMax = builder.seriesValue(EssMapping.chargeRateMax, KeyMetadata(metadata = analogStatusMetadata("kW", 2)))
    val dischargeRateMax = builder.seriesValue(EssMapping.dischargeRateMax, KeyMetadata(metadata = analogStatusMetadata("kW", 2)))
    val capacity = builder.seriesValue(EssMapping.capacity, KeyMetadata(metadata = analogStatusMetadata("kWh", 2)))
    val efficiency = builder.seriesValue(EssMapping.efficiency, KeyMetadata(metadata = analogStatusMetadata("", 2)))
    val chargeRateTarget = builder.seriesValue(EssMapping.chargeRateTarget, KeyMetadata(metadata = analogStatusMetadata("kW", 2, Path("gridValueType") -> ValueString(outputTargetType))))
    val faultStatus = builder.seriesValue(ChpMapping.faultStatus, KeyMetadata(metadata = labeledBool("Fault", "Clear", Path("gridValueType") -> ValueString(faultType))))

    val batteryMode = builder.outputStatus(EssMapping.setBatteryMode, KeyMetadata(metadata = labeledEnumOutput(modeMapping, Path("gridOutputType") -> ValueString("setEssMode"))))
    val batteryModeReceiver = builder.registerOutput(EssMapping.setBatteryMode)
    val setChargeRate = builder.outputStatus(EssMapping.setChargeRate, KeyMetadata(metadata = doubleSetpointOutput(Path("gridOutputType") -> ValueString("setOutputTarget"))))
    val setChargeRateReceiver = builder.registerOutput(EssMapping.setChargeRate)

    val faultEnable = builder.outputStatus(EssMapping.faultEnable, KeyMetadata(metadata = indicationOutput()))
    val faultEnableReceiver = builder.registerOutput(EssMapping.faultEnable)
    val faultDisable = builder.outputStatus(EssMapping.faultEnable, KeyMetadata(metadata = indicationOutput()))
    val faultDisableReceiver = builder.registerOutput(EssMapping.faultDisable)

    private val uuid = UUID.randomUUID()
    batteryMode.update(OutputKeyStatus(uuid, 0, None))
    setChargeRate.update(OutputKeyStatus(uuid, 0, None))
    faultEnable.update(OutputKeyStatus(uuid, 0, None))
    faultDisable.update(OutputKeyStatus(uuid, 0, None))

    val buffer = builder.build()
  }

  class PvPublisher(builder: EndpointBuilder) {

    builder.setMetadata(Map(Path("gridDeviceType") -> ValueString("gen")))

    val params = builder.latestKeyValue(PvMapping.params)

    val events = builder.topicEventValue(PvMapping.events)

    val pvOutputPower = builder.seriesValue(PvMapping.pvOutputPower, KeyMetadata(metadata = analogStatusMetadata("kW", 2, Path("gridValueType") -> ValueString(outputPowerType))))
    val pvCapacity = builder.seriesValue(PvMapping.pvCapacity, KeyMetadata(metadata = analogStatusMetadata("kW", 2)))
    val faultStatus = builder.seriesValue(PvMapping.faultStatus, KeyMetadata(metadata = labeledBool("Fault", "Clear", Path("gridValueType") -> ValueString(faultType))))

    val faultEnable = builder.outputStatus(PvMapping.faultEnable, KeyMetadata(metadata = indicationOutput()))
    val faultEnableReceiver = builder.registerOutput(PvMapping.faultEnable)
    val faultDisable = builder.outputStatus(PvMapping.faultEnable, KeyMetadata(metadata = indicationOutput()))
    val faultDisableReceiver = builder.registerOutput(PvMapping.faultDisable)

    private val uuid = UUID.randomUUID()
    faultEnable.update(OutputKeyStatus(uuid, 0, None))
    faultDisable.update(OutputKeyStatus(uuid, 0, None))

    val buffer = builder.build()
  }

}