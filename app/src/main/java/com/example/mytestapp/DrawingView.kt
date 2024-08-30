
package com.example.mytestapp

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.pow

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val paint = Paint().apply {
        color = 0x80000000.toInt() // Base color with a starting alpha
        strokeWidth = 10f // Line width
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN) // Set to darken mode
    }

    private val eraserPaint = Paint().apply {
        color = Color.TRANSPARENT
        style = Paint.Style.STROKE
        strokeWidth = 20f
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val eraserCursorPaint = Paint().apply {
        color = Color.argb(100, 128, 128, 128) // Semi-transparent grey
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }

    private var currentPaint = paint

    private val path = Path()
    private val paths = mutableListOf<Pair<Path, Paint>>() // Store all paths with corresponding paints
    var isErasing = false // To toggle between drawing and erasing
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val shadowPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 0) // Semi-transparent black
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }
    private var shadowX: Float = -1f
    private var shadowY: Float = -1f
    private val eraserRadius = 100f // Radius for both the eraser and shadow effect

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    private var touchX: Float = -1f
    private var touchY: Float = -1f

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        // Initialize bitmap and canvas for drawing
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmapCanvas = Canvas(bitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw the bitmap
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        // Draw the current path being drawn or erased
        canvas.drawPath(path, currentPaint)

        // Draw the eraser cursor if in erasing mode
        if (isErasing && touchX >= 0 && touchY >= 0) {
            canvas.drawCircle(touchX, touchY, eraserPaint.strokeWidth / 2, eraserCursorPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(event.x, event.y)
                currentPaint = if (isErasing) eraserPaint else paint
                touchX = event.x
                touchY = event.y
                vibrate() // Call vibration method
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                path.lineTo(event.x, event.y)
                // Draw on the bitmap canvas
                bitmapCanvas?.drawPath(path, currentPaint)
                invalidate() // Request to redraw the view
            }
            MotionEvent.ACTION_UP -> {
                if (!isErasing) {
                    paths.add(Pair(Path(path), paint)) // Add the completed path with corresponding paint
                }
                path.reset() // Reset the current path for the next drawing
                touchX = -1f
                touchY = -1f
            }
        }
        return super.onTouchEvent(event)
    }



    fun toggleEraserMode() {
        isErasing = !isErasing
        path.reset() // Reset path when toggling to avoid unwanted strokes
        if (!isErasing) {
            shadowX = -1f
            shadowY = -1f
        }
        Log.d("DrawingView", "Eraser mode toggled to $isErasing")
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)) // 50ms duration
        } else {
            vibrator.vibrate(50) // For older devices
        }
        Log.d("DrawingView", "Vibration triggered")
    }







    fun saveDrawingAsSVG(filePath: String) {
        // SVG header and footer
        val svgHeader = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="${width}" height="${height}" color="black">
"""
        val svgFooter = "</svg>"

        // Initialize new SVG content with header
        val svgContent = StringBuilder(svgHeader)

        // Append current paths to the new SVG content
        for ((savedPath, _) in paths) {
            val pathData = savedPath.toSVGPath()
            svgContent.append("<path d=\"$pathData\" fill=\"none\" stroke=\"black\"/>")
        }

        // Add the closing SVG tag
        svgContent.append(svgFooter)

        // Save the new SVG content to the file, overwriting existing content
        try {
            File(filePath).writeText(svgContent.toString())
            Log.d("DrawingView", "SVG successfully saved to $filePath")
        } catch (e: IOException) {
            Log.e("DrawingView", "Error saving SVG", e)
        }
    }


    fun loadSVG(filePath: String) {
        Log.d("DrawingView", "Attempting to load SVG from $filePath")

        // Clear existing drawing and background before loading
        clearDrawing()

        if (width <= 0 || height <= 0) {
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    loadSVG(filePath)
                }
            })
            return
        }

        try {
            val svgFile = File(filePath)
            if (!svgFile.exists()) {
                Log.e("DrawingView", "SVG file not found at: $filePath")
                return
            }

            // Clear the background bitmap before loading the new SVG
            background = null

            // Load SVG paths and render
            val svgContent = svgFile.readText()
            parseSVGPaths(svgContent)

            val svg = SVG.getFromInputStream(FileInputStream(svgFile))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // Clear canvas with white color
            svg.renderToCanvas(canvas)
            this.background = BitmapDrawable(resources, bitmap)

            // Request a redraw to apply changes
            invalidate()

            Log.d("DrawingView", "SVG loaded successfully.")
        } catch (e: IOException) {
            Log.e("DrawingView", "Error loading SVG", e)
        } catch (e: SVGParseException) {
            Log.e("DrawingView", "SVG parsing error", e)
        }
    }




    private fun parseSVGPaths(svgContent: String) {
        val pathPattern = Regex("""<path d="([^"]+)"[^>]*>""")
        val matchResults = pathPattern.findAll(svgContent)

        // Clear any existing paths to avoid duplication
        paths.clear()

        for (match in matchResults) {
            val pathData = match.groupValues[1]
            val path = Path().apply {
                parseSVGPathData(pathData)
            }
            paths.add(Pair(path, paint)) // Use default paint, or customize if needed
        }
    }

    private fun Path.parseSVGPathData(pathData: String) {
        // Regex to match command and coordinates
        val commandPattern = Regex("([MLCQAZ])([^MLCQAZ]*)")
        val commands = commandPattern.findAll(pathData)

        var currentCommand = ""
        var coordinates = mutableListOf<Float>()

        Log.d("DrawingView", "Starting SVG path parsing")

        for (match in commands) {
            val commandType = match.groupValues[1]
            val coords = match.groupValues[2].trim()

            // Update command type
            currentCommand = commandType

            // Parse coordinates
            if (coords.isNotEmpty()) {
                coordinates.addAll(coords.split("[,\\s]+".toRegex()).mapNotNull { it.toFloatOrNull() })
            }

            // Execute commands based on the command type
            when (currentCommand) {
                "M" -> {
                    if (coordinates.size >= 2) {
                        moveTo(coordinates[0], coordinates[1])
                        coordinates = coordinates.drop(2).toMutableList() // Move to next set of coordinates
                    } else {
                        Log.w("DrawingView", "Invalid moveTo command: $coords")
                    }
                }
                "L" -> {
                    if (coordinates.size >= 2) {
                        lineTo(coordinates[0], coordinates[1])
                        coordinates = coordinates.drop(2).toMutableList() // Move to next set of coordinates
                    } else {
                        Log.w("DrawingView", "Invalid lineTo command: $coords")
                    }
                }
                "C" -> {
                    if (coordinates.size >= 6) {
                        cubicTo(coordinates[0], coordinates[1], coordinates[2], coordinates[3], coordinates[4], coordinates[5])
                        coordinates = coordinates.drop(6).toMutableList() // Move to next set of coordinates
                    } else {
                        Log.w("DrawingView", "Invalid cubicTo command: $coords")
                    }
                }
                "Q" -> {
                    if (coordinates.size >= 4) {
                        quadTo(coordinates[0], coordinates[1], coordinates[2], coordinates[3])
                        coordinates = coordinates.drop(4).toMutableList() // Move to next set of coordinates
                    } else {
                        Log.w("DrawingView", "Invalid quadTo command: $coords")
                    }
                }
                "Z" -> close()
                else -> Log.w("DrawingView", "Unsupported SVG command: $currentCommand")
            }
        }

        Log.d("DrawingView", "SVG path parsing completed")
    }


    private fun Path.toSVGPath(): String {
        val builder = StringBuilder()
        val pathMeasure = PathMeasure(this, false)
        val pathLength = pathMeasure.length
        val segments = FloatArray(2)
        var distance = 0f

        while (distance < pathLength) {
            pathMeasure.getPosTan(distance, segments, null)
            val command = when {
                distance == 0f -> "M" // Move to
                else -> "L" // Line to
            }
            builder.append("$command ${segments[0]},${segments[1]} ")
            distance += 1f
        }

        val svgPathData = builder.toString().trim()
        Log.d("DrawingView", "SVG Path Data: $svgPathData") // Log SVG path data

        return svgPathData
    }

    fun clearDrawing() {
        // Clear the existing paths
        paths.clear()
        // Optionally clear the background (depends on your needs)
        background = null
        // Request to redraw the view to apply changes
        invalidate()
    }
}
    