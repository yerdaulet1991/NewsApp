package com.yerdaulet.newsapp.ui.headlines.viewmodel

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import com.yerdaulet.newsapp.repository.HeadlinesRepository
import com.yerdaulet.newsapp.ui.BaseViewModel
import com.yerdaulet.newsapp.ui.DataState
import com.yerdaulet.newsapp.ui.headlines.state.HeadlinesStateEvent
import com.yerdaulet.newsapp.ui.headlines.state.HeadlinesViewState
import com.yerdaulet.newsapp.util.TAG
import com.yerdaulet.newsapp.util.getCategory
import com.yerdaulet.newsapp.util.getCountry
import com.yerdaulet.newsapp.util.saveCountryAndCategory
import javax.inject.Inject


class HeadlinesViewModel
@Inject
constructor(
    private val headlinesRepository: HeadlinesRepository,
    private val sharedPreferences: SharedPreferences,
    private val editor: SharedPreferences.Editor
) : BaseViewModel<HeadlinesStateEvent, HeadlinesViewState>() {

    init {
        updateViewState { headlinesFields->
            headlinesFields.country = sharedPreferences.getCountry()
            headlinesFields.category = sharedPreferences.getCategory()
        }
    }
    override fun initNewViewState(): HeadlinesViewState =  HeadlinesViewState()

    override fun handleStateEvent(stateEvent: HeadlinesStateEvent) = when (stateEvent) {
        is HeadlinesStateEvent.HeadlinesSearchEvent -> {

            with(stateEvent,{
                headlinesRepository.getTopHeadlines(
                    country,category,searchQuery,page
                )
            })
        }
        is HeadlinesStateEvent.None -> {
            object: LiveData<DataState<HeadlinesViewState>>(){
                override fun onActive() {
                    super.onActive()
                    value = DataState.none()
                }
            }
        }
        is HeadlinesStateEvent.HeadlinesAddToFavEvent -> {
            headlinesRepository.insertArticleToDB(stateEvent.article)
        }
        is HeadlinesStateEvent.HeadlinesRemoveFromFavEvent -> {
            headlinesRepository.deleteArticleFromDB(stateEvent.article)
        }
        is HeadlinesStateEvent.HeadlinesCheckFavEvent->{
            headlinesRepository.checkFavorite(stateEvent.articles,stateEvent.isQueryExhausted)
        }
    }

    fun cancelActiveJobs() {
        headlinesRepository.cancelActiveJobs() //repository extends JobManager, cancelActiveJobs is part of the job Manager
        setStateEvent(HeadlinesStateEvent.None())
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: ")
        cancelActiveJobs()
    }

    fun saveCategoryAndCountry(country:String,category:String) = editor.saveCountryAndCategory(country,category)


    fun getVSHeadlines() = getCurrentViewStateOrNew().headlinesFields
    fun updateViewState(operation:(HeadlinesViewState.HeadlineFields)->Unit) = with(getCurrentViewStateOrNew()){
        operation(headlinesFields)
        setViewState(this)
    }



}