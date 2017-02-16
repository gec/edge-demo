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

object PvEndpointBuilder {

  /*

    val indexes = Map(Path("index01") -> ValueDouble(3.14d * n))
    val meta = Map(Path("meta01") -> ValueInt64(-12345 * n))
    val latestKvs = Map(
      Path("key01") -> LatestKeyValueEntry(
        ValueString("the current value"),
        MetadataDesc(
          Map(Path("keyIndex01") -> ValueString("a string")),
          Map(Path("keyMeta01") -> ValueBool(false)))))

    val timeSeries = Map(
      Path("key02") -> TimeSeriesValueEntry(
        TimeSeriesSample(System.currentTimeMillis(), ValueDouble(3.14 * n)),
        MetadataDesc(
          Map(Path("keyIndex02") -> ValueString("a string 2")),
          Map(Path("keyMeta02") -> ValueBool(false)))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, Map())

case class OutputEntry(initialValue: PublisherOutputValueStatus, meta: MetadataDesc)
   */
  def build(): ClientEndpointPublisherDesc = {
    val indexes = Map.empty[Path, IndexableValue]
    val meta = Map.empty[Path, Value]
    val latestKvs = Map.empty[Path, LatestKeyValueEntry]

    val timeSeries = Map(
      PvMapping.pvOutputPower -> TimeSeriesValueEntry(
        TimeSeriesSample(System.currentTimeMillis(), ValueDouble(0.0)),
        MetadataDesc(
          Map(Path("gridValueType") -> ValueString("outputPower")),
          Map())),
      PvMapping.pvCapacity -> TimeSeriesValueEntry(
        TimeSeriesSample(System.currentTimeMillis(), ValueDouble(0.0)),
        MetadataDesc(
          Map(),
          Map())))

    val outputs = Map(
      PvMapping.faultEnable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map())),
      PvMapping.faultDisable -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map())))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, Map())
  }

}

/*class PvSimPair(ioThread: CallMarshaller, id: String, params: PvParams) {
  private val sim = new PvSim(params, PvSim.PvState(1.0, fault = false))
  private val endpoint = new EndpointPublisherImpl(ioThread, NamedEndpointId(id), PvEndpointBuilder.build())
  def simulator: SimulatorComponent = sim
  def publisher: EndpointPublisher = endpoint
}*/

class SimulatorPublisherPair(eventThread: CallMarshaller, sim: SimulatorComponent, endId: String, desc: ClientEndpointPublisherDesc) {
  private val id = NamedEndpointId(endId)
  private val endpoint = new EndpointPublisherImpl(eventThread, id, desc)
  def simulator: SimulatorComponent = sim
  def endpointId: EndpointId = id
  def publisher: EndpointPublisher = endpoint
}

class SimulatorMgr(eventThread: CallMarshaller) extends LazyLogging {

  //private var pvs = Vector.empty[PvSim]
  private var publisherPairs = Vector.empty[SimulatorPublisherPair]

  private val pv1 = new PvSim(PvParams.basic, PvSim.PvState(1.0, fault = false))
  //pvs = pvs :+ pv1
  publisherPairs = publisherPairs :+ new SimulatorPublisherPair(eventThread, pv1, "PV1", PvEndpointBuilder.build())

  private val simulator = new Simulator(
    SimulatorState(0.0, 0.0, 0.0, 0.0, pccStatus = true, custBkrStatus = true),
    chps = Seq(),
    esses = Seq(),
    pvs = Seq(pv1),
    loads = Seq())

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
