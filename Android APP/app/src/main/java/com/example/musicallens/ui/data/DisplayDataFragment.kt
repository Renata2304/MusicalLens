package com.example.musicallens.ui.data

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.musicallens.R
import com.example.musicallens.databinding.FragmentDataDisplayBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


class DisplayDataFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentDataDisplayBinding? = null
    private val binding get() = _binding!!

    private var currentFolderName: String? = null
    private val scoreFiles = mutableListOf<File>()
    private val croppedImages = mutableListOf<String>()

    private lateinit var scoreFileAdapter: ScoreFileAdapter
    private lateinit var imageAdapter: ImageAdapter

    private val SERVER_URL = "http://192.168.0.73:5000"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.MINUTES)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            currentFileToDownload?.let { file ->
                downloadFileToDownloadsInternal(file)
                currentFileToDownload = null
            }
        } else {
            Toast.makeText(context, "Permisiunea de stocare este necesară pentru a salva fișierul.", Toast.LENGTH_LONG).show()
            currentFileToDownload = null
        }
    }
    private var currentFileToDownload: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack(R.id.navigation_home, true)
                findNavController().navigate(R.id.navigation_home)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDataDisplayBinding.inflate(inflater, container, false)
        val root: View = binding.root

        arguments?.let {
            currentFolderName = it.getString("folderName")
            binding.partituraNameTextView.text = currentFolderName ?: getString(R.string.default_partitura_name)
            if (currentFolderName == null) {
                Toast.makeText(context, "Niciun folder specificat pentru afișare.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            binding.partituraNameTextView.text = getString(R.string.default_partitura_name)
            Toast.makeText(context, "Niciun argument pentru folder primit.", Toast.LENGTH_SHORT).show()
        }

        setupViews()
        return root
    }

    override fun onResume() {
        super.onResume()
        loadFolderData()
    }

    private fun setupViews() {
        binding.partituraNameTextView.setOnClickListener {
            Toast.makeText(context, "Redenumirea partiturii: ${currentFolderName}...", Toast.LENGTH_SHORT).show()
            showRenameDialog()
        }

        binding.musicXmlRecyclerView.layoutManager = GridLayoutManager(context, 2)
        scoreFileAdapter = ScoreFileAdapter(scoreFiles, this::showFileOptionsDialog)
        binding.musicXmlRecyclerView.adapter = scoreFileAdapter

        binding.imagesRecyclerView.layoutManager = GridLayoutManager(context, 2)
        imageAdapter = ImageAdapter(croppedImages, this::showImageFullScreen)
        binding.imagesRecyclerView.adapter = imageAdapter
    }

    private fun loadFolderData() {
        currentFolderName?.let { folderName ->
            val folderPath = File(requireContext().filesDir, "MusicallensData" + File.separator + folderName)

            if (folderPath.exists() && folderPath.isDirectory) {
                scoreFiles.clear() // Clear existing data

                val tempMusicXmlFiles = mutableListOf<File>()
                val tempMidiFiles = mutableListOf<File>()

                folderPath.listFiles { file ->
                    file.isFile && file.name.endsWith(".musicxml") && file.name.startsWith(folderName)
                }?.let {
                    tempMusicXmlFiles.addAll(it.sortedBy { f -> f.name }) // Sort XMLs
                }
                Log.d("DisplayDataFragment", "Loaded ${tempMusicXmlFiles.size} MusicXML files from folder: $folderName")

                // Load MIDI files
                folderPath.listFiles { file ->
                    file.isFile && file.name.endsWith(".mid") && file.name.startsWith(folderName)
                }?.let {
                    tempMidiFiles.addAll(it.sortedBy { f -> f.name })
                }
                Log.d("DisplayDataFragment", "Loaded ${tempMidiFiles.size} MIDI files from folder: $folderName")

                scoreFiles.addAll(tempMusicXmlFiles)
                scoreFiles.addAll(tempMidiFiles)

                scoreFileAdapter.notifyDataSetChanged()


                croppedImages.clear()
                val imageFiles = folderPath.listFiles { file ->
                    file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".png")) && file.name.startsWith(folderName)
                }

                if (imageFiles != null) {
                    croppedImages.addAll(imageFiles.map { it.absolutePath }.sortedBy { it })
                    imageAdapter.notifyDataSetChanged()
                    Log.d("DisplayDataFragment", "Loaded ${croppedImages.size} images from folder: $folderName")
                } else {
                    Log.w("DisplayDataFragment", "No image files found in folder: $folderName")
                }

            } else {
                scoreFiles.clear()
                scoreFileAdapter.notifyDataSetChanged()
                croppedImages.clear()
                imageAdapter.notifyDataSetChanged()

                Toast.makeText(context, "Eroare: Directorul nu a fost găsit.", Toast.LENGTH_LONG).show()
                Log.e("DisplayDataFragment", "Folder '$folderName' not found or is not a directory.")
            }
        }
    }

    // Renamed function to handle options for both XML and MIDI files
    private fun showFileOptionsDialog(file: File) {
        when (file.extension.lowercase()) {
            "musicxml" -> {
                val options = arrayOf("Vizualizează conținut XML", "Descarcă fișier XML", "Convertește în MIDI")

                AlertDialog.Builder(requireContext())
                    .setTitle("Opțiuni fișier MusicXML: ${file.name}")
                    .setItems(options) { dialog, which ->
                        when (which) {
                            0 -> { // Vizualizează conținut XML
                                try {
                                    val xmlContent = file.readText(Charsets.UTF_8)
                                    AlertDialog.Builder(requireContext())
                                        .setTitle("Conținut MusicXML: ${file.name}")
                                        .setMessage(xmlContent)
                                        .setPositiveButton("OK", null)
                                        .show()
                                    Log.d("DisplayDataFragment", "XML content displayed from: ${file.absolutePath}")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Eroare la citirea fișierului XML: ${e.message}", Toast.LENGTH_LONG).show()
                                    Log.e("DisplayDataFragment", "Error reading XML file for display: ${e.message}", e)
                                }
                            }
                            1 -> { // Descarcă fișier XML
                                downloadFileToDownloadsInternal(file)
                            }
                            2 -> { // Convertește în MIDI (apel către server)
                                val midiFilename = file.name.replace(".musicxml", ".mid")
                                val midiFile = File(file.parent, midiFilename)

                                if (midiFile.exists()) {
                                    Toast.makeText(context, "Fișierul MIDI (${midiFilename}) există deja!", Toast.LENGTH_LONG).show()
                                    Log.d("DisplayDataFragment", "MIDI file already exists: ${midiFile.absolutePath}")
                                } else {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val musicXmlContent = file.readText(Charsets.UTF_8)

                                            val jsonObject = JSONObject().apply {
                                                put("music_xml_content", musicXmlContent)
                                            }
                                            val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                                            val request = Request.Builder()
                                                .url("$SERVER_URL/convert_musicxml_to_midi")
                                                .post(requestBody)
                                                .build()

                                            val response = httpClient.newCall(request).execute()

                                            if (response.isSuccessful) {
                                                val responseBody = response.body?.string()
                                                if (responseBody != null) {
                                                    val jsonResponse = JSONObject(responseBody)
                                                    val midiBase64 = jsonResponse.getString("midi_base64")
                                                    val midiBytes = Base64.decode(midiBase64, Base64.DEFAULT)

                                                    val savedMidiFile = saveMidiToFile(file.parentFile!!, midiBytes, midiFilename)

                                                    withContext(Dispatchers.Main) {
                                                        if (savedMidiFile != null) {
                                                            Toast.makeText(context, "Conversie MIDI reușită! Fișierul a fost salvat.", Toast.LENGTH_LONG).show()
                                                            Log.i("DisplayDataFragment", "Conversie MIDI reușită. Fișier MIDI salvat la: ${savedMidiFile.absolutePath}")
                                                            loadFolderData()
                                                        } else {
                                                            Toast.makeText(context, "Conversie MIDI reușită, dar salvarea fișierului a eșuat.", Toast.LENGTH_LONG).show()
                                                            Log.e("DisplayDataFragment", "Conversie MIDI reușită, dar salvarea fișierului a eșuat.")
                                                        }
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Eroare: Răspunsul serverului este gol.", Toast.LENGTH_LONG).show()
                                                        Log.e("DisplayDataFragment", "Răspuns gol de la server pentru conversie MIDI.")
                                                    }
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    val errorBody = response.body?.string()
                                                    Toast.makeText(context, "Eroare server (${response.code}): ${errorBody ?: "Eroare necunoscută"}", Toast.LENGTH_LONG).show()
                                                    Log.e("DisplayDataFragment", "Eroare server pentru conversie MIDI: ${response.code}, ${errorBody}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Eroare în timpul conversiei MIDI: ${e.message}", Toast.LENGTH_LONG).show()
                                                Log.e("DisplayDataFragment", "Eroare în timpul conversiei MIDI (client): ${e.message}", e)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Anulează", null)
                    .show()
            }
            "mid" -> {
                val options = arrayOf("Descarcă fișier MIDI", "sterge")
                AlertDialog.Builder(requireContext())
                    .setTitle("Opțiuni fișier MIDI: ${file.name}")
                    .setItems(options) { dialog, which ->
                        when (which) {
                            0 -> {
                                downloadFileToDownloadsInternal(file)
                            }
                            1 -> {
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Confirmă ștergerea")
                                    .setMessage("Ești sigur că vrei să ștergi fișierul '${file.name}'?")
                                    .setPositiveButton("Șterge") { _, _ ->
                                        if (file.delete()) {
                                            Toast.makeText(context, "Fișierul '${file.name}' a fost șters.", Toast.LENGTH_SHORT).show()
                                            loadFolderData()
                                        } else {
                                            Toast.makeText(context, "Eroare la ștergerea fișierului '${file.name}'.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .setNegativeButton("Anulează", null)
                                    .show()
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Anulează", null)
                    .show()
            }
            else -> {
                // Handle other unexpected file types
                Toast.makeText(context, "Opțiuni nu sunt disponibile pentru acest tip de fișier.", Toast.LENGTH_SHORT).show()
                Log.w("DisplayDataFragment", "No options defined for file type: ${file.extension}")
            }
        }
    }


    private fun showImageFullScreen(imagePath: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_image, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.fullscreenImageView)
        val bitmap = BitmapFactory.decodeFile(imagePath)
        imageView.setImageBitmap(bitmap)

        AlertDialog.Builder(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .setPositiveButton("Închide", null)
            .show()
    }

    private fun downloadFileToDownloadsInternal(fileToDownload: File) {
        val fileName = fileToDownload.name
        val mimeType = when (fileToDownload.extension.lowercase()) {
            "xml" -> "application/vnd.recordare.musicxml"
            "mid" -> "audio/midi"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = requireContext().contentResolver
            var uri: Uri? = null

            try {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let { outputUri ->
                    resolver.openOutputStream(outputUri)?.use { outputStream ->
                        fileToDownload.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, "Fișierul '$fileName' a fost salvat în Downloads!", Toast.LENGTH_LONG).show()
                    Log.d("DisplayDataFragment", "Fișierul '$fileName' salvat în Downloads (API 29+): $outputUri")
                } ?: run {
                    Toast.makeText(context, "Eroare: Nu s-a putut crea fișierul în Downloads.", Toast.LENGTH_SHORT).show()
                    Log.e("DisplayDataFragment", "Eroare: resolver.insert a returnat null pentru fișierul $fileName (API 29+)")
                }
            } catch (e: IOException) {
                Log.e("DisplayDataFragment", "Eroare la salvarea fișierului '$fileName' în Downloads (API 29+): ${e.message}", e)
                Toast.makeText(context, "Eroare la salvarea fișierului în Downloads: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: SecurityException) {
                Log.e("DisplayDataFragment", "Permisiuni insuficiente pentru salvarea în Downloads (API 29+): ${e.message}", e)
                Toast.makeText(context, "Eroare de permisiuni la salvarea fișierului.", Toast.LENGTH_LONG).show()
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                saveFileToLegacyDownloadsInternal(fileToDownload, fileName)
            } else {
                currentFileToDownload = fileToDownload
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun saveFileToLegacyDownloadsInternal(fileToDownload: File, fileName: String) {
        try {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()

            val outputFile = File(downloadsDir, fileName)

            fileToDownload.inputStream().use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(context, "Fișierul '$fileName' a fost salvat în Downloads!", Toast.LENGTH_LONG).show()
            Log.d("DisplayDataFragment", "Fișierul '$fileName' salvat în Downloads (API < 29): ${outputFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("DisplayDataFragment", "Eroare la salvarea fișierului '$fileName' în Downloads (legacy): ${e.message}", e)
            Toast.makeText(context, "Eroare la salvarea fișierului în Downloads: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Log.e("DisplayDataFragment", "Permisiuni insuficiente pentru salvarea în Downloads (legacy): ${e.message}", e)
            Toast.makeText(context, "Eroare de permisiuni la salvarea fișierului.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Salvează un ByteArray (MIDI) într-un fișier în directorul specificat.
     * @param directory Directorul unde va fi salvat fișierul.
     * @param midiBytes Conținutul MIDI ca ByteArray.
     * @param filename Numele fișierului (e.g., "nume_partitura_convertita.mid").
     * @return Obiectul File salvat, sau null în caz de eroare.
     */
    private fun saveMidiToFile(directory: File, midiBytes: ByteArray, filename: String): File? {
        val file = File(directory, filename)
        return try {
            FileOutputStream(file).use { fos ->
                fos.write(midiBytes)
            }
            Log.d("DisplayDataFragment_File", "MIDI saved successfully to: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Log.e("DisplayDataFragment_File", "Error saving MIDI to file: ${e.message}", e)
            null
        }
    }

    private fun showRenameDialog() {
        val inputEditText = EditText(requireContext())
        inputEditText.setText(currentFolderName)

        AlertDialog.Builder(requireContext())
            .setTitle("Redenumește Partitura")
            .setMessage("Introduceți noul nume pentru partitură:")
            .setView(inputEditText)
            .setPositiveButton("Redenumește") { dialog, _ ->
                val newName = inputEditText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentFolderName) {
                    renameFolder(newName)
                } else if (newName.isEmpty()) {
                    Toast.makeText(context, "Noul nume nu poate fi gol.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Anulează") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun renameFolder(newName: String) {
        val oldFolder = File(requireContext().filesDir, "MusicallensData" + File.separator + currentFolderName)
        val newFolder = File(requireContext().filesDir, "MusicallensData" + File.separator + newName)

        if (newFolder.exists()) {
            Toast.makeText(context, "Un folder cu acest nume există deja.", Toast.LENGTH_LONG).show()
            return
        }

        if (oldFolder.exists() && oldFolder.isDirectory) {
            if (oldFolder.renameTo(newFolder)) {
                newFolder.listFiles()?.forEach { file ->
                    val oldFileName = file.name
                    val regex = Regex("^${Regex.escape(currentFolderName ?: "")}_?")
                    val newFileName = oldFileName.replaceFirst(regex, newName + "_")

                    val newFile = File(newFolder, newFileName)
                    if (file.renameTo(newFile)) {
                        Log.d("DisplayDataFragment", "Renamed file from ${file.name} to ${newFile.name}")
                    } else {
                        Log.e("DisplayDataFragment", "Failed to rename internal file ${file.name} to ${newFile.name}")
                    }
                }

                currentFolderName = newName
                binding.partituraNameTextView.text = newName
                Toast.makeText(context, "Partitura redenumită cu succes: $newName", Toast.LENGTH_SHORT).show()
                Log.d("DisplayDataFragment", "Folder renamed from ${oldFolder.name} to ${newFolder.name}")
                loadFolderData()

            } else {
                Toast.makeText(context, "Eroare la redenumirea folderului.", Toast.LENGTH_LONG).show()
                Log.e("DisplayDataFragment", "Failed to rename folder from ${oldFolder.name} to ${newFolder.name}")
            }
        } else {
            Toast.makeText(context, "Folderul vechi nu există sau nu este un director.", Toast.LENGTH_LONG).show()
            Log.e("DisplayDataFragment", "Old folder ${oldFolder.absolutePath} does not exist or is not a directory.")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class ScoreFileAdapter(
        private val files: List<File>,
        private val onFileOptionsClick: (File) -> Unit
    ) : RecyclerView.Adapter<ScoreFileAdapter.ScoreFileViewHolder>() {

        class ScoreFileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fileNameTextView: TextView = view.findViewById(R.id.musicXmlFileNameTextView)
            val fileTypeTextView: TextView = view.findViewById(R.id.fileTypeTextView)
            val optionsButton: ImageButton = view.findViewById(R.id.musicXmlOptionsButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreFileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_music_xml, parent, false)
            return ScoreFileViewHolder(view)
        }

        override fun onBindViewHolder(holder: ScoreFileViewHolder, position: Int) {
            val file = files[position]
            holder.fileNameTextView.text = file.name

            when (file.extension.lowercase()) {
                "musicxml" -> {
                    holder.fileTypeTextView.text = "Type: MusicXML"
                }
                "mid" -> {
                    holder.fileTypeTextView.text = "Type: MIDI"
                }
                else -> {
                    holder.fileTypeTextView.text = "Type: Unknown"
                }
            }
            holder.optionsButton.setOnClickListener {
                onFileOptionsClick(file)
            }
        }

        override fun getItemCount() = files.size
    }

    class ImageAdapter(private val images: List<String>, private val onImageClick: (String) -> Unit) :
        RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

        class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.croppedImageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imagePath = images[position]
            val bitmap = BitmapFactory.decodeFile(imagePath)
            holder.imageView.setImageBitmap(bitmap)

            holder.imageView.setOnClickListener {
                onImageClick(imagePath)
            }
        }

        override fun getItemCount() = images.size
    }
}