package com.example.imagepicker

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException

/**
 * Handles all file operations related to images
 * Responsible for creating edit copies, loading images, and saving images
 */
class ImageFileManager(private val contentResolver: ContentResolver) {

    /**
     * Deletes the edited version of an image if it exists
     * @param originalUri URI of the original image
     * @return true if successfully deleted or no edit existed, false if deletion failed
     */
    fun resetImage(editionUri: Uri?): Boolean {
        editionUri?.let {
            try {
                contentResolver.delete(editionUri, null, null) > 0
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }?: false
        return true
    }

    /**
     * Creates a new edit copy of an image or returns an existing one
     * @param originalUri URI of the original image
     * @return URI of the edit copy, or null if creation failed
     */
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

    /**
     * Applies a filter function to an edit copy
     * @param editUri URI of the edit copy
     * @param filterFunction Function that transforms a bitmap
     * @return true if successful, false otherwise
     */
    fun applyFilterToEditCopy(editUri: Uri, filterFunction: (Bitmap) -> Bitmap): Boolean {
        val bitmap = getBitmapFromUri(editUri) ?: return false
        val processedBitmap = filterFunction(bitmap)

        // Update the existing file
        return updateExistingImage(editUri, processedBitmap)
    }

    /**
     * Gets a bitmap from a URI
     * @param uri URI to get the bitmap from
     * @return bitmap or null
     */
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

    /**
     * Updates an existing image file with a new bitmap
     * @param uri URI of the image file
     * @param bitmap New bitmap
     * @return true if successful, false otherwise
     */
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

    /**
     * Gets the file name from a URI
     * @param uri URI to get the file name from
     * @return file name or null
     */
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

    /**
     * Generates a file name for an edit copy
     * @param originalFileName Original file name
     * @return Generated edit file name
     */
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

    /**
     * Finds an edit file URI by file name
     * @param editFileName File name to find
     * @return URI if found, null otherwise
     */
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

    /**
     * Saves a new bitmap to storage
     * @param bitmap Bitmap to save
     * @param fileName File name to use
     * @return URI of the saved bitmap, or null if saving failed
     */
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
}