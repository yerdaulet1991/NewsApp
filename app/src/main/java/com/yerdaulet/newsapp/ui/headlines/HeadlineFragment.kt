package com.yerdaulet.newsapp.ui.headlines

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.yerdaulet.newsapp.R
import com.yerdaulet.newsapp.models.Article
import com.yerdaulet.newsapp.ui.BaseFragment
import com.yerdaulet.newsapp.ui.DataState
import com.yerdaulet.newsapp.ui.headlines.state.HeadlinesStateEvent
import com.yerdaulet.newsapp.ui.headlines.state.HeadlinesViewState
import com.yerdaulet.newsapp.ui.headlines.viewmodel.*
import com.yerdaulet.newsapp.util.*
import com.yerdaulet.newsapp.viewmodels.ViewModelProviderFactory
import com.bumptech.glide.RequestManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import javax.inject.Inject


class HeadlineFragment : BaseFragment(), HeadlinesListAdapter.Interaction,SwipeRefreshLayout.OnRefreshListener {

    @Inject
    lateinit var requestManager: RequestManager
    @Inject
    lateinit var  providerFactory: ViewModelProviderFactory
    lateinit var viewModel: HeadlinesViewModel

    private lateinit var headlinesAdapter: HeadlinesListAdapter
    private  var recyclerView : RecyclerView?=null
    private  var tv_error:TextView?=null
    private lateinit var searchView:androidx.appcompat.widget.SearchView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var chipGroup:ChipGroup

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.fragment_headlines, container, false)
        recyclerView = view.findViewById(R.id.rv_headlines)
        chipGroup = view.findViewById(R.id.category_list)
        tv_error = view.findViewById(R.id.tv_error)
        swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).apply {
            setOnRefreshListener(this@HeadlineFragment)
        }
        initRV()
        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(
            view,
            savedInstanceState
        ) //BaseHeadlineFragment implementation and Fragment()
        viewModel = activity?.run {
            ViewModelProvider(this,providerFactory).get(HeadlinesViewModel::class.java)
        }?:throw Exception ("Invalid Activity")
        Log.d(TAG, "HeadlineFragment: onViewCreated: ${viewModel} ")
        cancelActiveJobs()
        initChipGroup()
        subscribeObservers()
        executeRequest()

    }

    override fun getFragmentId(): Int = R.id.headlineFragment
    override fun cancelActiveJobs(){
        if(::viewModel.isInitialized) viewModel.cancelActiveJobs()
    }


    // no point of firing the event everytime we rotate or change graph
    //as long as the viewModel is alive, no need to re-fire
    //viewModel life span is tied to the MainActivity (store)
    //  queryEvent will be held in the viewModel and set to true when the viewModel is first created
    // when we rotate , the queryEvent liveData will still hold the same object/event, which is already been handled
    // NOTE: even though the DataState member variables are wrapped in event,
    // it will be refreshed because its event object are being updated, in the networkBoundResources and repositories every time we fire this request
    private fun executeRequest() = viewModel.executeQueryEvent.observe(viewLifecycleOwner, Observer { queryEvent->
        if( queryEvent.getContentIfNotHandled()!=null) { //only proceed if this query has never been handled
            Log.d(TAG, "HeadlineFragment: executeQueryEvent: $queryEvent")
            with(viewModel) {
                with(getVSHeadlines()) {
                    loadFirstPage(country, category)
                }
            }
        }else{
            if(!viewModel.getVSHeadlines().headlinesList.isNullOrEmpty()) {
                Log.d(TAG, "handlePaginationSuccessResult fragment: ${viewModel.getVSHeadlines().isQueryExhausted} ")
                viewModel.setStateEvent(HeadlinesStateEvent.HeadlinesCheckFavEvent(viewModel.getVSHeadlines().headlinesList,viewModel.getVSHeadlines().isQueryExhausted))
            }
        }
    })


    private fun executeSearchQuery(query:String){
        with(viewModel){
            with(getVSHeadlines()){
                loadFirstPage(country,category,query)
            }
        }
        resetUI()
    }

    private fun subscribeObservers() = with(viewModel){
        dataState.observe(viewLifecycleOwner, Observer { dataState ->
            dataState?.let {
                stateChangeListener?.onDataStateChange(it) //Listener(BaseActivity/Activity) will handle the loading (progress bar), error dialog/toast, data response msg (in this app is always null)
                handlePagination(it)
            }
        })
        //As soon as the HeadlineFrag is created , it will receive the viewState (if available) in the ViewModel
        // and everTime the viewState is changed , we update the ui
        viewState.observe(viewLifecycleOwner, Observer { viewModelViewState ->
            Log.d(TAG, "HeadlineFragment: viewState observer: ${viewModelViewState}")
            viewModelViewState?.let {
                with(it.headlinesFields){

                    headlinesAdapter.submitList(
                        list = headlinesList, //could be empty or not
                        isQueryExhausted = isQueryExhausted,
                        page = page
                    )
                    //only show error screen if the list is empty and the error message is not empty
                    //because if the user retrieved the list successfully, turns the wifi off and then pull to refresh, we dont want to show the error screen on top of the list
                    if(headlinesList.isNullOrEmpty() && !errorScreenMsg.isEmpty()){
                        Log.d(TAG, "subscribeObservers: ${errorScreenMsg}")
                        recyclerView!!.visibility  = View.GONE
                        tv_error!!.apply {
                            visibility = View.VISIBLE
                            setText( errorScreenMsg)
                        }
                    }else{
                        tv_error!!.visibility = View.GONE
                        recyclerView!!.visibility  = View.VISIBLE
                    }
                }
            }
        })
    }


    private fun handlePagination(dataState:DataState<HeadlinesViewState>){
        //this component handle the data data

        //we update the viewstate field 'QueryInProgress'
        //so we can be able to fire another request when we need to , and prevent another request when its currently loading
        viewModel.updateViewState { headlinesFields->
            headlinesFields.isQueryInProgress = dataState.loading.isLoading
        }

        dataState.data?.let {
            it.data?.let { eventViewState ->
                eventViewState.getContentIfNotHandled()?.let { networkViewState ->
                    Log.d(TAG, "HeadlineFragment: dataStateReturned: with data!=null, updating headlinesList")
                    //we are updating a field in the viewState, which will update the viewState itself
                    // and fire observers
                    Log.d(TAG, "networkViewState :${networkViewState.headlinesFields.isQueryExhausted}")
                    viewModel.handlePaginationSuccessResult(networkViewState)
                }
            }
        }
        dataState.error?.let { errorEvent ->
            //handle the error if not null
            //if the errorEvent hasNotBeenHandled, update the view state to update the ui, otherwise do nothing
            errorEvent.peekContent()?.let{ stateError->
                Log.d(TAG, "HeadlineFragment: dataStateReturned: with error!=null, updating errorMsgScreen")
                viewModel.updateViewState { headlinesFields->
                    headlinesFields.errorScreenMsg = stateError.response.message?:""
                }
            }
        }
    }

    private fun initRV() {
        recyclerView!!.apply {
            layoutManager = LinearLayoutManager(this@HeadlineFragment.context)
            headlinesAdapter = HeadlinesListAdapter(this@HeadlineFragment, requestManager)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastPosition = layoutManager.findLastVisibleItemPosition()
                    if (lastPosition == headlinesAdapter.itemCount.minus(1)) {
                        Log.d(TAG, "HeadlineFragment: load next page...")
                        if(requireActivity().application.isNetworkAvailable() && viewModel.getVSHeadlines().page >1 && viewModel.getVSHeadlines().errorScreenMsg.isNotEmpty()){
                            viewModel.updateViewState {headlineFields ->
                                headlineFields.errorScreenMsg = ""
                            }
                        }
                        viewModel.loadNextPage()
                    }
                }
            })
            adapter = headlinesAdapter
        }
    }
    private fun  initSV(menu: Menu){
        activity?.apply {
            //   val searchManager = getSystemService((SEARCH_SERVICE)) as SearchManager
            searchView = (menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView).apply {
                maxWidth = Integer.MAX_VALUE
                setIconifiedByDefault(true)
                isSubmitButtonEnabled = true
            }
        }
        val searchPlate = searchView.findViewById(R.id.search_src_text) as EditText
        searchPlate.setOnEditorActionListener { v, actionId, event ->
            if(actionId == EditorInfo.IME_ACTION_UNSPECIFIED || actionId == EditorInfo.IME_ACTION_SEARCH){
                val searchQuery = v.text.toString()
                executeSearchQuery(searchQuery)
            }
            true
        }
        (searchView.findViewById(R.id.search_go_btn) as View).setOnClickListener {
            val searchQuery = searchPlate.text.toString()
            executeSearchQuery(searchQuery)
        }
    }

    private fun resetUI(){
        //scroll the recyclerview to position zero
        recyclerView?.smoothScrollToPosition(0)
        //stateChangeListener is the activity or the base activity
        stateChangeListener?.hideSoftKeyboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView!!.adapter = null //to avoid memory leak
        tv_error = null

    }

    override fun onItemSelected(position: Int, item: Article) {
        Log.d(TAG, "onItemSelected: position,article: ${position}, ${item} ")
        fireIntent(item)
    }

    override fun onFavIconClicked(isFavorite: Boolean, item: Article) {
        viewModel.updateViewState { headlineFields ->
            val favArticle = item.copy(isFavorite = true)
            headlineFields.headlinesList = headlineFields.headlinesList.findCommonAndReplace(favArticle)
        }
        if(isFavorite) {
            viewModel.setStateEvent(HeadlinesStateEvent.HeadlinesAddToFavEvent(item))
        }
        else{
            viewModel.setStateEvent(HeadlinesStateEvent.HeadlinesRemoveFromFavEvent(item))
        }


    }

    override fun onShareIconClick(item: Article) {
        val sendIntent = Intent().apply{
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, item.url)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }


    private fun fireIntent(item: Article) = with(Intent(Intent.ACTION_VIEW)){
        data = Uri.parse(item.url)
        startActivity(this)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.search_menu,menu)
        initSV(menu)
    }

    override fun onRefresh() {
        with(viewModel){
            with(getVSHeadlines()){
                loadFirstPage(country,category,searchQuery)
            }
        }
        swipeRefreshLayout.isRefreshing = false
    }

    override fun onOptionsItemSelected(item: MenuItem) = when(item.itemId){
        R.id.action_filter_settings->{
            openCountryOptions()
            true
        }
        else->{
            super.onOptionsItemSelected(item)
        }
    }

    private fun openCountryOptions(){
        activity?.let {
            with(MaterialDialog(it).noAutoDismiss().customView(R.layout.layout_articles_country)) {
                getCustomView().run{
                    val selectedCountry = viewModel.getVSHeadlines().country
                    with(findViewById<RadioGroup>(R.id.country_options)){
                        if(selectedCountry.equals(AUSTRALIA)){
                            check(R.id.australia_option)
                        }else if (selectedCountry.equals(FRANCE)) {
                            check(R.id.france_option)
                        }else
                        {
                            check(R.id.united_state_option)
                        }
                    }
                    findViewById<TextView>(R.id.positive_button).setOnClickListener {
                        val newSelectedCountry = findViewById<RadioButton>(findViewById<RadioGroup>(R.id.country_options).checkedRadioButtonId)
                        var countryDefault = AUSTRALIA
                        if(newSelectedCountry.text.toString().equals(getString(R.string.united_states))){
                            countryDefault = USA
                        }
                        if(newSelectedCountry.text.toString().equals(getString(R.string.france))){
                            countryDefault = FRANCE
                        }

                        Log.d(TAG, "openCountryOptions: countryDefault $countryDefault")

                        viewModel.updateViewState { field->
                            field.country = countryDefault
                            field.searchQuery = EMPTY_STRING
                        }

                        with(viewModel.getVSHeadlines()){
                            Log.d(TAG, "openCountryOptions: $country, $category")
                            viewModel.loadFirstPage(country,category)
                        }
                        dismiss()
                    }
                    findViewById<TextView>(R.id.negative_button).setOnClickListener {
                        dismiss()
                    }

                    show()
                }
            }
        }
    }

    private fun initChipGroup(){
        val data = listOf<String>("General","Business","Entertainment","Health","Science","Sports","Technology")
        val inflater = LayoutInflater.from(chipGroup.context)
        val viewStateCategory = data.find { it.equals(viewModel.getVSHeadlines().category,true) }
        Log.d(TAG, "initChipGroup: $viewStateCategory ")
        val children = data.map { category->
            val chip = inflater.inflate(R.layout.category,chipGroup,false) as Chip
            chip.apply{
                text = category
                tag = if(text.toString().equals(viewStateCategory,true)) viewStateCategory else category
            }
        }
        for (chip in children){
            chipGroup.addView(chip)
            if(chip.tag.equals(viewStateCategory)){
                chipGroup.check(chip.id)
            }
        }

        chipGroup.setOnCheckedChangeListener { group, checkedId ->
            Log.d(TAG, "initChipGroup: check")
            viewModel.updateViewState { field->
                field.category = group.findViewById<Chip>(checkedId).text.toString().toLowerCase()
                field.searchQuery = EMPTY_STRING
            }
            with(viewModel.getVSHeadlines()){
                viewModel.loadFirstPage(country,category)
            }
        }



    }

    override fun onPause() {
        super.onPause()
        with(viewModel){
            with(getVSHeadlines()){
                saveCategoryAndCountry(country,category)
            }
        }

    }
}