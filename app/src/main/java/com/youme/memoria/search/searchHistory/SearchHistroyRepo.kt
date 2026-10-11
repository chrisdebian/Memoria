package com.youme.memoria.search.searchHistory

import android.content.Context
import android.util.TimeUtils
import com.youme.inkdex.roomCach.PhotosDatabase
import com.youme.inkdex.roomCach.SearchHistoryEntity
import java.sql.Timestamp

class SearchHistroyRepo(context: Context) {
    private val dao = PhotosDatabase.getInstance(context).SearchHistoryDao()



    suspend fun addToHistory(query:String,isPinned: Boolean){
        if (query.isEmpty()) return
        dao.insert(SearchHistoryEntity(query=query, timestamp =  System.currentTimeMillis(), pinned =isPinned))
    }


    suspend fun setPinned(query: String,pinned: Boolean) = dao.setPinned(query,pinned)

    suspend fun deleteHistory() = dao.deleteAll()
    suspend fun deleteHistroyItem(query: String) = dao.deleteItem(query)
}