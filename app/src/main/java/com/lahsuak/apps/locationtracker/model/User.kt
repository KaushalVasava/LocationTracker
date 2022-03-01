package com.lahsuak.apps.locationtracker.model

class User() {
    var name:String =""
    var uid: String = ""
    var lat: Double = 0.0
    var lng: Double = 0.0
    var phoneNumber: String = ""

    constructor(name: String, uid: String, lat: Double, lng: Double, phoneNumber: String) : this() {
        this.name = name
        this.uid = uid
        this.lat = lat
        this.lng = lng
        this.phoneNumber = phoneNumber
    }
}
