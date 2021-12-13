package swaydb.core.segment.block

import swaydb.core.cache.CacheUnsafe
import swaydb.core.segment.cache.sweeper.MemorySweeper
import swaydb.slice.{Slice, SliceOption}
import swaydb.utils.HashedMap

class BlockCacheState(val blockSize: Int,
                      val skipBlockCacheSeekSize: Int,
                      val sweeper: MemorySweeper.Block,
                      val mapCache: CacheUnsafe[Unit, HashedMap.Concurrent[Long, SliceOption[Byte], Slice[Byte]]]) {
  val blockSizeDouble: Double = blockSize

  def clear() =
    mapCache.clear()
}
