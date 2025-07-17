package com.example.musicallens.ui.img_process

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.musicallens.databinding.FragmentImgProcessBinding
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import com.example.musicallens.R
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.widget.Toast
import androidx.core.graphics.createBitmap
import java.io.IOException
import kotlin.math.sqrt


class ImgProcessFragment : Fragment() {

    private var _binding: FragmentImgProcessBinding? = null
    private val binding get() = _binding!!

    private var hasImage = false
    private var originalBitmap: Bitmap? = null
    private var cornerPoints: Array<PointF> = Array(4) { PointF(0f, 0f) }

    private var selectedCornerIndex: Int? = null

    // NOU: Variabila pentru a reține calea fișierului temporar salvat
    private var savedTempImagePath: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImgProcessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // NOU: Încearcă să încarci imaginea și punctele din starea salvată
        val imagePathFromSavedState = savedInstanceState?.getString("original_image_path")
        val cornersFromSavedState = savedInstanceState?.getParcelableArrayList<PointF>("corner_points")

        if (imagePathFromSavedState != null && cornersFromSavedState != null) {
            // RESTAURARE DIN STAREA SALVATĂ (DUPĂ IEȘIREA DIN APLICAȚIE/PROCES KILL)
            originalBitmap = loadBitmapFromTempFile(imagePathFromSavedState)
            if (originalBitmap != null) {
                hasImage = true
                binding.imageView.setImageBitmap(originalBitmap)
                cornerPoints = cornersFromSavedState.toTypedArray()
                Log.d("ImgProcessFragment", "Stare restaurată din savedInstanceState.")

                // Reține calea fișierului temporar pentru a-l șterge ulterior
                savedTempImagePath = imagePathFromSavedState

                // Asigură-te că desenul colțurilor este actualizat după restaurare
                binding.imageView.post { // Așteaptă ca ImageView să fie desenat și să aibă dimensiuni valide
                    drawDetectedEdges()
                }
            } else {
                Log.e("ImgProcessFragment", "Eroare la decodificarea imaginii din fișierul temporar salvat: $imagePathFromSavedState")
                showErrorAndGoBack("Eroare la restaurarea imaginii din stare salvată.")
                // Șterge fișierul temporar dacă nu a putut fi decodificat
                deleteTempFile(imagePathFromSavedState)
                return
            }
        } else {
            // ÎNCĂRCARE INIȚIALĂ (NAVIGARE CĂTRE FRAGMENT)
            val imageByteArray = arguments?.getByteArray("captured_image")
            if (imageByteArray != null && imageByteArray.isNotEmpty()) {
                originalBitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size)
                if (originalBitmap != null) {
                    hasImage = true
                    binding.imageView.setImageBitmap(originalBitmap)

                    // Așteaptă ca ImageView-ul să fie desenat și să aibă dimensiuni valide
                    binding.imageView.viewTreeObserver.addOnGlobalLayoutListener(
                        object : ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                // Asigură-te că listener-ul este șters pentru a preveni apeluri multiple
                                binding.imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                                originalBitmap?.let {
                                    if (binding.imageView.width > 0 && binding.imageView.height > 0) {
                                        detectEdges(it) // Detectează marginile pe imaginea originală
                                        drawDetectedEdges() // Desenează chenarul cu punctele detectate
                                    } else {
                                        Log.e("ImgProcessFragment", "Dimensiunile ImageView sunt 0, nu se pot detecta marginile.")
                                    }
                                }
                            }
                        }
                    )
                    Log.d("ImgProcessFragment", "Imaginea încărcată din argumente.")
                } else {
                    Log.e("ImgProcessFragment", "Eroare la decodificarea imaginii din byte array.")
                    showErrorAndGoBack("Eroare la încărcarea imaginii. Vă rugăm să reîncercați.")
                    return
                }
            } else {
                Log.e("ImgProcessFragment", "Nu s-au primit date imagine sau starea salvată este goală.")
                showErrorAndGoBack("Nu s-a primit nicio imagine pentru procesare.")
                return
            }
        }

        setupLineMovement()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            showExitConfirmationDialog()
        }

        binding.btnCrop.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                cropAndDeskewImage()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        originalBitmap?.let { bmp ->
            val tempFilePath = saveBitmapToTempFile(requireContext(), bmp)
            if (tempFilePath != null) {
                outState.putString("original_image_path", tempFilePath)
                Log.d("ImgProcessFragment", "Calea imaginii salvată în stare: $tempFilePath")
            } else {
                Log.e("ImgProcessFragment", "Eroare la salvarea bitmap-ului în fișier temporar pentru restaurarea stării.")
            }
        }

        val cornersList = ArrayList<PointF>(cornerPoints.toList())
        outState.putParcelableArrayList("corner_points", cornersList)
        Log.d("ImgProcessFragment", "Puncte colțuri salvate în stare: ${cornerPoints.size} puncte.")
    }

    private fun saveBitmapToTempFile(context: Context, bitmap: Bitmap): String? {
        return try {
            val fileName = "temp_img_${System.currentTimeMillis()}.png"
            val tempFile = File(context.cacheDir, fileName)
            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            tempFile.absolutePath
        } catch (e: IOException) {
            Log.e("ImgProcessFragment", "Eroare la salvarea bitmap-ului în fișier temporar: ${e.message}", e)
            null
        }
    }

    private fun loadBitmapFromTempFile(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            Log.e("ImgProcessFragment", "Eroare la încărcarea bitmap-ului din fișier temporar: ${e.message}", e)
            null
        }
    }

    private fun deleteTempFile(path: String?) {
        path?.let {
            val file = File(it)
            if (file.exists()) {
                if (file.delete()) {
                    Log.d("ImgProcessFragment", "Fișier temporar șters: $it")
                } else {
                    Log.w("ImgProcessFragment", "Eșec la ștergerea fișierului temporar: $it")
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        originalBitmap?.recycle()
        deleteTempFile(savedTempImagePath)
        savedTempImagePath = null
    }


    private fun detectEdges(bitmap: Bitmap) {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(mat, mat, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(mat, mat, 75.0, 200.0)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var biggest: MatOfPoint2f? = null
        var maxArea = 0.0

        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)

            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(approx)
                if (area > maxArea) {
                    maxArea = area
                    biggest = approx
                }
            }
        }

        biggest?.let {
            val points = it.toArray()
            cornerPoints = sortCorners(points)
        }
        mat.release()
        hierarchy.release()
    }

    private fun sortCorners(points: Array<org.opencv.core.Point>): Array<PointF> {
        val sortedBySum = points.sortedBy { it.x + it.y }
        val topLeft = sortedBySum.first()
        val bottomRight = sortedBySum.last()

        val sortedByDiff = points.sortedBy { it.y - it.x }
        val topRight = sortedByDiff.first()
        val bottomLeft = sortedByDiff.last()

        return arrayOf(
            PointF(topLeft.x.toFloat(), topLeft.y.toFloat()),
            PointF(topRight.x.toFloat(), topRight.y.toFloat()),
            PointF(bottomRight.x.toFloat(), bottomRight.y.toFloat()),
            PointF(bottomLeft.x.toFloat(), bottomLeft.y.toFloat())
        )
    }

    private fun getImageDisplayMatrix(imageView: ImageView, bitmap: Bitmap): Matrix {
        val matrix = Matrix()

        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        if (imageWidth <= 0 || imageHeight <= 0) return matrix

        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return matrix

        val scale: Float
        val dx: Float
        val dy: Float

        val bitmapRatio = imageWidth / imageHeight
        val viewRatio = viewWidth / viewHeight

        if (bitmapRatio > viewRatio) {
            scale = viewWidth / imageWidth
            dx = 0f
            dy = (viewHeight - (imageHeight * scale)) / 2f
        } else {
            scale = viewHeight / imageHeight
            dx = (viewWidth - (imageWidth * scale)) / 2f
            dy = 0f
        }

        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        return matrix
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun setupLineMovement() {
        binding.imageView.setOnTouchListener { v, event ->
            val parent = v as ImageView
            val originalBmp = originalBitmap ?: return@setOnTouchListener true

            if (parent.width == 0 || parent.height == 0) return@setOnTouchListener true

            val displayMatrix = getImageDisplayMatrix(parent, originalBmp)
            val inverseMatrix = Matrix()
            if (!displayMatrix.invert(inverseMatrix)) {
                Log.e("ImgProcessFragment", "Cannot invert display matrix. Line movement might be inaccurate.")
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val touchX = event.x
                    val touchY = event.y

                    var minDistance = Float.MAX_VALUE
                    var closestCornerIndex: Int? = null
                    val touchTolerance = 40f

                    val transformedPoints = FloatArray(8)
                    for (i in 0 until 4) {
                        transformedPoints[i * 2] = cornerPoints[i].x
                        transformedPoints[i * 2 + 1] = cornerPoints[i].y
                    }
                    displayMatrix.mapPoints(transformedPoints)

                    for (i in 0 until 4) {
                        val pointX = transformedPoints[i * 2]
                        val pointY = transformedPoints[i * 2 + 1]
                        val dx = touchX - pointX
                        val dy = touchY - pointY
                        val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                        if (distance < minDistance && distance <= touchTolerance) {
                            minDistance = distance
                            closestCornerIndex = i
                        }
                    }

                    selectedCornerIndex = closestCornerIndex
                    drawDetectedEdges(highlightCornerIndex = selectedCornerIndex)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (selectedCornerIndex != null) {
                        val touchX = event.x
                        val touchY = event.y

                        val originalBitmapCoords = floatArrayOf(touchX, touchY)
                        inverseMatrix.mapPoints(originalBitmapCoords)

                        val constrainedOriginalX = originalBitmapCoords[0].coerceIn(0f, originalBmp.width.toFloat())
                        val constrainedOriginalY = originalBitmapCoords[1].coerceIn(0f, originalBmp.height.toFloat())

                        cornerPoints[selectedCornerIndex!!] = PointF(constrainedOriginalX, constrainedOriginalY)

                        drawDetectedEdges(highlightCornerIndex = selectedCornerIndex)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    selectedCornerIndex = null
                    drawDetectedEdges()
                }
            }
            true
        }
    }

    private fun drawDetectedEdges(highlightCornerIndex: Int? = null) {
        originalBitmap?.let { bitmap ->
            val tempBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(tempBitmap)

            val imageDiagonal = Math.sqrt((bitmap.width * bitmap.width + bitmap.height * bitmap.height).toDouble()).toFloat()
            val baseStrokeWidth = imageDiagonal * 0.003f
            val highlightStrokeWidth = baseStrokeWidth * 2f
            val pointRadius = baseStrokeWidth * 4f

            val linePaint = Paint().apply {
                color = Color.RED
                strokeWidth = baseStrokeWidth
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            val highlightPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val highlightLinePaint = Paint(linePaint).apply {
                color = Color.BLUE
                strokeWidth = highlightStrokeWidth
            }

            val path = Path()
            path.moveTo(cornerPoints[0].x, cornerPoints[0].y)
            path.lineTo(cornerPoints[1].x, cornerPoints[1].y)
            path.lineTo(cornerPoints[2].x, cornerPoints[2].y)
            path.lineTo(cornerPoints[3].x, cornerPoints[3].y)
            path.close()
            canvas.drawPath(path, linePaint)

            if (highlightCornerIndex != null) {
                val prevIndex = (highlightCornerIndex - 1 + 4) % 4
                val nextIndex = (highlightCornerIndex + 1) % 4

                canvas.drawLine(
                    cornerPoints[highlightCornerIndex].x, cornerPoints[highlightCornerIndex].y,
                    cornerPoints[prevIndex].x, cornerPoints[prevIndex].y,
                    highlightLinePaint
                )
                canvas.drawLine(
                    cornerPoints[highlightCornerIndex].x, cornerPoints[highlightCornerIndex].y,
                    cornerPoints[nextIndex].x, cornerPoints[nextIndex].y,
                    highlightLinePaint
                )
            }

            for (i in 0 until 4) {
                val centerX = cornerPoints[i].x
                val centerY = cornerPoints[i].y
                val paintToUse = if (i == highlightCornerIndex) highlightPaint else linePaint
                canvas.drawCircle(centerX, centerY, pointRadius, if (i == highlightCornerIndex) highlightPaint else linePaint.apply{style = Paint.Style.FILL})
                canvas.drawCircle(centerX, centerY, pointRadius, linePaint.apply{style = Paint.Style.STROKE})
            }

            binding.imageView.setImageBitmap(tempBitmap)
        }
    }

    private suspend fun cropAndDeskewImage() {
        originalBitmap?.let { originalBmp ->
            withContext(Dispatchers.IO) {
                val srcMatInitial = Mat()
                Utils.bitmapToMat(originalBmp, srcMatInitial)

                val srcPointsArray = arrayOf(
                    org.opencv.core.Point(cornerPoints[0].x.toDouble(), cornerPoints[0].y.toDouble()),
                    org.opencv.core.Point(cornerPoints[1].x.toDouble(), cornerPoints[1].y.toDouble()),
                    org.opencv.core.Point(cornerPoints[2].x.toDouble(), cornerPoints[2].y.toDouble()),
                    org.opencv.core.Point(cornerPoints[3].x.toDouble(), cornerPoints[3].y.toDouble())
                )
                val srcPoints = MatOfPoint2f(*srcPointsArray)

                val widthTop = srcPointsArray[0].distanceTo(srcPointsArray[1])
                val widthBottom = srcPointsArray[3].distanceTo(srcPointsArray[2])
                val maxWidth = Math.max(widthTop, widthBottom).toInt()

                val heightLeft = srcPointsArray[0].distanceTo(srcPointsArray[3])
                val heightRight = srcPointsArray[1].distanceTo(srcPointsArray[2])
                val maxHeight = Math.max(heightLeft, heightRight).toInt()

                Log.d("CropDeskew", "Source points: ${srcPointsArray.joinToString { "(${it.x}, ${it.y})" }}")
                Log.d("CropDeskew", "Calculated dimensions: ${maxWidth}x${maxHeight}")

                if (maxWidth <= 0 || maxHeight <= 0) {
                    Log.e("CropDeskew", "Invalid dimensions calculated")
                    return@withContext
                }

                val dstPoints = MatOfPoint2f(
                    org.opencv.core.Point(0.0, 0.0),
                    org.opencv.core.Point(maxWidth.toDouble(), 0.0),
                    org.opencv.core.Point(maxWidth.toDouble(), maxHeight.toDouble()),
                    org.opencv.core.Point(0.0, maxHeight.toDouble())
                )

                val perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

                val croppedDeskewedMat = Mat()
                Imgproc.warpPerspective(srcMatInitial, croppedDeskewedMat, perspectiveTransform, Size(maxWidth.toDouble(), maxHeight.toDouble()), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0, 255.0, 255.0))

                val tempCroppedDeskewedBitmap =
                    createBitmap(croppedDeskewedMat.cols(), croppedDeskewedMat.rows())
                Utils.matToBitmap(croppedDeskewedMat, tempCroppedDeskewedBitmap)

                Log.d("CropDeskew", "After perspective transform: ${tempCroppedDeskewedBitmap.width}x${tempCroppedDeskewedBitmap.height}")

                val finalResultBitmap = deskewImage(tempCroppedDeskewedBitmap)

                Log.d("CropDeskew", "After deskew: ${finalResultBitmap.width}x${finalResultBitmap.height}")

                srcMatInitial.release()
                srcPoints.release()
                dstPoints.release()
                perspectiveTransform.release()
                croppedDeskewedMat.release()

                saveImageToGallery(finalResultBitmap)
                val bundle = Bundle()
                val stream = ByteArrayOutputStream()
                val success = finalResultBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                Log.d("CropDeskew", "Compress successful: $success")
                val byteArray = stream.toByteArray()
                Log.d("CropDeskew", "Compressed byte array size: ${byteArray.size}")
                bundle.putByteArray("cropped_image", byteArray)

                delay(500)

                withContext(Dispatchers.Main) {
                    findNavController().navigate(
                        R.id.action_imgProcessFragment_to_cropped_img,
                        bundle
                    )
                }
            }
        }
    }

    private suspend fun saveImageToGallery(bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            val filename = "MelodyLens_${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null
            var imageUri: Uri? = null

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "MelodyLens")
                }
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = requireContext().contentResolver

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                } else {
                    @Suppress("DEPRECATION")
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + File.separator + "MelodyLens")
                    if (!imagesDir.exists()) {
                        imagesDir.mkdirs()
                    }
                    val imageFile = File(imagesDir, filename)
                    fos = FileOutputStream(imageFile)
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    }
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }

                fos?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    Log.d("SaveImage", "Image saved successfully: $filename")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Imaginea a fost salvată în galerie!", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Log.e("SaveImage", "Failed to get output stream.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Eroare la salvarea imaginii.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SaveImage", "Error saving image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Eroare la salvarea imaginii: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && imageUri != null) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                fos?.close()
            }
        }
    }

    private fun deskewImage(bitmap: Bitmap): Bitmap {
        val grayMat = Mat()
        Utils.bitmapToMat(bitmap, grayMat)
        Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.threshold(grayMat, grayMat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        var bestAngle = 0.0
        var bestScore = -1.0
        val stddev = MatOfDouble()
        val projection = Mat()

        for (angle in -5..5) {
            val rotated = rotateMat(grayMat, angle.toDouble())
            Core.reduce(rotated, projection, 1, Core.REDUCE_SUM, CvType.CV_32F)
            Core.meanStdDev(projection, MatOfDouble(), stddev)
            val score = stddev.toArray()[0]
            if (score > bestScore) {
                bestScore = score
                bestAngle = angle.toDouble()
            }
            rotated.release()
        }

        Log.d("Deskew", "Cel mai bun unghi pentru 'slight twist': $bestAngle grade")

        if (Math.abs(bestAngle) < 0.1) {
            grayMat.release()
            projection.release()
            stddev.release()
            return bitmap
        }

        val originalColorMat = Mat()
        Utils.bitmapToMat(bitmap, originalColorMat)
        val deskewedMat = rotateMat(originalColorMat, bestAngle)

        val deskewedBitmap = createBitmap(deskewedMat.cols(), deskewedMat.rows())
        Utils.matToBitmap(deskewedMat, deskewedBitmap)

        grayMat.release()
        originalColorMat.release()
        deskewedMat.release()
        projection.release()
        stddev.release()

        return deskewedBitmap
    }

    private fun rotateMat(src: Mat, angle: Double): Mat {
        val center = org.opencv.core.Point(src.cols() / 2.0, src.rows() / 2.0)
        val rotMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0)
        val rotatedMat = Mat()
        Imgproc.warpAffine(src, rotatedMat, rotMatrix, src.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        rotMatrix.release()
        return rotatedMat
    }

    private fun PointF.distanceTo(other: PointF): Float {
        val dx = this.x - other.x
        val dy = this.y - other.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun org.opencv.core.Point.distanceTo(other: org.opencv.core.Point): Double {
        val dx = this.x - other.x
        val dy = this.y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun showErrorAndGoBack(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eroare")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> findNavController().navigateUp() }
            .show()
    }

    private fun showExitConfirmationDialog() {
        if (hasImage) {
            AlertDialog.Builder(requireContext())
                .setTitle("Ieșire din Editare Imagine")
                .setMessage("Ești sigur că vrei să te întorci? Modificările tale nu vor fi salvate.")
                .setPositiveButton("Da") { _, _ -> findNavController().navigateUp() }
                .setNegativeButton("Nu", null)
                .show()
        } else {
            findNavController().navigateUp()
        }
    }
}