package searchQuality

import cache.SearchQualityDBName
import com.mongodb.casbah.Imports._
import scala.util.parsing.json.JSONObject
import query._
import query.property.QueryElementToJSON
import query.property.SensisQueryElement
import cache.SplunkDatabaseName

object EvalQualityQuery extends SearchQualityQryTrait {

  def query(days: Int, sqe: SensisQueryElement, arr: String*): JSONObject = {
    QueryElementToJSON((queryRecord(days, sqe, arr.toArray)).toList)
  }

  def compare(begin: Int, end: Int, sqe: SensisQueryElement, arr: String*): JSONObject = {
    QueryElementToJSON(compareBase(begin, end, sqe, arr.toArray))
  }

  def queryTop(days: Int, top: Int, sqe: SensisQueryElement, arr: String*): JSONObject = {
    val records = queryRecord(days, sqe, arr.toArray).orderbyDecsending(x => x.getProperty[Int]("days")).top(10)
    QueryElementToJSON(records.toList)
  }

  def queryGrowth(b: Int, e: Int, sqe: SensisQueryElement, arr: String*): JSONObject = ???

  private def queryAsSensisQueryElem(args: Array[String]): MongoDBObject => SensisQueryElement = x => {
    val reVal = new SensisQueryElement
    reVal.insertProperty("days", x.getAs[Int]("days").get)
    for (it <- args) {
      if (it != "days")
        reVal.insertProperty(it, x.getAsOrElse(it, ""))
    }
    reVal
  }

  private def queryRecord(days: Int, sqe: SensisQueryElement, arr: Array[String]): IQueryable[SensisQueryElement] = {
    var dataRecord: IQueryable[SensisQueryElement] = null

    var fl: Array[String] = null
    if (arr.length == 1)
      fl = SearchQualityDBName.evaluation_matric_columns
    else
      fl = arr.toArray

    if (days == 0)
      dataRecord = from db () in SearchQualityDBName.evaluation_matric_data select queryAsSensisQueryElem(fl)
    else {
      dataRecord = from db () in SearchQualityDBName.evaluation_matric_data where ("days" -> days) select queryAsSensisQueryElem(fl)

      if (dataRecord.count < 1) {
        val prevRec = getPreviousRecord(days)
        if (prevRec != null)
          dataRecord = dataRecord :+ prevRec
      }
    }
    dataRecord
  }

  private def compareBase(begin: Int, end: Int, sqe: SensisQueryElement, arr: Array[String]): List[SensisQueryElement] = {

    def calcChange(left: String, right: String) = {
      if (left.equals("") || right.equals(""))
        "Quality values undefined"
      else
        "%.2f".format(right.toDouble - left.toDouble)
    }

    def getComparison(left: SensisQueryElement, right: SensisQueryElement) = {
      var result: SensisQueryElement = new SensisQueryElement

      for (it <- right.args) {
        if (it.name.toLowerCase.contains("comment"))
          result.insertProperty(it.name, if (it.get.toString.equals("")) "Undefined" else it.get.toString)
        else if (it.name != "days") {
          result.insertProperty("%s_curr".format(it.name), if (it.get.toString.equals("")) "Undefined" else (it.get.toString).toDouble)
          result.insertProperty("%s_prev".format(it.name), if (left.getProperty(it.name).equals("")) "Undefined" else (left.getProperty(it.name).toString).toDouble)
          result.insertProperty("%s_change".format(it.name), calcChange(left.getProperty(it.name).toString, it.get.toString))
        }
      }
      result
    }

    if (begin == 0 && end == 0) { /* Default view */
      val recordsList = (queryRecord(0, sqe, arr).orderbyDecsending(x => x.getProperty[Int]("days")).top(2)).toList

      if (recordsList.size >= 2) {
        val result: SensisQueryElement = getComparison(recordsList(1), recordsList(0))
        result :: List.empty[SensisQueryElement]
      } else
        List.empty[SensisQueryElement]

    } else if (begin != 0 && end != 0) { /* When both time points defined */
      val left = queryRecord(begin, sqe, arr).toList
      val right = queryRecord(end, sqe, arr).toList

      if (left.size == 1 && right.size == 1) {
        val result: SensisQueryElement = getComparison(left(0), right(0))
        result :: List.empty[SensisQueryElement]
      } else
        List.empty[SensisQueryElement]

    } else if (begin != 0 && end == 0) { /* Only one time point */
      val right = queryRecord(begin, sqe, arr).toList
      val left = getPreviousRecord(begin)

      if (right.size >= 1) getComparison(left, right(0)) :: List.empty[SensisQueryElement]
      else List.empty[SensisQueryElement]

    } else
      List.empty[SensisQueryElement]
  }

  private def getPreviousRecord(begin: Int) = {
    val records = from db () in SearchQualityDBName.evaluation_matric_data where ("days" $lt begin) select queryAsSensisQueryElem(SearchQualityDBName.evaluation_matric_columns)
    val prev = records.orderbyDecsending(x => x.getProperty[Int]("days")).top(1).toList

    if (prev.size >= 1) prev(0)
    else null
  }

}
