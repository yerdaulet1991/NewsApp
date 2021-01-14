package com.yerdaulet.newsapp.ui.favorites

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yerdaulet.newsapp.R
import com.yerdaulet.newsapp.models.Article
import com.yerdaulet.newsapp.ui.BaseFragment
import com.yerdaulet.newsapp.ui.headlines.state.FavoritesStateEvent
import com.yerdaulet.newsapp.util.TAG
import com.yerdaulet.newsapp.viewmodels.ViewModelProviderFactory
import com.bumptech.glide.RequestManager
import kotlinx.android.synthetic.main.fragment_favorites.*
import javax.inject.Inject

class FavoritesFragment : BaseFragment(), FavoritesListAdapter.Interaction {
    @Inject
    lateinit var requestManager: RequestManager
    @Inject
    lateinit var providerFactory: ViewModelProviderFactory
    lateinit var viewModel: FavoritesViewModel
    private lateinit var favoritesAdapter: FavoritesListAdapter
    private var recyclerView: RecyclerView? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_favorites, container, false)
        recyclerView = view.findViewById(R.id.rv_favorites)
        initRV()
        return view
    }

    private fun initRV() {
        recyclerView!!.apply {
            layoutManager = LinearLayoutManager(this@FavoritesFragment.context)
            favoritesAdapter = FavoritesListAdapter(this@FavoritesFragment, requestManager)
            adapter = favoritesAdapter
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = activity?.run {
            ViewModelProvider(this, providerFactory).get(FavoritesViewModel::class.java)
        } ?: throw Exception("Invalid Activity")
        cancelActiveJobs()
        subscribeObservers()
        executeRequest()
    }

    private fun executeRequest() {
        Log.d(TAG, "executeRequest: ")
        viewModel.setStateEvent(FavoritesStateEvent.GetFavoritesEvent())
    }

    private fun subscribeObservers() {
        Log.d(TAG, "subscribeObservers: ")
        viewModel.dataState.observe(viewLifecycleOwner, Observer { dataState ->
            Log.d(TAG, "subscribeObservers: $dataState")
            dataState?.let {
                it.data?.let {
                    it.data?.let { event ->
                        Log.d(TAG, "subscribeObservers: $event")
                        event.getContentIfNotHandled()?.let { dbViewState ->
                            viewModel.updateViewState { favoritesFields ->
                                favoritesFields.favoritesList =
                                    dbViewState.favoritesFields.favoritesList
                                favoritesFields.emptyFavoritesScreen =
                                    dbViewState.favoritesFields.emptyFavoritesScreen
                            }

                        }
                    }
                }
            }
        })
        //update the UI from ViewModel view State
        viewModel.viewState.observe(viewLifecycleOwner, Observer { viewModelViewState ->
            with(viewModelViewState.favoritesFields) {
                if (favoritesList.isNullOrEmpty()) {
                    tv_empty_screen.visibility = View.VISIBLE
                } else {
                    tv_empty_screen.visibility = View.GONE
                }
                favoritesAdapter.submitList(favoritesList)
            }

        })
    }

    override fun getFragmentId(): Int = R.id.favoritesFragment
    override fun cancelActiveJobs() {
        if (::viewModel.isInitialized) viewModel.cancelActiveJobs()
    }

    override fun onItemSelected(position: Int, item: Article) {
        fireIntent(item)
    }

    override fun onFavIconClicked(isFavorite: Boolean, item: Article) =
        viewModel.setStateEvent(FavoritesStateEvent.DeleteFromFavEvent(item))

    override fun onShareIconClick(item: Article) {
        val sendIntent = Intent().apply{
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, item.url)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun fireIntent(item: Article) {


        with(Intent(Intent.ACTION_VIEW)) {
            data = Uri.parse(item.url)
            startActivity(this)
        }

    }
}
