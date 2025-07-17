package com.example.musicallens.ui.favorites

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val _favoriteFolders = MutableLiveData<List<File>>()
    val favoriteFolders: LiveData<List<File>> = _favoriteFolders

    init {
        loadFavoriteFolders()
    }

    fun loadFavoriteFolders() {
        val context = getApplication<Application>().applicationContext
        val sharedPreferences = context.getSharedPreferences("Favorites", Context.MODE_PRIVATE)
        val favoriteFolderNames = sharedPreferences.getStringSet("favorite_folders", emptySet()) ?: emptySet()

        val appRootDirectory = File(context.filesDir, "MusicallensData")
        val currentFavorites = mutableListOf<File>()

        if (appRootDirectory.exists() && appRootDirectory.isDirectory) {
            favoriteFolderNames.forEach { folderName ->
                val folder = File(appRootDirectory, folderName)
                if (folder.exists() && folder.isDirectory) {
                    currentFavorites.add(folder)
                }
            }
        }
        currentFavorites.sortBy { it.name.lowercase() } // Sortare alfabetică
        _favoriteFolders.value = currentFavorites
    }
}