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
import io.greenbus.edge.client._
import io.greenbus.edge._

class SimulatorPublisherPair(eventThread: CallMarshaller, sim: SimulatorComponent, endId: String, desc: ClientEndpointPublisherDesc) {
  private val id = NamedEndpointId(endId)
  private val endpoint = new EndpointPublisherImpl(eventThread, id, desc)
  def simulator: SimulatorComponent = sim
  def endpointId: EndpointId = id
  def publisher: EndpointPublisher = endpoint
}

class SimulatorMgr(eventThread: CallMarshaller, load: LoadRecord) extends LazyLogging {

  private var publisherPairs = Vector.empty[SimulatorPublisherPair]

  private val pv1 = new PvSim(PvParams.basic, PvSim.PvState(1.0, fault = false))
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, pv1, "PV1", EndpointBuilders.buildPv())

  private val ess1 = {
    val params = EssParams.basic
    new EssSim(params, EssSim.EssState(EssSim.Constant, 0.5 * params.capacity, 0.0, 0.0, false))
  }
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, ess1, "ESS1", EndpointBuilders.buildEss())

  private val chp1 = new ChpSim(ChpParams.basic, ChpSim.ChpState(0.0, 0.0, false))
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, chp1, "CHP1", EndpointBuilders.buildChp())

  private val load1 = new LoadSim(LoadMapping.defaultParams, load, LoadSim.LoadState(0))
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, load1, "LOAD1", EndpointBuilders.buildLoad())

  private val simulator = new Simulator(
    SimulatorState(0.0, 0.0, 0.0, 0.0, pccStatus = true, custBkrStatus = true),
    chps = Seq(chp1),
    esses = Seq(ess1),
    pvs = Seq(pv1),
    loads = Seq(load1))

  def publishers: Seq[(EndpointId, EndpointPublisher)] = publisherPairs.map(p => (p.endpointId, p.publisher))

  private def handleOutputRequest(pair: SimulatorPublisherPair, req: UserOutputRequest): Unit = {
    pair.simulator.handlers.get(req.key) match {
      case None => req.resultAsync(OutputFailure("No handler for output"))
      case Some(handler) => {
        val dirty = handler(req.outputRequest.outputValueOpt)
        req.resultAsync(OutputSuccess(None))
        if (dirty) {
          eventThread.marshal(tick())
        }
      }
    }
  }

  def setup(): Unit = {
    publisherPairs.foreach { pair =>
      pair.publisher.outputReceiver.bind { batch =>
        batch.requests.foreach(req => handleOutputRequest(pair, req))
      }
    }
  }

  def tick(): Unit = {
    val now = System.currentTimeMillis()
    val lineState = simulator.tick(now)

    publisherPairs.foreach { pair =>
      val updates = pair.simulator.updates(lineState, now)
      updates.foreach {
        case update: TimeSeriesUpdate =>
          pair.publisher.timeSeriesStreams.get(update.path) match {
            case None => logger.warn("Path for update unrecognized: " + update.path)
            case Some(sink) => sink.push(TimeSeriesSample(now, update.v))
          }
      }
    }
  }
}
