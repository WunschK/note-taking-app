package com.example.mytestapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val paint = Paint().apply {
        color = 0xFF000000.toInt() // Black color
        strokeWidth = 10f // Line width
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val path = Path()
    private val paths = mutableListOf<Path>() // Store all paths drawn

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw all saved paths
        for (savedPath in paths) {
            canvas.drawPath(savedPath, paint)
        }
        // Draw the current path being drawn
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(event.x, event.y)
                vibrate() // Call vibration method
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(event.x, event.y)
                invalidate() // Request to redraw the view
            }
            MotionEvent.ACTION_UP -> {
                paths.add(Path(path)) // Add the completed path to the list of paths
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
        for (savedPath in paths) {
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
        val pathMeasure = android.graphics.PathMeasure(this, false)
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
