package com.example.musicallens.ui.img_process

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.musicallens.databinding.FragmentImgProcessBinding
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ImgProcessFragment : Fragment() {

    private var _binding: FragmentImgProcessBinding? = null
    private val binding get() = _binding!!

    private var hasImage = false
    private var originalBitmap: Bitmap? = null
    private var cornerPoints: Array<PointF> = Array(4) { PointF(0f, 0f) }
    private lateinit var dots: Array<ImageView>
    private var selectedDotIndex: Int? = null

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

        dots = arrayOf(
            binding.dotTopLeft,
            binding.dotTopRight,
            binding.dotBottomRight,
            binding.dotBottomLeft
        )

        val imageByteArray = arguments?.getByteArray("captured_image")
        if (imageByteArray != null) {
            originalBitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size)
            hasImage = true

            binding.imageView.post {
                originalBitmap?.let { detectEdges(it) }
            }
        }

        setupDotMovement()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            showExitConfirmationDialog()
        }

        binding.btnCrop.setOnClickListener {
            cropImage()
        }
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
            positionDots()
            drawDetectedEdges()
        }
    }

    private fun sortCorners(points: Array<org.opencv.core.Point>): Array<PointF> {
        // Sort by y (vertical) to separate top and bottom
        val sortedByY = points.sortedBy { it.y }

        // Split into top and bottom 2
        val topPoints = sortedByY.take(2).sortedBy { it.x }     // sort left to right
        val bottomPoints = sortedByY.takeLast(2).sortedBy { it.x }

        return arrayOf(
            PointF(topPoints[0].x.toFloat(), topPoints[0].y.toFloat()),         // Top-left
            PointF(topPoints[1].x.toFloat(), topPoints[1].y.toFloat()),         // Top-right
            PointF(bottomPoints[1].x.toFloat(), bottomPoints[1].y.toFloat()),   // Bottom-right
            PointF(bottomPoints[0].x.toFloat(), bottomPoints[0].y.toFloat())    // Bottom-left
        )
    }

    private fun positionDots() {
        // Get the actual dimensions of the ImageView
        val parent = binding.imageView
        val imageWidth = parent.width
        val imageHeight = parent.height

        // Calculate the scale factor based on the original bitmap dimensions
        val scaleX = imageWidth.toFloat() / (originalBitmap?.width ?: 1)
        val scaleY = imageHeight.toFloat() / (originalBitmap?.height ?: 1)

        // Iterate over all the dots and position them at the correct corner points
        dots.forEachIndexed { index, dot ->
            dot.visibility = View.VISIBLE // Ensure that dots are visible
            val point = cornerPoints[index]

            // Apply scaling based on ImageView size
            val scaledX = point.x * scaleX
            val scaledY = point.y * scaleY

            // Constrain the position within bounds of the ImageView
            val constrainedX = scaledX.coerceIn(0f, imageWidth.toFloat())
            val constrainedY = scaledY.coerceIn(0f, imageHeight.toFloat())

            // Position the dots at the correct location within the ImageView
            dot.translationX = constrainedX - dot.width / 2
            dot.translationY = constrainedY - dot.height / 2
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDotMovement() {
        dots.forEachIndexed { index, dot ->
            dot.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> selectedDotIndex = index
                    MotionEvent.ACTION_MOVE -> {
                        val parent = binding.imageView
                        val newX = event.rawX - parent.left
                        val newY = event.rawY - parent.top
                        val constrainedX = newX.coerceIn(0f, parent.width.toFloat())
                        val constrainedY = newY.coerceIn(0f, parent.height.toFloat())

                        cornerPoints[index] = PointF(constrainedX, constrainedY)
                        dot.translationX = constrainedX - dot.width / 2
                        dot.translationY = constrainedY - dot.height / 2
                        drawDetectedEdges()
                    }
                    MotionEvent.ACTION_UP -> selectedDotIndex = null
                }
                true
            }
        }
    }

    private fun drawDetectedEdges() {
        originalBitmap?.let { bitmap ->
            val tempBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(tempBitmap)
            val paint = Paint().apply {
                color = Color.RED
                strokeWidth = 5f
                style = Paint.Style.STROKE
            }

            // Draw the lines connecting the corners
            for (i in 0 until 4) {
                val start = cornerPoints[i]
                val end = cornerPoints[(i + 1) % 4]
                canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            }

            binding.imageView.setImageBitmap(tempBitmap)
        }
    }

    private fun cropImage() {
        // Implementation of perspective transform / crop can go here
        // For now, just print the corner points or send them to another fragment
    }

    private fun showExitConfirmationDialog() {
        if (hasImage) {
            AlertDialog.Builder(requireContext())
                .setTitle("Exit Image Editing")
                .setMessage("Are you sure you want to go back? Your changes will be lost.")
                .setPositiveButton("Yes") { _, _ -> findNavController().navigateUp() }
                .setNegativeButton("No", null)
                .show()
        } else {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
