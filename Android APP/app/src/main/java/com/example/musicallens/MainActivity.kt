package com.example.musicallens

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.musicallens.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)

        val lang = sharedPreferences.getString("language", "ro") ?: "ro"
        setLocale(lang)

        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView = binding.navView
        val navController = try {
            findNavController(R.id.nav_host_fragment_activity_main)
        } catch (e: Exception) {
            Log.e("Navigation", "Failed to find NavController", e)
            null
        }

        navController?.let {
            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.navigation_home,
                    R.id.navigation_camera,
                    R.id.navigation_settings
                )
            )

            navView.setupWithNavController(it)

            // Hide main navigation bar in CameraFragment
            it.addOnDestinationChangedListener { _, destination, _ ->
                navView.visibility = if (destination.id == R.id.navigation_camera ||
                    destination.id == R.id.imgProcessFragment   ||
                    destination.id == R.id.cropped_img          ||
                    destination.id == R.id.displayData          ||
                    destination.id == R.id.navigation_favorites ||
                    destination.id == R.id.navigation_tutorial) View.GONE else View.VISIBLE
            }
        }

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed.")
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully.")
        }
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

}
