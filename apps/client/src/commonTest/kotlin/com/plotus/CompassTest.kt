package com.plotus

import androidx.compose.ui.geometry.Offset
import kotlin.test.*

class CompassTest {
    @Test fun compassFindsInteriorSpaceInConcavePlotsAndShrinksForNarrowOnes() {
        val (rect,room)=compassPlacement(listOf(Offset(10f,10f),Offset(110f,10f),Offset(110f,90f),Offset(10f,90f)),120f,100f)
        assertEquals(Offset(60f,50f),rect);assertEquals(40f,room)
        val (centre,space)=compassPlacement(listOf(Offset(0f,0f),Offset(100f,0f),Offset(100f,20f),Offset(20f,20f),Offset(20f,100f),Offset(0f,100f)),100f,100f)
        assertTrue(centre.x<20||centre.y<20);assertTrue(space>0&&space<=10)
        val (_,narrow)=compassPlacement(listOf(Offset(0f,0f),Offset(100f,0f),Offset(100f,8f),Offset(0f,8f)),100f,100f)
        assertEquals(4f,narrow)
    }
}
