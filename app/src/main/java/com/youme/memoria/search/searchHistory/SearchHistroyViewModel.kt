package com.youme.memoria.search.searchHistory

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Query
import com.youme.inkdex.roomCach.PhotosDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchHistroyViewModel(application : Application) : AndroidViewModel(application) {

    private val dao = PhotosDatabase.getInstance(application).SearchHistoryDao()


    fun getSearchHistory() = dao.getSearchHistroy()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private val searchHistroyRepo  = SearchHistroyRepo(application)


    fun addToHistroy(query: String){
        viewModelScope.launch(Dispatchers.IO) {
            searchHistroyRepo.addToHistory(query,dao.getPinnedStatus(query)?:false)
        }
    }

    fun setPinned(query: String,pinned: Boolean){
        viewModelScope.launch {
            searchHistroyRepo.setPinned(query,pinned)
        }
    }

    fun deleteHistory() {
        viewModelScope.launch {
            searchHistroyRepo.deleteHistory()
        }
    }
    fun deleteHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistroyRepo.deleteHistroyItem(query)
        }
    }
}