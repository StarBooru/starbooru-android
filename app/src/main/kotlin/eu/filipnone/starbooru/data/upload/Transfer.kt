package eu.filipnone.starbooru.data.upload

/**
 * Works out a transfer's speed from the totals it is told, as
 * starbooru-core's `Meter`: each reading counts for a third, so the number
 * settles without lagging far behind.
 */
class Meter(nowMs: Long) {
    private var since = nowMs
    private var bytes = 0L
    private var speed = 0L

    /** Takes the bytes moved so far and gives the speed in bytes a second. */
    fun seen(
        moved: Long,
        nowMs: Long,
    ): Long {
        val ms = nowMs - since
        // Started over, or a clock set back: measure afresh.
        if (moved < bytes || ms < 0) {
            since = nowMs
            bytes = moved
            return speed
        }
        if (ms >= SAMPLE_MS) {
            val latest = (moved - bytes) * 1000 / ms.coerceAtLeast(1)
            speed = if (speed == 0L) latest else (speed * 2 + latest) / 3
            since = nowMs
            bytes = moved
        }
        return speed
    }

    private companion object {
        /** The least time between two readings, so one quick piece doesn't make the number jump. */
        const val SAMPLE_MS = 500L
    }
}

/**
 * What to say beside a transfer's bar: `42% · 1.2 MB/s`, or the bytes so far
 * when the size isn't known. The speed is left out until it is measured.
 * [size] writes a number of bytes.
 */
fun transferLabel(
    sent: Long,
    total: Long,
    speed: Long,
    size: (Long) -> String,
): String {
    val howFar = percentOf(sent, total)?.let { "$it%" } ?: size(sent)
    return if (speed <= 0) howFar else "$howFar · ${size(speed)}/s"
}
