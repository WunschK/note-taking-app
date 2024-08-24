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

    private val eraserRadius = 10f // Radius for the visual representation of the eraser

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE) // Clear the canvas with a white background

        // Draw all saved paths with their corresponding paints
        for ((savedPath, savedPaint) in paths) {
            canvas.drawPath(savedPath, savedPaint)
        }

        // Draw the current path being drawn or erased
        canvas.drawPath(path, if (isErasing) eraserPaint else paint)
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

    }

    private fun erase(x: Float, y: Float) {
        Log.d("DrawingView", "Erase initiated at ($x, $y) with radius $eraserRadius")


        // Split paths at the eraser position
        splitPathsAtEraser(x, y)

        // Trigger a redraw to show circles
        invalidate()

        Log.d("DrawingView", "Paths after erase: ${paths.size} remaining paths.")
    }

    private fun splitPathsAtEraser(x: Float, y: Float) {
        val eraserRadius = 100f // Eraser radius set to 100px
        val eraserRadiusSquared = eraserRadius * eraserRadius
        val newPaths = mutableListOf<Pair<Path, Paint>>()

        for ((savedPath, savedPaint) in paths) {
            val pathMeasure = PathMeasure(savedPath, false)
            val length = pathMeasure.length
            var start = 0f
            var currentPath = Path()
            var insideEraser = false
            var lastPos: FloatArray? = null

            // Use a smaller step size for better precision
            val stepSize = 0.5f // Smaller step size for finer path details

            while (start <= length) {
                val pos = FloatArray(2)
                pathMeasure.getPosTan(start, pos, null)
                val dx = pos[0] - x
                val dy = pos[1] - y
                val distanceSquared = dx * dx + dy * dy
                val isInsideEraser = distanceSquared <= eraserRadiusSquared

                if (isInsideEraser) {
                    if (!insideEraser) {
                        // Path entering the eraser area
                        if (lastPos != null) {
                            if (currentPath.isEmpty) {
                                currentPath.moveTo(lastPos[0], lastPos[1])
                            } else {
                                currentPath.lineTo(lastPos[0], lastPos[1])
                            }
                            // Add path segment up to the eraser entry point
                            newPaths.add(Pair(Path(currentPath), savedPaint))
                            currentPath.reset()
                        }
                        currentPath.moveTo(pos[0], pos[1])
                        insideEraser = true
                    } else {
                        // Continue path inside the eraser area
                        currentPath.lineTo(pos[0], pos[1])
                    }
                } else {
                    if (insideEraser) {
                        // Path exiting the eraser area
                        currentPath.lineTo(pos[0], pos[1])
                        newPaths.add(Pair(Path(currentPath), savedPaint))
                        currentPath.reset()
                        currentPath.moveTo(pos[0], pos[1])
                        insideEraser = false
                    } else {
                        if (currentPath.isEmpty) {
                            currentPath.moveTo(pos[0], pos[1])
                        } else {
                            currentPath.lineTo(pos[0], pos[1])
                        }
                    }
                }

                lastPos = pos.clone()
                start += stepSize // Use a smaller step size for finer granularity
            }

            // Add the last path segment if it's not empty
            if (!currentPath.isEmpty && !insideEraser) {
                currentPath.lineTo(lastPos?.get(0) ?: 0f, lastPos?.get(1) ?: 0f)
                newPaths.add(Pair(currentPath, savedPaint))
            }
        }

        // Clear old paths and add new paths excluding those completely inside the eraser radius
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
        Log.d("DrawingView", "Attempting to load SVG from $filePath")
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

            // Clear existing paths
            paths.clear()

            // Clear the background
            background = null

            val svgContent = svgFile.readText()

            // Parse and add new SVG paths
            parseSVGPaths(svgContent)

            // Render SVG onto a bitmap for preview (optional)
            val svg = SVG.getFromInputStream(FileInputStream(svgFile))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // Clear the canvas with white color
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
        // Use a regex or a proper SVG parser to extract path data from svgContent
        val pathPattern = Regex("""<path d="([^"]+)"[^>]*>""")
        val matchResults = pathPattern.findAll(svgContent)

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

    private fun clearDrawing() {
        // Clear the existing paths
        paths.clear()

        // Optionally clear the background (depends on your needs)
        background = null

        // Request to redraw the view to apply changes
        invalidate()
    }
}
