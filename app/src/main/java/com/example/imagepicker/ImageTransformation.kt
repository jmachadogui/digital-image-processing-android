package com.example.imagepicker

import ImageProcessor
import android.graphics.Bitmap

interface ImageTransformation {
    fun apply(bitmap: Bitmap): Bitmap
}

class ScaleTransformation(private val factor: Double) : ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().changeScale(bitmap, factor)
    }
}

class RotationTransformation(private val factor: Double) : ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().rotate(bitmap, factor)
    }
}

class MirrorHTransformation() : ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().horizontalMirror(bitmap)
    }
}

class MirrorVTransformation() : ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().verticalMirror(bitmap)
    }
}

class TranslationTransformation(private val xFactor: Double, private val yFactor: Double) : ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().translate(bitmap, xFactor, yFactor)
    }
}

class BrightnessTransformation(private val factor: Double): ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().modifyBrightness(bitmap, factor)
    }
}

class ContrastTransformation(private val factor: Float) : ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return  ImageProcessor().modifyContrast(bitmap, factor)
    }
}

class GrayscaleTransformation(): ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().convertToGrayscale(bitmap)
    }
}

class LowPassFilterTransformation(private val kernelSize: Int = 3): ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().applyLowPassFilter(bitmap, kernelSize)
    }
}

class GaussianFilterTransformation(): ImageTransformation {
    override fun apply(bitmap: Bitmap): Bitmap {
        return ImageProcessor().applyGaussianBlur(bitmap)
    }
}