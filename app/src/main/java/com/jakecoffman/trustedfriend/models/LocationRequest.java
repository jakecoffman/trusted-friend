package com.jakecoffman.trustedfriend.models;

import com.google.android.gms.maps.model.LatLng;

import java.util.Date;

public class LocationRequest {
    public String id;
    public String from;
    public String to;
    public LatLng location;
    public String friendlyName;
    public Date requestDate;
}
