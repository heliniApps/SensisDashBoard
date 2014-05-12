package query.property

import query.from

class SensisQueryElement {
	var args : List[Property] = Nil
	
	def getProperty[T](name : String) : T = {
	  	val query = (from[Property] in args where (x => x.name == name) select (x => x)).fistOrDefault
	  	if (query.isEmpty) null.asInstanceOf[T]
	  	else query.get.get.asInstanceOf[T]
	}
	
	def insertProperty[T](name : String, value : T) = {
	  	def propertyInstance : Property = { val n = new CommonProperty[T](name); n.set(value); n }
	  
	  	val query = (from[Property] in args where (x => x.name == name) select (x => x)).fistOrDefault
	  	if (query.isEmpty) args = args :+ propertyInstance
	  	else query.get.asInstanceOf[CommonProperty[T]].set(value)
	}
	
	def contains(name : String) : Boolean = 
		((from[Property] in args where (x => x.name == name) select (x => x)).fistOrDefault).isEmpty
	
	override def toString = args.toString
}