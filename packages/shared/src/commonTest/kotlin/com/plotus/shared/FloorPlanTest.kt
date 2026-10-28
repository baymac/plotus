package com.plotus.shared

import kotlin.test.*

class FloorPlanTest {
    private val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild()
    @Test fun customSetbacksKeepMeasuredMetresWhenDisplayUnitsChange() {
        val setup=FloorPlanSetup(setbackMode=PlanMode.CUSTOM,commonSetbackMetres=.9144)
        assertTrue(setup.errors(plot,true).isEmpty())
        assertEquals(setup.commonSetbackMetres,setup.copy(units=PlotUnits.METRES).commonSetbackMetres)
        assertEquals("Enter this setback.",setup.copy(commonSetbackMetres=null).errors(plot,true)["setback.all"])
        assertTrue(setup.copy(commonSetbackMetres=Double.NaN).errors(plot).containsKey("setback.all"))
        assertTrue(setup.copy(sameSetback=false,edgeSetbacksMetres=listOf(1.0)).errors(plot,true).containsKey("setbacks"))
    }
    @Test fun reducingHomesKeepsCustomRequirementsForRestoringThem() {
        val two=FloorPlanSetup(buildingFloors=2).withHomeCount(2)
        val home=two.homes[1].custom().copy(notes="Keep this brief",vastu=false)
        val restored=two.withHome(home).withHomeCount(1).withHomeCount(2)
        assertEquals(home,restored.homes[1])
        assertEquals("home-2",restored.homes[1].id)
    }
    @Test fun bedroomsAndFloorAssignmentsMustAgreeBeforeGeneration() {
        val home=PlanHome("home-1",bhk=3,startFloor=1,floorsOccupied=2).custom()
        val setup=FloorPlanSetup(buildingFloors=3,homes=listOf(home))
        assertTrue(setup.errors(plot,true).isEmpty())
        assertTrue(setup.copy(buildingFloors=2).errors(plot,true).containsKey("home.home-1.floors"))
        assertTrue(setup.withHome(home.copy(rooms=home.rooms.drop(1))).errors(plot,true).containsKey("home.home-1.bhk"))
        val outside=home.rooms.first().copy(floor=0)
        assertTrue(setup.withRoom(home.id,outside).errors(plot,true).containsKey("room.${outside.id}"))
        assertTrue(setup.errors(plot.copy(roads=emptyList()),true).containsKey("plot"))
    }
    @Test fun roomOwnershipAndFeatureValidationRejectAmbiguousRequests() {
        val setup=FloorPlanSetup()
        val bedroom=PlanRoom("bedroom-a",RoomKind.BEDROOM)
        assertTrue(setup.copy(sharedRooms=listOf(bedroom)).errors(plot,true).isNotEmpty())
        val wrong=PlanRoom("kitchen-a",RoomKind.KITCHEN,attachedBathroom=true)
        assertTrue(setup.withRoom("home-1",wrong).errors(plot).containsKey("room.kitchen-a"))
        assertFalse(validPlanID("room/../outside"));assertFalse(validPlanID("é"))
        val parking=PlanRoom("parking",RoomKind.PARKING,cars=0,bikes=0)
        assertTrue(setup.withRoom("home-1",parking).errors(plot,true).containsKey("room.parking"))
    }
    @Test fun editingRoomsPreservesOrderAndAddingRoomsAppendsOnce() {
        val home=PlanHome("home-1").custom();val setup=FloorPlanSetup(homes=listOf(home))
        val edited=home.rooms[0].copy(name="Guest room")
        val changed=setup.withRoom(home.id,edited)
        assertEquals(home.rooms.map {it.id},changed.homes[0].rooms.map {it.id})
        assertEquals("Guest room",changed.homes[0].rooms[0].title)
        val extra=PlanRoom("study",RoomKind.OFFICE)
        val added=changed.withRoom(home.id,extra).withRoom(home.id,extra.copy(name="Studio"))
        assertEquals(home.rooms.size+1,added.homes[0].rooms.size)
        assertEquals("Studio",added.homes[0].rooms.last().title)
    }
    @Test fun automaticModeRetainsInactiveCustomRoomFloorsWithoutApplyingThem() {
        val home=PlanHome("home-1").custom()
        val room=home.rooms.first().copy(floor=1)
        val setup=FloorPlanSetup(homes=listOf(home)).withRoom(home.id,room)
        assertTrue(setup.errors(plot,true).containsKey("room.${room.id}"))
        val automatic=setup.withHome(setup.homes[0].copy(mode=PlanMode.AUTO))
        assertTrue(automatic.errors(plot,true).isEmpty())
        assertEquals(room,automatic.homes[0].rooms[0])
    }
}
