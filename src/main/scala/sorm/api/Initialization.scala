package sorm.api

import sorm._
import reflection._
import mappings._
import jdbc._
import tableSorters._
import connection._
import Sorm._

import sext._, embrace._
import com.weiglewilczek.slf4s.Logging

object Initialization extends Logging {

  def errors ( mappings : Stream[EntityMapping] ) : Stream[String]
    = mappings
        .zipBy(_.deepContainedMappings.collect{case m : EntityMapping => m})
        .map{case (m, refs) => m -> refs.filterNot(r => mappings.exists(_.reflection =:= r.reflection))}
        .filter(_._2.nonEmpty)
        .map(_ $$ ("Entity `" + _.reflection.name + "` refers to unregistered entities: " + _.map("`" + _.reflection.name + "`").mkString(", "))) ++
      {
        val reflections = mappings.map(_.reflection)
        val diff = reflections.distinct diff reflections
        diff.nonEmpty.option("Reflections registered twice: " + diff.mkString(", "))
      }

  def errors ( e : Entity ) : Stream[String]
    = {
      val descendats
        = e.reflection.properties.values
            .unfold( a => a.notEmpty.map(a => a -> a.flatMap(_.generics)) )
            .flatten

      descendats
        .filter(r => r =:= Reflection[Any] || r =:= Reflection[AnyRef] || r =:= Reflection[AnyVal])
        .map("Specifying general types `Any`, `AnyRef` or `AnyVal` is not allowed. `" + _.name + "` detected") ++
      descendats
        .filter(_ <:< Reflection[TraversableOnce[_]])
        .filterNot(r =>
          //  using java class to erase generics
          r.javaClass == classOf[Seq[_]] ||
          r.javaClass == classOf[Set[_]] ||
          r.javaClass == classOf[Map[_, _]] ||
          r.javaClass == classOf[Range]
        )
        .map("Only general immutable `Seq`, `Set`, `Map` and `Range` are supported traversable types. `" + _ + "` detected instead") ++
      ( e.indexes.toStream ++ e.uniqueKeys )
        .flatten
        .filterNot(e.reflection.properties.contains)
        .map("Inexistent property: `" + _ + "`") ++
      ( e.indexes.toStream ++ e.uniqueKeys )
        .filter(ps => ps.distinct.size != ps.size )
        .map("Not a distinct properties list: `" + _.mkString(", ") + "`")
    }

  def initializeSchema ( mappings : Iterable[EntityMapping], connection : Connection, initMode : InitMode ) {
    initMode match {
      case InitMode.DropAllCreate =>
        try {
          connection.dropAllTables()
        } catch {
          case e : Throwable =>
            logger.warn("Couldn't drop all tables. " + e.getMessage)
        }
        mappings $ Create.tables foreach connection.createTable
      case InitMode.DropCreate =>
        mappings $ Drop.tables map (_.name) foreach { n =>
          try {
            connection.dropTable(n)
          } catch {
            case e : Throwable =>
              logger.warn("Couldn't drop table `" + n + "`. " + e.getMessage)
          }
        }
        mappings $ Create.tables foreach connection.createTable
      case InitMode.Create =>
        mappings $ Create.tables foreach { t =>
          try { connection.createTable(t) }
          catch { case e : Throwable => }
        }
      case InitMode.DoNothing =>
    }
  }

}
