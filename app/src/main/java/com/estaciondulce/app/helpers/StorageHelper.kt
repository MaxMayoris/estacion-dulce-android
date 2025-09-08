package com.estaciondulce.app.helpers

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.*

/**
 * Helper class for managing Firebase Storage operations.
 */
class StorageHelper {
    
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference
    
    companion object {
        private const val TAG = "StorageHelper"
    }
    
    /**
     * Validates and sanitizes a storage path to prevent issues.
     * @param path The path to validate
     * @return The sanitized path or null if invalid
     */
    private fun validateAndSanitizePath(path: String): String? {
        if (path.isEmpty()) {
            return null
        }
        
        // Remove leading/trailing slashes and normalize
        val sanitizedPath = path.trim().removePrefix("/").removeSuffix("/")
        
        // Check for invalid characters
        if (sanitizedPath.contains("..") || sanitizedPath.contains("//")) {
            return null
        }
        
        return sanitizedPath
    }
    
    /**
     * Uploads an image to Firebase Storage and returns the download URL.
     * @param imageUri The URI of the image to upload
     * @param folder The folder path in storage (e.g., "recetas")
     * @param fileName Optional custom filename. If null, generates a unique name.
     * @param onSuccess Callback with the download URL
     * @param onError Callback with error information (exact Firebase message)
     * @param onProgress Callback with upload progress (0-100)
     */
    fun uploadImage(
        imageUri: Uri,
        folder: String,
        fileName: String? = null,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit,
        onProgress: (Int) -> Unit = {}
    ) {
        try {
            // Validate input
            if (imageUri == Uri.EMPTY) {
                onError(Exception("Invalid image URI"))
                return
            }
            
            // Validate and sanitize folder path
            val sanitizedFolder = validateAndSanitizePath(folder)
            if (sanitizedFolder == null) {
                onError(Exception("Invalid folder path: $folder"))
                return
            }
            
            val finalFileName = fileName ?: "${UUID.randomUUID()}.jpg"
            val fullPath = "$sanitizedFolder/$finalFileName"
            
            val imageRef = storageRef.child(fullPath)
            val uploadTask = imageRef.putFile(imageUri)
            
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                onProgress(progress)
            }
            
            uploadTask.addOnSuccessListener { taskSnapshot ->
                // Use the taskSnapshot's storage reference to get download URL
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                    onSuccess(downloadUri.toString())
                }.addOnFailureListener { exception ->
                    onError(exception)
                }
            }
            
