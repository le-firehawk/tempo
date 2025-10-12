package com.cappielloantonio.tempo.util

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata

@UnstableApi
class StreamingCacheDataSource private constructor(
    private val cacheDataSource: CacheDataSource,
    private val removeIncompleteOnClose: Boolean,
): DataSource {
    private val TAG = "StreamingCacheDataSource"

    private var currentDataSpec: DataSpec? = null
    private var isEndOfInputReached: Boolean = false

    class Factory(
        private val cacheDatasourceFactory: CacheDataSource.Factory,
        private val removeIncompleteOnClose: Boolean,
    ): DataSource.Factory {
        override fun createDataSource(): DataSource {
            val dataSource = cacheDatasourceFactory.createDataSource() as CacheDataSource
            return StreamingCacheDataSource(dataSource, removeIncompleteOnClose)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val result = cacheDataSource.read(buffer, offset, length)
        if (result == C.RESULT_END_OF_INPUT) {
            isEndOfInputReached = true
        }
        return result
    }

    override fun addTransferListener(transferListener: TransferListener) {
        return cacheDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val ret = cacheDataSource.open(dataSpec)
        currentDataSpec = dataSpec
        isEndOfInputReached = false
        return ret
    }

    override fun getUri(): Uri? {
        return cacheDataSource.uri
    }

    override fun close() {
        cacheDataSource.close()

        val dataSpec = currentDataSpec

        if (removeIncompleteOnClose && dataSpec != null) {
            val cacheKey = cacheDataSource.cacheKeyFactory.buildCacheKey(dataSpec)
            val contentLength = ContentMetadata.getContentLength(cacheDataSource.cache.getContentMetadata(cacheKey));

            if (isEndOfInputReached || contentLength != C.LENGTH_UNSET.toLong()) {
                Log.d(TAG, "Key $cacheKey has been fully cached")
            } else {
                Log.d(TAG, "Removing partial cache for $cacheKey")
                cacheDataSource.cache.removeResource(cacheKey)
            }
        }

        currentDataSpec = null
    }
}