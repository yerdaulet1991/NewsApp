package com.yerdaulet.newsapp.ui.favorites.state

import com.yerdaulet.newsapp.models.Article

data class FavoritesViewState(
    val favoritesFields: FavoritesFields = FavoritesFields()
) {
    class FavoritesFields(
        var favoritesList: List<Article> = ArrayList<Article>(),
        var emptyFavoritesScreen: String = ""
    )
}