package io.wipro.training.hadoop
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext

case class LogRecord( clientIp: String, clientIdentity: String, user: String, dateTime: String, request:String,statusCode:Int, bytesSent:Long)
                      
//in24.inetnebr.com - - [01/Aug/1995:00:00:01 -0400] "GET /shuttle/missions/sts-68/news/sts-68-mcc-05.txt HTTP/1.0" 200 1839
object LogAnalysis {
    // A Regular Expression Pattern for parsing our Log Record
    val PATTERN =  """^(\S+) (\S+) (\S+) \[([\w:/]+\s[+\-]\d{4})\] "(\S+) (\S+)\s*(\S*)\s*" (\d{3}) (\S+)""".r
    // this simple method extracts the pieces of the line 
    def parseLogLine(log: String): LogRecord = {
      try {
        val res = PATTERN.findFirstMatchIn(log)
        if (res.isEmpty) {
          println("Rejected Log Line: " + log)
          LogRecord("Empty", "-", "-", "", "",  -1, -1)
        }
        else {
          val m = res.get
          // NOTE:   HEAD does not have a content size.
          if (m.group(9).equals("-")) {
            LogRecord(m.group(1), m.group(2), m.group(3), m.group(4),
              m.group(5), m.group(8).toInt, 0)
          }
          else {
            LogRecord(m.group(1), m.group(2), m.group(3), m.group(4),
              m.group(5), m.group(8).toInt, m.group(9).toLong)
          }
        }
      } catch
      {
        case e: Exception =>
          println("Exception on line:" + log + ":" + e.getMessage);
          LogRecord("Empty", "-", "-", "", "-", -1, -1 )
      }
    }
     def main(args: Array[String]) {
    // Set the name of the spark program
    val sparkConf = new SparkConf().setAppName("Logs")
    // only use what you need, I only want 4 cores
    sparkConf.set("spark.cores.max", "4")
    // use the faster more efficient Kryo Serializer instead of built-in Java one
    //sparkConf.set("spark.serializer", classOf[KryoSerializer].getName)
    // Tune Spark to 11 and use the Tungsten acceleartor
    sparkConf.set("spark.sql.tungsten.enabled", "true")
    // Turn on event logging so we can see in our history server
    sparkConf.set("spark.eventLog.enabled", "true")
    // I will name this app so I can see it in the logs
    sparkConf.set("spark.app.id", "Logs")
    // snappy compression works well for me
    sparkConf.set("spark.io.compression.codec", "snappy")
    // compress RDDs to save space
    sparkConf.set("spark.rdd.compress", "true")
    // create my context from this configuration to start.   Must do!
    val sc = new SparkContext(sparkConf)
    // read my log file
    val logFile = sc.textFile("/user/manhati/sparkPoc/loganalysisData/apache.access.log.PROJECT")
    // map each line to the function to parse it and filter out empties
    val accessLogs = logFile.map(parseLogLine).filter(!_.clientIp.equals("Empty")).cache()
    // cache it for future use
    try {
      println("Log Count: %s".format(accessLogs.count()))
      // lets bring back 25 rows to the worker to examine in the console
      accessLogs.take(25).foreach(println)
      // Calculate statistics based on the content size.
      val contentSizes = accessLogs.map(log => log.bytesSent).cache()
      val contentTotal = contentSizes.reduce(_ + _)
      println("Number of Log Records: %s  Content Size Total: %s, Avg: %s, Min: %s, Max: %s".format(
        contentSizes.count,
        contentTotal,
        contentTotal / contentSizes.count,
        contentSizes.min,
        contentSizes.max))
    } catch
    {
      case e: Exception =>
      println("Exception:" + e.getMessage);
      e.printStackTrace();
    }
    // stop this job, we are done.
    sc.stop()
  }

}