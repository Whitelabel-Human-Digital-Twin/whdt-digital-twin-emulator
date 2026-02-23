package io.github.whdt.emulator.dt

import com.hivemq.client.mqtt.MqttClient
import io.github.whdt.core.hdt.model.id.HdtId
import io.github.whdt.core.hdt.model.property.Properties.singleValueProperty
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.core.serde.Stub
import io.github.whdt.distributed.namespace.Namespace
import kotlin.random.Random
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val mqttClient = MqttClient.builder()
        .useMqttVersion5()
        .identifier(System.getenv("MQTT_IDENTIFIER") ?: "whdt-digital-twin-emulator")
        .serverHost(System.getenv("MQTT_HOST") ?: "localhost")
        .serverPort((System.getenv("MQTT_PORT") ?: "1883").toInt())
        .buildAsync()

    val connectFuture = mqttClient.connect()
    connectFuture.whenComplete { _, throwable ->
        if (throwable != null) {
            println("Failed to connect Mqtt client: ${throwable.message}")
        } else {
            println("Mqtt client connected!")
        }
    }.join()

    val ids = (0 until 201).map { HdtId("hdt-$it") }
    ids.forEach {
        val property = generateTestProperty()
        mqttClient
            .publishWith()
            .topic(Namespace.propertyUpdateNotificationTopic(it))
            .payload(Stub.propertyJsonSerDe().serialize(property).toByteArray())
            .send()
        println("${it}: sent property update of value ${property.valueMap["value"]}")
    }
    exitProcess(0)
}

fun generateTestProperty(): Property {
    return singleValueProperty(
        id= "test-property",
        name = "Test Property",
        valueName = "value",
        value = PropertyValue.DoublePropertyValue(Random.nextDouble(0.0, 1.0)),
    )
}