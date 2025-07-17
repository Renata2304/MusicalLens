package com.example.musicallens.ui.camera

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.musicallens.R
import com.example.musicallens.databinding.FragmentCameraBinding
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import org.opencv.imgproc.Imgproc
import org.opencv.core.Core
import androidx.core.graphics.createBitmap

class CameraFragment : Fragment(), CameraBridgeViewBase.CvCameraViewListener2 {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraView: JavaCameraView
    private lateinit var cameraButton: ImageView
    private var imgTaken: Boolean = false
    private var capturedBitmap: Bitmap? = null

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(activity, "Camera permission granted", Toast.LENGTH_SHORT).show()
                initializeCameraView()
            } else {
                Toast.makeText(activity, "Camera permission denied", Toast.LENGTH_SHORT).show()
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        android.Manifest.permission.CAMERA
                    )
                ) {
                    openAppSettings()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val cameraViewModel = ViewModelProvider(this)[CameraViewModel::class.java]

        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        val root: View = binding.root

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(activity, "OpenCV initialization failed!", Toast.LENGTH_SHORT).show()
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully.")
        }

        cameraView = root.findViewById(R.id.camera_view)
        cameraView.setCvCameraViewListener(this)
        cameraView.setCameraPermissionGranted()
        cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)

        checkAndRequestCameraPermission()
        initializeCameraView()

        cameraButton = root.findViewById(R.id.camera_button)
        cameraButton.setOnClickListener {
            imgTaken = true
        }

        return root
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        val mat = inputFrame?.rgba() ?: Mat()

        val edgeMat = Mat()

        if (imgTaken) {
            capturedBitmap = createBitmap(mat.cols(), mat.rows())
            Utils.matToBitmap(mat, capturedBitmap)
            navigateToEditFragment()
            imgTaken = false
        }
        return mat
    }

    private fun navigateToEditFragment() {
        capturedBitmap?.let {
            val bundle = Bundle()
            val stream = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bundle.putByteArray("captured_image", stream.toByteArray())
            activity?.runOnUiThread {
                findNavController().navigate(
                    R.id.action_navigation_camera_to_imgProcessFragment,
                    bundle
                )

            }
        }
    }

    private fun initializeCameraView() {
        cameraView.post {
            if (::cameraView.isInitialized && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                cameraView.setCameraPermissionGranted()
                cameraView.enableView()
            } else {
                Log.e("CameraFragment", "Camera not initialized or permission missing")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cameraView.enableView()
    }

    override fun onPause() {
        super.onPause()
        if (::cameraView.isInitialized) {
            cameraView.disableView()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraView.disableView()
        _binding = null
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = android.net.Uri.fromParts("package", requireActivity().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun checkAndRequestCameraPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        } else {
            initializeCameraView()
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}
    override fun onCameraViewStopped() {}
}