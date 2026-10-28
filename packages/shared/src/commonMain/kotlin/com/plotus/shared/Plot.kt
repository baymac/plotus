package com.plotus.shared

import kotlinx.serialization.Serializable
import kotlin.math.*

const val METRES_PER_FOOT = 0.3048
const val SQUARE_FEET_PER_SQUARE_METRE = 1.0 / (METRES_PER_FOOT * METRES_PER_FOOT)

@Serializable enum class PlotUnits { FEET_INCHES, METRES }
@Serializable enum class PlotStep(val segment: String) { BOUNDARY("boundary"), DIRECTION("direction"), ROADS("roads") }
@Serializable data class PlotPoint(val x: Double, val y: Double)
@Serializable data class PlotRoad(val side: Int, val name: String = "", val widthMetres: Double? = null, val priority: Int = 0)

/** Measurements and coordinates are always metres; changing display units never changes the site. */
@Serializable data class PlotSetup(
    val lengths: List<Double?> = List(4) { null },
    val vertices: List<PlotPoint> = emptyList(),
    val useDiagonals: Boolean = false,
    val diagonals: List<Double?> = List(1) { null },
    val units: PlotUnits = PlotUnits.FEET_INCHES,
    val northDegrees: Double = 0.0,
    val roads: List<PlotRoad> = emptyList(),
) {
    val areaSquareMetres: Double? get() = vertices.takeIf { it.size == lengths.size && it.size >= 3 }?.let(::polygonArea)
    val areaSquareFeet: Int? get() = areaSquareMetres?.times(SQUARE_FEET_PER_SQUARE_METRE)?.takeIf { it <= Int.MAX_VALUE }?.roundToInt()
    val perimeterMetres: Double? get() = lengths.takeIf { it.all { n -> n != null && n.isFinite() && n > 0 } }?.sumOf { it!! }

    fun errors(completeBoundary: Boolean = false, completeRoads: Boolean = false): Map<String, String> = buildMap {
        if (lengths.size !in 3..12) put("boundary", "Use between 3 and 12 sides.")
        lengths.forEachIndexed { i, length ->
            if (length != null && (!length.isFinite() || length <= 0 || length > 10000)) put("side.$i", "Enter a length greater than 0 and no more than 10,000 m.")
            if (completeBoundary && length == null) put("side.$i", "Enter this side’s length.")
        }
        if (diagonals.size != (lengths.size - 3).coerceAtLeast(0)) put("diagonals", "Check the number of diagonals.")
        if (useDiagonals) diagonals.forEachIndexed { i, length ->
            if (length != null && (!length.isFinite() || length <= 0 || length > 20000)) put("diagonal.$i", "Enter a valid diagonal length.")
            if (completeBoundary && length == null) put("diagonal.$i", "Enter this diagonal’s length.")
        }
        if (!northDegrees.isFinite() || northDegrees !in -180.0..180.0) put("direction", "Choose a direction between −180° and 180°.")
        if (vertices.isNotEmpty()) {
            if (vertices.size != lengths.size || vertices.any { !it.x.isFinite() || !it.y.isFinite() || abs(it.x) > 100000 || abs(it.y) > 100000 } || !simplePolygon(vertices)) {
                put("boundary", "The boundary must be a closed shape without crossing sides.")
            } else {
                lengths.forEachIndexed { i, length ->
                    if (length == null || abs(distance(vertices[i], vertices[(i + 1) % vertices.size]) - length) > 0.002) put("boundary", "The drawing and side measurements must agree.")
                }
                if (useDiagonals && diagonals.size == lengths.size - 3) diagonals.forEachIndexed { i, length ->
                    if (length == null || abs(distance(vertices[0], vertices[i + 2]) - length) > 0.002) put("diagonals", "The drawing and diagonal measurements must agree.")
                }
                if (polygonArea(vertices) * SQUARE_FEET_PER_SQUARE_METRE > Int.MAX_VALUE) put("boundary", "This plot is too large.")
            }
        }
        if (completeBoundary && vertices.isEmpty() && lengths.all { it != null } && (!useDiagonals || diagonals.all { it != null })) put("boundary", "These measurements do not form a boundary yet. Check the side and diagonal lengths.")
        if (roads.size > lengths.size || roads.map { it.side }.distinct().size != roads.size || roads.any { it.side !in lengths.indices }) put("roads", "Select each road-facing side once.")
        if (roads.map { it.priority }.sorted() != roads.indices.toList()) put("roads", "Give every road a different priority.")
        roads.forEach { road ->
            if (road.name.length > 120) put("road.${road.side}.name", "Use a road name up to 120 characters.")
            if (road.widthMetres != null && (!road.widthMetres.isFinite() || road.widthMetres <= 0 || road.widthMetres > 1000)) put("road.${road.side}.width", "Enter a valid road width.")
        }
        if (completeRoads && roads.isEmpty()) put("roads", "Select at least one road-facing side.")
    }

    fun withLength(index: Int, length: Double?): PlotSetup = copy(lengths = lengths.toMutableList().also { it[index] = length }).rebuild()
    fun withDiagonal(index: Int, length: Double?): PlotSetup = copy(diagonals = diagonals.toMutableList().also { it[index] = length }).rebuild()
    fun withSideCount(count: Int): PlotSetup {
        require(count in 3..12)
        return copy(lengths = List(count) { lengths.getOrNull(it) }, diagonals = List(count - 3) { diagonals.getOrNull(it) },
            roads = roads.filter { it.side < min(lengths.size, count) - 1 }.sortedBy { it.priority }.mapIndexed { i, road -> road.copy(priority = i) }).rebuild()
    }
    /** Remove an edge by merging its starting corner into the preceding edge. */
    fun withoutSide(index: Int): PlotSetup {
        require(lengths.size >= 5 && index in lengths.indices)
        val count = lengths.size
        val remainingRoads = roads.filter { it.side != index && it.side != (index+count-1)%count }
            .sortedBy { it.priority }.mapIndexed { priority,road -> road.copy(side=if(road.side>index)road.side-1 else road.side,priority=priority) }
        if(vertices.size==count) {
            val points=vertices.filterIndexed { i,_ -> i!=index }
            if(simplePolygon(points))return copy(roads=remainingRoads).withVertices(points)
        }
        val sides=lengths.filterIndexed { i,_ -> i!=index }.toMutableList()
        sides[(index+sides.size-1)%sides.size]=null
        return copy(lengths=sides,vertices=emptyList(),diagonals=List(count-4){null},roads=remainingRoads)
    }
    fun rebuild(variant: Int = 0): PlotSetup {
        if (lengths.any { it == null || !it.isFinite() || it <= 0 || it > 10000 } || (useDiagonals && diagonals.any { it == null || !it.isFinite() || it <= 0 })) return copy(vertices = emptyList())
        val sides = lengths.map { it!! }
        var result = boundaryFromMeasurements(sides, if (useDiagonals) diagonals.map { it!! } else null, variant) ?: return copy(vertices = emptyList())
        var angle = 0.0
        if (result.size == 4) {
            // Start with the opposite edge level, as in the supplied survey diagram.
            angle = PI - atan2(result[3].y-result[2].y,result[3].x-result[2].x)
        }
        result = result.map { p -> PlotPoint(p.x*cos(angle)-p.y*sin(angle),p.x*sin(angle)+p.y*cos(angle)) }
        return copy(vertices = result)
    }
    fun withVertices(points: List<PlotPoint>) = copy(vertices = points,
        lengths = points.indices.map { distance(points[it], points[(it + 1) % points.size]) },
        diagonals = (2 until points.size - 1).map { distance(points[0], points[it]) })
    fun toggleRoad(side: Int): PlotSetup {
        val next = if (roads.any { it.side == side }) roads.filterNot { it.side == side } else roads + PlotRoad(side, priority = roads.size)
        return copy(roads = next.sortedBy { it.priority }.mapIndexed { i, road -> road.copy(priority = i) })
    }
    fun withRoad(road: PlotRoad) = copy(roads = roads.map { if (it.side == road.side) road else it })
    fun prioritizeRoad(side: Int, priority: Int): PlotSetup {
        val ordered = roads.sortedBy { it.priority }.toMutableList()
        val road = ordered.firstOrNull { it.side == side } ?: return this
        ordered.remove(road); ordered.add(priority.coerceIn(0, ordered.size), road)
        return copy(roads = ordered.mapIndexed { i, item -> item.copy(priority = i) })
    }
}

