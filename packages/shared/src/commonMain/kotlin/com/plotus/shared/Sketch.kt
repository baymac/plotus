package com.plotus.shared

import kotlinx.serialization.Serializable

@Serializable data class SketchImage(val mimeType:String,val base64:String)
@Serializable data class SketchSuggestion(val setup:PlotSetup,val notes:List<String> = emptyList())
@Serializable data class SketchImport(val id:String,val status:String,val suggestion:SketchSuggestion?=null,val error:String?=null)
