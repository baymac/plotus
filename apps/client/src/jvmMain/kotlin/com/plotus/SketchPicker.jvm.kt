package com.plotus

import com.plotus.shared.SketchImage

internal actual suspend fun pickSketchImage(): SketchImage? = throw AuthFailure(0,"Choose an image in the iPhone or web app.")
