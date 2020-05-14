package com.kotlinapp.design

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint


//Draw for labels: interior style and exterior background style
class LabelsBorderText(interiorColor: Int, exteriorColor: Int, textSize: Float) {
    private val interiorTextDesign: Paint = Paint()
    private val exteriorTextDesign: Paint
    private val textSize: Float

    // Default values for interior and exterior with specific textSize
    constructor(textSize: Float) : this(
        Color.WHITE,
        Color.BLACK,
        textSize
    )

    fun drawText(
        canvas: Canvas,
        posX: Float,
        posY: Float,
        text: String?
    ) {
        canvas.drawText(text!!, posX, posY, exteriorTextDesign)
        canvas.drawText(text, posX, posY, interiorTextDesign)
    }


    fun drawText(
        canvas: Canvas,
        posX: Float,
        posY: Float,
        text: String?,
        bgPaint: Paint?
    ) {
        val width = exteriorTextDesign.measureText(text)
        val textSize = exteriorTextDesign.textSize
        val paint = Paint(bgPaint)
        paint.style = Paint.Style.FILL
        paint.alpha = 160
        canvas.drawRect(posX, posY + textSize.toInt(), posX + width.toInt(), posY, paint)
        canvas.drawText(text!!, posX, posY + textSize, interiorTextDesign)
    }

    init {
        // Text content color
        interiorTextDesign.textSize = textSize
        interiorTextDesign.color = interiorColor
        interiorTextDesign.style = Paint.Style.FILL
        interiorTextDesign.isAntiAlias = false
        interiorTextDesign.alpha = 255
        // Background for text
        exteriorTextDesign = Paint()
        exteriorTextDesign.textSize = textSize
        exteriorTextDesign.color = exteriorColor
        exteriorTextDesign.style = Paint.Style.FILL_AND_STROKE
        exteriorTextDesign.strokeWidth = textSize / 8
        exteriorTextDesign.isAntiAlias = false
        exteriorTextDesign.alpha = 255
        this.textSize = textSize
    }
}