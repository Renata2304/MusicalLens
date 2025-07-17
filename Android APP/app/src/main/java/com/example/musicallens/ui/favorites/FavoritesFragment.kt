package com.example.musicallens.ui.favorites

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

import com.example.musicallens.R
import com.example.musicallens.databinding.FragmentFavoritesBinding
import java.io.File

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val DEFAULT_MARGIN_DP = 16
    private val SMALL_MARGIN_DP = 8
    private val ICON_SIZE_DP = 40
    private val PADDING_MEDIUM_DP = 16
    private val PADDING_SMALL_DP = 8
    private val CORNER_RADIUS_DP = 8

    private val TEXT_COLOR_SECONDARY: Int by lazy { Color.parseColor("#666666") }
    private val ON_TERTIARY_COLOR: Int by lazy { ContextCompat.getColor(requireContext(), R.color.md_theme_onTertiaryFixed) }
    private val BACKGROUND_COLOR: Int by lazy { ContextCompat.getColor(requireContext(), R.color.md_theme_inversePrimary) }

    private lateinit var favoritesContainer: LinearLayout
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        val root: View = binding.root

        favoritesContainer = binding.favoritesContainer

        sharedPreferences = requireActivity().getSharedPreferences("Favorites", Context.MODE_PRIVATE)

        return root
    }

    override fun onResume() {
        super.onResume()
        displayFavoriteFolders()
    }

    private fun displayFavoriteFolders() {
        favoritesContainer.removeAllViews()

        val appRootDirectory = File(requireContext().filesDir, "MusicallensData")
        val favoriteFolderNames = getFavoriteFolderNames()

        val existingFavoriteFolders = appRootDirectory.listFiles { file ->
            file.isDirectory && favoriteFolderNames.contains(file.name)
        }?.toList() ?: emptyList()

        if (existingFavoriteFolders.isEmpty()) {
            binding.noFavoritesTextView.visibility = View.VISIBLE
            favoritesContainer.visibility = View.GONE
        } else {
            binding.noFavoritesTextView.visibility = View.GONE
            favoritesContainer.visibility = View.VISIBLE

            val sortedFavoriteFolders = existingFavoriteFolders.sortedBy { it.name.lowercase() }

            sortedFavoriteFolders.forEach { folder ->
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
                        setColor(BACKGROUND_COLOR)
                    }
                    setPadding(
                        PADDING_MEDIUM_DP.dpToPx(),
                        PADDING_SMALL_DP.dpToPx(),
                        PADDING_MEDIUM_DP.dpToPx(),
                        PADDING_SMALL_DP.dpToPx()
                    )

                    setOnClickListener {
                        openPartituraDetails(folderName)
                    }
                }

                // PICTOGRAMA INIMĂ
                val heartIcon = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ICON_SIZE_DP.dpToPx(),
                        ICON_SIZE_DP.dpToPx()
                    ).apply {
                        marginEnd = DEFAULT_MARGIN_DP.dpToPx()
                    }
                    setImageResource(R.drawable.ic_favorite_border_24)
                    contentDescription = "Favorite Icon"
                    setColorFilter(ON_TERTIARY_COLOR)
                }

                // NUMELE FIȘIERULUI
                val folderNameTextView = TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, // Lățime 0 pentru ponderare
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f // Ocupă tot spațiul rămas
                    )
                    text = folderName
                    textSize = 16f
                    setTextColor(ON_TERTIARY_COLOR)
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
                    contentDescription = "Opțiuni partitură favorită"
                    setColorFilter(ON_TERTIARY_COLOR)

                    setOnClickListener {
                        showFavoriteFolderOptionsDialog(folder)
                    }
                }

                folderEntryLayout.addView(heartIcon)
                folderEntryLayout.addView(folderNameTextView)
                folderEntryLayout.addView(optionsButton)

                favoritesContainer.addView(folderEntryLayout)
            }
        }
    }

    private fun openPartituraDetails(folderName: String) {
        val bundle = Bundle().apply {
            putString("folderName", folderName)
        }
        findNavController().navigate(R.id.action_navigation_favorites_to_displayData, bundle)
    }

    private fun showFavoriteFolderOptionsDialog(folder: File) {
        val folderName = folder.name
        val options = arrayOf("Șterge din Favorite", "Vizualizează Partitura")

        AlertDialog.Builder(requireContext())
            .setTitle("Opțiuni Partitură Favorită: ${folder.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        showRemoveFavoriteConfirmationDialog(folderName)
                    }
                    1 -> {
                        openPartituraDetails(folderName)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    private fun showRemoveFavoriteConfirmationDialog(folderName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Șterge din Favorite")
            .setMessage("Ești sigur că vrei să ștergi partitura '$folderName' din favorite? Fișierul nu va fi șters de pe dispozitiv.")
            .setPositiveButton("Șterge") { dialog, _ ->
                removeFolderFromFavorites(folderName)
                dialog.dismiss()
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    // Funcții pentru gestionarea SharedPreferences (favorite)
    private fun getFavoriteFolderNames(): MutableSet<String> {
        return sharedPreferences.getStringSet("favorite_folders", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveFavoriteFolderNames(favoriteFolderNames: Set<String>) {
        val editor = sharedPreferences.edit()
        editor.putStringSet("favorite_folders", favoriteFolderNames)
        editor.apply()
    }

    private fun removeFolderFromFavorites(folderName: String) {
        val favoriteFolderNames = getFavoriteFolderNames()
        if (favoriteFolderNames.remove(folderName)) {
            saveFavoriteFolderNames(favoriteFolderNames)
            Toast.makeText(context, "'$folderName' a fost șters din favorite.", Toast.LENGTH_SHORT).show()
            displayFavoriteFolders()
        } else {
            Toast.makeText(context, "Eroare: Partitura '$folderName' nu a fost găsită în favorite.", Toast.LENGTH_SHORT).show()
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