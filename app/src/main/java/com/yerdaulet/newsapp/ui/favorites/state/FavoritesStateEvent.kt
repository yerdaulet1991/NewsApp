package com.yerdaulet.newsapp.ui.favorites.state

import com.yerdaulet.newsapp.models.Article

sealed class FavoritesStateEvent {
    class GetFavoritesEvent(
    ) : FavoritesStateEvent()

    class None : FavoritesStateEvent()

    class DeleteFromFavEvent(
        val article: Article
    ) : FavoritesStateEvent()
}
