package query.traits

import com.mongodb.casbah.Imports._
import scala.util.parsing.json.JSONObject
import query._
import query.property.SensisQueryElement
import cache.SplunkDatabaseName
import query.property.QueryElementToJSON

object SplunkTermTrendQuery extends QueryTraits {

  def isQueryable(property: String): Boolean = false

  def query(b: Int, e: Int, p: SensisQueryElement, r: String*): JSONObject = ???

  def queryWithQueryable(b: Int, e: Int, p: SensisQueryElement, r: String*): IQueryable[SensisQueryElement] = {
    queryBase(-1, b, e, p, r.toArray)
  }

  def queryTops(t: Int, b: Int, e: Int, p: SensisQueryElement, r: String*): JSONObject = {
    QueryElementToJSON(queryBase(t, b, e, p, r.toArray).orderbyDecsending(x => x.getProperty[Int]("times")).top(t).toList)
  }

  def queryTopsWithQueryable(t: Int, b: Int, e: Int, p: SensisQueryElement, r: String*): IQueryable[SensisQueryElement] = {
    queryBase(t, b, e, p, r.toArray).orderbyDecsending(x => x.getProperty[Int]("times")).top(t)
  }

  /**
   * Calculate the total number of occurrences of each query as a whole string, regardless of its location.
   * @param t - number of top queries
   * @param b - search being date
   * @param e -  search end date
   * @param p - necessary query conditions
   * @param r - column projection
   * @return List of queries with the total number of occurrences for each.
   */
  private def queryBase(t: Int, b: Int, e: Int, p: SensisQueryElement, r: Array[String]) = {
    def queryConditions: DBObject = {
      val builder = MongoDBObject.newBuilder
      for (it <- p) {
        builder += it.name -> it.get
      }

      builder.result
    }

    def resultConditons(fl: Array[String]): MongoDBObject => SensisQueryElement = { x =>
      var re: SensisQueryElement = new SensisQueryElement
      re.insertProperty("query", x.getAsOrElse("query", ""))
      re.insertProperty("location", x.getAsOrElse("location", ""))
      for (it <- fl) {
        re.insertProperty(it, x.getAsOrElse(it, 0))
      }
      re
    }

    def unionResult(left: IQueryable[SensisQueryElement], right: IQueryable[SensisQueryElement], fl: Array[String]): IQueryable[SensisQueryElement] = {
      if (left != null) {
        left.union(right)(x => x.getProperty[String]("query")) { (x, y) =>
          if (x == null) y
          else if (y == null) x
          else {
            val re = new SensisQueryElement
            re.insertProperty("query", x.getProperty("query"))
            for (it <- fl) re.insertProperty(it, x.getProperty[Int](it) + y.getProperty[Int](it))
            re
          }
        }
      } else right
    }

    val fl = r.toArray
    def getQuery(d: String): IQueryable[SensisQueryElement] = {
      if (t > 0) (from db () in d where queryConditions).selectTop(t)("times")(resultConditons(fl))
      else from db () in d where queryConditions select resultConditons(fl)
    }

    var queryCan: IQueryable[SensisQueryElement] = null
    for (i <- b to e) {
      val cur = SplunkDatabaseName.splunk_query_data.format(i)
      val tmp = getQuery(cur)
      queryCan = unionResult(queryCan, tmp, fl)
    }

    var query: IQueryable[SensisQueryElement] = null
    for (i <- b to e) {
      var con: DBObject = null
      for (c <- queryCan) {
        val tmp_c = "query" $eq c.getProperty[String]("query")
        if (con == null) con = tmp_c
        else con = $or(con, tmp_c)
      }
      val cur = SplunkDatabaseName.splunk_query_data.format(i)
      val tmp = from db () in cur where con select resultConditons(fl)

      query = unionResult(query, tmp, fl)
    }
    query
  }

}