@Serializable data class SavePlotRequest(val setup: PlotSetup, val revision: Long)
fun distance(a: PlotPoint, b: PlotPoint) = hypot(a.x - b.x, a.y - b.y)
fun polygonArea(points: List<PlotPoint>): Double = abs(points.indices.sumOf { i -> val a = points[i]; val b = points[(i + 1) % points.size]; a.x * b.y - a.y * b.x }) / 2

fun simplePolygon(points: List<PlotPoint>): Boolean {
    if (points.size !in 3..12 || points.any { !it.x.isFinite() || !it.y.isFinite() } || polygonArea(points) < 0.01) return false
    fun cross(a: PlotPoint, b: PlotPoint, c: PlotPoint) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    fun on(a: PlotPoint, b: PlotPoint, p: PlotPoint) = abs(cross(a,b,p)) < 1e-8 && p.x in min(a.x,b.x)-1e-8..max(a.x,b.x)+1e-8 && p.y in min(a.y,b.y)-1e-8..max(a.y,b.y)+1e-8
    for (i in points.indices) {
        val a = points[i]; val b = points[(i + 1) % points.size]
        if (distance(a,b) < 0.001) return false
        for (j in i + 1 until points.size) {
            if (j == (i + 1) % points.size || i == (j + 1) % points.size) continue
            val c = points[j]; val d = points[(j + 1) % points.size]
            if (cross(a,b,c) * cross(a,b,d) < 0 && cross(c,d,a) * cross(c,d,b) < 0 || on(a,b,c) || on(a,b,d) || on(c,d,a) || on(c,d,b)) return false
        }
    }
    return true
}

