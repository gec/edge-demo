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

class SimulatorPublisherPair(eventThread: CallMarshaller, sim: SimulatorComponent, endId: Path, desc: ClientEndpointPublisherDesc) {
  private val id = NamedEndpointId(endId)
  private val endpoint = new EndpointPublisherImpl(eventThread, id, desc)
  def simulator: SimulatorComponent = sim
  def endpointId: EndpointId = id
  def publisher: EndpointPublisher = endpoint
}

class SimulatorMgr(eventThread: CallMarshaller, load: LoadRecord, ctx: SimulatorContext) extends LazyLogging {

  private var publisherPairs = Vector.empty[SimulatorPublisherPair]

  private val pvParams = PvParams.basic
  private val pv1 = new PvSim(pvParams, PvSim.PvState(1.0, fault = false))
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, pv1, Path(ctx.equipmentPrefix :+ "PV"), EndpointBuilders.buildPv(pvParams))

  private val essParams = EssParams.basic
  private val ess1 = new EssSim(essParams, EssSim.EssState(EssSim.Constant, 0.5 * essParams.capacity, 0.0, 0.0, false))
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, ess1, Path(ctx.equipmentPrefix :+ "ESS"), EndpointBuilders.buildEss(essParams))

  private val chpParams = ChpParams.basic
  private val chp1 = new ChpSim(chpParams, ChpSim.ChpState(0.0, 0.0, false))
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, chp1, Path(ctx.equipmentPrefix :+ "CHP"), EndpointBuilders.buildChp(chpParams))

  private val loadParams = LoadMapping.defaultParams
  private val load1 = new LoadSim(loadParams, load, LoadSim.LoadState(0))
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, load1, Path(ctx.equipmentPrefix :+ "Load"), EndpointBuilders.buildLoad(loadParams))

  private val pccBkr = new BreakerSim(true)
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, pccBkr, Path(ctx.equipmentPrefix :+ "PCC_BKR"), EndpointBuilders.buildBreaker(pcc = true))

  private val custBkr = new BreakerSim(true)
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, custBkr, Path(ctx.equipmentPrefix :+ "CUST_BKR"), EndpointBuilders.buildBreaker(pcc = false))

  private val simulator = new Simulator(
    SimulatorState(0.0, 0.0, 0.0, 0.0),
    pccBkr = pccBkr,
    custBkr = custBkr,
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

    var publishersToFlush = Set.empty[EndpointPublisher]

    publisherPairs.foreach { pair =>
      val updates = pair.simulator.updates(lineState, now)
      updates.foreach {
        case update: TimeSeriesUpdate =>
          pair.publisher.timeSeriesStreams.get(update.path) match {
            case None => logger.warn("Path for update unrecognized: " + update.path)
            case Some(sink) =>
              println("publishing: " + update.path + "/" + update.v)
              sink.push(TimeSeriesSample(now, update.v))
              publishersToFlush += pair.publisher
          }
        case update: SeqValueUpdate =>
          pair.publisher.keyValueStreams.get(update.path) match {
            case None => logger.warn("Path for update unrecognized: " + update.path)
            case Some(sink) =>
              sink.push(update.v)
              publishersToFlush += pair.publisher
          }
      }
      val events = pair.simulator.eventQueue.dequeue()
      events.foreach {
        case (path, topicEvent) =>
          pair.publisher.eventStreams.get(path).foreach(_.push(topicEvent))
          publishersToFlush += pair.publisher
      }
    }

    publishersToFlush.foreach(_.flush())
  }
}
