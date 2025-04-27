package com.example.imagepicker

import ImageProcessor
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : ComponentActivity() {
    private lateinit var btnSelectImage: Button
    private lateinit var btnResetImage: Button
    private lateinit var imageView: ImageView
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var imageFileManager: ImageFileManager

    private var sourceImageUri: Uri? = null
    private var editImageUri: Uri? = null
    private val PERMISSION_REQUEST_CODE = 100

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
    ){ result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK){
            val data: Intent? = result.data
            val imageUri: Uri? = data?.data
            imageUri?.let { uri ->
                sourceImageUri = uri
                val workingCopyUri = imageFileManager.createOrGetEditCopy(uri)
                workingCopyUri?.let {copyUri ->
                    editImageUri = copyUri
                    imageView.setImageURI(copyUri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        imageProcessor = ImageProcessor()
        imageFileManager = ImageFileManager(contentResolver)

        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnResetImage = findViewById(R.id.btnResetImage)
        imageView = findViewById(R.id.imageView)

        btnSelectImage.setOnClickListener {
            requestPermission()
        }
        btnResetImage.setOnClickListener {
            imageFileManager.resetImage(editImageUri)
            imageView.setImageURI(sourceImageUri)
        }

        addButtonsInGrid()
    }

    private fun applyFilter(filterFunction: (Bitmap) -> Bitmap) {
        editImageUri?.let { uri ->
            if(imageFileManager.applyFilterToEditCopy(uri, filterFunction)) {
                imageView.setImageURI(null)
                imageView.setImageURI(uri)
            }
        }?: run {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
        }
    }

    data class FilterOption(
        val name: String,
        val filterFunction: (Bitmap) -> Bitmap
    )

    private fun addButtonsInGrid() {
        val gridLayout = GridLayout(this)
        gridLayout.columnCount = 2
        gridLayout.useDefaultMargins = true
        gridLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

        val filters = listOf(
            FilterOption("+ Scale", {bitmap ->  ScaleTransformation(1.1).apply(bitmap)}),
            FilterOption("- Scale", {bitmap ->  ScaleTransformation(0.9).apply(bitmap)}),
            FilterOption("Rotate left", {bitmap ->  RotationTransformation(-90.00).apply(bitmap)}),
            FilterOption("Rotate right", {bitmap ->  RotationTransformation(90.00).apply(bitmap)}),
            FilterOption("Mirror V", {bitmap ->  MirrorVTransformation().apply(bitmap)}),
            FilterOption("Mirror H", {bitmap ->  MirrorHTransformation().apply(bitmap)}),
            FilterOption("Translate X", {bitmap -> TranslationTransformation(25.0, 0.0).apply(bitmap)}),
            FilterOption("Translate Y", {bitmap -> TranslationTransformation(0.0, 25.0).apply(bitmap)}),
            FilterOption("+ Brightness", {bitmap -> BrightnessTransformation(10.0).apply(bitmap) }),
            FilterOption("- Brightness", {bitmap -> BrightnessTransformation(-10.0).apply(bitmap) }),
            FilterOption("+ Contrast", {bitmap -> ContrastTransformation(1.1f).apply(bitmap)}),
            FilterOption("- Contrast", {bitmap -> ContrastTransformation(0.9f).apply(bitmap)}),
            FilterOption("Grayscale", {bitmap -> GrayscaleTransformation().apply(bitmap)}),
            FilterOption("Low Pass Filter", {bitmap ->  LowPassFilterTransformation(3).apply(bitmap)}),
            FilterOption("Gaussian Blur", {bitmap -> GaussianFilterTransformation().apply(bitmap) }),
        )

        for(i in filters.indices) {
            val filter = filters[i]

            val button = Button(this)
            button.text = filter.name
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)

            button.setOnClickListener {
                applyFilter(filter.filterFunction)
            }

            val param = GridLayout.LayoutParams()
            param.width = 0
            param.height = GridLayout.LayoutParams.WRAP_CONTENT
            param.columnSpec = GridLayout.spec(i % gridLayout.columnCount, 1, 1f)
            param.rowSpec = GridLayout.spec(i / gridLayout.columnCount, 1)
            param.setMargins(8, 8, 8, 8)
            button.layoutParams = param

            gridLayout.addView(button)
        }

        val scrollView = ScrollView(this)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        scrollView.addView(gridLayout)

        val mainLayout = findViewById<ViewGroup>(R.id.mainLayout)
        mainLayout.addView(scrollView)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE) // Ensures only openable files are shown
        intent.type = "image/*" // Restrict to images only
        pickImageLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        println("ON REQUEST PERMISSION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission already granted, open the image picker
            openImagePicker()
        }
    }





}