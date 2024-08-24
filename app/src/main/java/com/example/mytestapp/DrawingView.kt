package com.example.mytestapp

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ViewTreeObserver
import android.graphics.drawable.BitmapDrawable
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.pow
import kotlin.math.sqrt

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val paint = Paint().apply {
        color = 0xFF000000.toInt() // Black color
        strokeWidth = 10f // Line width
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val eraserPaint = Paint().apply {
        strokeWidth = 5f // Eraser width
        isAntiAlias = true
        style = Paint.Style.STROKE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // Set to erase mode
    }

    private val path = Path()
    private val paths = mutableListOf<Pair<Path, Paint>>() // Store all paths with corresponding paints
    var isErasing = false // To toggle between drawing and erasing

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val debugCircles = mutableListOf<Pair<Float, Float>>()
    private val eraserRadius = 10f // Radius for the visual representation of the eraser

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw all saved paths with their corresponding paints
        for ((savedPath, savedPaint) in paths) {
            canvas.drawPath(savedPath, savedPaint)
        }
        // Draw the current path being drawn
        canvas.drawPath(path, if (isErasing) eraserPaint else paint)

        // Draw the eraser preview circles
        val circlePaint = Paint().apply {
            color = Color.RED // Color for the eraser preview
            style = Paint.Style.STROKE // Outline style to show the area affected
            strokeWidth = 5f // Thickness of the outline
            alpha = 100 // Semi-transparent to differentiate from paths
        }
        for ((x, y) in debugCircles) {
            canvas.drawCircle(x, y, eraserRadius, circlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(event.x, event.y)
                vibrate() // Call vibration method
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isErasing) {
                    erase(event.x, event.y)
                } else {
                    path.lineTo(event.x, event.y)
                }
                invalidate() // Request to redraw the view
            }
            MotionEvent.ACTION_UP -> {
                if (!isErasing) {
                    paths.add(Pair(Path(path), paint)) // Add the completed path with corresponding paint
                }
                path.reset() // Reset the current path for the next drawing
            }
        }
        return super.onTouchEvent(event)
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)) // 50ms duration
        } else {
            vibrator.vibrate(50) // For older devices
        }
        Log.d("DrawingView", "Vibration triggered")
    }

    fun toggleEraserMode() {
        isErasing = !isErasing
        path.reset() // Reset path when toggling to avoid unwanted strokes
        // Clear the debug circles when toggling mode
        debugCircles.clear()
    }

    private fun erase(x: Float, y: Float) {
        Log.d("DrawingView", "Erase initiated at ($x, $y) with radius $eraserRadius")

        // Add circle positions to debugCircles list


        // Split paths at the eraser position
        splitPathsAtEraser(x, y)

        // Trigger a redraw to show circles
        invalidate()

        Log.d("DrawingView", "Paths after erase: ${paths.size} remaining paths.")
    }

    private fun splitPathsAtEraser(x: Float, y: Float) {
        val eraserRadius = 30f // Eraser radius set to 30px
        val eraserRadiusSquared = eraserRadius * eraserRadius
        val newPaths = mutableListOf<Pair<Path, Paint>>()

        for ((savedPath, savedPaint) in paths) {
            val pathMeasure = PathMeasure(savedPath, false)
            val length = pathMeasure.length
            var start = 0f
            var currentPath = Path()
            var insideEraser = false
            var lastPos: FloatArray? = null
            var isFirstSegment = true

            while (start <= length) {
                val pos = FloatArray(2)
                pathMeasure.getPosTan(start, pos, null)
                val dx = pos[0] - x
                val dy = pos[1] - y
                val distanceSquared = dx * dx + dy * dy
                val isInsideEraser = distanceSquared <= eraserRadiusSquared

                if (isInsideEraser) {
                    if (!insideEraser) {
                        if (lastPos != null) {
                            if (!isFirstSegment) {
                                currentPath.lineTo(pos[0], pos[1])
                                newPaths.add(Pair(Path(currentPath), savedPaint))
                                currentPath.reset()
                            }
                            currentPath.moveTo(lastPos[0], lastPos[1])
                            currentPath.lineTo(pos[0], pos[1])
                            isFirstSegment = false
                        } else {
                            currentPath.moveTo(pos[0], pos[1])
                        }
                        insideEraser = true
                    } else {
                        currentPath.lineTo(pos[0], pos[1])
                    }
                } else {
                    if (insideEraser) {
                        currentPath.lineTo(pos[0], pos[1])
                        newPaths.add(Pair(Path(currentPath), savedPaint))
                        currentPath.reset()
                        currentPath.moveTo(pos[0], pos[1])
                        insideEraser = false
                    } else {
                        if (isFirstSegment) {
                            currentPath.moveTo(pos[0], pos[1])
                            isFirstSegment = false
                        } else {
                            currentPath.lineTo(pos[0], pos[1])
                        }
                    }
                }

                lastPos = pos.clone()
                start += 1f // Move to the next segment of the path
            }

            // Add the last segment if it's not empty
            if (!currentPath.isEmpty) {
                newPaths.add(Pair(currentPath, savedPaint))
            }
        }

        // Clear old paths and add new split paths
        paths.clear()
        paths.addAll(newPaths)
    }


    fun saveDrawingAsSVG(filePath: String) {
        val svgHeader = """<?xml version="1.0" encoding="UTF-8"?>
    <svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="${width}" height="${height}" color="black">
    """
        val svgFooter = "</svg>"

        // Load existing SVG content if the file exists
        val existingSVGContent = StringBuilder()
        val file = File(filePath)
        if (file.exists()) {
            existingSVGContent.append(file.readText())
            // Remove the old closing SVG tag
            val index = existingSVGContent.lastIndexOf("</svg>")
            if (index != -1) {
                existingSVGContent.delete(index, existingSVGContent.length)
            }
        } else {
            existingSVGContent.append(svgHeader)
        }

        // Append the new path data
        for ((savedPath, _) in paths) {
            val pathData = savedPath.toSVGPath()
            existingSVGContent.append("<path d=\"$pathData\" fill=\"none\" stroke=\"black\"/>")
        }

        // Add the closing SVG tag
        existingSVGContent.append(svgFooter)

        try {
            file.writeText(existingSVGContent.toString())
            Log.d("DrawingView", "SVG successfully saved to $filePath")
        } catch (e: IOException) {
            Log.e("DrawingView", "Error saving SVG", e)
        }
    }

    fun loadSVG(filePath: String) {
        Log.d("DrawingView", "Attempting to load SVG from $filePath") // Confirm file path

        if (width <= 0 || height <= 0) {
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // Remove the listener to avoid calling it multiple times
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

            val svg = SVG.getFromInputStream(FileInputStream(svgFile))

            // Render the SVG to a Bitmap and use it as a background
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            svg.renderToCanvas(canvas)
            this.background = BitmapDrawable(resources, bitmap)

            Log.d("DrawingView", "SVG loaded successfully.")
        } catch (e: IOException) {
            Log.e("DrawingView", "Error loading SVG", e)
        } catch (e: SVGParseException) {
            Log.e("DrawingView", "SVG parsing error", e)
        }
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
}
