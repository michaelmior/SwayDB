/*
 * Copyright 2018 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package swaydb

import swaydb.effect.Reserve

import java.lang
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import scala.jdk.CollectionConverters._

/**
 * Exception types for all known [[Error]]s that can occur. Each [[Error]] can be converted to
 * Exception which which can then be converted back to [[Error]].
 *
 * SwayDB's code itself does not use these exception it uses [[Error]] type. These types are handy when
 * converting an [[IO]] type to [[scala.util.Try]] by the client using [[toTry]].
 */
object Exception {

  val maxFunctionsToLog = 2

  case class Busy(error: Error.Recoverable) extends Exception("Is busy")
  case class OpeningFile(file: Path, reserve: Reserve[Unit]) extends Exception(s"Failed to open file $file")
  case class NoSuchFile(file: Path) extends Exception(s"No such file $file")

  case class ReservedResource(reserve: Reserve[Unit]) extends Exception("ReservedResource is busy.")
  case class NullMappedByteBuffer(exception: Exception, reserve: Reserve[Unit]) extends Exception(exception)

  case object OverlappingPushSegment extends Exception("Contains overlapping busy Segments")
  case object NoSegmentsRemoved extends Exception("No Segments Removed")
  case object NotSentToNextLevel extends Exception("Not sent to next Level")

  case class InvalidAccessException(message: String, cause: Throwable) extends Exception(message, cause)

  /**
   * Report missing functions.
   */
  def pluralFunction(size: Int) =
    if (size == 1)
      "function"
    else
      "functions"

  def listMissingFunctions(functions: Iterable[String]) =
    if (functions.size <= maxFunctionsToLog)
      s"""List(${functions.take(maxFunctionsToLog).map(id => s""""$id"""").mkString(", ")})"""
    else
      s"""List(${functions.take(maxFunctionsToLog).map(id => s""""$id"""").mkString("", ", ", ", ...")})"""

  def missingFunctionsMessage(functions: Iterable[String]) =
    s"Missing ${functions.size} ${pluralFunction(functions.size)}. ${listMissingFunctions(functions)}" + {
      if (functions.size > maxFunctionsToLog)
        s". See this exception's 'functions' value for a list of missing functions. " +
          s"Or set 'clearAppliedFunctionsOnBoot = true' to clear already applied functions."
      else
        "."
    }

  case class MissingFunctions(functions: Iterable[String]) extends Exception(missingFunctionsMessage(functions)) {
    def asJava: lang.Iterable[String] =
      functions.asJava
  }

  /**
   * [[functionId]] itself is not logged or printed to console since it may contain sensitive data but instead this Exception
   * with the [[functionId]] is returned to the client for reads and the exception's string message is only logged.
   *
   * @param functionId the id of the missing function.
   */
  case class FunctionNotFound(functionId: String) extends Exception(s"Function with id '$functionId' not found. Make sure the function is added to the instance. See documentation https://swaydb.simer.au/.")
  case class OverlappingFileLock(exception: OverlappingFileLockException) extends Exception("Failed to get directory lock.")
  case class FailedToWriteAllBytes(written: Int, expected: Int, bytesSize: Int) extends Exception(s"Failed to write all bytes written: $written, expected : $expected, bytesSize: $bytesSize")
  case class CannotCopyInMemoryFiles(file: Path) extends Exception(s"Cannot copy in-memory files $file")
  case class SegmentFileMissing(path: Path) extends Exception(s"$path: Segment file missing.")

  object InvalidBaseId {
    def apply(id: Int): InvalidBaseId =
      new InvalidBaseId(id, null) //null = there is no parent cause.
  }
  case class InvalidBaseId(id: Int, cause: Throwable) extends Exception(s"Invalid keyValueId: $id.", cause)

  case class InvalidDataId(id: Int, message: String = "") extends Exception(s"Invalid data id: $id. $message")

  case class NotAnIntFile(path: Path) extends Exception
  case class UnknownExtension(path: Path) extends Exception

  case class GetOnIncompleteDeferredFutureIO(reserve: Reserve[Unit]) extends Exception("Get invoked on in-complete Future within Deferred IO.")

  case class InvalidDirectoryType(invalidType: String, expected: String) extends Exception(s"Invalid data type $invalidType for the directory of type $expected.")
  case class MissingMultiMapGenFolder(path: Path) extends Exception(s"Missing multimap gen file or folder: $path")

  case class IncompatibleVersions(previous: String, current: String)
    extends Exception(
      s"Incompatible versions! SwayDB v$current is not compatible with files created by v$previous. Use a different directory."
    )

  case class MissingBuildInfo(buildInfoFileName: String, version: String) extends Exception(s"Missing $buildInfoFileName file. This directory might be an incompatible older version of SwayDB. Current version: v$version.")

  case class InvalidLevelReservation(message: String) extends Exception(message)

}
