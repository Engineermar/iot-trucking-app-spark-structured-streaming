package com.hortonworks.spark.benchmark.streaming.sessionwindow

import java.sql.Timestamp

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.streaming.{GroupState, GroupStateTimeout, OutputMode}

case class Event(sessionId: String, timestamp: Timestamp)

case class SessionInfo(numEvents: Int,
                       startTimestampMs: Long,
                       endTimestampMs: Long) {

  /** Duration of the session, between the first and last events */
  def durationMs: Long = endTimestampMs - startTimestampMs
}

case class SessionUpdate(id: String,
                         startTimestampMs: Long,
                         endTimestampMs: Long,
                         durationMs: Long,
                         numEvents: Int,
                         expired: Boolean)

class BaseBenchmarkSessionWindowListenerWordCountMapGroupsWithStateUpdateMode(args: Array[String])
  extends BaseBenchmarkSessionWindowListener(
    args,
    "SessionWindowWordCountAsMapGroupsWithStateUpdateMode",
    "SessionWindowWordCountAsMapGroupsWithStateUpdateMode",
    OutputMode.Update()) {

  override def applyOperations(ss: SparkSession, df: DataFrame): DataFrame = {
    import ss.implicits._

    // Split the lines into words, treat words as sessionId of events
    // NOTE: in order to apply watermark we can't map typed columns to an object
    val events = df
      .select($"value", $"timestamp")
      .as[(Long, Timestamp)]
      .flatMap { case (value, timestamp) =>
        TestSentences.SENTENCES.apply((value % 10).intValue()).split(" ")
          .map { word => (word, timestamp) }
      }
      .as[(String, Timestamp)]
      .withWatermark("_2", "10 seconds")

    // Sessionize the events. Track number of events, start and end timestamps of session, and
    // and report session updates.
    val sessionUpdates = events
      .groupByKey(event => event._1)
      .mapGroupsWithState[SessionInfo, SessionUpdate](GroupStateTimeout.EventTimeTimeout()) {

      case (sessionId: String, events: Iterator[(String, Timestamp)], state: GroupState[SessionInfo]) =>

        // If timed out, then remove session and send final update
        if (state.hasTimedOut) {
          val finalUpdate =
            SessionUpdate(sessionId, state.get.startTimestampMs, state.get.endTimestampMs,
              state.get.durationMs, state.get.numEvents, expired = true)
          state.remove()
          finalUpdate
        } else {
          // Update start and end timestamps in session
          // actually this is not a correct implementation, but we ignore for performance comparison
          val timestamps = events.map(_._2.getTime).toSeq
          val updatedSession = if (state.exists) {
            val oldSession = state.get
            SessionInfo(
              oldSession.numEvents + timestamps.size,
              oldSession.startTimestampMs,
              math.max(oldSession.endTimestampMs, timestamps.max))
          } else {
            SessionInfo(timestamps.size, timestamps.min, timestamps.max)
          }
          state.update(updatedSession)

          // Set timeout such that the session will be expired if no data received for 10 seconds
          state.setTimeoutTimestamp(timestamps.max, "10 seconds")
          SessionUpdate(sessionId, state.get.startTimestampMs, state.get.endTimestampMs,
            state.get.durationMs, state.get.numEvents, expired = false)
        }
    }

    sessionUpdates.toDF()
  }
}

object BaseBenchmarkSessionWindowListenerWordCountMapGroupsWithStateUpdateMode {
  def main(args: Array[String]): Unit = {
    new BaseBenchmarkSessionWindowListenerWordCountMapGroupsWithStateUpdateMode(args).runBenchmark()
  }

}
