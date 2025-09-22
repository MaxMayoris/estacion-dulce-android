package com.estaciondulce.app.activities

import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.estaciondulce.app.databinding.ActivityFullScreenImageBinding

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenImageBinding
    private lateinit var gestureDetector: GestureDetector
    private var currentImageIndex = 0
    private var allImages = listOf<String>()
    
    // Zoom variables
    private var matrix = Matrix()
    private var savedMatrix = Matrix()
    private var mode = NONE
    private var start = PointF()
    private var mid = PointF()
    private var oldDist = 1f
    
    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityFullScreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup button listeners
        setupButtonListeners()
        
        // Setup gesture detector for swipe navigation
        setupGestureDetector()
        
        // Get data from intent
        val imageUrl = intent.getStringExtra("imageUrl")
        val images = intent.getStringArrayListExtra("images")
        
        if (imageUrl != null && images != null) {
            allImages = images
            currentImageIndex = images.indexOf(imageUrl).takeIf { it >= 0 } ?: 0
            setupNavigation()
            loadCurrentImage()
        } else if (imageUrl != null) {
            // Single image mode
            allImages = listOf(imageUrl)
            currentImageIndex = 0
            setupNavigation()
            loadCurrentImage()
        } else {
            finish()
        }
    }
    
    private fun setupButtonListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }
        
        binding.prevButton.setOnClickListener {
            showPreviousImage()
        }
        
        binding.nextButton.setOnClickListener {
            showNextImage()
        }
    }
    
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // Only handle swipe gestures when image is not zoomed
                val values = FloatArray(9)
                matrix.getValues(values)
                val scale = values[Matrix.MSCALE_X]
                
                if (scale <= 1.0f) {
                    val diffX = e2.x - (e1?.x ?: 0f)
                    val diffY = e2.y - (e1?.y ?: 0f)
                    
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                            if (diffX > 0) {
                                // Swipe right - previous image (with circular navigation)
                                showPreviousImage()
                            } else {
                                // Swipe left - next image (with circular navigation)
                                showNextImage()
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })
        
        // Set up touch listener for zoom and pan
        binding.imageView.setOnTouchListener { _, event ->
            handleTouch(event)
        }
    }
    
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                start.set(event.x, event.y)
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                    mode = ZOOM
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    matrix.set(savedMatrix)
                    matrix.postTranslate(event.x - start.x, event.y - start.y)
                } else if (mode == ZOOM) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        matrix.set(savedMatrix)
                        val scale = newDist / oldDist
                        matrix.postScale(scale, scale, mid.x, mid.y)
                    }
                }
            }
        }
        
        binding.imageView.imageMatrix = matrix
        
        // If not zooming, let gesture detector handle swipe
        if (mode == NONE) {
            gestureDetector.onTouchEvent(event)
        }
        
        return true
    }
    
    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
    }
    
    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }
    
    private fun setupNavigation() {
        if (allImages.size > 1) {
            // Show navigation buttons and counter
            binding.prevButton.visibility = View.VISIBLE
            binding.nextButton.visibility = View.VISIBLE
            binding.imageCounter.visibility = View.VISIBLE
            updateNavigationButtons()
            updateImageCounter()
        } else {
            // Hide navigation elements for single image
            binding.prevButton.visibility = View.GONE
            binding.nextButton.visibility = View.GONE
            binding.imageCounter.visibility = View.GONE
        }
    }
    
    private fun updateNavigationButtons() {
        // With circular navigation, buttons are always enabled when there are multiple images
        binding.prevButton.isEnabled = allImages.size > 1
        binding.nextButton.isEnabled = allImages.size > 1
    }
    
    private fun updateImageCounter() {
        binding.imageCounter.text = "${currentImageIndex + 1} / ${allImages.size}"
    }
    
    private fun loadCurrentImage() {
        if (currentImageIndex >= 0 && currentImageIndex < allImages.size) {
            val imageUrl = allImages[currentImageIndex]
            android.util.Log.d("FullScreenImageActivity", "Loading image $currentImageIndex: $imageUrl")
            
            // Reset zoom and center the image when loading new image
            matrix.reset()
            binding.imageView.imageMatrix = matrix
            
            Glide.with(this)
                .load(imageUrl)
                .fitCenter()
                .into(binding.imageView)
            
            // Center the image after a short delay to ensure it's loaded
            binding.imageView.post {
                centerImage()
            }
        }
    }
    
    private fun centerImage() {
        val imageView = binding.imageView
        val drawable = imageView.drawable
        if (drawable != null) {
            val imageWidth = drawable.intrinsicWidth
            val imageHeight = drawable.intrinsicHeight
            val viewWidth = imageView.width
            val viewHeight = imageView.height
            
            if (imageWidth > 0 && imageHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                val scaleX = viewWidth.toFloat() / imageWidth
                val scaleY = viewHeight.toFloat() / imageHeight
                val scale = minOf(scaleX, scaleY)
                
                val scaledWidth = imageWidth * scale
                val scaledHeight = imageHeight * scale
                
                val translateX = (viewWidth - scaledWidth) / 2f
                val translateY = (viewHeight - scaledHeight) / 2f
                
                matrix.reset()
                matrix.postScale(scale, scale)
                matrix.postTranslate(translateX, translateY)
                imageView.imageMatrix = matrix
            }
        }
    }
    
    private fun showNextImage() {
        if (allImages.size > 1) {
            // Circular navigation: if at last image, go to first
            currentImageIndex = if (currentImageIndex < allImages.size - 1) {
                currentImageIndex + 1
            } else {
                0 // Go back to first image
            }
            loadCurrentImage()
            updateNavigationButtons()
            updateImageCounter()
        }
    }
    
    private fun showPreviousImage() {
        if (allImages.size > 1) {
            // Circular navigation: if at first image, go to last
            currentImageIndex = if (currentImageIndex > 0) {
                currentImageIndex - 1
            } else {
                allImages.size - 1 // Go to last image
            }
            loadCurrentImage()
            updateNavigationButtons()
            updateImageCounter()
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
