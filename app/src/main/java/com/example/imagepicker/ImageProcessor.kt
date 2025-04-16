import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import java.io.IOException
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ImageProcessor(private val contentResolver: ContentResolver) {

    private fun resetImage(uri: Uri) {

    }

    private fun getFileNameFromUri(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return it.getString(displayNameIndex)
                }
            }
        }
        return null
    }

    private fun generateEditFileName(originalFileName: String): String {
        val dotIndex = originalFileName.lastIndexOf(".")
        return if (dotIndex != -1) {
            val name = originalFileName.substring(0, dotIndex)
            val extension = originalFileName.substring(dotIndex)
            "${name}_edit$extension"
        } else {
            "${originalFileName}_edit.jpg"
        }
    }

    private fun findEditFileUri(editFileName: String): Uri? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(editFileName)

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    fun createOrGetEditCopy(originalUri: Uri): Uri? {
        val originalFileName = getFileNameFromUri(originalUri)
        if (originalFileName == null) {
            return null
        }

        val editFileName = generateEditFileName(originalFileName)

        // Check if edit file already exists
        val existingEditUri = findEditFileUri(editFileName)
        if (existingEditUri != null) {
            return existingEditUri
        }

        // If not, create a new edit file
        val bitmap = getBitmapFromUri(originalUri) ?: return null
        return saveNewBitmapToStorage(bitmap, editFileName)
    }

    fun applyFilterToEditCopy(editUri: Uri, filterFunction: (Bitmap) -> Bitmap): Boolean {
        val bitmap = getBitmapFromUri(editUri) ?: return false
        val processedBitmap = filterFunction(bitmap)

        // Update the existing file
        return updateExistingImage(editUri, processedBitmap)
    }

    private fun saveNewBitmapToStorage(bitmap: Bitmap, fileName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val imageUri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        imageUri?.let { uri ->
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }

        return imageUri
    }

    // Update existing image without creating a new file
    private fun updateExistingImage(uri: Uri, bitmap: Bitmap): Boolean {
        return try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    // Convert URI to Bitmap
    fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun modifyContrast(originalBitmap: Bitmap, contrastFactor: Float): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val resultBitmap = originalBitmap.config?.let {
            Bitmap.createBitmap(width, height, it)
        } ?: run {
            return originalBitmap
        }

        // Calculate translation value (pivot around middle gray)
        val translationValue = 128 * (1 - contrastFactor)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = originalBitmap.getPixel(x, y)

                // Apply contrast formula to each channel
                val red = (contrastFactor * Color.red(pixel) + translationValue).coerceIn(0f, 255f).toInt()
                val green = (contrastFactor * Color.green(pixel) + translationValue).coerceIn(0f, 255f).toInt()
                val blue = (contrastFactor * Color.blue(pixel) + translationValue).coerceIn(0f, 255f).toInt()
                val alpha = Color.alpha(pixel) // Preserve alpha

                val newPixel = Color.argb(alpha, red, green, blue)
                resultBitmap.setPixel(x, y, newPixel)
            }
        }

        return resultBitmap
    }

    fun modifyBrightness(originalBitmap: Bitmap, brightnessValue: Double): Bitmap {

        val width = originalBitmap.width
        val height = originalBitmap.height
        val resultBitmap = originalBitmap.config?.let {
            Bitmap.createBitmap(width, height, it)
        } ?: run {
            return originalBitmap
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = originalBitmap.getPixel(x, y)
                val red = (Color.red(pixel) + brightnessValue).coerceIn(0.0, 255.0).toInt()
                val green = (Color.green(pixel) + brightnessValue).coerceIn(0.0, 255.0).toInt()
                val blue = (Color.blue(pixel) + brightnessValue).coerceIn(0.0, 255.0).toInt()

                val newPixel = Color.rgb(red, green, blue)
                resultBitmap.setPixel(x, y, newPixel)
            }
        }

        return resultBitmap
    }

    fun convertToGrayscale(originalBitmap: Bitmap): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val resultBitmap = originalBitmap.config?.let {
            Bitmap.createBitmap(width, height, it)
        } ?: run {
            return originalBitmap
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = originalBitmap.getPixel(x, y)

                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                val gray = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()

                resultBitmap?.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }

        return resultBitmap
    }

    private fun Double.toRadians(): Float {
        return Math.toRadians(this).toFloat()
    }

    fun rotate(originalBitmap: Bitmap, degrees: Double): Bitmap {
        val angleInRadians = degrees.toRadians()
        val cos = cos(angleInRadians)
        val sin = sin(angleInRadians)

        val matrix = Matrix()
        val values = floatArrayOf(
            cos, -sin, 0f,
            sin,  cos, 0f,
            0f,   0f,  1f
        )
        matrix.setValues(values)

        return Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)

    }

    fun translate(originalBitmap: Bitmap, xTranslation: Double = 0.0, yTranslation: Double = 0.0): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val resultBitmap = originalBitmap.config?.let {
            Bitmap.createBitmap(width, height, it)
        } ?: run {
            return originalBitmap
        }

        for (y in 0 until height) {
            for(x in 0 until width ){
                val translatedXPosition = (1*x + 0*y + xTranslation).toInt()
                val translatedYPosition = (0*x + 1*y + yTranslation).toInt()
                val isXInsideFrame = translatedXPosition in 0..<width
                val isYInsideFrame = translatedYPosition in 0 ..<height
                println("X: $translatedXPosition / Y: $translatedYPosition")
                if(isXInsideFrame && isYInsideFrame){
                    val pixel = originalBitmap.getPixel(x, y)
                    resultBitmap.setPixel(translatedXPosition, translatedYPosition, pixel)
                }

            }
        }
        return resultBitmap
    }

    fun changeScale(originalBitmap: Bitmap, multiplier: Double): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val resultBitmap = originalBitmap.config?.let {
            val scaleWidth = (multiplier*width).toInt()
            val scaleHeight = (multiplier*height).toInt()

            Bitmap.createBitmap(scaleWidth.toInt(), scaleHeight.toInt(), it)
        } ?: run {
            return originalBitmap
        }

        for(y in 0 until height){
            for (x in  0 until width) {
                val pixel = originalBitmap.getPixel(x, y)
                val newXPosition = multiplier * x + 0*y
                val newYPosition = 0*x + multiplier*y
                resultBitmap?.setPixel(newXPosition.toInt(), newYPosition.toInt(), pixel)
            }

        }

        return resultBitmap
    }

    fun horizontalMirror(originalBitmap: Bitmap): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height

        val resultBitmap = originalBitmap.config?.let {
            Bitmap.createBitmap(width, height, it)
        } ?:run {
            return originalBitmap
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = originalBitmap.getPixel(x, y)
                var xMirrorPosition = -1 * x + 0 * y
                if(xMirrorPosition < 0)
                    xMirrorPosition += width -1
                val yPosition = 0 * x + 1 * y
                resultBitmap.setPixel(xMirrorPosition, yPosition, pixel)
            }
        }
        return resultBitmap
    }

    fun verticalMirror(originalBitmap: Bitmap): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height

        val resultBitmap = originalBitmap.config?.let {
            Bitmap.createBitmap(width, height, it)
        } ?:run {
            return originalBitmap
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = originalBitmap.getPixel(x, y)
                val xPosition = 1 * x + 0 * y
                var mirrorYPosition = 0 * x + -1 * y
                if (mirrorYPosition < 0)
                    mirrorYPosition += height -1
                resultBitmap.setPixel(xPosition, mirrorYPosition, pixel)
            }
        }
        return resultBitmap
    }


    //Filtros Passa Baixa
    fun applyLowPassFilter(bitmap: Bitmap, kernelSize: Int = 3): Bitmap {
        val kernel = createLowPassKernel(kernelSize)

        return applyConvolution(bitmap, kernel)
    }

    private fun createLowPassKernel(size: Int): Array<FloatArray> {
        val kernel = Array(size) { FloatArray(size) }
        val value = 1.0f / (size * size)

        for (i in 0 until size) {
            for (j in 0 until size) {
                kernel[i][j] = value
            }
        }

        return kernel
    }

    fun createGaussianKernel(): Array<FloatArray> {
        return arrayOf(
            floatArrayOf(1/16f, 2/16f, 1/16f),
            floatArrayOf(2/16f, 4/16f, 2/16f),
            floatArrayOf(1/16f, 2/16f, 1/16f)
        )
    }

    fun applyConvolution(bitmap: Bitmap, kernel: Array<FloatArray>): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = bitmap.config?.let { Bitmap.createBitmap(width, height, it) }

        val kernelWidth = kernel[0].size
        val kernelHeight = kernel.size
        val kernelRadiusX = kernelWidth / 2
        val kernelRadiusY = kernelHeight / 2

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val resultPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var redSum = 0f
                var greenSum = 0f
                var blueSum = 0f

                for (ky in 0 until kernelHeight) {
                    for (kx in 0 until kernelWidth) {
                        val pixelX = max(0, min(width - 1, x + kx - kernelRadiusX))
                        val pixelY = max(0, min(height - 1, y + ky - kernelRadiusY))

                        val pixelIndex = pixelY * width + pixelX
                        val pixel = pixels[pixelIndex]

                        val kernelValue = kernel[ky][kx]
                        redSum += Color.red(pixel) * kernelValue
                        greenSum += Color.green(pixel) * kernelValue
                        blueSum += Color.blue(pixel) * kernelValue
                    }
                }

                val red = max(0, min(255, redSum.toInt()))
                val green = max(0, min(255, greenSum.toInt()))
                val blue = max(0, min(255, blueSum.toInt()))

                resultPixels[y * width + x] = Color.rgb(red, green, blue)
            }
        }

        if (resultBitmap != null) {
            resultBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
            return resultBitmap
        }
        return bitmap
    }

    fun applyGaussianBlur(bitmap: Bitmap): Bitmap {
        val kernel = createGaussianKernel()
        return applyConvolution(bitmap, kernel)
    }

}