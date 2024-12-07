package org.example

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.

/*
fun main() {
    val name = "Kotlin"
    //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
    // to see how IntelliJ IDEA suggests fixing it.
    println("Hello, " + name + "!")

    for (i in 1..5) {
        //TIP Press <shortcut actionId="Debug"/> to start debugging your code. We have set one <icon src="AllIcons.Debugger.Db_set_breakpoint"/> breakpoint
        // for you, but you can always add more by pressing <shortcut actionId="ToggleLineBreakpoint"/>.
        println("i = $i")
    }
}

#!/usr/bin/env kotlin*/

import com.google.gson.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    val rpcUrl = "https://api.mainnet-beta.solana.com"
    val client = OkHttpClient()
    val gson = Gson()
    val distinctAddresses = mutableSetOf<String>()

    // Log statistics
    var requestCount = 0
    val startTime = Instant.now()
    val rateLimiter = Executors.newScheduledThreadPool(1)

    // Shared delay between requests to respect rate limits (e.g., 10 requests per second)
    val rateLimitDelay = 1000L // Milliseconds (100ms = 10 requests/sec)

    // Step 1: Get the latest block height with rate-limiting
    val latestBlockHeight = scheduleRequest(rateLimiter, rateLimitDelay) {
        getBlockHeight(client, gson, rpcUrl) { code, message ->
            logRequest(++requestCount, code, message, startTime)
        }
    } ?: run {
        println("Failed to fetch the latest block height.")
        return
    }

    // Step 2: Fetch the last 1000 blocks in manageable batches
    val blockRange = latestBlockHeight - 3..latestBlockHeight
    val batchSize = 100  // Adjust batch size to meet the rate limits

    for (batch in blockRange.chunked(batchSize)) {
        batch.forEach { block ->
            scheduleRequest(rateLimiter, rateLimitDelay) {
                // Step 3: Fetch transactions and extract addresses
                val transactions = getTransactionsFromBlock(client, gson, rpcUrl, block) { code, message ->
                    logRequest(++requestCount, code, message, startTime)
                }
                //println("Num transactions: ${transactions}")
                for (transaction in transactions) {
                    transaction?.asJsonObject?.getAsJsonObject("transaction")?.getAsJsonObject("message")
                        ?.getAsJsonArray("accountKeys")?.forEach { key ->
                        distinctAddresses.add(key.asString)
                    }
                }
            }
        }
        Thread.sleep(10000)  // Pause between batches (10 seconds)
    }

    // Shutdown executor and print results
    rateLimiter.shutdown()
    rateLimiter.awaitTermination(1, TimeUnit.HOURS)
    println("Distinct addresses (${distinctAddresses.size}):")
    distinctAddresses.forEach { println(it) }
}

fun getBlockHeight(client: OkHttpClient, gson: Gson, rpcUrl: String, log: (Int, String) -> Unit): Long? {
    val requestBody = """{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "getBlockHeight"
    }""".trimIndent()

    val response = makeRpcRequest(client, rpcUrl, requestBody, log)
    return response?.get("result")?.asLong
}

fun getTransactionsFromBlock(
    client: OkHttpClient,
    gson: Gson,
    rpcUrl: String,
    block: Long,
    log: (Int, String) -> Unit
): JsonArray {
    val requestBody = """{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "getBlock",
        "params": [$block, {"transactionDetails": "full", "maxSupportedTransactionVersion": 0}]
    }""".trimIndent()

    val response = makeRpcRequest(client, rpcUrl, requestBody, log)
    return response?.getAsJsonObject("result")?.getAsJsonArray("transactions") ?: JsonArray()
}

fun makeRpcRequest(client: OkHttpClient, rpcUrl: String, requestBody: String, log: (Int, String) -> Unit): JsonObject? {
    val request = Request.Builder()
        .url(rpcUrl)
        .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBody))
        .build()

    return try {
        client.newCall(request).execute().use { response ->
            log(response.code, response.message)
            if (!response.isSuccessful) {
                println("RPC request failed: ${response.message}")
                return null
            }
            JsonParser.parseString(response.body?.string()).asJsonObject
        }
    } catch (e: Exception) {
        println("Error making RPC request: ${e.message}")
        null
    }
}

fun logRequest(requestCount: Int, responseCode: Int, responseMessage: String, startTime: Instant) {
    val elapsedTime = java.time.Duration.between(startTime, Instant.now()).toMillis()
    println("Request #$requestCount | Time: ${elapsedTime}ms | Code: $responseCode | Message: $responseMessage")
}

fun <T> scheduleRequest(
    rateLimiter: java.util.concurrent.ScheduledExecutorService,
    delay: Long,
    request: () -> T?
): T? {
    var result: T? = null
    val latch = java.util.concurrent.CountDownLatch(1)
    rateLimiter.schedule({
        result = request()
        latch.countDown()
    }, delay, TimeUnit.MILLISECONDS)
    latch.await()
    return result
}





