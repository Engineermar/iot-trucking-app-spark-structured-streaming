package com.hortonworks.spark.benchmark.streaming.sessionwindow

import org.apache.spark.sql.streaming.OutputMode

class BenchmarkSessionWindowListenerWordCountSessionFunctionUpdateMode(args: Array[String])
  extends BaseBenchmarkSessionWindowListenerWordCountSessionFunction(
    args,
    "SessionWindowWordCountAsSessionFunctionUpdateMode",
    "SessionWindowWordCountAsSessionFunctionUpdateMode",
    OutputMode.Update())

object BenchmarkSessionWindowListenerWordCountSessionFunctionUpdateMode {
  def main(args: Array[String]): Unit = {
    new BenchmarkSessionWindowListenerWordCountSessionFunctionUpdateMode(args).runBenchmark()
  }

}