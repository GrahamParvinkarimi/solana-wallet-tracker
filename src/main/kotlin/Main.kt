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

// Comprehensive list of Solana DeFi program addresses and patterns
val KNOWN_DEFI_PROGRAMS = setOf(
    // Major DEX and Swap Protocols
    "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSAt5Q8q", // Raydium
    "whirLd2Sn9YF4NVYhh3Aehkr6CPA6Xkw8NaCsv4xZEKs", // Orca
    "9xQeWvG816bUx9EPjHmaT23yvVM2ZWb4G9bV4PxzBt1M", // Serum DEX
    "JUP2jxvXaqu7NQY1GmNF4m1vodw12LVXYNqkpZscJsat", // Jupiter Aggregator
    "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4", // Jupiter Aggregator v6

    // Lending Protocols
    "So1endDq2YkqhipRh3WLWZnQ7jy6m3k8qZnsxwPDadvc", // Solend

    // Liquidity Pools and AMMs
    "AMM55ShdkoVcPERmjJvxLtjNFxgFqxgzgsXodRjQrRtg", // Saber

    // Additional known DeFi program patterns
    //"PengSync", // Governance/Yield protocols
    //"Swap", // Generic swap programs
    //"Pool", // Liquidity pool identifiers
    //"Farm" // Yield farming programs
)

fun main() {
    val rpcUrl = "https://api.mainnet-beta.solana.com"
    val client = OkHttpClient()
    val gson = Gson()
    val defiAddresses = mutableSetOf<String>()
    val defiTransactionDetails = mutableListOf<String>()

    // Log statistics
    var requestCount = 0
    val startTime = Instant.now()
    val rateLimiter = Executors.newScheduledThreadPool(1)

    // Shared delay between requests to respect rate limits
    val rateLimitDelay = 2000L // Milliseconds

    // Step 1: Get the latest block height with rate-limiting
    val latestBlockHeight = scheduleRequest(rateLimiter, rateLimitDelay) {
        getBlockHeight(client, gson, rpcUrl) { code, message ->
            logRequest(++requestCount, code, message, startTime)
        }
    } ?: run {
        println("Failed to fetch the latest block height.")
        return
    }

    // Step 2: Fetch the last 100 blocks in manageable batches
    val blockRange = latestBlockHeight - 10..latestBlockHeight
    val batchSize = 10  // Smaller batch size to avoid rate limiting

    for (batch in blockRange.chunked(batchSize)) {
        batch.forEach { block ->
            scheduleRequest(rateLimiter, rateLimitDelay) {
                // Step 3: Fetch transactions and extract DeFi-related addresses
                val transactions = getTransactionsFromBlock(client, gson, rpcUrl, block) { code, message ->
                    logRequest(++requestCount, code, message, startTime)
                }

                for (transaction in transactions) {
                    val txnDetails = transaction?.asJsonObject

                    // More robust DeFi transaction detection
                    val isDeFiTransaction = isDeFiRelatedTransaction(txnDetails)

                    if (isDeFiTransaction) {
                        println("🟢 DeFi Transaction Found in Block $block")

                        // Store transaction details for debugging
                        defiTransactionDetails.add(txnDetails.toString())

                        // Extract and add addresses from DeFi transactions
                        txnDetails?.getAsJsonObject("transaction")
                            ?.getAsJsonObject("message")
                            ?.getAsJsonArray("accountKeys")
                            ?.forEach { key ->
                                val address = key.asString
                                defiAddresses.add(address)
                            }
                    }
                }
                null  // Return null as required by scheduleRequest
            }
        }
        Thread.sleep(5000)  // Pause between batches
    }

    // Shutdown executor and print results
    rateLimiter.shutdown()
    rateLimiter.awaitTermination(1, TimeUnit.HOURS)

    println("\n📈 Results:")
    println("Total Blocks Scanned: ${blockRange.last - blockRange.first + 1}")
    println("DeFi-related addresses (${defiAddresses.size}):")
    defiAddresses.forEach { println(it) }
}

fun isDeFiRelatedTransaction(transaction: JsonObject?): Boolean {
    transaction ?: return false

    try {
        // Extract message from transaction
        val message = transaction.getAsJsonObject("transaction")
            ?.getAsJsonObject("message")
            ?: return false

        // Get account keys and instructions
        val accountKeys = message.getAsJsonArray("accountKeys") ?: return false
        val instructions = message.getAsJsonArray("instructions") ?: return false

        // Check each instruction for DeFi-related program
        for (instructionElement in instructions) {
            val instruction = instructionElement.asJsonObject

            // Get the program ID using programIdIndex
            val programIdIndex = instruction.get("programIdIndex")?.asInt ?: continue
            val programId = accountKeys[programIdIndex].asString

            // Flexible matching against known DeFi programs
            val isDeFiProgram = KNOWN_DEFI_PROGRAMS.any { knownProgram ->
                programId.contains(knownProgram, ignoreCase = true)
            }

            if (isDeFiProgram) {
                println("🔍 Found DeFi Program: $programId")
                return true
            }

            // Additional check for instruction data
            val instructionData = instruction.get("data")?.asString ?: ""
            val isDeFiData = KNOWN_DEFI_PROGRAMS.any { knownProgram ->
                instructionData.contains(knownProgram, ignoreCase = true)
            }

            if (isDeFiData) {
                println("🔍 Found DeFi Instruction Data: $instructionData")
                return true
            }
        }

        // Check inner instructions if available
        val innerInstructions = transaction.getAsJsonArray("innerInstructions")
        if (innerInstructions != null) {
            for (innerInstructionGroup in innerInstructions) {
                val innerGroup = innerInstructionGroup.asJsonObject
                val groupInstructions = innerGroup.getAsJsonArray("instructions") ?: continue

                for (innerInstruction in groupInstructions) {
                    val innerInstrObj = innerInstruction.asJsonObject
                    val innerProgramIdIndex = innerInstrObj.get("programIdIndex")?.asInt ?: continue
                    val innerProgramId = accountKeys[innerProgramIdIndex].asString

                    val isDeFiInnerProgram = KNOWN_DEFI_PROGRAMS.any { knownProgram ->
                        innerProgramId.contains(knownProgram, ignoreCase = true)
                    }

                    if (isDeFiInnerProgram) {
                        println("🔍 Found DeFi Inner Instruction Program: $innerProgramId")
                        return true
                    }
                }
            }
        }

        return false
    } catch (e: Exception) {
        println("❌ Error parsing transaction for DeFi detection: ${e.message}")
        return false
    }
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





