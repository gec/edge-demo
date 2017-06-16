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

import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge.api.{ EndpointId, Path, ProducerService }
import io.greenbus.edge.demo.sim.EndpointBuilders._
import io.greenbus.edge.thread.CallMarshaller

class SimulatorMgr(eventThread: CallMarshaller, load: LoadRecord, ctx: SimulatorContext, service: ProducerService) extends LazyLogging {

  private val pvParams = PvParams.basic
  private val pv1 = new PvSim(pvParams, PvSim.PvState(1.0, fault = false), new PvPublisher(service.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "PV")))))

  private val essParams = EssParams.basic
  private val ess1 = new EssSim(essParams, EssSim.EssState(EssSim.Constant, 0.5 * essParams.capacity, 0.0, 0.0, false), new EssPublisher(service.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "ESS")))))

  private val chpParams = ChpParams.basic
  private val chp1 = new ChpSim(chpParams, ChpSim.ChpState(0.0, 0.0, false), new ChpPublisher(service.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "CHP")))))

  private val loadParams = LoadMapping.defaultParams
  private val load1 = new LoadSim(loadParams, load, LoadSim.LoadState(0), new LoadPublisher(service.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "Load")))))

  private val pccBkr = new BreakerSim(true, new BreakerPublisher(true, service.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "PCC_BKR")))))

  private val custBkr = new BreakerSim(true, new BreakerPublisher(false, service.endpointBuilder(EndpointId(Path(ctx.equipmentPrefix :+ "CUST_BKR")))))

  private val simulators = Seq(pv1, ess1, chp1, load1, pccBkr, custBkr)

  private val simulator = new Simulator(
    SimulatorState(0.0, 0.0, 0.0, 0.0),
    pccBkr = pccBkr,
    custBkr = custBkr,
    chps = Seq(chp1),
    esses = Seq(ess1),
    pvs = Seq(pv1),
    loads = Seq(load1))

  // TODO: need to run tick() when a device changes
  def tick(): Unit = {
    val now = System.currentTimeMillis()
    val lineState = simulator.tick(now)
    simulators.foreach(_.updates(lineState, now))
  }
}
