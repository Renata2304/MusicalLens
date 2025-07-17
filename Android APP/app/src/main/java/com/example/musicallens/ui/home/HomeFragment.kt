package com.example.musicallens.ui.home

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.core.content.ContextCompat
import android.widget.SearchView

import com.example.musicallens.R
import com.example.musicallens.databinding.FragmentHomeBinding
import java.io.File

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val DEFAULT_MARGIN_DP = 16
    private val SMALL_MARGIN_DP = 8
    private val ICON_SIZE_DP = 40
    private val PADDING_MEDIUM_DP = 16
    private val PADDING_SMALL_DP = 8
    private val CORNER_RADIUS_DP = 8

    private val TEXT_COLOR_PRIMARY: Int by lazy { Color.parseColor("#333333") }
    private val TEXT_COLOR_SECONDARY: Int by lazy { Color.parseColor("#666666") }
    private val FOLDER_ITEM_BACKGROUND_COLOR: Int by lazy { Color.parseColor("#F5F5F5") }

    private lateinit var sharedPreferences: SharedPreferences
    private var allMusicFolders: List<File> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        sharedPreferences = requireActivity().getSharedPreferences("Favorites", Context.MODE_PRIVATE)

        loadAllMusicFolders()

        setupListeners()
        return root
    }

    override fun onResume() {
        super.onResume()
        displayMusicFolders(null)
    }

    private fun loadAllMusicFolders() {
        val appRootDirectory = File(requireContext().filesDir, "MusicallensData")

        if (!appRootDirectory.exists()) {
            appRootDirectory.mkdirs()
        }

        allMusicFolders = appRootDirectory.listFiles { file ->
            file.isDirectory
        }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun setupListeners() {
        binding.favoritesLayout.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_navigation_favorites)
        }

        binding.tutorialLayout.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_navigation_tutorial)
        }


        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                displayMusicFolders(newText)
                return true
            }
        })
    }

    private fun displayMusicFolders(query: String?) {
        val container = binding.recentFilesContainer
        container.removeAllViews()

        val filteredFolders = if (query.isNullOrBlank()) {
            allMusicFolders
        } else {
            allMusicFolders.filter { folder ->
                folder.name.contains(query, ignoreCase = true)
            }
        }

        if (filteredFolders.isNullOrEmpty()) {
            val noFoldersTextView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, DEFAULT_MARGIN_DP.dpToPx(), 0, 0)
                }
                text = if (query.isNullOrBlank()) {
                    "Nu există partituri salvate. Începe prin a adăuga una nouă!"
                } else {
                    "Nicio partitură găsită care să se potrivească cu '$query'."
                }
                gravity = Gravity.CENTER
                textSize = 18f
                setTextColor(TEXT_COLOR_SECONDARY)
            }
            container.addView(noFoldersTextView)
        } else {
            filteredFolders.forEach { folder ->
                val folderName = folder.name
                val folderEntryLayout = LinearLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(
                            DEFAULT_MARGIN_DP.dpToPx(),
                            SMALL_MARGIN_DP.dpToPx(),
                            DEFAULT_MARGIN_DP.dpToPx(),
                            SMALL_MARGIN_DP.dpToPx()
                        )
                    }
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = CORNER_RADIUS_DP.dpToPx().toFloat()
                        setColor(ContextCompat.getColor(requireContext(), R.color.md_theme_background))
                    }
                    setPadding(
                        PADDING_MEDIUM_DP.dpToPx(),
                        PADDING_SMALL_DP.dpToPx(),
                        PADDING_MEDIUM_DP.dpToPx(),
                        PADDING_SMALL_DP.dpToPx()
                    )

                    setOnClickListener {
                        val bundle = Bundle().apply {
                            putString("folderName", folderName)
                        }
                        findNavController().navigate(R.id.action_navigation_home_to_displayData, bundle)
                    }
                }

                // ICONIȚA DE FIȘIER
                val icon = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ICON_SIZE_DP.dpToPx(),
                        ICON_SIZE_DP.dpToPx()
                    ).apply {
                        marginEnd = DEFAULT_MARGIN_DP.dpToPx()
                    }
                    setImageResource(R.drawable.ic_baseline_save_24)
                    contentDescription = "Folder Icon"
                    setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_theme_onTertiary))
                }

                // NUMELE FIȘIERULUI
                val folderNameTextView = TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                    )
                    text = folderName
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onTertiary))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }

                // BUTONUL DE OPȚIUNI (SĂGEATĂ DREAPTA)
                val optionsButton = ImageButton(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ICON_SIZE_DP.dpToPx(),
                        ICON_SIZE_DP.dpToPx()
                    ).apply {
                        marginStart = DEFAULT_MARGIN_DP.dpToPx()
                    }
                    background = ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
                    setImageResource(R.drawable.baseline_arrow_right_24)
                    contentDescription = "MusicXML Options"
                    setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_theme_onTertiary))

                    setOnClickListener {
                        showFolderOptionsDialog(it, folder)
                    }
                }

                folderEntryLayout.addView(icon)
                folderEntryLayout.addView(folderNameTextView)
                folderEntryLayout.addView(optionsButton)

                container.addView(folderEntryLayout)
            }
        }
    }

    private fun showFolderOptionsDialog(view: View, folder: File) {
        val folderName = folder.name
        val isFavorite = getFavoriteFolderNames().contains(folderName)

        val optionsList = mutableListOf<String>()
        if (isFavorite) {
            optionsList.add("Șterge din Favorite")
        } else {
            optionsList.add("Adaugă la Favorite")
        }
        optionsList.add("Șterge Partitura")

        val optionsArray = optionsList.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Opțiuni partitură: ${folder.name}")
            .setItems(optionsArray) { dialog, which ->
                when (optionsArray[which]) {
                    "Adaugă la Favorite" -> {
                        toggleFavorite(folderName, true)
                    }
                    "Șterge din Favorite" -> {
                        toggleFavorite(folderName, false)
                    }
                    "Șterge Partitura" -> {
                        showDeleteConfirmationDialog(folder)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(folderToDelete: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("Șterge Partitură")
            .setMessage("Ești sigur că vrei să ștergi partitura '${folderToDelete.name}'? Această acțiune nu poate fi anulată.")
            .setPositiveButton("Șterge") { dialog, _ ->
                if (folderToDelete.isDirectory) {
                    try {
                        folderToDelete.deleteRecursively()
                        removeFolderFromFavorites(folderToDelete.name)
                        Toast.makeText(context, "Partitura '${folderToDelete.name}' ștearsă cu succes.", Toast.LENGTH_SHORT).show()
                        displayMusicFolders(binding.searchView.query?.toString())
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Eroare la ștergerea folderului: ${e.message}", e)
                        Toast.makeText(context, "Eroare la ștergerea partiturii.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Nu este un director valid.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    private fun getFavoriteFolderNames(): MutableSet<String> {
        return sharedPreferences.getStringSet("favorite_folders", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveFavoriteFolderNames(favoriteFolderNames: Set<String>) {
        val editor = sharedPreferences.edit()
        editor.putStringSet("favorite_folders", favoriteFolderNames)
        editor.apply()
    }

    private fun toggleFavorite(folderName: String, add: Boolean) {
        val favoriteFolderNames = getFavoriteFolderNames()
        if (add) {
            favoriteFolderNames.add(folderName)
            Toast.makeText(context, "'$folderName' a fost adăugat la favorite.", Toast.LENGTH_SHORT).show()
        } else {
            favoriteFolderNames.remove(folderName)
            Toast.makeText(context, "'$folderName' a fost șters din favorite.", Toast.LENGTH_SHORT).show()
        }
        saveFavoriteFolderNames(favoriteFolderNames)
        displayMusicFolders(binding.searchView.query?.toString())
    }

    private fun removeFolderFromFavorites(folderName: String) {
        val favoriteFolderNames = getFavoriteFolderNames()
        if (favoriteFolderNames.remove(folderName)) {
            saveFavoriteFolderNames(favoriteFolderNames)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private val Int.sp: Float get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        this.toFloat(),
        resources.displayMetrics
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}