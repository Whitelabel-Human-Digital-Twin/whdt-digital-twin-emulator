package io.github.whdt.emulator.dt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import io.github.whdt.core.hdt.model.id.HdtId
import io.github.whdt.distributed.namespace.Namespace
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.time.Clock

fun main(args: Array<String>) {
    val publishers = args[0].toInt()
    val messagesPerPublisher = args[1].toInt()
    val nRuns = args[2].toInt()
    val waitBetweenRuns = 2000L
    val maxJitter = 51L
    val executor = Executors.newCachedThreadPool()
    val clients = (0 until publishers).map {
        MqttClient.builder()
            .useMqttVersion5()
            .identifier(System.getenv("MQTT_IDENTIFIER") ?: "whdt-digital-twin-emulator-${it}")
            .serverHost(System.getenv("MQTT_HOST") ?: "localhost")
            .serverPort((System.getenv("MQTT_PORT") ?: "1883").toInt())
            .buildAsync()
    }
    CompletableFuture.allOf(*clients.map { it.connect() }.toTypedArray()).join()
    println("Connected $publishers clients")

    val publishFutures = mutableListOf<CompletableFuture<*>>()
    val csvRows = Collections.synchronizedList(mutableListOf<String>())
    csvRows.add("dt_id,timestamp_at_send,timestamp_at_publish,payload_size, n_run")

    (1 until nRuns + 1).forEach {
        executeRun(
            clients,
            executor,
            messagesPerPublisher,
            maxJitter,
            it,
            csvRows,
            publishFutures,
        )
        clients[0].publishWith().topic("${Namespace.MQTT_PREFIX}/benchmark/end_run").send().join()
        Thread.sleep(waitBetweenRuns)
    }

    executor.shutdown()
    executor.awaitTermination(1, TimeUnit.MINUTES)
    CompletableFuture.allOf(*publishFutures.toTypedArray()).join()

    File("mqtt_metrics.csv").writeText(
        csvRows.joinToString("\n")
    )
    println("CSV written to mqtt_metrics.csv")

    clients[0].publishWith().topic("${Namespace.MQTT_PREFIX}/benchmark/done").send().join()

    clients.forEach { it.disconnect() }
    exitProcess(0)
}

fun executeRun(
    clients: List<Mqtt5AsyncClient>,
    executor: ExecutorService,
    messagesPerPublisher: Int,
    maxJitter: Long,
    nRun: Int,
    csvRows: MutableList<String>,
    publishFutures: MutableList<CompletableFuture<*>>
) {
    clients.forEachIndexed { clientIdx, client ->
        executor.submit {
            val clientId = "whdt-dt-emulator-$clientIdx"

            (0 until messagesPerPublisher).forEach { _ ->
                val payloadBytes = "hdt-$clientId".toByteArray()
                val payloadSize = payloadBytes.size

                val jitterMs = ThreadLocalRandom.current().nextLong(0, maxJitter)
                if (jitterMs > 0) Thread.sleep(jitterMs)

                val timestampAtSend = Clock.System.now()

                val f = client.publishWith()
                    .topic(Namespace.propertyUpdateNotificationTopic(HdtId("hdt-$clientId"), "test")) // or your own mapping
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payload(payloadBytes)
                    .send()

                f.whenComplete { _, throwable ->
                    val timestampAtPublish = Clock.System.now()
                    if (throwable == null) {
                        csvRows.add("hdt-$clientId,${timestampAtSend.toEpochMilliseconds()},${timestampAtPublish.toEpochMilliseconds()},$payloadSize, $nRun")
                    } else {
                        csvRows.add("$clientId,$timestampAtSend,ERROR,$payloadSize, $nRun")
                        println("Publish failed client=$clientId: ${throwable.message}")
                    }
                }
                publishFutures.add(f)
            }
        }
    }
}