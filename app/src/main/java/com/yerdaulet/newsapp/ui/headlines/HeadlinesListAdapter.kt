package com.yerdaulet.newsapp.ui.headlines

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.*
import com.yerdaulet.newsapp.R
import com.yerdaulet.newsapp.models.Article
import com.yerdaulet.newsapp.util.GenericViewHolder
import com.yerdaulet.newsapp.util.formatStringDate
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import kotlinx.android.synthetic.main.layout_headlines_list_item.view.*

class HeadlinesListAdapter(
    private val interaction: Interaction? = null,
    private val requestManager: RequestManager
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    //identifier of no  result article
    private val NO_RESULTS = -2
    //identifier of no more result article
    private val NO_MORE_RESULTS = -1
    //Identifier of a default article
    private val HEADLINE_ITEM = 0
    //article Item with id = -1
    private val NO_MORE_RESULTS_HEADLINE_MARKER = Article(
        title = "com.android.myapplication.newsfeed.NO_MORE_RESULT"
    )
    private val NO_RESULTS_HEADLINE_MARKER = Article(
        title = "com.android.myapplication.newsfeed.NO_RESULT"
    )
    val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Article>() {

        override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem.url.equals(newItem.url)
        }

        override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
            return  (oldItem.url.equals(newItem.url) && (oldItem.isFavorite.equals(newItem.isFavorite)))
        }

    }

    internal inner class HeadlinesRVChangeCallback(
        private val adapter: HeadlinesListAdapter
    ) : ListUpdateCallback {
        override fun onChanged(position: Int, count: Int, payload: Any?)
                = adapter.notifyItemRangeChanged(position, count, payload) //default


        override fun onMoved(fromPosition: Int, toPosition: Int) = adapter.notifyDataSetChanged() //reset completely


        override fun onInserted(position: Int, count: Int) = adapter.notifyItemRangeChanged(position, count) //default


        override fun onRemoved(position: Int, count: Int) = adapter.notifyDataSetChanged() //reset the list completely


    }

    private val differ = AsyncListDiffer(
        HeadlinesRVChangeCallback(this),
        AsyncDifferConfig.Builder(DIFF_CALLBACK).build()
    )


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return   when(viewType){
            NO_MORE_RESULTS -> GenericViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.layout_no_more_results,
                    parent,
                    false
                )
            )
            NO_RESULTS -> GenericViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.layout_empty_results,
                    parent,
                    false
                )
            )

            HEADLINE_ITEM-> HeadlinesViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.layout_headlines_list_item,
                    parent,
                    false
                ),
                requestManager,
                interaction
            )

            else-> HeadlinesViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.layout_headlines_list_item,
                    parent,
                    false
                ),
                requestManager,
                interaction
            )
        }



    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeadlinesViewHolder -> {
                holder.bind(differ.currentList.get(position))
            }
        }
    }

    override fun getItemCount() = differ.currentList.size

    //if the id of the last article is greater than -1, means its a normal article
    //otherwise its a no more result article
    override fun getItemViewType(position: Int) = if(differ.currentList[position].title == "com.android.myapplication.newsfeed.NO_MORE_RESULT" ){
        NO_MORE_RESULTS
    }else if (differ.currentList[position].title == "com.android.myapplication.newsfeed.NO_RESULT"){
        NO_RESULTS
    } else {
        HEADLINE_ITEM
    }

    fun submitList(list: List<Article>?, isQueryExhausted:Boolean,page:Int) {
        val newList = list?.toMutableList()
        if(isQueryExhausted && page == 1){
            newList?.add(NO_RESULTS_HEADLINE_MARKER)
        }else if(isQueryExhausted){
            //if the query is exhausted , append to the list the no more result article (id = -1 )
            newList?.add(NO_MORE_RESULTS_HEADLINE_MARKER)
        }
        differ.submitList(newList)
    }

    class HeadlinesViewHolder
    constructor(
        itemView: View,
        val requestManager: RequestManager,
        private val interaction: Interaction?
    ) : RecyclerView.ViewHolder(itemView) {
        private val TAG: String = "AppDebug"

        fun bind(item: Article) = with(this@HeadlinesViewHolder.itemView) {
            itemView.setOnClickListener {
                Log.d(TAG, "HeadlinesViewHolder itemView clicked...")
                interaction?.onItemSelected(adapterPosition, item)
            }
            iv_share_image.setOnClickListener {
                interaction?.onShareIconClick(item)
            }
            cb_favorite_image.apply {
                setOnClickListener {
                    interaction?.onFavIconClicked(isChecked,item)
                }
            }
            requestManager
                .load(item.urlToImage)
                .transition(withCrossFade())
                .into(itemView.iv_article_image)
            itemView.apply {
                tv_article_author.text = item.author
                tv_article_date.text = item.publishDate?.formatStringDate()
                tv_article_description.text = item.description
                tv_article_title.text =item.title
                tv_article_source_name.text = item.source?.name
                cb_favorite_image.isChecked = item.isFavorite
            }

        }
    }

    interface Interaction {
        fun onItemSelected(position: Int, item: Article)
        fun onFavIconClicked(isFavorite:Boolean,item:Article)
        fun onShareIconClick(item:Article)
    }
}