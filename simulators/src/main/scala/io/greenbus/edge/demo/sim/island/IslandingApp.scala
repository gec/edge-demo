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
package io.greenbus.edge.demo.sim.island

import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge.api._
import io.greenbus.edge.api.stream.ServiceClient
import io.greenbus.edge.demo.sim.EssSim.GridForming
import io.greenbus.edge.demo.sim.{ BreakerMapping, ChpMapping, EssMapping, EssSim }
import io.greenbus.edge.flow
import io.greenbus.edge.flow.Sender
import io.greenbus.edge.thread.CallMarshaller

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Promise }

object ControlParams {
  import play.api.libs.json._
  implicit val writer = Json.writes[ControlParams]
  implicit val reader = Json.reads[ControlParams]
}
case class ControlParams(microgridName: String, chpRampedUp: Double, chpNominal: Double, chargingBatteryRate: Double)

object SenderHelpers {

  def request[A, B](sender: Sender[A, B], obj: A): Future[B] = {
    val promise = Promise[B]
    sender.send(obj, promise.complete)
    promise.future
  }

}

class IslandingApp(eventThread: CallMarshaller, publisher: IslandAppPublisher, subscriber: IslandAppSubscriber, services: ServiceClient, params: ControlParams) extends LazyLogging {

  private var enabled = false
  private var pccClosed = true

  def init(): Unit = {
    publisher.enabled.update(ValueBool(enabled), System.currentTimeMillis())
    publisher.flush()
  }

  publisher.setEnableRcv.bind(new flow.Responder[OutputParams, OutputResult] {
    override def handle(obj: OutputParams, respond: (OutputResult) => Unit): Unit = {
      respond(OutputSuccess(None))
      handleEnable()
    }
  })

  publisher.setDisableRcv.bind(new flow.Responder[OutputParams, OutputResult] {
    override def handle(obj: OutputParams, respond: (OutputResult) => Unit): Unit = {
      respond(OutputSuccess(None))
      handleDisable()
    }
  })

  private def handleState(state: BreakerStates): Unit = {
    if (pccClosed && !state.pcc) {
      onTrip()
      pccClosed = false
    } else if (!pccClosed && state.pcc) {
      pccClosed = true
      onClose()
    }
  }

  private def onTrip(): Unit = {

    val tripPath = EndpointPath(EndpointId(Path(Seq("Rankin", "MGRID", "CUST_BKR"))), BreakerMapping.bkrTrip)
    val tripFut = SenderHelpers.request(services, OutputRequest(tripPath, OutputParams()))

    val battEndPath = EndpointPath(EndpointId(Path(Seq("Rankin", "MGRID", "ESS"))), EssMapping.setBatteryMode)
    val battFut = SenderHelpers.request(services, OutputRequest(battEndPath, OutputParams(outputValueOpt = Some(ValueUInt32(GridForming.numeric)))))

    val chpEndPath = EndpointPath(EndpointId(Path(Seq("Rankin", "MGRID", "CHP"))), ChpMapping.setTarget)
    val chpFut = SenderHelpers.request(services, OutputRequest(chpEndPath, OutputParams(outputValueOpt = Some(ValueDouble(params.chpRampedUp)))))

    publisher.events.update(Path(Seq("application", "action")), ValueString("Executed actions on PCC trip"), System.currentTimeMillis())
    publisher.flush()

    val allFut = Future.sequence(Seq(tripFut, battFut, chpFut))

    allFut.foreach { results =>
      logger.info("Commands successful: " + results)
      publisher.events.update(Path(Seq("application", "action")), ValueString("Executed actions on PCC trip success"), System.currentTimeMillis())
      publisher.flush()
    }

    allFut.failed.foreach { results =>
      logger.info("Commands failued: " + results)
      publisher.events.update(Path(Seq("application", "action")), ValueString("Executed actions on PCC trip failure"), System.currentTimeMillis())
      publisher.flush()
    }
  }

  private def onClose(): Unit = {

    val closePath = EndpointPath(EndpointId(Path(Seq("Rankin", "MGRID", "CUST_BKR"))), BreakerMapping.bkrClose)
    val closeFut = SenderHelpers.request(services, OutputRequest(closePath, OutputParams()))

    val battId = EndpointId(Path(Seq("Rankin", "MGRID", "ESS")))
    val battFut = SenderHelpers.request(services, OutputRequest(EndpointPath(battId, EssMapping.setBatteryMode), OutputParams(outputValueOpt = Some(ValueUInt32(EssSim.Constant.numeric)))))
    val battSpFut = SenderHelpers.request(services, OutputRequest(EndpointPath(battId, EssMapping.setChargeRate), OutputParams(outputValueOpt = Some(ValueDouble(params.chargingBatteryRate)))))

    val chpEndPath = EndpointPath(EndpointId(Path(Seq("Rankin", "MGRID", "CHP"))), ChpMapping.setTarget)
    val chpFut = SenderHelpers.request(services, OutputRequest(chpEndPath, OutputParams(outputValueOpt = Some(ValueDouble(params.chpNominal)))))

    publisher.events.update(Path(Seq("application", "action")), ValueString("Executed actions on PCC close"), System.currentTimeMillis())
    publisher.flush()

    val allFut = Future.sequence(Seq(closeFut, battFut, battSpFut, chpFut))

    allFut.foreach { results =>
      logger.info("Commands successful: " + results)
      publisher.events.update(Path(Seq("application", "action")), ValueString("Executed actions on PCC close success"), System.currentTimeMillis())
      publisher.flush()
    }

    allFut.failed.foreach { results =>
      logger.info("Commands failued: " + results)
      publisher.events.update(Path(Seq("application", "action")), ValueString("Executed actions on PCC close failure"), System.currentTimeMillis())
      publisher.flush()
    }
  }

  private def postEvent(path: Seq[String], value: String): Unit = {
    publisher.events.update(Path(path), ValueString(value), System.currentTimeMillis())
  }

  private def handleEnable(): Unit = {
    enabled = true
    postEvent(Seq("application", "status"), "Application enabled")
    publisher.flush()
  }

  private def handleDisable(): Unit = {
    enabled = false
    postEvent(Seq("application", "status"), "Application disabled")
    publisher.flush()
  }

}