            uploadTask.addOnFailureListener { exception ->
                onError(exception)
            }
            
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    /**
     * Deletes an image from Firebase Storage.
     * @param imageUrl The download URL of the image to delete
     * @param onSuccess Callback when deletion is successful
     * @param onError Callback with error information (exact Firebase message)
     */
    fun deleteImage(
        imageUrl: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            if (imageUrl.isEmpty()) {
                onSuccess() // Consider empty URL as already deleted
                return
            }
            
            val imageRef = storage.getReferenceFromUrl(imageUrl)
            
            imageRef.delete()
                .addOnSuccessListener { 
                    onSuccess() 
                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    /**
     * Generates a unique filename for recipe images.
     * @param recipeId The ID of the recipe
     * @return A unique filename
     */
    fun generateRecipeImageFileName(recipeId: String): String {
        return "recipe_${recipeId}_${System.currentTimeMillis()}.jpg"
    }
    
    /**
     * Generates the storage path for recipe images using the new structure.
     * @param recipeId The ID of the recipe
     * @return The storage path in format: recipes/{recipeId}/image.jpg
     */
    fun generateRecipeImagePath(recipeId: String): String {
        return "recipes/$recipeId/image.jpg"
    }
    
    /**
     * Uploads an image for a specific recipe using the organized folder structure.
     * @param imageUri The URI of the image to upload
     * @param recipeId The ID of the recipe
     * @param onSuccess Callback with the download URL
     * @param onError Callback with error information (exact Firebase message)
     * @param onProgress Callback with upload progress (0-100)
     */
    fun uploadRecipeImage(
        imageUri: Uri,
        recipeId: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit,
        onProgress: (Int) -> Unit = {}
    ) {
        try {
            // Validate input
            if (imageUri == Uri.EMPTY) {
                onError(Exception("Invalid image URI"))
                return
            }
            
            if (recipeId.isEmpty()) {
                onError(Exception("Recipe ID cannot be empty"))
                return
            }
            
            // Validate and sanitize recipe ID
            val sanitizedRecipeId = validateAndSanitizePath(recipeId)
            if (sanitizedRecipeId == null) {
                onError(Exception("Invalid recipe ID: $recipeId"))
                return
            }
            
            // Use the new organized structure: recipes/{recipeId}/image.jpg
            val imagePath = "recipes/$sanitizedRecipeId/image.jpg"
            
            val imageRef = storageRef.child(imagePath)
            val uploadTask = imageRef.putFile(imageUri)
            
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                onProgress(progress)
            }
            
            uploadTask.addOnSuccessListener { taskSnapshot ->
                // Use the taskSnapshot's storage reference to get download URL
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                    onSuccess(downloadUri.toString())
                }.addOnFailureListener { exception ->
                    onError(exception)
                }
            }
            
            uploadTask.addOnFailureListener { exception ->
                onError(exception)
            }
            
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    /**
     * Migrates a temporary image to the final recipe location.
     * @param tempImageUrl The URL of the temporary image
     * @param newRecipeId The final recipe ID
     * @param onSuccess Callback with the new download URL
     * @param onError Callback with error information (exact Firebase message)
     */
    fun migrateTempImageToRecipe(
        tempImageUrl: String,
        newRecipeId: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            if (tempImageUrl.isEmpty() || !tempImageUrl.contains("temp_")) {
                onSuccess(tempImageUrl)
                return
            }
            
            // Validate and sanitize recipe ID
            val sanitizedRecipeId = validateAndSanitizePath(newRecipeId)
            if (sanitizedRecipeId == null) {
                onError(Exception("Invalid recipe ID for migration: $newRecipeId"))
                return
            }
            
            // Get reference to temp image
            val tempImageRef = storage.getReferenceFromUrl(tempImageUrl)
            
            // Create new reference for final location
            val finalPath = "recipes/$sanitizedRecipeId/image.jpg"
            val finalImageRef = storageRef.child(finalPath)
            
            // Copy the image to the final location
            tempImageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
                finalImageRef.putBytes(bytes).addOnSuccessListener { taskSnapshot ->
                    // Get the new download URL
                    taskSnapshot.storage.downloadUrl.addOnSuccessListener { newUrl ->
                        // Delete the temp image
                        tempImageRef.delete().addOnSuccessListener {
                            onSuccess(newUrl.toString())
                        }.addOnFailureListener { _ ->
                            // Migration succeeded even if cleanup failed
                            onSuccess(newUrl.toString())
                        }
                    }.addOnFailureListener { urlError ->
                        onError(urlError)
                    }
                }.addOnFailureListener { copyError ->
                    onError(copyError)
                }
            }.addOnFailureListener { readError ->
                onError(readError)
            }
            
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    /**
     * Reads the test.txt file from Firebase Storage root to verify storage access.
     * This is used for production environment testing.
     * @param onSuccess Callback with the file content
     * @param onError Callback with error information (exact Firebase message)
     */
    fun readTestFile(
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            // Reference to the test.txt file in the root of the storage bucket
            val testFileRef = storageRef.child("test.txt")
            
            // Download the file as bytes and convert to string
            testFileRef.getBytes(Long.MAX_VALUE)
                .addOnSuccessListener { bytes ->
                    try {
                        val content = String(bytes, Charsets.UTF_8)
                        onSuccess(content)
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }
            
        } catch (e: Exception) {
            onError(e)
        }
    }
}

