#!/usr/bin/python3

import re
import sys
from math import sin,cos,atan2,sqrt
import math
import copy

def hdgDiff(h1, h2):
    r = h1 - h2;
    if (r < 180): r = r + 360
    if (r > 180): r = r - 360
    return r

def distance(p1, p2):
    R = 6371e3
    dlat = (p2.lat - p1.lat) * math.pi / 180.0
    dlon = (p2.lon - p1.lon) * math.pi / 180.0 
        
    a = sin(dlat/2) * sin(dlat/2) + cos(p1.lat) * cos(p2.lat) * sin(dlon/2) * sin(dlon/2)
    c = 2 * atan2(sqrt(a), sqrt(1-a))
    return c * R
    
class Entry: 
    def __init__(self, t, la, lo, sp, hd, bu, w):
        self.tim = float(t)
        self.lat = float(la)
        self.lon = float(lo)
        self.spd = float(sp)
        self.hdg = float(hd)
        self.but = float(bu)
        self.weight = float(w)
        self.crv = 0

    def average(self, e):
        newWeight = self.weight + e.weight
        self.lat = (self.lat * self.weight + e.lat * e.weight) / newWeight
        self.lon = (self.lon * self.weight + e.lon * e.weight) / newWeight
        self.hdg = (self.hdg * self.weight + e.hdg * e.weight) / newWeight
        self.spd = (self.spd * self.weight + e.spd * e.weight) / newWeight
        self.crv = (self.crv * self.weight + e.crv * e.weight) / newWeight
        self.but = (int)(self.but) | (int)(e.but)
        self.weight = newWeight
        
    def getCurve(self, e):
        if e == None:
            return 0 
        timeD = self.tim - e.tim
        hdgD = hdgDiff(self.hdg, e.hdg)
        if timeD * self.spd == 0:
            c = 0
        else:
            c = hdgD * 100000 / (timeD * self.spd);
        c = max(-25, min(25, c));
        return c
      
    def print(self):
        print("lat %+012.8f lon %+012.8f speed %06.2f hdg %07.3f" 
              " gcurve %+08.3f but %04.0f %03.0f" % (
            self.lat, self.lon, self.spd, self.hdg, 
            self.crv, self.but, self.weight))

entries = []

lastEntry = None
for line in sys.stdin:
    tok = re.split("\s+", line)
    m = {}
    for i in range(0, len(tok) - 1, 2):
        m[tok[i]] = tok[i + 1]
    e = Entry(m["t"], m["lat"], m["lon"], m["speed"], m["hdg"], m["but"], 1)
    e.crv = e.getCurve(lastEntry)
    entries.append(e)
    lastEntry = e


entries.sort(key = lambda x : (x.lat, x.hdg, x.lon))

radius = 5
for e in entries:
    i = entries.index(e) + 1
    while i < entries.__len__() - 1 and math.fabs(entries[i].lat - e.lat) * 60 * 1852  <= radius:
        ne = entries[i]
        if distance(ne, e) < radius and math.fabs(hdgDiff(ne.hdg, e.hdg)) < 5:
            e.average(ne)
            entries.remove(ne)
        else:    
            i = i + 1

entries.sort(key = lambda x : (x.lat, x.hdg, x.lon))

for e in entries:
    e.print()


    