/** A feasible cyclic shape when only sides are known; fan triangulation when surveyed diagonals are supplied. */
fun boundaryFromMeasurements(lengths: List<Double>, diagonals: List<Double>? = null, variant: Int = 0): List<PlotPoint>? {
    if (lengths.size !in 3..12 || lengths.any { !it.isFinite() || it <= 0 || it > 10000 }) return null
    val longest = lengths.maxOrNull()!!
    if (longest >= lengths.sum() - longest - 1e-6) return null
    fun fan(radii: List<Double>): List<PlotPoint>? {
        var angle = 0.0
        val points = mutableListOf(PlotPoint(0.0,0.0),PlotPoint(lengths[0],0.0))
        var prior = lengths[0]
        for (i in 2 until lengths.size) {
            val radius = if (i == lengths.lastIndex) lengths.last() else radii[i - 2]
            val side = lengths[i - 1]
            if (radius <= 0 || abs(prior-radius) >= side || prior+radius <= side) return null
            angle += acos(((prior*prior+radius*radius-side*side)/(2*prior*radius)).coerceIn(-1.0,1.0))
            if (angle >= 2*PI - 1e-6) return null
            points += PlotPoint(radius*cos(angle),radius*sin(angle)); prior = radius
        }
        return points.takeIf(::simplePolygon)
    }
    if (diagonals != null) return if (diagonals.size == lengths.size - 3 && diagonals.all { it.isFinite() && it > 0 && it <= 20000 }) fan(diagonals) else null
    val longestIndex = lengths.indexOf(longest)
    fun angles(radius: Double) = lengths.map { 2*asin((it/(2*radius)).coerceIn(0.0,1.0)) }
    val major = angles(longest/2).sum() < 2*PI
    fun objective(radius: Double): Double {
        val a = angles(radius)
        return if (major) a.sum() - 2*a[longestIndex] else a.sum() - 2*PI
    }
    var low = longest/2; var high = longest
    repeat(60) { if ((objective(high) < 0) != major) return@repeat; high *= 2 }
    repeat(90) {
        val mid = (low+high)/2
        if ((objective(mid) > 0) != major) low = mid else high = mid
    }
    val radius = (low+high)/2
    val arc = angles(radius).toMutableList()
    if (major) arc[longestIndex] = 2*PI - arc[longestIndex]
    var angle = 0.0
    val circular = arc.map { theta -> PlotPoint(radius*cos(angle),radius*sin(angle)).also { angle += theta } }
    val a = circular[0]; val b = circular[1]
    val turn = -atan2(b.y-a.y,b.x-a.x)
    val points = circular.map { p -> PlotPoint((p.x-a.x)*cos(turn)-(p.y-a.y)*sin(turn),(p.x-a.x)*sin(turn)+(p.y-a.y)*cos(turn)) }
    if (variant != 0 && lengths.size > 3) {
        val radii = (2 until lengths.lastIndex).map { distance(points[0],points[it]) }
        for (factor in listOf(0.9,1.1,0.95,1.05)) fan(radii.map { it*factor })?.let { return it }
    }
    return points.takeIf(::simplePolygon)
}

fun vertexName(index: Int) = ('A'.code + index).toChar().toString()
fun sideName(index: Int, count: Int) = "${vertexName(index)} → ${vertexName((index + 1) % count)}"
fun mirrorBoundary(points: List<PlotPoint>): List<PlotPoint> {
    if(points.size<3)return points
    val a=points[0];val angle=atan2(points[1].y-a.y,points[1].x-a.x)
    return points.map { p ->
        val x=(p.x-a.x)*cos(angle)+(p.y-a.y)*sin(angle)
        val y=-(p.x-a.x)*sin(angle)+(p.y-a.y)*cos(angle)
        PlotPoint(a.x+x*cos(angle)+y*sin(angle),a.y+x*sin(angle)-y*cos(angle))
    }
}
fun roadPriority(index: Int) = when(index) { 0 -> "Primary"; 1 -> "Secondary"; 2 -> "Tertiary"; else -> "Priority ${index + 1}" }
fun decimal(value: Double, places: Int = 2): String {
    val scale = 10.0.pow(places)
    val rounded = round(value*scale).toLong()
    val whole = abs(rounded / scale.toLong())
    val fraction = abs(rounded % scale.toLong()).toString().padStart(places,'0').trimEnd('0')
    val sign = if (rounded < 0) "-" else ""
    return sign + if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
fun measureLabel(metres: Double?, units: PlotUnits): String {
    if (metres == null) return "—"
    if (units == PlotUnits.METRES) return "${decimal(metres)} m"
    val inches = (metres/METRES_PER_FOOT*12).roundToInt()
    return "${inches/12}′ ${inches%12}″"
}
