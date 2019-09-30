/*
 * Copyright (c) 2019 Simer Plaha (@simerplaha)
 *
 * This file is a part of SwayDB.
 *
 * SwayDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SwayDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 */

package swaydb.core.data

import swaydb.core.segment.format.a.block._
import swaydb.core.segment.format.a.block.binarysearch.BinarySearchIndexBlock
import swaydb.core.segment.format.a.block.hashindex.HashIndexBlock
import swaydb.data.slice.Slice

import scala.concurrent.duration.Deadline

private[core] object Stats {

  def apply(keySize: Int,
            indexEntry: Slice[Byte],
            value: Option[Slice[Byte]],
            isRemoveRange: Boolean,
            isRange: Boolean,
            isPut: Boolean,
            isPrefixCompressed: Boolean,
            previousKeyValueAccessIndexPosition: Option[Int],
            sortedIndex: SortedIndexBlock.Config,
            bloomFilter: BloomFilterBlock.Config,
            hashIndex: HashIndexBlock.Config,
            binarySearch: BinarySearchIndexBlock.Config,
            values: ValuesBlock.Config,
            previousStats: Option[Stats],
            deadline: Option[Deadline]): Stats = {

    val valueLength =
      value.foldLeft(0)(_ + _.size)

    val hasRemoveRange =
      previousStats.exists(_.segmentHasRemoveRange) || isRemoveRange

    val linkedPosition =
      previousStats.map(_.linkedPosition + 1) getOrElse 1

    val hasPrefixCompressed =
      isPrefixCompressed || previousStats.exists(_.hasPrefixCompression)

    val thisKeyValuesSortedIndexSize =
      indexEntry.size

    val segmentMaxSortedIndexEntrySize =
      previousStats.map(_.segmentMaxSortedIndexEntrySize max thisKeyValuesSortedIndexSize) getOrElse thisKeyValuesSortedIndexSize

    val segmentMinSortedIndexEntrySize =
      previousStats.map(_.segmentMinSortedIndexEntrySize min thisKeyValuesSortedIndexSize) getOrElse thisKeyValuesSortedIndexSize

    val thisKeyValuesSortedIndexSizeWithoutFooter =
      SortedIndexBlock.headerSize(false) +
        thisKeyValuesSortedIndexSize

    val thisKeyValuesRealIndexOffset =
      previousStats map {
        previous =>
          previous.thisKeyValueRealIndexOffset + previous.thisKeyValuesSortedIndexSize
      } getOrElse 0

    //starts from 0. Do not need the actual index offset for space efficiency. The actual indexOffset can be adjust during read.
    val thisKeyValuesAccessIndexOffset =
      if (isPrefixCompressed)
        previousStats.map(_.thisKeyValuesAccessIndexOffset) getOrElse thisKeyValuesRealIndexOffset
      else
        thisKeyValuesRealIndexOffset

    val thisKeyValuesSegmentValueSize =
      if (valueLength == 0)
        0
      else
        ValuesBlock.headerSize(false) +
          valueLength

    val thisKeyValuesSegmentSortedIndexAndValueSize =
      thisKeyValuesSegmentValueSize +
        thisKeyValuesSortedIndexSizeWithoutFooter

    val segmentHasRange =
      hasRemoveRange || previousStats.exists(_.segmentHasRange) || isRange

    val segmentHasPut =
      previousStats.exists(_.segmentHasPut) || isPut

    val segmentTotalNumberOfRanges =
      if (isRange)
        previousStats.map(_.segmentTotalNumberOfRanges + 1) getOrElse 1
      else
        previousStats.map(_.segmentTotalNumberOfRanges) getOrElse 0

    //unique keys that do not have prefix compressed keys.
    val uncompressedKeyCounts =
      if (isPrefixCompressed)
        previousStats.map(_.uncompressedKeyCounts) getOrElse 0
      else
        previousStats.map(_.uncompressedKeyCounts + 1) getOrElse 1

    val segmentHashIndexSize =
      if (uncompressedKeyCounts < hashIndex.minimumNumberOfKeys)
        0
      else if (isPrefixCompressed)
        previousStats.map(_.segmentHashIndexSize) getOrElse 0
      else
        HashIndexBlock.optimalBytesRequired( //just a rough calculation. This does not need to be accurate but needs to be lower than the actual
          keyCounts = uncompressedKeyCounts,
          minimumNumberOfKeys = hashIndex.minimumNumberOfKeys,
          writeAbleLargestValueSize =
            if (hashIndex.copyIndex)
              thisKeyValuesSortedIndexSize //this does not compute the size of crc long but it's ok since it's just an estimate.
            else
              1, //some low number for calculating approximate hashIndexSize does not have to be accurate.
          allocateSpace = hashIndex.allocateSpace,
          copyIndex = hashIndex.copyIndex,
          hasCompression = false
        )

    val segmentBinarySearchIndexSize =
      if (isPrefixCompressed)
        previousStats.map(_.segmentBinarySearchIndexSize) getOrElse 0
      else if (binarySearch.enabled && !sortedIndex.normaliseIndex)
        previousStats flatMap {
          previousStats =>
            if (previousStats.thisKeyValuesAccessIndexOffset == thisKeyValuesAccessIndexOffset)
              Some(previousStats.segmentBinarySearchIndexSize)
            else
              None
        } getOrElse {
          BinarySearchIndexBlock.optimalBytesRequired(
            largestValue = thisKeyValuesAccessIndexOffset,
            hasCompression = false,
            minimNumberOfKeysForBinarySearchIndex = binarySearch.minimumNumberOfKeys,
            //binary search indexes are only created for non-prefix compressed or reset point keys.
            //size calculation should only account for those entries because duplicates are not allowed.
            valuesCount = uncompressedKeyCounts
          )
        }
      else
        0

    val segmentValuesSizeWithoutHeader: Int =
      previousStats.map(_.segmentValuesSizeWithoutHeader).getOrElse(0) +
        valueLength

    val segmentValuesSize: Int =
      if (segmentValuesSizeWithoutHeader != 0)
        ValuesBlock.headerSize(false) +
          segmentValuesSizeWithoutHeader
      else if (valueLength != 0)
        ValuesBlock.headerSize(false) +
          segmentValuesSizeWithoutHeader
      else
        0

    val segmentSortedIndexSizeWithoutHeader =
      previousStats.map(_.segmentSortedIndexSizeWithoutHeader).getOrElse(0) +
        thisKeyValuesSortedIndexSize

    val segmentSortedIndexSize =
      SortedIndexBlock.headerSize(false) +
        segmentSortedIndexSizeWithoutHeader

    val segmentValueAndSortedIndexEntrySize =
      if (segmentValuesSizeWithoutHeader == 0)
        segmentSortedIndexSizeWithoutHeader +
          SortedIndexBlock.headerSize(false)
      else
        segmentValuesSizeWithoutHeader +
          segmentSortedIndexSizeWithoutHeader +
          SortedIndexBlock.headerSize(false) +
          ValuesBlock.headerSize(false)

    val segmentBloomFilterSize =
      if (bloomFilter.falsePositiveRate <= 0.0 || hasRemoveRange || linkedPosition < bloomFilter.minimumNumberOfKeys)
        0
      else
        BloomFilterBlock.optimalSize(
          numberOfKeys = linkedPosition,
          falsePositiveRate = bloomFilter.falsePositiveRate,
          hasCompression = false,
          minimumNumberOfKeys = bloomFilter.minimumNumberOfKeys,
          updateMaxProbe = _ => 1
        )

    val segmentSizeWithoutFooter: Int =
      segmentValuesSize +
        segmentSortedIndexSize +
        segmentHashIndexSize +
        segmentBinarySearchIndexSize +
        segmentBloomFilterSize

    val segmentSize: Int =
      segmentSizeWithoutFooter +
        SegmentFooterBlock.optimalBytesRequired

    val segmentUncompressedKeysSize: Int =
      previousStats.map(_.segmentUncompressedKeysSize).getOrElse(0) + keySize

    new Stats(
      valueLength = valueLength,
      segmentSize = segmentSize,
      linkedPosition = linkedPosition,
      segmentValueAndSortedIndexEntrySize = segmentValueAndSortedIndexEntrySize,
      segmentSortedIndexSizeWithoutHeader = segmentSortedIndexSizeWithoutHeader,
      segmentValuesSize = segmentValuesSize,
      segmentValuesSizeWithoutHeader = segmentValuesSizeWithoutHeader,
      segmentSortedIndexSize = segmentSortedIndexSize,
      segmentUncompressedKeysSize = segmentUncompressedKeysSize,
      segmentSizeWithoutFooter = segmentSizeWithoutFooter,
      uncompressedKeyCounts = uncompressedKeyCounts,
      thisKeyValuesSegmentKeyAndValueSize = thisKeyValuesSegmentSortedIndexAndValueSize,
      thisKeyValuesSortedIndexSize = thisKeyValuesSortedIndexSize,
      thisKeyValuesAccessIndexOffset = thisKeyValuesAccessIndexOffset,
      thisKeyValueRealIndexOffset = thisKeyValuesRealIndexOffset,
      segmentMaxSortedIndexEntrySize = segmentMaxSortedIndexEntrySize,
      segmentMinSortedIndexEntrySize = segmentMinSortedIndexEntrySize,
      segmentHashIndexSize = segmentHashIndexSize,
      segmentBloomFilterSize = segmentBloomFilterSize,
      segmentBinarySearchIndexSize = segmentBinarySearchIndexSize,
      segmentTotalNumberOfRanges = segmentTotalNumberOfRanges,
      segmentHasRemoveRange = hasRemoveRange,
      segmentHasRange = segmentHasRange,
      segmentHasPut = segmentHasPut,
      hasPrefixCompression = hasPrefixCompressed
    )
  }
}

