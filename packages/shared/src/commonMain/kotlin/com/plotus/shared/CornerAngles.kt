package com.plotus.shared

import kotlin.math.*

/** Flex the two chains joining a corner to its opposite anchor, keeping every measured edge fixed. */
fun PlotSetup.withCornerAngle(index: Int, target: PlotPoint): PlotSetup? {
    if (index !in vertices.indices || vertices.size != lengths.size || vertices.size < 4 || useDiagonals ||
        lengths.any { it == null || !it.isFinite() || it <= 0 } || !target.x.isFinite() || !target.y.isFinite()) return null
    val count = vertices.size
    val anchorIndex = (index + count / 2) % count
    fun chain(direction: Int): List<Int> {
        val indices = mutableListOf(anchorIndex)
        while (indices.last() != index) indices += (indices.last() + direction + count) % count
        return indices
    }
    val chains = listOf(chain(1), chain(-1))
    fun edges(indices: List<Int>) = indices.zipWithNext { a, b -> lengths[if ((a + 1) % count == b) a else b]!! }
    val ranges = chains.map { indices -> edges(indices).let { sides -> max(0.0, 2 * sides.max() - sides.sum()) to sides.sum() } }
    val minimum = ranges.maxOf { it.first } + 1e-7
    val maximum = ranges.minOf { it.second } - 1e-7
    if (minimum >= maximum) return null
    val anchor = vertices[anchorIndex]
    val wanted = distance(anchor, target)
    val radius = wanted.coerceIn(minimum, maximum)
    val direction = if (wanted > 1e-9) target else vertices[index]
    val directionLength = distance(anchor, direction)
    if (directionLength < 1e-9) return null
    val end = PlotPoint(anchor.x + (direction.x-anchor.x)*radius/directionLength, anchor.y + (direction.y-anchor.y)*radius/directionLength)
    val result = vertices.toMutableList()
    for (indices in chains) {
        val solved = fitCornerChain(indices.map { vertices[it] }, edges(indices), end) ?: return null
        indices.forEachIndexed { i, vertex -> result[vertex] = solved[i] }
    }
    if (!simplePolygon(result) || lengths.indices.any { abs(distance(result[it],result[(it+1)%count])-lengths[it]!!) > 1e-5 }) return null
    return copy(vertices = result)
}

private fun fitCornerChain(original: List<PlotPoint>, sides: List<Double>, end: PlotPoint): List<PlotPoint>? {
    val anchor = original.first()
    if (sides.size == 2) {
        // The two exact circle intersections avoid drift, including near straight corners.
        val span = distance(anchor,end)
        val along = (sides[0]*sides[0]-sides[1]*sides[1]+span*span)/(2*span)
        val height = sqrt(max(0.0,sides[0]*sides[0]-along*along))
        val ux=(end.x-anchor.x)/span;val uy=(end.y-anchor.y)/span
        val centre=PlotPoint(anchor.x+ux*along,anchor.y+uy*along)
        val candidates=listOf(PlotPoint(centre.x-uy*height,centre.y+ux*height),PlotPoint(centre.x+uy*height,centre.y-ux*height))
        return listOf(anchor,candidates.minBy {distance(it,original[1])},end)
    }
    val points=original.toMutableList()
    fun atLength(from:PlotPoint,toward:PlotPoint,length:Double,fallback:PlotPoint):PlotPoint {
        val span=distance(from,toward)
        val direction=if(span>1e-10)toward else fallback
        val d=distance(from,direction)
        if(d<1e-10)return PlotPoint(from.x+length,from.y)
        return PlotPoint(from.x+(direction.x-from.x)*length/d,from.y+(direction.y-from.y)*length/d)
    }
    repeat(240) {
        points[points.lastIndex]=end
        for(i in points.lastIndex-1 downTo 0)points[i]=atLength(points[i+1],points[i],sides[i],original[i])
        points[0]=anchor
        for(i in 1..points.lastIndex)points[i]=atLength(points[i-1],points[i],sides[i-1],original[i])
        if(distance(points.last(),end)<1e-7) {points[points.lastIndex]=end;return points}
    }
    return null
}
