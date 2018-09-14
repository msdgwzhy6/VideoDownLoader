package com.mzp.player

import android.util.Log
import com.mzp.player.http.FileCallBack
import com.mzp.player.http.RetrofitHttpClient
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * author : ice-coffee.

 * date : 2018/9/13.

 * description :
 */
object M3U8Manager {

    private var tsCacheDir = "tscache"

    fun downloadM3u8(url: String, cacheDir: String, callBack: FileCallBack<MutableList<M3U8Ts>?>) {
        Observable.defer {
            Observable.just(RetrofitHttpClient.retrofitApi!!.getGoodsDetails(url).execute().body()!!.byteStream())
        }
                .map {
                    inputStream ->
                    val m3U8 = M3U8Utils.parseM3u8Url(url, inputStream)
                    //cacheDirectory/***.m3u8
                    val cachePath: String = cacheDir + File.separator + m3U8?.name
                    FileIOUtils.writeFileFromIS(File(cachePath), inputStream)
                    m3U8
                }
                .flatMap {
                    m3u8 -> Observable.fromIterable<M3U8Ts>(m3u8.tsList)
                }
                .map {
                    m3u8ts ->

                    //cacheDirectory/tscache/***.ts
                    val tsCachePath: String = cacheDir + File.separator + tsCacheDir + File.separator + m3u8ts.name
                    val cacheFile = File(tsCachePath)

                    if (cacheFile.exists()) {
                        m3u8ts.file = cacheFile
                    } else {
                        var inputStream: InputStream? = null

                        try {
                            inputStream = RetrofitHttpClient.retrofitApi!!.getGoodsDetails(m3u8ts.tsUrl!!).execute().body()!!.byteStream()
                            m3u8ts.file = StorageUtil.saveFile(inputStream, cacheFile)
                            Log.e("requestcallback1", "${cacheFile.name}")
                        } catch (e: Exception) {
                            Log.e("requestcallback1", "${e.message}")
                        }

                    }
                    m3u8ts
                }
                .filter { m3u8ts -> m3u8ts.file == null }
                .toList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ list ->
                    if (list.size == 0) {
                        File(cacheDir).delete()
                    }
                    callBack.onSuccess(list)}, {e -> callBack.onError(e?.message)})

    }

    fun mergeM3u8(cacheDirectory: String, saveDirectory: String) {
        try {
            Observable.defer {
                val tsCacheDirPath = cacheDirectory + File.separator + tsCacheDir

                val files = File(tsCacheDirPath).listFiles()

                val fileList = Arrays.asList<File>(*files!!)
                try {
                    M3U8Utils.mergeM3u8ts(fileList, File(saveDirectory))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                Observable.just(1)
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { Log.e("merge_m3u8", "merge_success") }

        } catch (e: Exception) {
            Log.e("requestcallback2", "${e.message}")
        }
    }
}