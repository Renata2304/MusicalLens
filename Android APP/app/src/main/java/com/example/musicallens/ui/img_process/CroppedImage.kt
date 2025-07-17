package com.example.musicallens.ui.img_process

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.musicallens.R
import com.example.musicallens.databinding.FragmentCroppedImageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import androidx.core.graphics.createBitmap

class CroppedImage : Fragment() {
    private var _binding: FragmentCroppedImageBinding? = null
    private val binding get() = _binding!!

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()

    private val serverUrl = "http://192.168.0.73:5000/process_image"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCroppedImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val croppedImageByteArray = arguments?.getByteArray("cropped_image")
        Log.d("CroppedImage", "Received byte array size: ${croppedImageByteArray?.size ?: 0}")

        if (croppedImageByteArray != null) {
            val croppedBitmap = BitmapFactory.decodeByteArray(croppedImageByteArray, 0, croppedImageByteArray.size)

            if (croppedBitmap != null) {
                Log.d("CroppedImage", "Bitmap decoded successfully: ${croppedBitmap.width}x${croppedBitmap.height}")

                CoroutineScope(Dispatchers.Main).launch {
                    val processedBitmap = withContext(Dispatchers.Default) {
                        processImageForNoiseCorrection(croppedBitmap)
                    }
                    binding.imageCropped.setImageBitmap(processedBitmap)
                    binding.imageCropped.visibility = View.VISIBLE

                    val stream = ByteArrayOutputStream()
                    processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val finalProcessedImageByteArray = stream.toByteArray()

                    binding.btnSendToOemer.setOnClickListener {
                        showFolderNameInputDialog(finalProcessedImageByteArray)
                    }
                }

            } else {
                Log.e("CroppedImage", "Failed to decode bitmap from byte array")
            }
        } else {
            Log.e("CroppedImage", "No image byte array received for cropping/processing.")
        }
    }

    /**
     * Afișează un dialog pentru ca utilizatorul să introducă numele directorului.
     */
    private fun showFolderNameInputDialog(imageByteArray: ByteArray) {
        val inputEditText = EditText(requireContext())
        inputEditText.hint = "Introduceți numele partiturii (ex: Beethoven 5)"

        AlertDialog.Builder(requireContext())
            .setTitle("Nume Partitură")
            .setMessage("Introduceți numele pentru acest document (va fi numele folderului și prefixul fișierelor):")
            .setView(inputEditText)
            .setPositiveButton("Procesează") { dialog, _ ->
                val folderName = inputEditText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    processImageWithOemer(imageByteArray, folderName)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Anulează") { dialog, _ ->
                dialog.cancel()
                binding.btnSendToOemer.isEnabled = true
            }
            .show()

        binding.btnSendToOemer.isEnabled = false
    }

    private fun processImageWithOemer(imageByteArray: ByteArray, folderName: String) {
        binding.oemerProgressBar.visibility = View.VISIBLE
        binding.btnSendToOemer.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputDirectory = File(requireContext().filesDir, "MusicallensData" + File.separator + folderName)
                if (!outputDirectory.exists()) {
                    outputDirectory.mkdirs()
                }

                val originalImageFilename = "${folderName}_original.png"
                val savedImgFile = saveImageToFile(outputDirectory, imageByteArray, originalImageFilename)
                if (savedImgFile != null) {
                    Log.i("CroppedImage", "Original image saved to: ${savedImgFile.absolutePath}")
                } else {
                    Log.e("CroppedImage", "Failed to save original image.")
                }

                val base64Image = Base64.encodeToString(imageByteArray, Base64.NO_WRAP)
                val jsonBody = JSONObject().apply {
                    put("image_base64", base64Image)
                    put("oemer_extra_args", JSONArray(listOf("--without-deskew")))
                }
                val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder().url(serverUrl).post(requestBody).build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val jsonResponse = JSONObject(responseBody)
                            val musicXml = jsonResponse.optString("music_xml", "")
                            val status = jsonResponse.optString("status", "unknown")
                            val oemerStdout = jsonResponse.optString("oemer_stdout", "")
                            val oemerStderr = jsonResponse.optString("oemer_stderr", "")

                            if (musicXml.isNotEmpty()) {
                                val musicXmlFilename = "${folderName}_oemer_output_${UUID.randomUUID()}.musicxml"
                                val savedMusicXmlFile = saveMusicXmlToFile(outputDirectory, musicXml, musicXmlFilename)
                                if (savedMusicXmlFile != null) {
                                    Log.i("CroppedImage", "MusicXML processing successful. Server Status: $status")
                                    Log.i("CroppedImage", "MusicXML file saved to: ${savedMusicXmlFile.absolutePath}")

                                    // Partea de conversie MIDI cu Python a fost eliminată de aici

                                } else {
                                    Log.e("CroppedImage", "MusicXML processing successful, but file save failed.")
                                }
                            } else {
                                Log.w("CroppedImage", "Nu s-a primit MusicXML de la server sau date insuficiente. Status: $status")
                            }

                            Log.d("CroppedImage", "Oemer Stdout: $oemerStdout")
                            Log.d("CroppedImage", "Oemer Stderr: $oemerStderr")

                        } else {
                            Log.e("CroppedImage", "Serverul a returnat un corp de răspuns gol.")
                        }
                    } else {
                        val errorBody = response.body?.string()
                        Log.e("CroppedImage", "Procesarea Oemer a eșuat. Eroare HTTP ${response.code}: ${response.message}. Detalii: ${errorBody?.take(200)}...")
                    }
                }

            } catch (e: IOException) {
                Log.e("CroppedImage", "Eroare de rețea: Nu s-a putut conecta la server. Verificați conexiunea și URL-ul (${serverUrl}). Mesaj: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("CroppedImage", "Eroare neașteptată în timpul procesării Oemer: ${e.message?.take(200)}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    binding.oemerProgressBar.visibility = View.GONE
                    binding.btnSendToOemer.isEnabled = true
                    Log.i("CroppedImage", "Încercarea de procesare s-a încheiat. Navigare la DisplayDataFragment.")

                    val bundle = Bundle().apply {
                        putString("folderName", folderName)
                    }
                    findNavController().navigate(R.id.action_cropped_img_to_displayData, bundle)
                }
            }
        }
    }

    private fun processImageForNoiseCorrection(originalBitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(originalBitmap, src)

        Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(src, src, Size(1.0, 1.0), 0.0)

        val denoisedBitmap = createBitmap(src.cols(), src.rows())
        Utils.matToBitmap(src, denoisedBitmap)

        src.release()
        return denoisedBitmap
    }

    /**
     * Salvează conținutul MusicXML într-un fișier în directorul specificat.
     * @param directory Directorul unde va fi salvat fișierul.
     * @param content Conținutul MusicXML ca String.
     * @param filename Numele fișierului (e.g., "nume_partitura_oemer_output.xml").
     * @return Obiectul File salvat, sau null în caz de eroare.
     */
    private fun saveMusicXmlToFile(directory: File, content: String, filename: String): File? {
        val file = File(directory, filename)
        return try {
            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }
            Log.d("CroppedImage_File", "MusicXML saved successfully to: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Log.e("CroppedImage_File", "Error saving MusicXML to file: ${e.message}", e)
            null
        }
    }

    /**
     * Salvează un ByteArray (imagine) într-un fișier în directorul specificat.
     * @param directory Directorul unde va fi salvat fișierul.
     * @param imageBytes Conținutul imaginii ca ByteArray.
     * @param filename Numele fișierului (e.g., "nume_partitura_original.png").
     * @return Obiectul File salvat, sau null în caz de eroare.
     */
    private fun saveImageToFile(directory: File, imageBytes: ByteArray, filename: String): File? {
        val file = File(directory, filename)
        return try {
            FileOutputStream(file).use { fos ->
                fos.write(imageBytes)
            }
            Log.d("CroppedImage_File", "Image saved successfully to: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Log.e("CroppedImage_File", "Error saving image to file: ${e.message}", e)
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}