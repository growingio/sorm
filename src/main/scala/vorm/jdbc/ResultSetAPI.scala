package vorm.jdbc

import collection.mutable.ListBuffer
import java.sql._


class ResultSetAPI(rs: ResultSet) {
  type JdbcType = Int

  private def parseIndexTypeSeq(indexTypeSeq: Seq[(Int, JdbcType)]): Seq[Seq[Any]] = {
    val b = ListBuffer[Seq[Any]]()

    while( rs.next() ){
      b += indexTypeSeq.map{ case (i, t) => value(i, t) }
    }

    rs.close()
    b.toList
  }

  def parseAndClose(types: Seq[JdbcType]) =
    parseIndexTypeSeq( (1 to types.size) zip types )


  def parseAndClose() = {
    val md = rs.getMetaData

    val indexTypeSeq =
      (1 to md.getColumnCount)
        .map(i => i -> md.getColumnType(i))

    parseIndexTypeSeq(indexTypeSeq)
  }

  @deprecated("use parseAndClose()")
  def parseToListsAndClose() = {
    val b = ListBuffer[Seq[Any]]()

    val md = rs.getMetaData

    val indexTypeSeq =
      (1 to md.getColumnCount)
        .map(i => i -> md.getColumnType(i))

    while( rs.next() ){
      b += indexTypeSeq.map{ case (i, t) => value(i, t) }
    }

    rs.close()
    b.toList
  }

  @deprecated("index based approach is preferred")
  def parseToMapsAndClose() = {
    val b = ListBuffer[Map[String, Any]]()

    val md = rs.getMetaData

    val indexTypeByNameMap =
      (1 to md.getColumnCount)
        .map(i => md.getColumnName(i) -> (i -> md.getColumnType(i)))
        .toMap

    while( rs.next() ){
      b += indexTypeByNameMap.mapValues{ case (i, t) => value(i, t) }
    }

    rs.close()
    b.toList
  }

  def value(i: Int, t: Int): Any = {
    import Types._
    t match {
      case INTEGER => rs.getInt(i)
      case BIGINT => rs.getLong(i)
      case CHAR | VARCHAR => rs.getString(i)
      case _ => throw new NotImplementedError
    }
  }

  def value(name: String, t: Int): Any = {
    import Types._
    t match {
      case INTEGER => rs.getInt(name)
      case BIGINT => rs.getLong(name)
      case CHAR | VARCHAR => rs.getString(name)
      case _ => throw new NotImplementedError
    }
  }


}
