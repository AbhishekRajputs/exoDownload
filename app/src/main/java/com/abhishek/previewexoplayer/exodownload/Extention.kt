package com.abhishek.previewexoplayer.exodownload

import android.view.View
import android.widget.FrameLayout
import com.abhishek.previewexoplayer.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DecimalFormat


fun BottomSheetDialog.createRounderBottomSheet() {
    setOnShowListener { dia ->
        val bottomSheetDialog = dia as BottomSheetDialog
        val bottomSheetInternal: FrameLayout =
            bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet)!!
        bottomSheetInternal.setBackgroundResource(R.drawable.rounded_top_corners)
    }
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}

fun Long.formatFileSize(): String {
    val b = this.toDouble()
    val k = this / 1024.0
    val m = this / 1024.0 / 1024.0
    val g = this / 1024.0 / 1024.0 / 1024.0
    val t = this / 1024.0 / 1024.0 / 1024.0 / 1024.0
    val dec = DecimalFormat("0.00")
    return when {
        t > 1 -> dec.format(t) + " TB"
        g > 1 -> dec.format(g) + " GB"
        m > 1 -> dec.format(m) + " MB"
        k > 1 -> dec.format(k) + " KB"
        else -> dec.format(b) + " Bytes"
    }
}