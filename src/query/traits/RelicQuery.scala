package query.traits

import scala.util.parsing.json.JSONObject
import query.property.SensisQueryElement

object RelicQuery extends QueryTraits {
	def isQueryable(property : String) : Boolean = false
	def query(b : Int, e : Int, p : SensisQueryElement, r : String*) : JSONObject = ???
	def queryTops(t : Int, b : Int, e : Int, p : SensisQueryElement, r : String*) : JSONObject = ???
}