package io.greenbus.edge.demo.sim.island

import io.greenbus.edge.api._
import io.greenbus.edge.api.stream.{EndpointBuilder, KeyMetadata}


object IslandAppPublisher {
  val eventsKey = Path("Events")
  val paramsKey = Path("Params")
  val enabledKey = Path("IsEnabled")

  val setEnableKey = Path("SetEnable")
  val setDisableKey = Path("SetDisable")

  val boolMappingKey = Path("boolMapping")

  val enabledMapping = ValueArray(Vector(
    ValueObject(Map(
      "value" -> ValueBool(false),
      "name" -> ValueString("Disabled"))),
    ValueObject(Map(
      "value" -> ValueBool(true),
      "name" -> ValueString("Enabled")))))

  val enabledMappingKv = boolMappingKey -> enabledMapping
}
class IslandAppPublisher(builder: EndpointBuilder, seriesBufferSize: Int, eventsBufferSize: Int) {
  import IslandAppPublisher._

  builder.setIndexes(Map(Path("role") -> ValueString("application")))

  val events = builder.topicEventValue(eventsKey)

  val enabled = builder.seriesValue(enabledKey, KeyMetadata(indexes = Map(Path("applicationStatusType") -> ValueString("enabled")), metadata = Map(enabledMappingKv)))

  val setEnable = builder.outputStatus(setEnableKey, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
  val setEnableRcv = builder.registerOutput(setEnableKey)
  val setDisable = builder.outputStatus(setDisableKey, KeyMetadata(Map(), Map(Path("simpleInputType") -> ValueString("indication"))))
  val setDisableRcv = builder.registerOutput(setDisableKey)

  private val handle = builder.build(seriesBufferSize, eventsBufferSize)
  def flush(): Unit = handle.flush()
}
