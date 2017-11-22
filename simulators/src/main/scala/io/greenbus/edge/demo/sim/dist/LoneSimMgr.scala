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

import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge.api._
import io.greenbus.edge.demo.sim.EndpointBuilders.{ BreakerPublisher, EssPublisher }
import io.greenbus.edge.demo.sim._
import io.greenbus.edge.demo.sim.dist.LoneSimMgr.MeasState
import io.greenbus.edge.thread.CallMarshaller

class SimSubscribeConfig(chpId: EndpointId, loadId: EndpointId, pvId: EndpointId) {

  val chpPath = EndpointPath(chpId, ChpMapping.power)
  val loadPath = EndpointPath(loadId, LoadMapping.power)
  val pvPath = EndpointPath(pvId, PvMapping.pvOutputPower)

  def params: SubscriptionParams = {
    SubscriptionParams(dataKeys = Set(chpPath, loadPath, pvPath))
  }
}

object LoneSimMgr {

  def subscriptionParams(chpId: EndpointId, loadId: EndpointId, pvId: EndpointId): SubscriptionParams = {

    val chpPath = EndpointPath(chpId, ChpMapping.power)
    val loadPath = EndpointPath(loadId, LoadMapping.power)
    val pvPath = EndpointPath(pvId, PvMapping.pvOutputPower)

    SubscriptionParams(dataKeys = Set(chpPath, loadPath, pvPath))
  }

  case class MeasState(load: Option[Double], pv: Option[Double], chp: Option[Double])

  private def doubleUpdateOpt(status: EdgeDataStatus[DataKeyUpdate]): Option[Double] = {
    status match {
      case ResolvedValue(up) =>
        up.value match {
          case series: SeriesUpdate =>
            Some(series.value.toDouble)
          case _ => None
        }
      case _ => None
    }
  }
}
class LoneSimMgr(eventThread: CallMarshaller, ctx: SimulatorContext, producerService: ProducerService, consumerService: ConsumerService, subConfig: SimSubscribeConfig) extends Tickable with LazyLogging {

  private val essParams = EssParams.basic
  private val ess1 = new EssSim(essParams, EssSim.EssState(EssSim.Constant, 0.5 * essParams.capacity, 0.0, 0.0, false), new EssPublisher(producerService.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "ESS")))))

  private val pccBkr = new BreakerSim(true, new BreakerPublisher(true, producerService.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "PCC_BKR")))))

  private val custBkr = new BreakerSim(true, new BreakerPublisher(false, producerService.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "CUST_BKR")))))

  private val simulator = new LoneSimulator(SimulatorState(0.0, 0.0, 0.0, 0.0), pccBkr, custBkr, Seq(ess1))

  private val simulators = Seq(ess1, pccBkr, custBkr)

  private var state = MeasState(None, None, None)

  private val subscription = {
    val client = consumerService.subscriptionClient

    val sub = client.subscribe(subConfig.params)

    sub.updates.bind(updates => {
      eventThread.marshal(handleUpdates(updates))
    })

    sub
  }

  def tick(): Unit = {
    for {
      load <- state.load
      pv <- state.pv
      chp <- state.chp
    } {
      val now = System.currentTimeMillis()
      val lineState = simulator.tick(now, pv, chp, 0.0, load)
      simulators.foreach(_.updates(lineState, now))
    }
  }

  def close(): Unit = {
    subscription.close()
  }

  private def handleUpdates(updates: Seq[IdentifiedEdgeUpdate]): Unit = {
    updates.foreach {
      case up: IdDataKeyUpdate =>
        up.id match {
          case subConfig.loadPath =>
            LoneSimMgr.doubleUpdateOpt(up.data).foreach { value =>
              state = state.copy(load = Some(value))
            }
          case subConfig.pvPath =>
            LoneSimMgr.doubleUpdateOpt(up.data).foreach { value =>
              state = state.copy(pv = Some(value))
            }
          case subConfig.chpPath =>
            LoneSimMgr.doubleUpdateOpt(up.data).foreach { value =>
              state = state.copy(chp = Some(value))
            }
          case _ =>
        }
      case _ =>
    }
  }
}
