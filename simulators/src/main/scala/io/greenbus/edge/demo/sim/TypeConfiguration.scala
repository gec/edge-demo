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

import io.greenbus.edge.channel.Sink
import io.greenbus.edge._
import io.greenbus.edge.client.{ ClientEndpointPublisherDesc, MetadataDesc, TimeSeriesValueEntry }

/*trait TypeConfiguration[Params, Result] {


  val equipmentType: String
  val pointTypes: Seq[String]
  val commandTypes: Seq[String]

  def defaultParams: Params
}*/

/*
case class ClientEndpointPublisherDesc(
  indexes: Map[Path, IndexableValue],
  metadata: Map[Path, Value],
  latestKeyValueEntries: Map[Path, LatestKeyValueEntry],
  timeSeriesValueEntries: Map[Path, TimeSeriesValueEntry],
  outputEntries: Map[Path, OutputEntry])


  def keyValueStreams: Map[Path, Sink[Value]] = keyValueSinks

  def timeSeriesStreams: Map[Path, Sink[TimeSeriesSample]] = timeSeriesSinks


case class MetadataDesc(indexes: Map[Path, IndexableValue], metadata: Map[Path, Value])

case class LatestKeyValueEntry(initialValue: Value, meta: MetadataDesc)
case class TimeSeriesValueEntry(initialValue: TimeSeriesSample, meta: MetadataDesc)
case class OutputEntry(initialValue: PublisherOutputValueStatus, meta: MetadataDesc)
 */

/*trait SinkBindable[A] {
  private var sinkOpt = Option.empty[Sink[A]]
  def setSink(sink: Sink[A]): Unit = {
    sinkOpt = Some(sink)
  }
  def sink: Option[Sink[A]]
}

case class TimeSeriesDef(key: Path, original: SampleValue, indexes: Map[Path, IndexableValue], metadata: Map[Path, Value])

trait SimulatorDef {
  //private var timeSeriesDefs = Vector.newBuilder[(Path, TimeSeriesValueEntry)]
  private var timeSeriesDefs = Vector.newBuilder[TimeSeriesDef]

  val indexes: Map[Path, IndexableValue]
  val metadata: Map[Path, Value]

  /*def build
  def timeSeriesBindings: Map[Path, SinkBindable[SampleValue]]*/

  protected def timeSeries(key: Path, original: SampleValue, indexes: Map[Path, IndexableValue], metadata: Map[Path, Value]): Path = {
    //timeSeriesDefs += (key -> TimeSeriesValueEntry(original, MetadataDesc(indexes, metadata)))
    timeSeriesDefs += TimeSeriesDef(key, original, indexes, metadata)
    key
  }

  def result(): ClientEndpointPublisherDesc = {
    ClientEndpointPublisherDesc(indexes, metadata,)
  }
}

object PvDef extends SimulatorDef {

  val outputPower: Path = timeSeries(Path("OutputPower"), ValueDouble(0.0), Map(), Map())

}*/

sealed trait SimUpdate
case class TimeSeriesUpdate(path: Path, v: SampleValue) extends SimUpdate

trait SimulatorComponent {
  def updates(line: LineState, time: Long): Seq[SimUpdate]
  def handlers: Map[Path, Option[Value] => Boolean]
}