private[core] case class Stats(valueLength: Int,
                               segmentSize: Int,
                               linkedPosition: Int,
                               segmentValueAndSortedIndexEntrySize: Int,
                               segmentSortedIndexSizeWithoutHeader: Int,
                               segmentValuesSize: Int,
                               segmentValuesSizeWithoutHeader: Int,
                               segmentSortedIndexSize: Int,
                               segmentUncompressedKeysSize: Int,
                               segmentSizeWithoutFooter: Int,
                               uncompressedKeyCounts: Int,
                               thisKeyValuesSegmentKeyAndValueSize: Int,
                               thisKeyValuesSortedIndexSize: Int,
                               thisKeyValuesAccessIndexOffset: Int,
                               //do not access this from outside, used in stats only.
                               private[Stats] val thisKeyValueRealIndexOffset: Int,
                               segmentHashIndexSize: Int,
                               segmentBloomFilterSize: Int,
                               segmentBinarySearchIndexSize: Int,
                               segmentTotalNumberOfRanges: Int,
                               segmentHasRemoveRange: Boolean,
                               segmentHasRange: Boolean,
                               segmentHasPut: Boolean,
                               segmentMaxSortedIndexEntrySize: Int,
                               segmentMinSortedIndexEntrySize: Int,
                               hasPrefixCompression: Boolean) {

  def memorySegmentSize =
    segmentUncompressedKeysSize + segmentValuesSize

  def thisKeyValueMemorySize =
    thisKeyValuesSortedIndexSize + valueLength

  def hasSameIndexSizes(): Boolean =
    segmentMinSortedIndexEntrySize == segmentMaxSortedIndexEntrySize
}
