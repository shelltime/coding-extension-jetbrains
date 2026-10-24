package xyz.shelltime.jetbrains.heartbeat

/**
 * A heartbeat with the given id, for tests that only care which heartbeats travel
 */
fun testHeartbeat(id: String, time: Long = 1704067200): HeartbeatData = HeartbeatData(
    heartbeatId = id,
    entity = "/project/src/Main.kt",
    entityType = "file",
    category = "coding",
    time = time,
    project = "user/project",
    projectRootPath = "/project",
    branch = "main",
    language = "kotlin",
    editor = "jetbrains",
    editorVersion = "IntelliJ IDEA 2024.1",
    plugin = "shelltime",
    pluginVersion = "0.0.1",
    machine = "laptop",
    os = "Linux",
    osVersion = "6.1",
    isWrite = false
)

fun testHeartbeats(count: Int, prefix: String = "hb"): List<HeartbeatData> =
    (0 until count).map { testHeartbeat("$prefix-$it") }
