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

sealed trait SimUpdate
case class TimeSeriesUpdate(path: Path, v: SampleValue) extends SimUpdate
case class SeqValueUpdate(path: Path, v: Value) extends SimUpdate

trait SimulatorComponent {
  def updates(line: LineState, time: Long): Unit
}

case class TopicEvent(topic: Path, value: Value, timeMs: Long)

trait UpdateHandler {
  def handle(path: Path, update: SimUpdate): Unit
}

//class DoubleSeriesHandler

class SimEventQueue extends EventQueue {
  private var q = Vector.empty[(Path, TopicEvent)]

  def enqueue(path: Path, topicEvent: TopicEvent): Unit = {
    q = q :+ ((path, topicEvent))
  }

  def dequeue(): Seq[(Path, TopicEvent)] = {
    val all = q
    q = Vector.empty[(Path, TopicEvent)]
    all
  }
}

trait EventQueue {
  def dequeue(): Seq[(Path, TopicEvent)]
}